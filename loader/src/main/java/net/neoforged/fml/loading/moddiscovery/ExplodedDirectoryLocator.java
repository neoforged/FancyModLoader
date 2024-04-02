/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery;

import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.JarContentsBuilder;
import cpw.mods.jarhandling.SecureJar;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModLocator;
import org.slf4j.Logger;

public class ExplodedDirectoryLocator implements IModLocator {
    private static final Logger LOGGER = LogUtils.getLogger();

    public record ExplodedMod(String modid, List<Path> paths) {}

    private final List<ExplodedMod> explodedMods = new ArrayList<>();
    private final Map<ExplodedMod, IModFile> mods = new HashMap<>();

    @Override
    public List<IModLocator.ModFileOrException> scanMods() {
        explodedMods.forEach(explodedMod -> {
            var jarContents = new JarContentsBuilder().paths(explodedMod.paths().toArray(Path[]::new)).build();
            if (jarContents.findFile(AbstractModProvider.MODS_TOML).isPresent()) {
                var mjm = new ModJarMetadata(jarContents);
                var mf = new ModFile(SecureJar.from(jarContents, mjm), this, ModFileParser::modsTomlParser);
                mjm.setModFile(mf);
                mods.put(explodedMod, mf);
            } else {
                LOGGER.warn(LogMarkers.LOADING, "Failed to find exploded resource {} in directory {}", AbstractModProvider.MODS_TOML, explodedMod.paths().get(0).toString());
            }
        });
        return mods.values().stream().map(mf -> new IModLocator.ModFileOrException(mf, null)).toList();
    }

    @Override
    public String name() {
        return "exploded directory";
    }

    @Override
    public void scanFile(final IModFile file, final Consumer<Path> pathConsumer) {
        LOGGER.debug(LogMarkers.SCAN, "Scanning exploded directory {}", file.getFilePath().toString());
        try (Stream<Path> files = Files.find(file.getSecureJar().getRootPath(), Integer.MAX_VALUE, (p, a) -> p.getNameCount() > 0 && p.getFileName().toString().endsWith(".class"))) {
            files.forEach(pathConsumer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        LOGGER.debug(LogMarkers.SCAN, "Exploded directory scan complete {}", file.getFilePath().toString());
    }

    @Override
    public String toString() {
        return "{ExplodedDir locator}";
    }

    @SuppressWarnings("unchecked")
    @Override
    public void initArguments(final Map<String, ?> arguments) {
        final var explodedTargets = ((Map<String, List<ExplodedMod>>) arguments).get("explodedTargets");
        if (explodedTargets != null && !explodedTargets.isEmpty()) {
            explodedMods.addAll(explodedTargets);
        }
    }

    @Override
    public boolean isValid(final IModFile modFile) {
        return true;
    }
}
