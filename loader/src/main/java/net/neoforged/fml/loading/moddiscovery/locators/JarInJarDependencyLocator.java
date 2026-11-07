/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.JarContents;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLPaths;
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

    private Optional<IModFile> loadModFileFrom(
            IModFile file,
            final Path path,
            IDiscoveryPipeline pipeline,
            Map<EmbeddedJarKey, IModFile> createdModFiles) {
        var innerModFile = createdModFiles.computeIfAbsent(new EmbeddedJarKey(file, path), ignored -> {
            var cachedPath = extractToCache(file, path);
            try {
                var jar = JarContents.of(cachedPath);
                try {
                    var providerResult = pipeline.readModFile(jar, ModFileDiscoveryAttributes.DEFAULT.withParent(file));
                    if (providerResult == null) {
                        jar.close();
                    }
                    return providerResult;
                } catch (Throwable t) {
                    try {
                        jar.close();
                    } catch (IOException closeEx) {
                        t.addSuppressed(closeEx);
                    }
                    throw t;
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load mod file {} from {}", path, file.getFileName());
                final RuntimeException exception = new ModFileLoadingException("Failed to load mod file " + file.getFileName());
                exception.initCause(e);
                throw exception;
            }
        });

        return Optional.ofNullable(innerModFile);
    }

    private static Path extractToCache(IModFile file, Path path) {
        var cacheDir = FMLPaths.JIJ_CACHEDIR.get();
        try {
            Files.createDirectories(cacheDir);
            var tempFile = Files.createTempFile(cacheDir, "_jij", ".tmp");
            try {
                String checksum = extractEmbeddedJarFile(file, path, tempFile);
                // We must keep the original filename, since JarMetadata may derive the module name and version from it
                var cachedPath = cacheDir.resolve(checksum).resolve(path.getFileName().toString());
                moveIntoCache(tempFile, cachedPath);
                return cachedPath;
            } finally {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    LOGGER.error("Failed to remove temporary file {}", tempFile, e);
                }
            }
        } catch (IOException e) {
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.technical_error", "Failed to extract " + path + " into " + cacheDir)
                    .withAffectedModFile(file)
                    .withCause(e));
        }
    }

    private static String extractEmbeddedJarFile(IModFile file, Path path, Path destination) throws IOException {
        var pathInModFile = file.findResource(path.toString());
        try (var inStream = Files.newInputStream(pathInModFile); var outStream = Files.newOutputStream(destination)) {
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Missing default JCA algorithm SHA-256.", e);
            }

            var digestOut = new DigestOutputStream(outStream, digest);
            inStream.transferTo(digestOut);

            return HexFormat.of().formatHex(digest.digest());
        }
    }

    private static void moveIntoCache(Path tempFile, Path cachedPath) throws IOException {
        Files.createDirectories(cachedPath.getParent());
        try {
            Files.move(tempFile, cachedPath);
        } catch (FileAlreadyExistsException e) {
            // Another game instance sharing this directory might have the existing file open, so we only replace it if it looks truncated
            if (Files.size(cachedPath) != Files.size(tempFile)) {
                Files.move(tempFile, cachedPath, StandardCopyOption.REPLACE_EXISTING);
            }
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
