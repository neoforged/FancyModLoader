/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import net.neoforged.fml.loading.moddiscovery.MinecraftLocator;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import net.neoforged.fml.loading.toposort.CyclePresentException;
import net.neoforged.fml.loading.toposort.TopologicalSort;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

public class ModSorter
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private final UniqueModListBuilder uniqueModListBuilder;
    private List<ModFile> modFiles;
    private List<ModInfo> sortedList;
    private Map<String, IModInfo> modIdNameLookup;
    private List<ModFile> systemMods;

    private ModSorter(final List<ModFile> modFiles)
    {
        this.uniqueModListBuilder = new UniqueModListBuilder(modFiles);
    }

    public static LoadingModList sort(List<ModFile> mods, final List<EarlyLoadingException.ExceptionData> discoveryError)
    {
        final ModSorter ms = new ModSorter(mods);
        // If a discovery error was encountered, abort
        if (!discoveryError.isEmpty()) {
            return ms.buildSystemMods(new EarlyLoadingException("encountered discovery error", null, discoveryError));
        }

        try {
            ms.buildUniqueList();
        } catch (EarlyLoadingException e) {
            // We cannot build any list with duped mods. We have to abort immediately and report it
            return ms.buildSystemMods(e);
        }

        // try and validate dependencies
        final DependencyResolutionResult resolutionResult = ms.verifyDependencyVersions();

        final LoadingModList list;

        // if we miss a dependency or detect an incompatibility, we abort now
        if (!resolutionResult.versionResolution.isEmpty() || !resolutionResult.incompatibilities.isEmpty()) {
            list = ms.buildSystemMods(new EarlyLoadingException("failure to validate mod list", null, resolutionResult.buildErrorMessages()));
        } else {
            // Otherwise, lets try and sort the modlist and proceed
            EarlyLoadingException earlyLoadingException = null;
            try {
                ms.sort();
            } catch (EarlyLoadingException e) {
                earlyLoadingException = e;
            }
            list = LoadingModList.of(ms.modFiles, ms.sortedList, earlyLoadingException);
        }

        // If we have conflicts those are considered warnings
        if (!resolutionResult.discouraged.isEmpty()) {
            list.getWarnings().add(new EarlyLoadingException(
                    "found mod conflicts",
                    null,
                    resolutionResult.buildWarningMessages()
            ));
        }
        return list;
    }

    public LoadingModList buildSystemMods(EarlyLoadingException exception) {
        return LoadingModList.of(systemMods, systemMods.stream().map(mf -> (ModInfo) mf.getModInfos().get(0)).toList(), exception);
    }

    @SuppressWarnings("UnstableApiUsage")
    private void sort()
    {
        // lambdas are identity based, so sorting them is impossible unless you hold reference to them
        final MutableGraph<ModFileInfo> graph = GraphBuilder.directed().build();
        AtomicInteger counter = new AtomicInteger();
        Map<ModFileInfo, Integer> infos = modFiles.stream()
                .map(ModFile::getModFileInfo)
                .filter(ModFileInfo.class::isInstance)
                .map(ModFileInfo.class::cast)
                .collect(toMap(Function.identity(), e -> counter.incrementAndGet()));
        infos.keySet().forEach(graph::addNode);
        modFiles.stream()
                .map(ModFile::getModInfos)
                .<IModInfo>mapMulti(Iterable::forEach)
                .map(IModInfo::getDependencies)
                .<IModInfo.ModVersion>mapMulti(Iterable::forEach)
                .forEach(dep -> addDependency(graph, dep));

        final List<ModFileInfo> sorted;
        try
        {
            sorted = TopologicalSort.topologicalSort(graph, Comparator.comparing(infos::get));
        }
        catch (CyclePresentException e)
        {
            Set<Set<ModFileInfo>> cycles = e.getCycles();
            if (LOGGER.isErrorEnabled(LogMarkers.LOADING))
            {
                LOGGER.error(LogMarkers.LOADING, "Mod Sorting failed.\nDetected Cycles: {}\n", cycles);
            }
            var dataList = cycles.stream()
                    .<ModFileInfo>mapMulti(Iterable::forEach)
                    .<IModInfo>mapMulti((mf,c)->mf.getMods().forEach(c))
                    .map(IModInfo::getModId)
                    .map(list -> new EarlyLoadingException.ExceptionData("fml.modloading.cycle", list))
                    .toList();
            throw new EarlyLoadingException("Sorting error", e, dataList);
        }
        this.sortedList = sorted.stream()
                .map(ModFileInfo::getMods)
                .<IModInfo>mapMulti(Iterable::forEach)
                .map(ModInfo.class::cast)
                .collect(toList());
        this.modFiles = sorted.stream()
                .map(ModFileInfo::getFile)
                .collect(toList());
    }

    @SuppressWarnings("UnstableApiUsage")
    private void addDependency(MutableGraph<ModFileInfo> topoGraph, IModInfo.ModVersion dep)
    {
        final ModFileInfo self = (ModFileInfo)dep.getOwner().getOwningFile();
        final IModInfo targetModInfo = modIdNameLookup.get(dep.getModId());
        // soft dep that doesn't exist. Just return. No edge required.
        if (targetModInfo == null || !(targetModInfo.getOwningFile() instanceof final ModFileInfo target)) return;
        if (self == target)
            return; // in case a jar has two mods that have dependencies between
        switch (dep.getOrdering()) {
            case BEFORE -> topoGraph.putEdge(self, target);
            case AFTER -> topoGraph.putEdge(target, self);
        }
    }

    private void buildUniqueList()
    {
        final UniqueModListBuilder.UniqueModListData uniqueModListData = uniqueModListBuilder.buildUniqueList();

        this.modFiles = uniqueModListData.modFiles();

        detectSystemMods(uniqueModListData.modFilesByFirstId());

        modIdNameLookup = uniqueModListData.modFilesByFirstId().entrySet().stream()
                .filter(e -> !e.getValue().get(0).getModInfos().isEmpty())
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().get(0).getModInfos().get(0)
                  ));
    }

    private void detectSystemMods(final Map<String, List<ModFile>> modFilesByFirstId)
    {
        // Capture system mods (ex. MC, Forge) here, so we can keep them for later
        final Set<String> systemMods = new HashSet<>();
        // The minecraft mod is always a system mod
        systemMods.add("minecraft");
        // Find mod file from MinecraftLocator to define the system mods
        modFiles.stream()
                .filter(modFile -> modFile.getProvider().getClass() == MinecraftLocator.class)
                .map(ModFile::getSecureJar)
                .map(SecureJar::moduleDataProvider)
                .map(SecureJar.ModuleDataProvider::getManifest)
                .map(Manifest::getMainAttributes)
                .map(mf -> mf.getValue("FML-System-Mods"))
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(value -> systemMods.addAll(Arrays.asList(value.split(","))));
        LOGGER.debug("Configured system mods: {}", systemMods);

        this.systemMods = new ArrayList<>();
        for (String systemMod : systemMods) {
            var container = modFilesByFirstId.get(systemMod);
            if (container != null && !container.isEmpty()) {
                LOGGER.debug("Found system mod: {}", systemMod);
                this.systemMods.add((ModFile) container.get(0));
            } else {
                throw new IllegalStateException("Failed to find system mod: " + systemMod);
            }
        }
    }

    public record DependencyResolutionResult(
            Collection<IModInfo.ModVersion> incompatibilities,
            Collection<IModInfo.ModVersion> discouraged,
            Collection<IModInfo.ModVersion> versionResolution,
            Map<String, ArtifactVersion> modVersions
    ) {
        public List<EarlyLoadingException.ExceptionData> buildWarningMessages() {
            return Stream.concat(discouraged.stream()
                    .map(mv -> new EarlyLoadingException.ExceptionData("fml.modloading.discouragedmod",
                            mv.getOwner(), mv.getModId(), mv.getOwner().getModId(), mv.getVersionRange(),
                            modVersions.get(mv.getModId()), mv.getReason().orElse("fml.modloading.discouragedmod.noreason"))),

                        Stream.of(new EarlyLoadingException.ExceptionData("fml.modloading.discouragedmod.proceed")))
                    .toList();
        }

        public List<EarlyLoadingException.ExceptionData> buildErrorMessages() {
            return Stream.concat(
                            versionResolution.stream()
                                    .map(mv -> new EarlyLoadingException.ExceptionData(mv.getType() == IModInfo.DependencyType.REQUIRED ? "fml.modloading.missingdependency" : "fml.modloading.missingdependency.optional",
                                            mv.getOwner(), mv.getModId(), mv.getOwner().getModId(), mv.getVersionRange(),
                                            modVersions.getOrDefault(mv.getModId(), new DefaultArtifactVersion("null")), mv.getReason())),
                            incompatibilities.stream()
                                    .map(mv -> new EarlyLoadingException.ExceptionData("fml.modloading.incompatiblemod",
                                            mv.getOwner(), mv.getModId(), mv.getOwner().getModId(), mv.getVersionRange(),
                                            modVersions.get(mv.getModId()), mv.getReason().orElse("fml.modloading.incompatiblemod.noreason")))
                    )
                    .toList();
        }
    }

    private DependencyResolutionResult verifyDependencyVersions()
    {
        final var modVersions = modFiles.stream()
                .map(ModFile::getModInfos)
                .<IModInfo>mapMulti(Iterable::forEach)
                .collect(toMap(IModInfo::getModId, IModInfo::getVersion));

        final var modVersionDependencies = modFiles.stream()
                .map(ModFile::getModInfos)
                .<IModInfo>mapMulti(Iterable::forEach)
                .collect(groupingBy(Function.identity(), flatMapping(e -> e.getDependencies().stream(), toList())));

        final var modRequirements = modVersionDependencies.values().stream()
                .<IModInfo.ModVersion>mapMulti(Iterable::forEach)
                .filter(mv -> mv.getSide().isCorrectSide())
                .collect(toSet());

        final long mandatoryRequired = modRequirements.stream().filter(ver -> ver.getType() == IModInfo.DependencyType.REQUIRED).count();
        LOGGER.debug(LogMarkers.LOADING, "Found {} mod requirements ({} mandatory, {} optional)", modRequirements.size(), mandatoryRequired, modRequirements.size() - mandatoryRequired);
        final var missingVersions = modRequirements.stream()
                .filter(mv -> (mv.getType() == IModInfo.DependencyType.REQUIRED || (modVersions.containsKey(mv.getModId()) && mv.getType() == IModInfo.DependencyType.OPTIONAL)) && this.modVersionNotContained(mv, modVersions))
                .collect(toSet());
        final long mandatoryMissing = missingVersions.stream().filter(mv -> mv.getType() == IModInfo.DependencyType.REQUIRED).count();
        LOGGER.debug(LogMarkers.LOADING, "Found {} mod requirements missing ({} mandatory, {} optional)", missingVersions.size(), mandatoryMissing, missingVersions.size() - mandatoryMissing);

        final var incompatibleVersions = modRequirements.stream().filter(ver -> ver.getType() == IModInfo.DependencyType.INCOMPATIBLE)
                .filter(ver -> modVersions.containsKey(ver.getModId()) && !this.modVersionNotContained(ver, modVersions))
                .collect(toSet());

        final var discouragedVersions = modRequirements.stream().filter(ver -> ver.getType() == IModInfo.DependencyType.DISCOURAGED)
                .filter(ver -> modVersions.containsKey(ver.getModId()) && !this.modVersionNotContained(ver, modVersions))
                .collect(toSet());

        if (!discouragedVersions.isEmpty()) {
            LOGGER.error(
                    LogMarkers.LOADING,
                    "Conflicts between mods:\n{}\n\tIssues may arise. Continue at your own risk.",
                    discouragedVersions.stream()
                            .map(ver -> formatIncompatibleDependencyError(ver, "discourages", modVersions))
                            .collect(Collectors.joining("\n"))
            );
        }

        if (mandatoryMissing > 0) {
            LOGGER.error(
                    LogMarkers.LOADING,
                    "Missing or unsupported mandatory dependencies:\n{}",
                    missingVersions.stream()
                            .filter(mv -> mv.getType() == IModInfo.DependencyType.REQUIRED)
                            .map(ver -> formatDependencyError(ver, modVersions))
                            .collect(Collectors.joining("\n"))
            );
        }
        if (missingVersions.size() - mandatoryMissing > 0) {
            LOGGER.error(
                    LogMarkers.LOADING,
                    "Unsupported installed optional dependencies:\n{}",
                    missingVersions.stream()
                            .filter(ver -> ver.getType() == IModInfo.DependencyType.OPTIONAL)
                            .map(ver -> formatDependencyError(ver, modVersions))
                            .collect(Collectors.joining("\n"))
            );
        }

        if (!incompatibleVersions.isEmpty()) {
            LOGGER.error(
                    LogMarkers.LOADING,
                    "Incompatibilities between mods:\n{}",
                    incompatibleVersions.stream()
                            .map(ver -> formatIncompatibleDependencyError(ver, "is incompatible with", modVersions))
                            .collect(Collectors.joining("\n"))
            );
        }

        return new DependencyResolutionResult(incompatibleVersions, discouragedVersions, missingVersions, modVersions);
    }

    private static String formatDependencyError(IModInfo.ModVersion dependency, Map<String, ArtifactVersion> modVersions)
    {
        ArtifactVersion installed = modVersions.get(dependency.getModId());
        return String.format(
                "\tMod ID: '%s', Requested by: '%s', Expected range: '%s', Actual version: '%s'",
                dependency.getModId(),
                dependency.getOwner().getModId(),
                dependency.getVersionRange(),
                installed != null ? installed.toString() : "[MISSING]"
        );
    }

    private static String formatIncompatibleDependencyError(IModInfo.ModVersion dependency, String type, Map<String, ArtifactVersion> modVersions)
    {
        return String.format(
                "\tMod '%s' %s '%s', versions: '%s'; Version found: '%s'",
                dependency.getOwner().getModId(),
                type,
                dependency.getModId(),
                dependency.getVersionRange(),
                modVersions.get(dependency.getModId()).toString()
        );
    }

    private boolean modVersionNotContained(final IModInfo.ModVersion mv, final Map<String, ArtifactVersion> modVersions)
    {
        return !(VersionSupportMatrix.testVersionSupportMatrix(mv.getVersionRange(), mv.getModId(), "mod", (modId, range) -> modVersions.containsKey(modId) &&
                (range.containsVersion(modVersions.get(modId)) || modVersions.get(modId).toString().equals("0.0NONE"))));
    }
}
