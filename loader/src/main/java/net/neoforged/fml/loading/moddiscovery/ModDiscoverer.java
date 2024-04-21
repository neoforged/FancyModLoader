/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery;

import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.JarContents;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.ImmediateWindowHandler;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.fml.loading.UniqueModListBuilder;
import net.neoforged.fml.util.ServiceLoaderUtil;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDependencyLocator;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IModFileProvider;
import net.neoforged.neoforgespi.locating.IModFileReader;
import net.neoforged.neoforgespi.locating.LoadResult;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class ModDiscoverer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final List<IModFileCandidateLocator> modFileLocators;
    private final List<IDependencyLocator> dependencyLocators;
    private final List<IModFileReader> modFileReaders;
    private final List<IModFileProvider> modFileProviders;
    private final ILaunchContext launchContext;

    public ModDiscoverer(ILaunchContext launchContext) {
        this(launchContext, List.of(), List.of());
    }

    public ModDiscoverer(ILaunchContext launchContext,
            Collection<IModFileCandidateLocator> additionalModFileLocators,
            Collection<IModFileProvider> additionalModFileProviders) {
        this.launchContext = launchContext;

        modFileLocators = ServiceLoaderUtil.loadServices(launchContext, IModFileCandidateLocator.class, additionalModFileLocators);
        modFileReaders = ServiceLoaderUtil.loadServices(launchContext, IModFileReader.class);
        modFileProviders = ServiceLoaderUtil.loadServices(launchContext, IModFileProvider.class, additionalModFileProviders);
        dependencyLocators = ServiceLoaderUtil.loadServices(launchContext, IDependencyLocator.class);
    }

    public ModValidator discoverMods() {
        LOGGER.debug(LogMarkers.SCAN, "Scanning for mods and other resources to load. We know {} ways to find mods", modFileLocators.size());
        List<ModFile> loadedFiles = new ArrayList<>();
        List<ModLoadingIssue> discoveryIssues = new ArrayList<>();
        boolean successfullyLoadedMods = true;
        ImmediateWindowHandler.updateProgress("Discovering mod files");
        // Provide implicitly loaded mods
        for (var provider : modFileProviders) {
            for (var loadResult : provider.provideModFiles(launchContext)) {
                handleModFileLoadResult(loadResult, loadedFiles, discoveryIssues);
            }
        }
        LOGGER.info("Found {} mod files through direct providers", loadedFiles.size());

        // Now search for mods in file system locations
        for (var candidate : locateCandidates(discoveryIssues)) {
            var loadResult = readModFile(candidate, null);
            if (loadResult != null) {
                handleModFileLoadResult(loadResult, loadedFiles, discoveryIssues);
            } else if (candidate.getPrimaryPath().startsWith(FMLLoader.getGamePath())) {
                // If a jar file was found in a subdirectory of the game directory, but could not be loaded,
                // it might be an incompatible mod type. We do not perform this validation for jars that we
                // found on the classpath or other locations since these are usually not under user control.
                var reason = IncompatibleModReason.detect(candidate);
                if (reason.isPresent()) {
                    LOGGER.warn(LogMarkers.SCAN, "Found incompatible jar {} with reason {}. Skipping.", candidate.getPrimaryPath(), reason.get());
                    discoveryIssues.add(ModLoadingIssue.warning(reason.get().getReason(), candidate.getPrimaryPath()).withAffectedPath(candidate.getPrimaryPath()));
                } else {
                    LOGGER.warn(LogMarkers.SCAN, "Ignoring incompatible jar {} for an unknown reason.", candidate.getPrimaryPath());
                    discoveryIssues.add(ModLoadingIssue.warning("fml.modloading.brokenfile", candidate.getPrimaryPath()).withAffectedPath(candidate.getPrimaryPath()));
                }
            }
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
                    var additionalDependencies = locator.scanMods(List.copyOf(loadedFiles), this::readModFile);
                    for (var dependency : additionalDependencies) {
                        handleModFileLoadResult(new LoadResult.Success<>(dependency), loadedFiles, discoveryIssues);
                    }
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

    private static void handleModFileLoadResult(LoadResult<IModFile> loadResult, List<ModFile> loadedFiles, List<ModLoadingIssue> discoveryErrorData) {
        switch (loadResult) {
            case LoadResult.Success<IModFile>(ModFile mf) -> {
                if (mf.getParent() != null) {
                    LOGGER.info(LogMarkers.SCAN, "Found mod file \"{}\" of type {} with provider {} contained in {}", mf.getFileName(), mf.getType(), mf.getSource(), mf.getParent().getFileName());
                } else {
                    LOGGER.info(LogMarkers.SCAN, "Found mod file \"{}\" of type {} with provider {}", mf.getFileName(), mf.getType(), mf.getSource());
                }
                loadedFiles.add(mf);
            }
            case LoadResult.Success<IModFile>(IModFile modFile) -> {
                // It's a fatal technical error if a locator subclasses IModFile itself
                throw new IllegalStateException("Locator returned custom subclass of IModFile: " + modFile);
            }
            case LoadResult.Error<IModFile>(var error) -> discoveryErrorData.add(error);
        }
    }

    private List<JarContents> locateCandidates(List<ModLoadingIssue> discoveryErrorData) {
        // Loop all mod locators to get the root mods to load from.
        List<JarContents> candidates = new ArrayList<>();
        for (var locator : modFileLocators) {
            LOGGER.debug(LogMarkers.SCAN, "Trying locator {}", locator.name());
            try (var candidateStream = locator.findCandidates(launchContext)) {
                var successCount = 0;
                var errorCount = 0;
                for (var candidate : candidateStream.toList()) {
                    switch (candidate) {
                        case LoadResult.Success<JarContents>(var jarContents) -> {
                            if (!launchContext.addLocated(jarContents.getPrimaryPath())) {
                                LOGGER.info("Skipping {} because it was already located earlier", jarContents.getPrimaryPath());
                                continue;
                            }

                            successCount++;
                            candidates.add(jarContents);
                        }
                        case LoadResult.Error<JarContents>(var exceptionData) -> {
                            errorCount++;
                            discoveryErrorData.add(exceptionData);
                        }
                    }
                }
                LOGGER.debug(LogMarkers.SCAN, "Locator {} found {} candidates and {} errors", locator.name(), successCount, errorCount);
            }
        }
        return candidates;
    }

    private LoadResult<IModFile> readModFile(JarContents jarContents, @Nullable IModFile parent) {
        for (var reader : modFileReaders) {
            try {
                var provided = reader.read(jarContents, parent);
                if (provided != null) {
                    return provided;
                }
            } catch (Exception e) {
                // TODO Translation key
                return new LoadResult.Error<>(ModLoadingIssue.error("TECHNICAL_ERROR", reader));
            }
        }
        return null;
    }
}
