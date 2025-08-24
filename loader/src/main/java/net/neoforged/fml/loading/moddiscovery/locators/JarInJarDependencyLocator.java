/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.JarContents;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

@ApiStatus.Internal
public class JarInJarDependencyLocator implements IDependencyLocator {
    private static final Logger LOGGER = LogUtils.getLogger();

    record EmbeddedJarKey(IModFile modFile, String relativePath) {}

    @Override
    public void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) {
        Map<EmbeddedJarKey, IModFile> createdModFiles = new HashMap<>();
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
                if (!pipeline.addModFile(modFile)) {
                    ((ModFile) modFile).close();
                }
            }
        }
    }

    private Optional<IModFile> loadModFileFrom(IModFile file,
            String relativePath,
            IDiscoveryPipeline pipeline,
            Map<EmbeddedJarKey, IModFile> createdModFiles) {
        var key = new EmbeddedJarKey(file, relativePath);
        var innerModFile = createdModFiles.computeIfAbsent(key, ignored -> {
            // Copy it to disk as we go, while hashing it
            var jijCacheDir = FMLPaths.JIJ_CACHEDIR.get();
            Path tempFile;
            try {
                tempFile = Files.createTempFile(jijCacheDir, "_jij", ".tmp");
            } catch (IOException e) {
                throw new ModFileLoadingException("Failed to create a temporary file for JIJ in " + jijCacheDir + ": " + e);
            }

            // Copy the file to the temp-file, while hashing it to produce its final filename
            Path finalPath;
            try {
                String checksum;
                try (var inStream = file.getContents().openFile(relativePath); var outStream = Files.newOutputStream(tempFile)) {
                    if (inStream == null) {
                        LOGGER.error("Mod file {} declares Jar-in-Jar {} but does not contain it.", file, relativePath);
                        throw new ModFileLoadingException("Mod file " + file + " declares Jar-in-Jar " + relativePath + " but does not contain it.");
                    }

                    MessageDigest digest;
                    try {
                        digest = MessageDigest.getInstance("SHA-256");
                    } catch (NoSuchAlgorithmException e) {
                        throw new RuntimeException("Missing default JCA algorithm SHA-256.", e);
                    }

                    var digestOut = new DigestOutputStream(outStream, digest);
                    inStream.transferTo(digestOut);

                    checksum = HexFormat.of().formatHex(digest.digest());
                } catch (IOException e) {
                    LOGGER.error("Failed to copy Jar-in-Jar file {} from mod file {} to {}", relativePath, file, tempFile, e);
                    final RuntimeException exception = new ModFileLoadingException("Failed to load mod file " + file.getFileName());
                    exception.initCause(e);
                    throw exception;
                }

                var lastSeparator = relativePath.lastIndexOf('/');
                String filename = (lastSeparator != -1) ? relativePath.substring(lastSeparator + 1) : relativePath;
                finalPath = jijCacheDir.resolve(checksum + "/" + filename);
                // If the file already exists, reuse it, since it might already be opened.
                if (!Files.isRegularFile(finalPath)) {
                    try {
                        Files.createDirectories(finalPath.getParent());
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to create parent directory for extracted JiJ-file " + tempFile + " at " + finalPath, e);
                    }
                    try {
                        atomicMoveIfPossible(tempFile, finalPath);
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to move temporary JiJ-file " + tempFile + " to its final location " + finalPath, e);
                    }
                }
            } finally {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    LOGGER.error("Failed to remove temporary file {}: {}", tempFile, e);
                }
            }

            JarContents jar;
            try {
                jar = JarContents.ofPath(finalPath);
            } catch (IOException e) {
                LOGGER.error("Failed to read Jar-in-Jar file {} extracted from mod file {} to {}", relativePath, file, finalPath, e);
                final RuntimeException exception = new ModFileLoadingException("Failed to load mod file " + relativePath + " from " + file);
                exception.initCause(e);
                throw exception;
            }
            return pipeline.readModFile(jar, ModFileDiscoveryAttributes.DEFAULT.withParent(file));
        });

        return Optional.ofNullable(innerModFile);
    }

    /**
     * Atomically moves the given source file to the given destination file.
     * If the atomic move is not supported, the file will be moved normally.
     *
     * @param source      The source file
     * @param destination The destination file
     * @throws IOException If an I/O error occurs
     */
    private static void atomicMoveIfPossible(final Path source, final Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private ModLoadingException exception(Collection<JarSelector.ResolutionFailureInformation<IModFile>> failedDependencies) {
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

    private String identifyMod(final IModFile modFile) {
        if (modFile.getModFileInfo() == null) {
            return modFile.getFileName();
        }
        // If this is a library, it won't have any mod IDs, so we use the module name instead.
        if (modFile.getModInfos().isEmpty()) {
            // Prefix to ensure this cannot collide with any true mod ID.
            return "library:" + modFile.getId();
        }

        return modFile.getModInfos().stream().map(IModInfo::getModId).collect(Collectors.joining());
    }

    private record ModWithVersionRange(IModInfo modInfo, VersionRange versionRange, ArtifactVersion artifactVersion) {}

    private Optional<InputStream> loadResourceFromModFile(final IModFile modFile, final String relativePath) {
        try {
            return Optional.ofNullable(modFile.getContents().openFile(relativePath));
        } catch (final NoSuchFileException e) {
            LOGGER.trace("Failed to load resource {} from {}, it does not contain dependency information.", relativePath, modFile.getFileName());
            return Optional.empty();
        } catch (final Exception e) {
            LOGGER.error("Failed to load resource {} from mod {}, cause {}", relativePath, modFile.getFileName(), e);
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return "jarinjar";
    }
}
