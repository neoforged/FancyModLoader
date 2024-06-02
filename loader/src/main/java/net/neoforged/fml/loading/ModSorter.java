/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import net.neoforged.fml.loading.toposort.CyclePresentException;
import net.neoforged.fml.loading.toposort.TopologicalSort;
import net.neoforged.neoforgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.slf4j.Logger;

public class ModSorter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final UniqueModListBuilder uniqueModListBuilder;
    private List<ModFile> modFiles;
    private List<ModInfo> sortedList;
    private Map<ModInfo, List<ModInfo>> modDependencies;
    private Map<String, IModInfo> modIdNameLookup;
    private List<ModFile> systemMods;

    private ModSorter(final List<ModFile> modFiles) {
        this.uniqueModListBuilder = new UniqueModListBuilder(modFiles);
    }

    public static LoadingModList sort(List<ModFile> plugins, List<ModFile> mods, final List<ModLoadingIssue> issues) {
        final ModSorter ms = new ModSorter(mods);
        try {
            ms.buildUniqueList();
        } catch (ModLoadingException e) {
            // We cannot build any list with duped mods. We have to abort immediately and report it
            return LoadingModList.of(plugins, ms.systemMods, ms.systemMods.stream().map(mf -> (ModInfo) mf.getModInfos().get(0)).collect(toList()), concat(issues, e.getIssues()), Map.of());
        }

        // try and validate dependencies
        final DependencyResolutionResult resolutionResult = ms.verifyDependencyVersions();

        final LoadingModList list;

        // if we miss a dependency or detect an incompatibility, we abort now
        if (!resolutionResult.versionResolution.isEmpty() || !resolutionResult.incompatibilities.isEmpty()) {
            list = LoadingModList.of(plugins, ms.systemMods, ms.systemMods.stream().map(mf -> (ModInfo) mf.getModInfos().get(0)).collect(toList()), concat(issues, resolutionResult.buildErrorMessages()), Map.of());
        } else {
            // Otherwise, lets try and sort the modlist and proceed
            ModLoadingException modLoadingException = null;
            try {
                ms.sort();
            } catch (ModLoadingException e) {
                modLoadingException = e;
            }
            if (modLoadingException == null) {
                list = LoadingModList.of(plugins, ms.modFiles, ms.sortedList, issues, ms.modDependencies);
            } else {
                list = LoadingModList.of(plugins, ms.modFiles, ms.sortedList, concat(issues, modLoadingException.getIssues()), Map.of());
            }
        }

        // If we have conflicts those are considered warnings
        if (!resolutionResult.discouraged.isEmpty()) {
            list.getModLoadingIssues().add(ModLoadingIssue.warning(
                    "found mod conflicts",
                    resolutionResult.buildWarningMessages()));
        }
        return list;
    }

    @SafeVarargs
    private static <T> List<T> concat(List<T>... lists) {
        var lst = new ArrayList<T>();
        for (List<T> list : lists) {
            lst.addAll(list);
        }
        return lst;
    }

    @SuppressWarnings("UnstableApiUsage")
    private void sort() {
        // lambdas are identity based, so sorting them is impossible unless you hold reference to them
        final MutableGraph<ModInfo> graph = GraphBuilder.directed().build();
        AtomicInteger counter = new AtomicInteger();
        Map<ModInfo, Integer> infos = modFiles.stream()
                .flatMap(mf -> mf.getModInfos().stream())
                .map(ModInfo.class::cast)
                .collect(toMap(Function.identity(), e -> counter.incrementAndGet()));
        infos.keySet().forEach(graph::addNode);
        modFiles.stream()
                .map(ModFile::getModInfos)
                .<IModInfo>mapMulti(Iterable::forEach)
                .map(IModInfo::getDependencies).<IModInfo.ModVersion>mapMulti(Iterable::forEach)
                .forEach(dep -> addDependency(graph, dep));

        final List<ModInfo> sorted;
        try {
            sorted = TopologicalSort.topologicalSort(graph, Comparator.comparing(infos::get));
        } catch (CyclePresentException e) {
            Set<Set<ModFileInfo>> cycles = e.getCycles();
            if (LOGGER.isErrorEnabled(LogMarkers.LOADING)) {
                LOGGER.error(LogMarkers.LOADING, "Mod Sorting failed.\nDetected Cycles: {}\n", cycles);
            }
            var dataList = cycles.stream()
                    .<ModFileInfo>mapMulti(Iterable::forEach)
                    .<IModInfo>mapMulti((mf, c) -> mf.getMods().forEach(c))
                    .map(IModInfo::getModId)
                    .map(list -> ModLoadingIssue.error("fml.modloading.cycle", list).withCause(e))
                    .toList();
            throw new ModLoadingException(dataList);
        }
        this.sortedList = List.copyOf(sorted);
        this.modDependencies = sorted.stream()
                .collect(Collectors.toMap(modInfo -> modInfo, modInfo -> List.copyOf(graph.predecessors(modInfo))));
        this.modFiles = sorted.stream()
                .map(mi -> mi.getOwningFile().getFile())
                .distinct()
                .toList();
    }

    @SuppressWarnings("UnstableApiUsage")
    private void addDependency(MutableGraph<ModInfo> topoGraph, IModInfo.ModVersion dep) {
        final ModInfo self = (ModInfo) dep.getOwner();
        final IModInfo targetModInfo = modIdNameLookup.get(dep.getModId());
        // soft dep that doesn't exist. Just return. No edge required.
        if (!(targetModInfo instanceof ModInfo target)) return;
        if (self == target)
            return; // in case a jar has two mods that have dependencies between
        switch (dep.getOrdering()) {
            case BEFORE -> topoGraph.putEdge(self, target);
            case AFTER -> topoGraph.putEdge(target, self);
        }
    }

    private void buildUniqueList() {
        final UniqueModListBuilder.UniqueModListData uniqueModListData = uniqueModListBuilder.buildUniqueList();

        this.modFiles = uniqueModListData.modFiles();

        detectSystemMods(uniqueModListData.modFilesByFirstId());

        modIdNameLookup = uniqueModListData.modFiles().stream()
                .flatMap(mf -> mf.getModInfos().stream())
                .collect(Collectors.toMap(IModInfo::getModId, mi -> mi));
    }

    private void detectSystemMods(final Map<String, List<ModFile>> modFilesByFirstId) {
        // Capture system mods (ex. MC, Forge) here, so we can keep them for later
        final Set<String> systemMods = new HashSet<>();
        // The minecraft and neoforge mods are always system mods
        systemMods.add("minecraft");
        systemMods.add("neoforge");
        // Find system mod files and scan them for system mods
        modFiles.stream()
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
                this.systemMods.add(container.getFirst());
            }
        }
    }

    public record DependencyResolutionResult(
            Collection<IModInfo.ModVersion> incompatibilities,
            Collection<IModInfo.ModVersion> discouraged,
            Collection<IModInfo.ModVersion> versionResolution,
            Map<String, ArtifactVersion> modVersions) {
        public List<ModLoadingIssue> buildWarningMessages() {
            return Stream.concat(discouraged.stream()
                    .map(mv -> ModLoadingIssue.warning("fml.modloading.discouragedmod",
                            mv.getModId(), mv.getOwner().getModId(), mv.getVersionRange(),
                            modVersions.get(mv.getModId()), mv.getReason().orElse("fml.modloading.discouragedmod.noreason")).withAffectedMod(mv.getOwner())),

                    Stream.of(ModLoadingIssue.warning("fml.modloading.discouragedmod.proceed")))
                    .toList();
        }

        public List<ModLoadingIssue> buildErrorMessages() {
            return Stream.concat(
                    versionResolution.stream()
                            .map(mv -> ModLoadingIssue.error(mv.getType() == IModInfo.DependencyType.REQUIRED ? "fml.modloading.missingdependency" : "fml.modloading.missingdependency.optional",
                                    mv.getModId(), mv.getOwner().getModId(), mv.getVersionRange(),
                                    modVersions.getOrDefault(mv.getModId(), new DefaultArtifactVersion("null")), mv.getReason()).withAffectedMod(mv.getOwner())),
                    incompatibilities.stream()
                            .map(mv -> ModLoadingIssue.error("fml.modloading.incompatiblemod",
                                    mv.getModId(), mv.getOwner().getModId(), mv.getVersionRange(),
                                    modVersions.get(mv.getModId()), mv.getReason().orElse("fml.modloading.incompatiblemod.noreason")).withAffectedMod(mv.getOwner())))
                    .toList();
        }
    }

    private DependencyResolutionResult verifyDependencyVersions() {
        final var modVersions = modFiles.stream()
                .map(ModFile::getModInfos)
                .<IModInfo>mapMulti(Iterable::forEach)
                .collect(toMap(IModInfo::getModId, IModInfo::getVersion));

        final var modVersionDependencies = modFiles.stream()
                .map(ModFile::getModInfos)
                .<IModInfo>mapMulti(Iterable::forEach)
                .collect(groupingBy(Function.identity(), flatMapping(e -> e.getDependencies().stream(), toList())));

        final var modRequirements = modVersionDependencies.values().stream().<IModInfo.ModVersion>mapMulti(Iterable::forEach)
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
                            .collect(Collectors.joining("\n")));
        }

        if (mandatoryMissing > 0) {
            LOGGER.error(
                    LogMarkers.LOADING,
                    "Missing or unsupported mandatory dependencies:\n{}",
                    missingVersions.stream()
                            .filter(mv -> mv.getType() == IModInfo.DependencyType.REQUIRED)
                            .map(ver -> formatDependencyError(ver, modVersions))
                            .collect(Collectors.joining("\n")));
        }
        if (missingVersions.size() - mandatoryMissing > 0) {
            LOGGER.error(
                    LogMarkers.LOADING,
                    "Unsupported installed optional dependencies:\n{}",
                    missingVersions.stream()
                            .filter(ver -> ver.getType() == IModInfo.DependencyType.OPTIONAL)
                            .map(ver -> formatDependencyError(ver, modVersions))
                            .collect(Collectors.joining("\n")));
        }

        if (!incompatibleVersions.isEmpty()) {
            LOGGER.error(
                    LogMarkers.LOADING,
                    "Incompatibilities between mods:\n{}",
                    incompatibleVersions.stream()
                            .map(ver -> formatIncompatibleDependencyError(ver, "is incompatible with", modVersions))
                            .collect(Collectors.joining("\n")));
        }

        return new DependencyResolutionResult(incompatibleVersions, discouragedVersions, missingVersions, modVersions);
    }

    private static String formatDependencyError(IModInfo.ModVersion dependency, Map<String, ArtifactVersion> modVersions) {
        ArtifactVersion installed = modVersions.get(dependency.getModId());
        return String.format(
                "\tMod ID: '%s', Requested by: '%s', Expected range: '%s', Actual version: '%s'",
                dependency.getModId(),
                dependency.getOwner().getModId(),
                dependency.getVersionRange(),
                installed != null ? installed.toString() : "[MISSING]");
    }

    private static String formatIncompatibleDependencyError(IModInfo.ModVersion dependency, String type, Map<String, ArtifactVersion> modVersions) {
        return String.format(
                "\tMod '%s' %s '%s', versions: '%s'; Version found: '%s'",
                dependency.getOwner().getModId(),
                type,
                dependency.getModId(),
                dependency.getVersionRange(),
                modVersions.get(dependency.getModId()).toString());
    }

    private boolean modVersionNotContained(final IModInfo.ModVersion mv, final Map<String, ArtifactVersion> modVersions) {
        return !(VersionSupportMatrix.testVersionSupportMatrix(mv.getVersionRange(), mv.getModId(), "mod", (modId, range) -> modVersions.containsKey(modId) &&
                (range.containsVersion(modVersions.get(modId)) || modVersions.get(modId).toString().equals("0.0NONE"))));
    }
}
