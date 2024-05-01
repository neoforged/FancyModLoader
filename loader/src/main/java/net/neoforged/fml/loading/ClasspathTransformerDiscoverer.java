/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static net.neoforged.fml.loading.TransformerDiscovererConstants.shouldLoadInServiceLayer;

import cpw.mods.modlauncher.api.NamedPath;
import cpw.mods.modlauncher.serviceapi.ITransformerDiscoveryService;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import net.neoforged.fml.loading.targets.CommonLaunchHandler;
import org.apache.logging.log4j.LogManager;

public class ClasspathTransformerDiscoverer implements ITransformerDiscoveryService {
    private final List<Path> legacyClasspath = Arrays.stream(System.getProperty("legacyClassPath", "").split(File.pathSeparator)).map(Path::of).toList();

    @Override
    public List<NamedPath> candidates(Path gameDirectory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<NamedPath> candidates(final Path gameDirectory, final String launchTarget) {
        if (launchTarget != null && launchTarget.contains("dev")) {
            return scan();
        }
        return List.of();
    }

    private List<NamedPath> scan() {
        try {
            var result = new HashSet<NamedPath>();
            for (var serviceClass : TransformerDiscovererConstants.SERVICES) {
                locateTransformers("META-INF/services/" + serviceClass, result);
            }

            scanModClasses(result);
            return new ArrayList<>(result);
        } catch (IOException e) {
            LogManager.getLogger().error("Error during discovery of transform services from the classpath", e);
            return List.of();
        }
    }

    private void locateTransformers(String resource, Collection<NamedPath> result) throws IOException {
        final Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(resource);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            Path path = ClasspathLocatorUtils.findJarPathFor(resource, url.toString(), url);
            if (legacyClasspath.stream().anyMatch(path::equals) || !Files.exists(path) || Files.isDirectory(path))
                continue;
            result.add(new NamedPath(path.toUri().toString(), path));
        }
    }

    private void scanModClasses(Collection<NamedPath> result) {
        var modClassPaths = CommonLaunchHandler.getGroupedModFolders();
        for (var entry : modClassPaths.entrySet()) {
            String modid = entry.getKey();
            List<Path> paths = entry.getValue();
            if (shouldLoadInServiceLayer(paths)) {
                result.add(new NamedPath(modid, paths.toArray(Path[]::new)));
            }
        }
    }
}
