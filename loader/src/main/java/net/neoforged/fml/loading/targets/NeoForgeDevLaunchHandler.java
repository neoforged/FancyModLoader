/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.fml.loading.moddiscovery.locators.NeoForgeDevProvider;
import net.neoforged.fml.loading.moddiscovery.locators.UserdevLocator;
import net.neoforged.fml.util.DevEnvUtils;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For the NeoForge development environment.
 */
public abstract class NeoForgeDevLaunchHandler extends CommonLaunchHandler {
    private static final Logger LOG = LoggerFactory.getLogger(NeoForgeDevLaunchHandler.class);

    /**
     * A file we expect to find in the classpath entry that contains the Minecraft code.
     */
    private static final String MINECRAFT_CLASS_PATH = "net/minecraft/server/MinecraftServer.class";

    @Override
    public boolean isProduction() {
        return false;
    }

    @Override
    public void collectAdditionalModFileLocators(VersionInfo versionInfo, Consumer<IModFileCandidateLocator> output) {
        super.collectAdditionalModFileLocators(versionInfo, output);

        NeoForgeDevProvider neoForgeProvider = null;
        var groupedModFolders = getGroupedModFolders();
        var minecraftFolders = groupedModFolders.get("minecraft");
        if (minecraftFolders != null) {
            // A user can theoretically also pass a minecraft folder group when we're in userdev,
            // we have to make sure the folder group actually contains a Minecraft class.
            for (var candidateFolder : minecraftFolders) {
                if (Files.isRegularFile(candidateFolder.resolve(MINECRAFT_CLASS_PATH))) {
                    LOG.debug("Launching with NeoForge from {}", minecraftFolders);
                    neoForgeProvider = new NeoForgeDevProvider(minecraftFolders);
                    break;
                }
            }
        }

        if (neoForgeProvider == null) {
            // Userdev is similar to neoforge dev with the only real difference being that the combined
            // output of the neoforge and patched mincraft sources are combined into a jar file
            var classesRoot = DevEnvUtils.findFileSystemRootOfFileOnClasspath(MINECRAFT_CLASS_PATH);
            LOG.debug("Launching with NeoForge from {}", classesRoot);
            neoForgeProvider = new NeoForgeDevProvider(List.of(classesRoot));
        }

        output.accept(neoForgeProvider);
        output.accept(new UserdevLocator(groupedModFolders));
    }
}
