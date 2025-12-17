/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.game;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.loading.MavenCoordinate;
import net.neoforged.fml.loading.ProgramArgs;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModJarMetadata;
import net.neoforged.fml.loading.moddiscovery.locators.NeoForgeDevDistCleaner;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.fml.util.ClasspathResourceUtils;
import net.neoforged.fml.util.PathPrettyPrinting;
import net.neoforged.neoforgespi.LocatedPaths;
import net.neoforged.neoforgespi.installation.GameDiscoveryOrInstallationService;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for finding the minecraft and neoforge "mods".
 */
public final class GameDiscovery {
    private static final Logger LOG = LoggerFactory.getLogger(GameDiscovery.class);
    public static final String LIBRARIES_DIRECTORY_PROPERTY = "libraryDirectory";
    public static final String[] NEOFORGE_SPECIFIC_PATH_PREFIXES = { "net/neoforged/neoforge/", "META-INF/services/", JarModsDotTomlModFileReader.MODS_TOML };

    private GameDiscovery() {}

    public static GameDiscoveryResult discoverGame(ProgramArgs programArgs, LocatedPaths locatedPaths, Dist requiredDist, @Nullable GameDiscoveryOrInstallationService gameDiscoveryOrInstallationService) {
        var ourCl = Thread.currentThread().getContextClassLoader();

        var neoForgeVersion = getNeoForgeVersion(programArgs, ourCl);

        programArgs.remove("fml.neoForgeVersion");
        programArgs.remove("fml.neoFormVersion"); // Remove legacy arguments
        programArgs.remove("fml.mcVersion"); // Remove legacy arguments

        // 0) Vanilla Launcher puts the obfuscated jar on the classpath. We mark it as claimed to prevent it from
        // being hoisted into a module, occupying the entrypoint packages.
        preventLoadingOfObfuscatedClientJar(locatedPaths, ourCl);

        // We look for a class present in both Minecraft distributions on the classpath, which would be obfuscated in production (DetectedVersion)
        // If that is present, we assume we're launching in dev (NeoDev or ModDev).
        try (var systemFiles = RequiredSystemFiles.find(locatedPaths::isLocated, ourCl)) {
            if (!systemFiles.isEmpty() && systemFiles.hasMinecraft()) {
                // If we've only been able to find some of the required files, we need to error
                systemFiles.checkForMissingMinecraftFiles(requiredDist == Dist.CLIENT);

                return handleMergedMinecraftAndNeoForgeJar(requiredDist, locatedPaths, systemFiles);
            } else {
                LOG.info("Failed to find common Minecraft classes and resources on the classpath. Assuming we're launching production.");
            }
        }

        // In production, it's in the libraries directory, and we're passed the version to look for on the commandline
        return locateProductionMinecraft(neoForgeVersion, requiredDist, gameDiscoveryOrInstallationService);
    }

    private static String getNeoForgeVersion(ProgramArgs programArgs, ClassLoader ourCl) {
        var neoForgeVersion = getNeoForgeVersionFromClasspath(ourCl);
        if (neoForgeVersion == null) {
            neoForgeVersion = programArgs.get("fml.neoForgeVersion");
            if (neoForgeVersion == null) {
                LOG.error("NeoForge version must be known to launch FML, in normal environments this is set as a command-line option (--fml.neoForgeVersion)");
                throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation"));
            }
            LOG.debug("Using NeoForge version found on commandline: {}", neoForgeVersion);
        } else {
            LOG.debug("Using NeoForge version found on classpath: {}", neoForgeVersion);
        }
        return neoForgeVersion;
    }

    @Nullable
    private static String getNeoForgeVersionFromClasspath(ClassLoader classLoader) {
        try (var in = classLoader.getResourceAsStream("net/neoforged/neoforge/common/version.properties")) {
            if (in == null) {
                return null;
            }

            Properties p = new Properties();
            p.load(new BufferedInputStream(in));
            return p.getProperty("neoforge_version");
        } catch (IOException ignored) {
            return null;
        }
    }

    @Nullable
    private static ModFile readModFile(JarContents jarContents) {
        return (ModFile) new JarModsDotTomlModFileReader().read(jarContents, ModFileDiscoveryAttributes.DEFAULT);
    }

