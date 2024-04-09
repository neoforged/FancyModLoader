/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.JarContents;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import net.neoforged.fml.loading.ClasspathLocatorUtils;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.LoadResult;
import org.slf4j.Logger;

public class UserdevClasspathLocator implements IModFileCandidateLocator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final List<Path> legacyClasspath = JarLocatorUtils.getLegacyClasspath();

    @Override
    public String name() {
        return "userdev classpath";
    }

    @Override
    public Stream<LoadResult<JarContents>> findCandidates(ILaunchContext context) {
        try {
            var claimed = new ArrayList<>(legacyClasspath);
            var paths = Stream.<Path>builder();

            findPaths(claimed, JarModsDotTomlModFileReader.MODS_TOML).forEach(paths::add);
            findPaths(claimed, JarModsDotTomlModFileReader.MANIFEST).forEach(paths::add);

            return paths.build().map(IModFileCandidateLocator::result);
        } catch (IOException e) {
            LOGGER.error(LogMarkers.SCAN, "Error trying to find resources", e);
            throw new RuntimeException(e);
        }
    }

    private List<Path> findPaths(List<Path> claimed, String resource) throws IOException {
        var ret = new ArrayList<Path>();
        final Enumeration<URL> resources = ClassLoader.getSystemClassLoader().getResources(resource);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            Path path = ClasspathLocatorUtils.findJarPathFor(resource, resource, url);
            if (claimed.stream().anyMatch(path::equals) || !Files.exists(path) || Files.isDirectory(path))
                continue;
            ret.add(path);
        }
        return ret;
    }
}
