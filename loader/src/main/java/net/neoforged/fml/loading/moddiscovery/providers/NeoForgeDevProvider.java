/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.providers;

import com.google.common.collect.Streams;
import cpw.mods.jarhandling.JarContentsBuilder;
import cpw.mods.jarhandling.SecureJar;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.neoforged.fml.loading.moddiscovery.ModJarMetadata;
import net.neoforged.fml.loading.moddiscovery.locators.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileProvider;
import net.neoforged.neoforgespi.locating.LoadResult;

/**
 * Provides the Minecraft and Neoforge mods in a Neoforge dev environment.
 */
public class NeoForgeDevProvider implements IModFileProvider, ISystemModSource {
    private final List<Path> paths;

    public NeoForgeDevProvider(List<Path> paths) {
        this.paths = paths;
    }

    @Override
    public List<LoadResult<IModFile>> provideModFiles(ILaunchContext launchContext) {
        var minecraftResourcesRoot = DevEnvUtils.findFileSystemRootOfFileOnClasspath("assets/.mcassetsroot");
        var packages = getNeoforgeSpecificPathPrefixes();
        var minecraftResourcesPrefix = minecraftResourcesRoot.toString().replace('\\', '/');

        var mcJarContents = new JarContentsBuilder()
                .paths(Streams.concat(paths.stream(), Stream.of(minecraftResourcesRoot)).toArray(Path[]::new))
                .pathFilter((entry, basePath) -> {
                    // We serve everything, except for things in the forge packages.
                    if (basePath.equals(minecraftResourcesPrefix) || entry.endsWith("/")) {
                        return true;
                    }
                    // Any non-class file will be served from the client extra jar file mentioned above
                    if (!entry.endsWith(".class")) {
                        return false;
                    }
                    for (var pkg : packages) {
                        if (entry.startsWith(pkg)) {
                            return false;
                        }
                    }
                    return true;
                })
                .build();

        var mcJarMetadata = new ModJarMetadata(mcJarContents);
        var mcSecureJar = SecureJar.from(mcJarContents, mcJarMetadata);
        var minecraftModFile = IModFile.create(mcSecureJar, this, MinecraftModInfo::buildMinecraftModInfo, null);
        mcJarMetadata.setModFile(minecraftModFile);

        // We need to separate out our resources/code so that we can show up as a different data pack.
        var neoforgeJarContents = new JarContentsBuilder()
                .paths(paths.toArray(Path[]::new))
                .pathFilter((entry, basePath) -> {
                    if (!entry.endsWith(".class")) return true;
                    for (var pkg : packages)
                        if (entry.startsWith(pkg)) return true;
                    return false;
                })
                .build();

        return List.of(
                new LoadResult.Success<>(minecraftModFile),
                // TODO insufficient error handling
                JarModsDotTomlModFileReader.createModFile(neoforgeJarContents, this, null));
    }

    private static String[] getNeoforgeSpecificPathPrefixes() {
        return new String[] { "net/neoforged/neoforge/", "META-INF/services/", "META-INF/coremods.json", JarModsDotTomlModFileReader.MODS_TOML };
    }

    @Override
    public String name() {
        return "neoforge devenv";
    }
}
