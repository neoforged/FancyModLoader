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
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.loading.targets.CommonLaunchHandler;
import org.apache.logging.log4j.LogManager;

public class ClasspathTransformerDiscoverer implements ITransformerDiscoveryService {
    private final List<Path> legacyClasspath = Arrays.stream(System.getProperty("legacyClassPath", "").split(File.pathSeparator)).map(Path::of).toList();

    @Override
    public List<NamedPath> candidates(Path gameDirectory) {
        return Collections.emptyList();
    }

    @Override
    public List<NamedPath> candidates(final Path gameDirectory, final String launchTarget) {
        if (launchTarget != null && launchTarget.contains("dev")) {
            this.scan(gameDirectory);
        }
        return List.copyOf(found);
    }

    private final static List<NamedPath> found = new ArrayList<>();

    public static List<Path> allExcluded() {
        return found.stream().map(np -> np.paths()[0]).toList();
    }

    private void scan(final Path gameDirectory) {
        try {
            for (final String serviceClass : TransformerDiscovererConstants.SERVICES) {
                locateTransformers("META-INF/services/" + serviceClass);
            }

            scanModClasses();
        } catch (IOException e) {
            LogManager.getLogger().error("Error during discovery of transform services from the classpath", e);
        }
    }

    private void locateTransformers(String resource) throws IOException {
        final Enumeration<URL> resources = ClassLoader.getSystemClassLoader().getResources(resource);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            Path path = ClasspathLocatorUtils.findJarPathFor(resource, url.toString(), url);
            if (legacyClasspath.stream().anyMatch(path::equals) || !Files.exists(path) || Files.isDirectory(path))
                continue;
            found.add(new NamedPath(path.toUri().toString(), path));
        }
    }

    private void scanModClasses() {
        final Map<String, List<Path>> modClassPaths = CommonLaunchHandler.getModClasses();
        modClassPaths.forEach((modid, paths) -> {
            if (shouldLoadInServiceLayer(paths.toArray(Path[]::new))) {
                found.add(new NamedPath(modid, paths.toArray(Path[]::new)));
            }
        });
    }
}
