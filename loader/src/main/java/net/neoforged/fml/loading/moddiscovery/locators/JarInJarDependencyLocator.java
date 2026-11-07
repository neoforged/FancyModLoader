/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.JarContents;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.jarjar.selection.JarSelector;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IDependencyLocator;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import net.neoforged.neoforgespi.locating.ModFileLoadingException;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class JarInJarDependencyLocator implements IDependencyLocator {
    private static final Logger LOGGER = LogUtils.getLogger();

    record EmbeddedJarKey(IModFile modFile, Path path) {}

    @Override
    public void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) {
        Map<EmbeddedJarKey, IModFile> createdModFiles = new HashMap<>();
        Set<IModFile> loadedDependencies = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            List<IModFile> dependenciesToLoad = JarSelector.detectAndSelect(
                    loadedMods,
                    this::loadResourceFromModFile,
                    (file, path) -> loadModFileFrom(file, path, pipeline, createdModFiles),
                    this::identifyMod,
                    this::exception);

            if (dependenciesToLoad.isEmpty()) {
                LOGGER.info("No dependencies to load found. Skipping!");
            } else {
                LOGGER.info("Found {} dependencies adding them to mods collection", dependenciesToLoad.size());
                for (var modFile : dependenciesToLoad) {
                    if (pipeline.addModFile(modFile)) {
                        loadedDependencies.add(modFile);
                    }
                }
            }
        } finally {
            for (var modFile : createdModFiles.values()) {
                if (!loadedDependencies.contains(modFile) && modFile instanceof ModFile mf) {
                    mf.close();
                    // Each embedded jar has its own jij: file system, which holds another copy of the jar in memory
                    closeQuietly(mf.getFilePath().getFileSystem());
                }
            }
        }
    }

    /**
     * @deprecated No longer called by {@link #scanMods}, so overriding it has no effect.
     */
    @Deprecated
    protected Optional<IModFile> loadModFileFrom(IModFile file, final Path path, IDiscoveryPipeline pipeline) {
        return loadModFileFrom(file, path, pipeline, new HashMap<>());
    }

    @SuppressWarnings("resource")
    private Optional<IModFile> loadModFileFrom(
            IModFile file,
            final Path path,
            IDiscoveryPipeline pipeline,
            Map<EmbeddedJarKey, IModFile> createdModFiles) {
        var innerModFile = createdModFiles.computeIfAbsent(new EmbeddedJarKey(file, path), ignored -> {
            FileSystem zipFS = null;
            JarContents jar = null;
            try {
                var pathInModFile = file.findResource(path.toString());
                var filePathUri = new URI("jij:" + (pathInModFile.toAbsolutePath().toUri().getRawSchemeSpecificPart())).normalize();
                var outerFsArgs = ImmutableMap.of("packagePath", pathInModFile);
                zipFS = FileSystems.newFileSystem(filePathUri, outerFsArgs);
                jar = JarContents.of(zipFS.getPath("/"));
                var providerResult = pipeline.readModFile(jar, ModFileDiscoveryAttributes.DEFAULT.withParent(file));
                if (providerResult == null) {
                    closeQuietly(jar);
                    closeQuietly(zipFS);
                }
                return providerResult;
            } catch (Exception e) {
                closeQuietly(jar);
                closeQuietly(zipFS);
                LOGGER.error("Failed to load mod file {} from {}", path, file.getFileName());
                final RuntimeException exception = new ModFileLoadingException("Failed to load mod file " + file.getFileName());
                exception.initCause(e);

                throw exception;
            }
        });

        return Optional.ofNullable(innerModFile);
    }

    private static void closeQuietly(@Nullable AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            LOGGER.error("Failed to close {}", closeable, e);
        }
    }

    protected ModLoadingException exception(Collection<JarSelector.ResolutionFailureInformation<IModFile>> failedDependencies) {
        final List<ModLoadingIssue> errors = failedDependencies.stream()
                .filter(entry -> !entry.sources().isEmpty()) //Should never be the case, but just to be sure
                .map(this::buildExceptionData)
                .toList();

        return new ModLoadingException(errors);
    }

    private ModLoadingIssue buildExceptionData(final JarSelector.ResolutionFailureInformation<IModFile> entry) {
        var artifact = entry.identifier().group() + ":" + entry.identifier().artifact();
        var requestedBy = entry.sources()
                .stream()
                .flatMap(this::getModWithVersionRangeStream)
                .map(this::formatError)
                .collect(Collectors.joining(", "));
        return ModLoadingIssue.error(getErrorTranslationKey(entry), artifact, requestedBy);
    }

    private String getErrorTranslationKey(final JarSelector.ResolutionFailureInformation<IModFile> entry) {
        return entry.failureReason() == JarSelector.FailureReason.VERSION_RESOLUTION_FAILED ? "fml.modloadingissue.dependencyloading.conflictingdependencies" : "fml.modloadingissue.dependencyloading.mismatchedcontaineddependencies";
    }

    private Stream<ModWithVersionRange> getModWithVersionRangeStream(final JarSelector.SourceWithRequestedVersionRange<IModFile> file) {
        return file.sources()
                .stream()
                .map(IModFile::getModFileInfo)
                .flatMap(modFileInfo -> modFileInfo.getMods().stream())
                .map(modInfo -> new ModWithVersionRange(modInfo, file.requestedVersionRange(), file.includedVersion()));
    }

    private String formatError(final ModWithVersionRange modWithVersionRange) {
        return "\u00a7e" + modWithVersionRange.modInfo().getModId() + "\u00a7r - \u00a74" + modWithVersionRange.versionRange().toString() + "\u00a74 - \u00a72" + modWithVersionRange.artifactVersion().toString() + "\u00a72";
    }

    protected String identifyMod(final IModFile modFile) {
        if (modFile.getModFileInfo() == null) {
            return modFile.getFileName();
        }
        // If this is a library, it won't have any mod IDs, so we use the module name instead.
        if (modFile.getModInfos().isEmpty()) {
            // Prefix to ensure this cannot collide with any true mod ID.
            return "library:" + modFile.getModFileInfo().moduleName();
        }

        return modFile.getModInfos().stream().map(IModInfo::getModId).collect(Collectors.joining());
    }

    private record ModWithVersionRange(IModInfo modInfo, VersionRange versionRange, ArtifactVersion artifactVersion) {}

    protected Optional<InputStream> loadResourceFromModFile(final IModFile modFile, final Path path) {
        try {
            return Optional.of(Files.newInputStream(modFile.findResource(path.toString())));
        } catch (final NoSuchFileException e) {
            LOGGER.trace("Failed to load resource {} from {}, it does not contain dependency information.", path, modFile.getFileName());
            return Optional.empty();
        } catch (final Exception e) {
            LOGGER.error("Failed to load resource {} from mod {}, cause {}", path, modFile.getFileName(), e);
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return "jarinjar";
    }
}
