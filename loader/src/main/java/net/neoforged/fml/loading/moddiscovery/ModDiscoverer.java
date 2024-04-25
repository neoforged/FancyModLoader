/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery;

import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.JarContents;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.ImmediateWindowHandler;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.fml.loading.UniqueModListBuilder;
import net.neoforged.fml.util.ServiceLoaderUtil;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDependencyLocator;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IModFileReader;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class ModDiscoverer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final List<IModFileCandidateLocator> modFileLocators;
    private final List<IDependencyLocator> dependencyLocators;
    private final List<IModFileReader> modFileReaders;
    private final ILaunchContext launchContext;

    public ModDiscoverer(ILaunchContext launchContext) {
        this(launchContext, List.of());
    }

    public ModDiscoverer(ILaunchContext launchContext,
            Collection<IModFileCandidateLocator> additionalModFileLocators) {
        this.launchContext = launchContext;

        modFileLocators = ServiceLoaderUtil.loadServices(launchContext, IModFileCandidateLocator.class, additionalModFileLocators);
        modFileReaders = ServiceLoaderUtil.loadServices(launchContext, IModFileReader.class);
        dependencyLocators = ServiceLoaderUtil.loadServices(launchContext, IDependencyLocator.class);
    }

    public ModValidator discoverMods() {
        LOGGER.debug(LogMarkers.SCAN, "Scanning for mods and other resources to load. We know {} ways to find mods", modFileLocators.size());
        List<ModFile> loadedFiles = new ArrayList<>();
        List<ModLoadingIssue> discoveryIssues = new ArrayList<>();
        boolean successfullyLoadedMods = true;
        ImmediateWindowHandler.updateProgress("Discovering mod files");

        // Loop all mod locators to get the root mods to load from.
        for (var locator : modFileLocators) {
            LOGGER.debug(LogMarkers.SCAN, "Trying locator {}", locator);

            var defaultAttributes = ModFileDiscoveryAttributes.DEFAULT.withLocator(locator);
            var pipeline = new DiscoveryPipeline(defaultAttributes, loadedFiles, discoveryIssues);
            locator.findCandidates(launchContext, pipeline);

            LOGGER.debug(LogMarkers.SCAN, "Locator {} found {} mods, {} warnings, {} errors and skipped {} candidates", locator,
                    pipeline.successCount, pipeline.warningCount, pipeline.errorCount, pipeline.skipCount);
        }

        //First processing run of the mod list. Any duplicates will cause resolution failure and dependency loading will be skipped.
        Map<IModFile.Type, List<ModFile>> modFilesMap = Collections.emptyMap();
        try {
            final UniqueModListBuilder modsUniqueListBuilder = new UniqueModListBuilder(loadedFiles);
            final UniqueModListBuilder.UniqueModListData uniqueModsData = modsUniqueListBuilder.buildUniqueList();

            //Grab the temporary results.
            //This allows loading to continue to a base state, in case dependency loading fails.
            modFilesMap = uniqueModsData.modFiles().stream()
                    .collect(Collectors.groupingBy(IModFile::getType));
            loadedFiles = uniqueModsData.modFiles();
        } catch (ModLoadingException exception) {
            LOGGER.error(LogMarkers.SCAN, "Failed to build unique mod list after mod discovery.", exception);
            discoveryIssues.addAll(exception.getIssues());
            successfullyLoadedMods = false;
        }

        //We can continue loading if prime mods loaded successfully.
        if (successfullyLoadedMods) {
            LOGGER.debug(LogMarkers.SCAN, "Successfully Loaded {} mods. Attempting to load dependencies...", loadedFiles.size());
            for (var locator : dependencyLocators) {
                try {
                    LOGGER.debug(LogMarkers.SCAN, "Trying locator {}", locator);
                    var pipeline = new DiscoveryPipeline(ModFileDiscoveryAttributes.DEFAULT.withDependencyLocator(locator), loadedFiles, discoveryIssues);
                    locator.scanMods(List.copyOf(loadedFiles), pipeline);
                } catch (ModLoadingException exception) {
                    LOGGER.error(LogMarkers.SCAN, "Failed to load dependencies with locator {}", locator, exception);
                    discoveryIssues.addAll(exception.getIssues());
                }
            }

            //Second processing run of the mod list. Any duplicates will cause resolution failure and only the mods list will be loaded.
            try {
                final UniqueModListBuilder modsAndDependenciesUniqueListBuilder = new UniqueModListBuilder(loadedFiles);
                final UniqueModListBuilder.UniqueModListData uniqueModsAndDependenciesData = modsAndDependenciesUniqueListBuilder.buildUniqueList();

                //We now only need the mod files map, not the list.
                modFilesMap = uniqueModsAndDependenciesData.modFiles().stream()
                        .collect(Collectors.groupingBy(IModFile::getType));
            } catch (ModLoadingException exception) {
                LOGGER.error(LogMarkers.SCAN, "Failed to build unique mod list after dependency discovery.", exception);
                discoveryIssues.addAll(exception.getIssues());
                modFilesMap = loadedFiles.stream().collect(Collectors.groupingBy(IModFile::getType));
            }
        } else {
            //Failure notify the listeners.
            LOGGER.error(LogMarkers.SCAN, "Mod Discovery failed. Skipping dependency discovery.");
        }

        //Validate the loading. With a deduplicated list, we can now successfully process the artifacts and load
        //transformer plugins.
        var validator = new ModValidator(modFilesMap, discoveryIssues);
        validator.stage1Validation();
        return validator;
    }

    private class DiscoveryPipeline implements IDiscoveryPipeline {
        private final ModFileDiscoveryAttributes defaultAttributes;
        private final List<ModFile> loadedFiles;
        private final List<ModLoadingIssue> issues;

        private int successCount;
        private int errorCount;
        private int warningCount;
        private int skipCount;

        public DiscoveryPipeline(ModFileDiscoveryAttributes defaultAttributes,
                List<ModFile> loadedFiles,
                List<ModLoadingIssue> issues) {
            this.defaultAttributes = defaultAttributes;
            this.loadedFiles = loadedFiles;
            this.issues = issues;
        }

        @Override
        public Optional<IModFile> addPath(List<Path> groupedPaths, ModFileDiscoveryAttributes attributes, IncompatibleFileReporting reporting) {
            var primaryPath = groupedPaths.getFirst();

            if (!launchContext.addLocated(primaryPath)) {
                LOGGER.debug("Skipping {} because it was already located earlier", primaryPath);
                skipCount++;
                return Optional.empty();
            }

            JarContents jarContents;
            try {
                jarContents = JarContents.of(groupedPaths);
            } catch (Exception e) {
                addIssue(ModLoadingIssue.error("corrupted_file", groupedPaths).withAffectedPath(primaryPath).withCause(e));
                return Optional.empty();
            }

            return addJarContent(jarContents, attributes, reporting);
        }

        @Override
        public @Nullable IModFile readModFile(JarContents jarContents, ModFileDiscoveryAttributes attributes) {
            for (var reader : modFileReaders) {
                var provided = reader.read(jarContents, attributes);
                if (provided != null) {
                    if (addModFile(provided)) {
                        return provided;
                    }
                    return null;
                }
            }

            throw new RuntimeException("No mod reader felt responsible for " + jarContents.getPrimaryPath());
        }

        @Override
        public Optional<IModFile> addJarContent(JarContents jarContents, ModFileDiscoveryAttributes attributes, IncompatibleFileReporting reporting) {
            for (var reader : modFileReaders) {
                try {
                    var provided = reader.read(jarContents, attributes);
                    if (provided != null) {
                        if (addModFile(provided)) {
                            return Optional.of(provided);
                        }
                        return Optional.empty();
                    }
                } catch (Exception e) {
                    // TODO Translation key
                    addIssue(ModLoadingIssue.error("TECHNICAL_ERROR", reader).withAffectedPath(jarContents.getPrimaryPath()).withCause(e));
                    return Optional.empty();
                }
            }

            // If a jar file was found in a subdirectory of the game directory, but could not be loaded,
            // it might be an incompatible mod type. We do not perform this validation for jars that we
            // found on the classpath or other locations since these are usually not under user control.
            if (reporting == IncompatibleFileReporting.ERROR) {
                addIssue(ModLoadingIssue.error("TECHNICAL_ERROR", jarContents.getPrimaryPath()));
            } else if (reporting == IncompatibleFileReporting.WARN_ON_KNOWN_INCOMPATIBILITY || reporting == IncompatibleFileReporting.WARN_ALWAYS) {
                var reason = IncompatibleModReason.detect(jarContents);
                if (reason.isPresent()) {
                    LOGGER.warn(LogMarkers.SCAN, "Found incompatible jar {} with reason {}. Skipping.", jarContents.getPrimaryPath(), reason.get());
                    addIssue(ModLoadingIssue.warning(reason.get().getReason(), jarContents.getPrimaryPath()).withAffectedPath(jarContents.getPrimaryPath()));
                } else if (reporting == IncompatibleFileReporting.WARN_ALWAYS) {
                    LOGGER.warn(LogMarkers.SCAN, "Ignoring incompatible jar {} for an unknown reason.", jarContents.getPrimaryPath());
                    addIssue(ModLoadingIssue.warning("fml.modloading.brokenfile", jarContents.getPrimaryPath()).withAffectedPath(jarContents.getPrimaryPath()));
                }
            }
            return Optional.empty();
        }

        @Override
        public boolean addModFile(IModFile mf) {
            if (!(mf instanceof ModFile modFile)) {
                // TODO: Translation
                addIssue(ModLoadingIssue.error("locator returned custom implementation of ModFile", defaultAttributes, mf.getClass()));
                return false;
            }

            var discoveryAttributes = mf.getDiscoveryAttributes();
            LOGGER.info(LogMarkers.SCAN, "Found mod file \"{}\" of type {} {}", mf.getFileName(), mf.getType(), discoveryAttributes);

            loadedFiles.add(modFile);
            successCount++;
            return true;
        }

        @Override
        public void addIssue(ModLoadingIssue issue) {
            issues.add(issue);
            switch (issue.severity()) {
                case WARNING -> warningCount++;
                case ERROR -> errorCount++;
            }
        }
    }
}