    private static GameDiscoveryResult handleMergedMinecraftAndNeoForgeJar(Dist requiredDist, LocatedPaths locatedPaths, RequiredSystemFiles systemFiles) {
        LOG.info("Detected a joined NeoForge and Minecraft configuration. Applying filtering...");

        var mcJarContents = getCombinedMinecraftJar(requiredDist, systemFiles);
        ModFile minecraftModFile;
        if (mcJarContents.containsFile("META-INF/neoforged.mods.toml")) {
            // In this branch, the jar already has a neoforge.mods.toml
            minecraftModFile = readModFile(mcJarContents);
            if (minecraftModFile == null) {
                throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_minecraft_jar"));
            }
        } else {
            var minecraftVersion = detectMinecraftVersion(mcJarContents);
            var mcJarMetadata = new ModJarMetadata();
            minecraftModFile = (ModFile) IModFile.create(mcJarContents, mcJarMetadata, new MinecraftModInfo(minecraftVersion)::buildMinecraftModInfo);
            mcJarMetadata.setModFile(minecraftModFile);
        }
        if (!minecraftModFile.getId().equals("minecraft")) {
            LOG.error("The mod id for the Minecraft jar is not 'minecraft': {}", minecraftModFile.getId());
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_minecraft_jar"));
        }

        // We need to separate out our resources/code so that we can show up as a different data pack.
        JarContents.PathFilter nfJarFilter = relativePath -> {
            if (!relativePath.endsWith(".class")) {
                return true;
            }
            for (var includedPrefix : NEOFORGE_SPECIFIC_PATH_PREFIXES) {
                if (relativePath.startsWith(includedPrefix)) {
                    return true;
                }
            }
            return false;
        };
        var nfJarRoots = new ArrayList<JarContents.FilteredPath>();
        for (var nfJarRoot : systemFiles.getNeoForgeJarComponents()) {
            nfJarRoots.add(new JarContents.FilteredPath(nfJarRoot.getPrimaryPath(), nfJarFilter));
        }
        JarContents nfJarContents;
        try {
            nfJarContents = JarContents.ofFilteredPaths(nfJarRoots);
        } catch (IOException e) {
            LOG.error("Failed to construct filtered NeoForge jar from {}", nfJarRoots);
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_neoforge_jar").withCause(e));
        }

        var modFile = (ModFile) JarModsDotTomlModFileReader.createModFile(nfJarContents, ModFileDiscoveryAttributes.DEFAULT);
        if (modFile == null) {
            LOG.error("Failed to construct NeoForge mod file from {}", nfJarContents);
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_neoforge_jar"));
        }

        systemFiles.getAll().stream().map(JarContents::getPrimaryPath).forEach(locatedPaths::addLocated);

        return new GameDiscoveryResult(modFile, minecraftModFile, false);
    }

