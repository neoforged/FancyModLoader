/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.api.LambdaExceptionUtils;
import cpw.mods.modlauncher.api.NamedPath;
import cpw.mods.modlauncher.serviceapi.ITransformerDiscoveryService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

public class ModDirTransformerDiscoverer implements ITransformerDiscoveryService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private UncheckedIOException alreadyFailed;

    @Override
    public List<NamedPath> candidates(final Path gameDirectory, final String launchTarget) {
        try {
            FMLPaths.loadAbsolutePaths(gameDirectory);
            FMLConfig.load();
            return candidates(gameDirectory);
        } catch (UncheckedIOException e) {
            // we capture any error here and then return an empty list so we can
            // show an error in earlyInitialization which fires next
            this.alreadyFailed = e;
            return List.of();
        }
    }

    @Override
    public void earlyInitialization(final String launchTarget, final String[] arguments) {
        ImmediateWindowHandler.load(launchTarget, arguments);
        if (this.alreadyFailed != null) {
            String errorCause;
            if (this.alreadyFailed.getCause() instanceof FileAlreadyExistsException faee) {
                errorCause = "File already exists: " + faee.getFile() + "\nYou need to move this out of the way, so we can put a directory there.";
            } else if (this.alreadyFailed.getCause() instanceof AccessDeniedException ade) {
                errorCause = "Access denied trying to create a file or directory " + ade.getMessage() + "\nThe game directory is probably read-only. Check the write permission on it.";
            } else {
                errorCause = "An unexpected IO error occurred trying to setup the game directory\n" + this.alreadyFailed.getCause().getMessage();
            }
            ImmediateWindowHandler.crash(errorCause);
            throw this.alreadyFailed;
        }
    }

    @Override
    public List<NamedPath> candidates(final Path gameDirectory) {
        scan(gameDirectory);
        return List.copyOf(found);
    }

    private final static List<NamedPath> found = new ArrayList<>();

    public static List<Path> allExcluded() {
        return found.stream().map(np -> np.paths()[0]).toList();
    }

    private static void scan(final Path gameDirectory) {
        final Path modsDir = gameDirectory.resolve(FMLPaths.MODSDIR.relative()).toAbsolutePath().normalize();
        if (!Files.exists(modsDir)) {
            // Skip if the mods dir doesn't exist yet.
            return;
        }
        try (var walk = Files.walk(modsDir, 1)) {
            // Collect to list first, and then parallel stream it.
            // Before JDK 19, Files.walk streams are not parallelized efficiently for small numbers of elements.
            // See https://bugs.openjdk.org/browse/JDK-8280915.
            walk.toList().parallelStream()
                    .filter(ModDirTransformerDiscoverer::shouldLoadInServiceLayer)
                    .forEachOrdered(p -> found.add(new NamedPath(p.getFileName().toString(), p)));
        } catch (IOException | IllegalStateException ioe) {
            LOGGER.error("Error during early discovery", ioe);
        }
    }

    private static boolean shouldLoadInServiceLayer(Path path) {
        if (!Files.isRegularFile(path)) return false;
        if (!path.toString().endsWith(".jar")) return false;
        if (LambdaExceptionUtils.uncheck(() -> Files.size(path)) == 0) return false;

        return TransformerDiscovererConstants.shouldLoadInServiceLayer(path);
    }
}
