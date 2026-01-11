/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.game;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModJarMetadata;
import net.neoforged.fml.loading.moddiscovery.locators.NeoForgeDevDistCleaner;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.fml.util.ClasspathResourceUtils;
import net.neoforged.neoforgespi.LocatedPaths;
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
    public static final String[] NEOFORGE_SPECIFIC_PATH_PREFIXES = { "net/neoforged/neoforge/", "META-INF/services/", JarModsDotTomlModFileReader.MODS_TOML };

    private GameDiscovery() {}

    public static GameDiscoveryResult discoverGame(ClassLoader classLoader, LocatedPaths locatedPaths, Dist requiredDist) {
        var neoForgeVersion = findNeoForge(classLoader);

        // 0) Vanilla Launcher puts the obfuscated jar on the classpath. We mark it as claimed to prevent it from
        // being hoisted into a module, occupying the entrypoint packages.
        preventLoadingOfObfuscatedClientJar(locatedPaths, classLoader);

        // We look for a class present in both Minecraft distributions on the classpath, which would be obfuscated in production (DetectedVersion)
        // If that is present, we assume we're launching in dev (NeoDev or ModDev).
        try (var systemFiles = RequiredSystemFiles.find(locatedPaths::isLocated, classLoader)) {
            if (!systemFiles.isEmpty() && systemFiles.hasMinecraft()) {
                // If we've only been able to find some of the required files, we need to error
                systemFiles.checkForMissingMinecraftFiles(requiredDist == Dist.CLIENT);

                return handleMergedMinecraftAndNeoForgeJar(requiredDist, locatedPaths, systemFiles);
            } else {
                LOG.info("Failed to find common Minecraft classes and resources on the classpath. Assuming we're launching production.");
            }
        }

        // In production, it's in the libraries directory, and we're passed the version to look for on the commandline
        return locateProductionMinecraft(neoForgeVersion, requiredDist, classLoader);
    }

    private static NeoForgeInfo findNeoForge(ClassLoader ourCl) {
        var neoForgeVersion = NeoForgeInfo.fromClasspath(ourCl);

        LOG.debug("Found NeoForge {} for Minecraft {}", neoForgeVersion.neoForgeVersion(), neoForgeVersion.minecraftVersion());
        return neoForgeVersion;
    }

    private static GameDiscoveryResult handleMergedMinecraftAndNeoForgeJar(Dist requiredDist, LocatedPaths locatedPaths, RequiredSystemFiles systemFiles) {
        LOG.info("Detected a joined NeoForge and Minecraft configuration. Applying filtering...");

        var mcJarContents = getCombinedMinecraftJar(requiredDist, systemFiles);
        ModFile minecraftModFile;
        if (mcJarContents.containsFile("META-INF/neoforged.mods.toml")) {
            // In this branch, the jar already has a neoforge.mods.toml
            minecraftModFile = (ModFile) new JarModsDotTomlModFileReader().read(mcJarContents, ModFileDiscoveryAttributes.DEFAULT);
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

        var mcJarRoots = new ArrayList<>(getMinecraftResourcesRoots(requiredDist, systemFiles));

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
    private static GameDiscoveryResult locateProductionMinecraft(NeoForgeInfo neoForgeVersion, Dist requiredDist, ClassLoader classLoader) {
        var nfModFile = openModFile(neoForgeVersion.sourcePath(), "neoforge", "fml.modloadingissue.corrupted_neoforge_jar");

        var patchedMinecraftFilename = switch (requiredDist) {
            case CLIENT -> "minecraft-client-patched-" + neoForgeVersion.neoForgeVersion() + ".jar";
            case DEDICATED_SERVER -> "minecraft-server-patched-" + neoForgeVersion.neoForgeVersion() + ".jar";
        };
        var patchedMinecraftPath = FMLPaths.CACHEDIR.get().resolve(patchedMinecraftFilename);

        if (!Files.exists(patchedMinecraftPath)) {
            LOG.info("Patched Minecraft does not exist. Creating new patched game jar...");
            try {
                MinecraftPatcher.discoverOrInstall(classLoader, requiredDist, neoForgeVersion, patchedMinecraftPath);
            } catch (Throwable e) {
                nfModFile.close();
                throw e;
            }
        }

        ModFile minecraftModFile;
        try {
            minecraftModFile = openModFile(patchedMinecraftPath, "minecraft", "fml.modloadingissue.corrupted_minecraft_jar");
        } catch (Exception e) {
            // Try deleting it to not leave behind a corrupted Minecraft jar
            LOG.info("Deleting corrupted Minecraft jar at {}", patchedMinecraftPath);
            try {
                Files.delete(patchedMinecraftPath);
            } catch (IOException ex) {
                LOG.error("Failed to delete corrupted Minecraft jar at {}", patchedMinecraftPath);
            }
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
            var resources = ourCl.getResources(RequiredSystemFiles.COMMON_CLASS);
            while (resources.hasMoreElements()) {
                Path jarPath = ClasspathResourceUtils.findJarPathFor(RequiredSystemFiles.COMMON_CLASS, "minecraft jar", resources.nextElement());
                try (var zip = new ZipFile(jarPath.toFile())) {
                    // An unmodified Minecraft Jar must have the resources too
                    if (zip.getEntry(RequiredSystemFiles.COMMON_RESOURCE_ROOT) == null) {
                        continue;
                    }
                    // If the client class is in it, also make sure it has the client resources.
                    // Vice versa is not true since the dedicated server jar also has assets/.mcassetsroots
                    // because it has the English language files.
                    boolean hasClientClasses = zip.getEntry(RequiredSystemFiles.CLIENT_CLASS) != null;
                    boolean hasClientResources = zip.getEntry(RequiredSystemFiles.CLIENT_RESOURCE_ROOT) != null;
                    if (hasClientClasses && !hasClientResources) {
                        continue;
                    }

                    // If it has a neoforge.mods.toml, it's likely a patched Minecraft
                    if (zip.getEntry("META-INF/neoforge.mods.toml") != null) {
                        continue;
                    }

                    LOG.info("Marking unmodified client jar as claimed to prevent loading: {}", jarPath);
                    locatedPaths.addLocated(jarPath);
                    return;
                } catch (IOException ignored) {}
            }
        } catch (IOException ignored) {}
    }

    public static Dist detectDist(ClassLoader classLoader) {
        var clientAvailable = classLoader.getResource("net/minecraft/client/main/Main.class") != null;
        return clientAvailable ? Dist.CLIENT : Dist.DEDICATED_SERVER;
    }
}