    private static JarContents getCombinedMinecraftJar(Dist requiredDist, RequiredSystemFiles systemFiles) {
        if (systemFiles.getCommonResources() == systemFiles.getNeoForgeResources()) {
            throw new IllegalStateException("The Minecraft and NeoForge resources cannot come from the same jar: "
                    + systemFiles.getCommonResources() + " and " + systemFiles.getNeoForgeResources());
        }

        var mcJarRoots = new ArrayList<JarContents.FilteredPath>();
        mcJarRoots.addAll(getMinecraftResourcesRoots(requiredDist, systemFiles));

        JarContents.PathFilter mcClassesFilter = relativePath -> {
            if (relativePath.endsWith(".class")) {
                for (var pkg : NEOFORGE_SPECIFIC_PATH_PREFIXES) {
                    if (relativePath.startsWith(pkg)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        };
        addContentRoot(mcJarRoots, systemFiles.getCommonClasses(), mcClassesFilter);
        addContentRoot(mcJarRoots, systemFiles.getClientClasses(), mcClassesFilter);

        JarContents mcJarContents;
        try {
            mcJarContents = JarContents.ofFilteredPaths(mcJarRoots);
        } catch (IOException e) {
            LOG.error("Failed to construct filtered Minecraft jar from {}", mcJarRoots);
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_minecraft_jar").withCause(e));
        }
        return mcJarContents;
    }

    /**
     * Reads the version.json found in Minecraft jars (both server and client have it) to detect which version of
     * Minecraft is in the given Jar.
     */
    private static String detectMinecraftVersion(JarContents mcJarContents) {
        String minecraftVersion;
        try (var in = mcJarContents.openFile("version.json")) {
            if (in == null) {
                LOG.error("Minecraft version.json not found in {}.", mcJarContents);
                throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_minecraft_jar"));
            }
            var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            var versionElement = new Gson().fromJson(reader, JsonObject.class);
            var idPrimitive = versionElement.getAsJsonPrimitive("id");
            if (idPrimitive == null) {
                LOG.error("Minecraft version.json found in {} is missing 'id' field. Available fields are: {}", mcJarContents, versionElement.keySet());
                throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_minecraft_jar"));
            }
            minecraftVersion = idPrimitive.getAsString();
        } catch (IOException e) {
            LOG.error("Failed to read Minecraft version.json from {}", mcJarContents);
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_minecraft_jar").withCause(e));
        }
        return minecraftVersion;
    }

    private static List<JarContents.FilteredPath> getMinecraftResourcesRoots(Dist requiredDist, RequiredSystemFiles systemFiles) {
        JarContents commonResources = systemFiles.getCommonResources();
        JarContents clientResources = systemFiles.getClientResources();

        // If the resource roots are separate from classes, no filter needs to be applied.
        List<JarContents.FilteredPath> result = new ArrayList<>();
        result.add(buildFilteredMinecraftResourcesFilteredPath(commonResources, requiredDist, systemFiles));

        if (clientResources != null && clientResources != commonResources) {
            result.add(buildFilteredMinecraftResourcesFilteredPath(clientResources, requiredDist, systemFiles));
        }

        return result;
    }

    private static JarContents.FilteredPath buildFilteredMinecraftResourcesFilteredPath(JarContents container, Dist requiredDist, RequiredSystemFiles systemFiles) {
        JarContents.PathFilter pathFilter = null;

        // If the resources container is also used for classes, we filter out the .class files
        if (systemFiles.getClassesRoots().contains(container)) {
            pathFilter = relativePath -> !relativePath.endsWith(".class");
        }

        // If there are masked, non-class resources, we also need to filter these out
        pathFilter = JarContents.PathFilter.and(pathFilter, getMaskedResourceFilter(container, requiredDist));

        return new JarContents.FilteredPath(container.getPrimaryPath(), pathFilter);
    }

    private static JarContents.@Nullable PathFilter getMaskedResourceFilter(JarContents jar, Dist requiredDist) {
        var maskedResources = NeoForgeDevDistCleaner.getMaskedFiles(jar, requiredDist)
                .filter(path -> !path.endsWith(".class"))
                .collect(Collectors.toSet());
        if (!maskedResources.isEmpty()) {
            return relativePath -> {
                if (maskedResources.contains(relativePath)) {
                    LOG.debug("Masking access to {} since it's from a different Minecraft distribution.", relativePath);
                    return false;
                }
                return true;
            };
        }
        return null;
    }

    private static void addContentRoot(List<JarContents.FilteredPath> roots, JarContents jarContents, JarContents.PathFilter filter) {
        if (jarContents == null) {
            return;
        }
        for (var root : roots) {
            if (root.path().equals(jarContents.getPrimaryPath()) && root.filter() == filter) {
                return;
            }
        }
        roots.add(new JarContents.FilteredPath(jarContents.getPrimaryPath(), filter));
    }

    /**
     * In production, the client and neoforge jars are assembled from partial jars in the libraries folder.
     */
    private static GameDiscoveryResult locateProductionMinecraft(String neoForgeVersion, Dist requiredDist, @Nullable GameDiscoveryOrInstallationService gameDiscoveryOrInstallationService) {
        // 2) It's neither, but a libraries directory and desired versions are given on the commandline
        var librariesDirectory = System.getProperty(LIBRARIES_DIRECTORY_PROPERTY);
        if (librariesDirectory == null) {
            LOG.error("When launching in production, the system property {} must point to the libraries directory.", LIBRARIES_DIRECTORY_PROPERTY);
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation"));
        }

        var librariesRoot = Path.of(librariesDirectory);
        if (!Files.isDirectory(librariesRoot)) {
            LOG.error("Libraries directory is not readable: {}", librariesRoot);
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation"));
        }

        PathPrettyPrinting.addSubstitution(librariesRoot, "~libraries/", "");

        // The versions for Minecraft and NeoForm can be read from the NeoForge jar
        var neoforgeCoordinate = new MavenCoordinate("net.neoforged", "neoforge", "", "universal", neoForgeVersion);
        var neoforgeJar = librariesRoot.resolve(neoforgeCoordinate.toRelativeRepositoryPath());
        if (!Files.exists(neoforgeJar)) {
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.missing_neoforge_jar").withAffectedPath(neoforgeJar));
        }

        // Detect if the newer Minecraft installation method is available. If not, we assume the old method.
        var patchedMinecraftPath = librariesRoot.resolve((switch (requiredDist) {
            case CLIENT -> new MavenCoordinate("net.neoforged", "minecraft-client-patched", "", "", neoForgeVersion);
            case DEDICATED_SERVER -> new MavenCoordinate("net.neoforged", "minecraft-server-patched", "", "", neoForgeVersion);
        }).toRelativeRepositoryPath());

        var nfModFile = openModFile(neoforgeJar, "neoforge", "fml.modloadingissue.corrupted_neoforge_jar");

        if (!Files.exists(patchedMinecraftPath)) {
            if (gameDiscoveryOrInstallationService != null) {
                LOG.info("Patched minecraft does not exist. Triggering external discovery or installation service!");
                try {
                    var result = gameDiscoveryOrInstallationService.discoverOrInstall(requiredDist);
                    if (result != null) {
                        patchedMinecraftPath = result.minecraft();
                    } else {
                        LOG.info("Game discovery or installation service: {} did not return a result. Skipping.", gameDiscoveryOrInstallationService.name());
                    }
                } catch (Throwable e) {
                    nfModFile.close();
                    throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.discovery_service_failure").withCause(e).withSeverity(ModLoadingIssue.Severity.ERROR));
                }
            }
        }

        ModFile minecraftModFile;
        try {
            minecraftModFile = openModFile(patchedMinecraftPath, "minecraft", "fml.modloadingissue.corrupted_minecraft_jar");
        } catch (Exception e) {
            nfModFile.close();
            throw e;
        }

        return new GameDiscoveryResult(nfModFile, minecraftModFile, true);
    }

    private static ModFile openModFile(Path path, String expectedModId, String errorTranslation) {
        JarContents jarContents;
        try {
            jarContents = JarContents.ofPath(path);
        } catch (IOException e) {
            throw new ModLoadingException(ModLoadingIssue.error(errorTranslation).withAffectedPath(path).withCause(e));
        }
        try {
            var modFile = JarModsDotTomlModFileReader.createModFile(jarContents, ModFileDiscoveryAttributes.DEFAULT);
            if (modFile == null || modFile.getType() != IModFile.Type.MOD) {
                throw new ModLoadingException(ModLoadingIssue.error(errorTranslation).withAffectedPath(path));
            }
            var containedModIds = modFile.getModInfos().stream().map(IModInfo::getModId).toList();
            if (!containedModIds.equals(List.of(expectedModId))) {
                LOG.error("The mod file {} does not contain only the expected mod '{}': {}", path, expectedModId, containedModIds);
                throw new ModLoadingException(ModLoadingIssue.error(errorTranslation).withAffectedPath(path));
            }
            return (ModFile) modFile;
        } catch (Exception e) {
            try {
                jarContents.close();
            } catch (IOException ex) {
                e.addSuppressed(ex);
            }
            throw e;
        }
    }

    private static void preventLoadingOfObfuscatedClientJar(LocatedPaths locatedPaths, ClassLoader ourCl) {
        try {
            var jarsWithEntrypoint = new HashSet<Path>();

            var resources = ourCl.getResources("net/minecraft/client/main/Main.class");
            while (resources.hasMoreElements()) {
                jarsWithEntrypoint.add(ClasspathResourceUtils.findJarPathFor("net/minecraft/client/main/Main.class", "minecraft jar", resources.nextElement()));
            }

            // This class would only be present in deobfuscated jars
            resources = ourCl.getResources("net/minecraft/client/Minecraft.class");
            while (resources.hasMoreElements()) {
                jarsWithEntrypoint.remove(ClasspathResourceUtils.findJarPathFor("net/minecraft/client/Minecraft.class", "minecraft jar", resources.nextElement()));
            }

            for (Path path : jarsWithEntrypoint) {
                LOG.info("Marking obfuscated client jar as claimed to prevent loading: {}", path);
                locatedPaths.addLocated(path);
            }

        } catch (IOException ignored) {}
    }

    public static Dist detectDist(ClassLoader classLoader) {
        var clientAvailable = classLoader.getResource("net/minecraft/client/main/Main.class") != null;
        return clientAvailable ? Dist.CLIENT : Dist.DEDICATED_SERVER;
    }
}
