package net.neoforged.fml.loading;

import net.neoforged.fml.startup.FatalStartupException;
import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;
import net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider;
import net.neoforged.neoforgespi.locating.IDependencyLocator;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IModFileReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

final class EarlyServiceDiscovery {
    private static final Logger LOGGER = LogManager.getLogger(EarlyServiceDiscovery.class);

    private static final Set<String> SERVICES = Set.of(
            IModFileCandidateLocator.class.getName(),
            IModFileReader.class.getName(),
            IDependencyLocator.class.getName(),
            GraphicsBootstrapper.class.getName(),
            ImmediateWindowProvider.class.getName()
    );

    private EarlyServiceDiscovery() {
    }

    /**
     * Find and load early services from the mods directory.
     */
    public static List<Path> findEarlyServices(Path directory) {
        if (!Files.exists(directory)) {
            // Skip if the mods dir doesn't exist yet.
            return List.of();
        }

        long start = System.currentTimeMillis();

        var candidates = new ArrayList<Path>();
        try {
            Files.walkFileTree(directory, Set.of(), 1, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().endsWith(".jar") && attrs.isRegularFile() && attrs.size() > 0) {
                        candidates.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new FatalStartupException("Failed to find early startup services: " + e);
        }

        var earlyServices = candidates.parallelStream()
                .filter(EarlyServiceDiscovery::shouldLoadInServiceLayer)
                .toList();

        LOGGER.info(
                "Found {} early service jars (out of {}) in {}ms",
                earlyServices.size(),
                candidates.size(),
                System.currentTimeMillis() - start
        );

        return earlyServices;
    }

    private static boolean shouldLoadInServiceLayer(Path path) {
        // We do not need to verify the Jar since we just test for existence of the service file and do not
        // actually load any code here.
        try (var jarFile = new JarFile(path.toFile(), false, JarFile.OPEN_READ)) {
            for (var service : SERVICES) {
                if (jarFile.getEntry("META-INF/services/" + service) != null) {
                    LOGGER.debug("{} contains early service {}", path, service);
                    return true;
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read Jar file {} in mods directory: {}", path, e);
        }

        return false;
    }
}
