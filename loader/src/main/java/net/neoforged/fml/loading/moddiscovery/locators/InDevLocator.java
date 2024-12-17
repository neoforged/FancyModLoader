/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import cpw.mods.jarhandling.JarContents;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This locator finds mods and services that are passed via the classpath and are grouped explicitly.
 */
public class InDevLocator implements IModFileCandidateLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(InDevLocator.class);

    private static final String VIRTUAL_JAR_MANIFEST_PATH = "build/virtualJarManifest.properties";

    final Set<File> searchedDirectories = new HashSet<>();
    private final Map<File, VirtualJarManifestEntry> virtualJarMemberIndex = new HashMap<>();
    private boolean manifestLoaded;

    record VirtualJarManifestEntry(String name, List<File> files) {}

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        // Prioritize the "old" way of specifying a grouping
        loadFromSystemProperty();

        // Try to find the groupings based on CWD
        try {
            attemptToFindManifest(new File(".").getAbsoluteFile());
        } catch (IOException e) {
            pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.technical_error").withCause(e));
            return;
        }

        var groupedEntries = new IdentityHashMap<VirtualJarManifestEntry, List<File>>(virtualJarMemberIndex.size());

        for (var file : context.getUnclaimedClassPathEntries()) {
            var virtualJarSpec = virtualJarMemberIndex.get(file);
            if (virtualJarSpec != null) {
                var contentList = groupedEntries.computeIfAbsent(virtualJarSpec, k -> new ArrayList<>());
                contentList.add(file);
            }
        }

        for (var entry : groupedEntries.entrySet()) {
            var locations = entry.getValue();
            var paths = new ArrayList<Path>(locations.size());
            for (var location : locations) {
                var path = location.toPath();
                context.addLocated(path);
                paths.add(path);
            }
            pipeline.addJarContent(JarContents.of(paths), ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.ERROR);
        }

        // Add groups that remain but are not on the classpath at all to support legacy configurations
        // TODO: Decide whether to keep this or not
        for (var entry : new HashSet<>(virtualJarMemberIndex.values())) {
            var paths = entry.files.stream().map(File::toPath).toList();
            if (paths.stream().noneMatch(context::isLocated)) {
                pipeline.addJarContent(JarContents.of(paths), ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.ERROR);
            }
        }
    }

    public void attemptToFindManifest(File file) throws IOException {
        if (manifestLoaded) {
            return;
        }

        if (file.isDirectory() && searchedDirectories.add(file)) {
            var groupingsFile = new File(file, VIRTUAL_JAR_MANIFEST_PATH);

            if (groupingsFile.exists() && loadVirtualJarManifest(groupingsFile)) {
                return;
            }
        }

        var parent = file.getParentFile();
        if (parent != null) {
            attemptToFindManifest(parent);
        }
    }

    private boolean loadVirtualJarManifest(File manifestFile) throws IOException {
        Properties p = new Properties();
        try (var input = new BufferedReader(new InputStreamReader(new FileInputStream(manifestFile)))) {
            p.load(input);
        } catch (FileNotFoundException ignored) {
            return false;
        }

        LOGGER.info("Loading Virtual Jar manifest from {}", manifestFile);

        for (var virtualJarId : p.stringPropertyNames()) {
            var paths = p.getProperty(virtualJarId).split(File.pathSeparator);
            var files = new ArrayList<File>(paths.length);
            for (String path : paths) {
                files.add(new File(path));
            }

            var entry = new VirtualJarManifestEntry(virtualJarId, files);
            for (var containedFile : files) {
                virtualJarMemberIndex.put(containedFile, entry);
            }
        }
        manifestLoaded = true;
        return true;
    }

    private void loadFromSystemProperty() {
        var modFolders = Optional.ofNullable(System.getenv("MOD_CLASSES"))
                .orElse(System.getProperty("fml.modFolders", ""));
        if (!modFolders.isEmpty()) {
            LOGGER.info(LogMarkers.CORE, "Got mod coordinates {} from env", modFolders);
            // "a/b/;c/d/;" ->"modid%%c:\fish\pepper;modid%%c:\fish2\pepper2\;modid2%%c:\fishy\bums;modid2%%c:\hmm"
            var groupedEntries = Arrays.stream(modFolders.split(File.pathSeparator))
                    .collect(Collectors.groupingBy(
                            inp -> {
                                var splitIdx = inp.indexOf("%%");
                                if (splitIdx != -1) {
                                    return inp.substring(0, splitIdx);
                                } else {
                                    return "defaultmodid";
                                }
                            },
                            Collectors.mapping(inp -> {
                                var splitIdx = inp.indexOf("%%");
                                if (splitIdx != -1) {
                                    inp = inp.substring(splitIdx + "%%".length());
                                }
                                return new File(inp);
                            }, Collectors.toList())));
            for (var group : groupedEntries.entrySet()) {
                var virtualJar = new VirtualJarManifestEntry(
                        group.getKey(),
                        group.getValue());
                for (var file : group.getValue()) {
                    virtualJarMemberIndex.put(file, virtualJar);
                }
            }
        }
    }

    @Override
    public int getPriority() {
        // We need to get the explicitly grouped items out of the way before anyone else claims them
        return HIGHEST_SYSTEM_PRIORITY;
    }

    @Override
    public String toString() {
        return "indev";
    }
}
