/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.neoforged.fml.classloading.JarContentsModule;
import net.neoforged.fml.classloading.ResourceMaskingClassLoader;
import net.neoforged.fml.jarcontents.CompositeJarContents;
import net.neoforged.fml.jarcontents.EmptyJarContents;
import net.neoforged.fml.jarcontents.FolderJarContents;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.jarcontents.JarFileContents;
import net.neoforged.fml.util.ClasspathResourceUtils;
import net.neoforged.fml.util.PathPrettyPrinting;
import net.neoforged.neoforgespi.LocatedPaths;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClassLoaderStack implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClassLoaderStack.class);

    private final List<AutoCloseable> ownedResources = new ArrayList<>();
    private final LocatedPaths locatedPaths;
    /**
     * The context class-loader that will be restored when the loader is closed.
     */
    @Nullable
    private final ClassLoader originalClassLoader;
    /**
     * The current tail of the class-loader chain. It is moved whenever a new set of Jars is loaded.
     */
    private ClassLoader currentClassLoader;

    public ClassLoaderStack(ClassLoader initialLoader, LocatedPaths locatedPaths) {
        this.currentClassLoader = initialLoader;
        this.locatedPaths = locatedPaths;
        this.originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(currentClassLoader);
    }

    /**
     * Loads the given services into a URL classloader.
     */
    public void appendLoader(String loaderName, List<JarContents> jars) {
        if (jars.isEmpty()) {
            LOGGER.info("No additional classpath items for {} were found.", loaderName);
            return;
        }

        LOGGER.info("Loading {}:", loaderName);

        List<URL> rootUrls = new ArrayList<>(jars.size());
        for (var jar : jars) {
            if (jar instanceof CompositeJarContents compositeJarContents && compositeJarContents.isFiltered()) {
                throw new IllegalArgumentException("Cannot use simple URLClassLoader for filtered content " + jar);
            }

            // TODO: Order on the classpath matters, we need to double-check the content roots are in the right order here
            for (var contentRoot : jar.getContentRoots()) {
                LOGGER.info(" - {}", PathPrettyPrinting.prettyPrint(contentRoot));
                try {
                    rootUrls.add(contentRoot.toUri().toURL());
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e); // This should not happen for file URLs
                }
                locatedPaths.addLocated(contentRoot); // Prevents it from getting picked up again
            }
        }

        var loader = new URLClassLoader(loaderName, rootUrls.toArray(URL[]::new), currentClassLoader);
        ownedResources.add(loader);
        currentClassLoader = loader;
        Thread.currentThread().setContextClassLoader(loader);
    }

    public ClassLoader getCurrentClassLoader() {
        return currentClassLoader;
    }

    public void append(ClassLoader loader) {
        currentClassLoader = loader;
    }

    /**
     * If any location being added is already on the classpath, we add a masking classloader to ensure
     * that resources are not double-reported when using getResources/getResource.
     * <p>
     * The primary purpose of this is in mod and NeoForge development environments, where IDEs put the mod
     * on the app classpath, but we also add it as content to the game layer. This method is responsible
     * for setting up a classloader that prevents getResource/getResources from reporting Jar resources
     * for both the jar on the App classpath and on the transforming classloader.
     */
    public void maskContentAlreadyOnClasspath(List<JarContentsModule> content) {
        var classpathItems = ClasspathResourceUtils.getAllClasspathItems(getCurrentClassLoader());

        // Collect all paths that make up the game content, which are already on the classpath
        Set<Path> needsMasking = new HashSet<>();
        for (var secureJar : content) {
            for (var basePath : getBasePaths(secureJar.contents(), true)) {
                if (classpathItems.contains(basePath)) {
                    needsMasking.add(basePath);
                }
            }
        }

        if (!needsMasking.isEmpty()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Masking classpath elements: {}", needsMasking.stream().map(PathPrettyPrinting::prettyPrint).toList());
            }

            var maskedLoader = new ResourceMaskingClassLoader(currentClassLoader, needsMasking);
            if (Thread.currentThread().getContextClassLoader() == currentClassLoader) {
                Thread.currentThread().setContextClassLoader(maskedLoader);
            }
            currentClassLoader = maskedLoader;
        }
    }

    private static List<Path> getBasePaths(JarContents contents, boolean ignoreFilter) {
        var result = new ArrayList<Path>();
        switch (contents) {
            case CompositeJarContents compositeModContainer -> {
                if (!ignoreFilter && compositeModContainer.isFiltered()) {
                    throw new IllegalStateException("Cannot load filtered Jar content into a URL classloader");
                }
                for (var delegate : compositeModContainer.getDelegates()) {
                    result.addAll(getBasePaths(delegate, ignoreFilter));
                }
            }
            case EmptyJarContents ignored -> {}
            case FolderJarContents folderModContainer -> result.add(folderModContainer.getPrimaryPath());
            case JarFileContents jarModContainer -> result.add(jarModContainer.getPrimaryPath());
            default -> throw new IllegalStateException("Don't know how to handle " + contents);
        }
        return result;
    }

    @Override
    public void close() {
        for (var ownedResource : ownedResources) {
            try {
                ownedResource.close();
            } catch (Exception e) {
                LOGGER.error("Failed to close resource {} owned by class loader stack", ownedResource, e);
            }
        }
        ownedResources.clear();

        if (Thread.currentThread().getContextClassLoader() == currentClassLoader) {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
}
