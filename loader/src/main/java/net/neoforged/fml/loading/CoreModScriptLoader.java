/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.modlauncher.api.ITransformer;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.util.List;
import net.neoforged.coremod.CoreModScriptingEngine;
import net.neoforged.coremod.ICoreModScriptSource;
import net.neoforged.fml.loading.moddiscovery.CoreModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses the coremod scripting engine from https://github.com/neoforged/CoreMods
 * to load JS-based coremods. Avoids loading any of the classes unless a mod
 * contains a JS-based coremod.
 */
class CoreModScriptLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoreModScriptLoader.class);

    private CoreModScriptLoader() {}

    /**
     * Enumerate script-based coremods.
     */
    public static List<ITransformer<?>> loadCoreModScripts(List<ModFileInfo> modFileInfos) {
        CoreModScriptingEngine engine;
        try {
            engine = new CoreModScriptingEngine();
        } catch (NoClassDefFoundError e) {
            // Fail for all mods that require a coremod scripting engine to be present
            throw new IllegalStateException("Could not find the coremod script-engine, but the following mods require it: " + modFileInfos, e);
        }

        LOGGER.debug(LogMarkers.CORE, "Loading coremod scripts");
        for (var modFile : modFileInfos) {
            for (var coreMod : modFile.getFile().getCoreMods()) {
                engine.loadCoreMod(new ScriptSourceAdapter(coreMod));
            }
        }

        return engine.initializeCoreMods();
    }

    private record ScriptSourceAdapter(CoreModFile coreMod) implements ICoreModScriptSource {
        @Override
        public Reader readCoreMod() throws IOException {
            return Files.newBufferedReader(coreMod.path());
        }

        @Override
        public String getDebugSource() {
            return coreMod.path().toString();
        }

        @Override
        public Reader getAdditionalFile(final String fileName) throws IOException {
            return Files.newBufferedReader(coreMod.file().findResource(fileName));
        }

        @Override
        public String getOwnerId() {
            return this.coreMod.file().getModInfos().getFirst().getModId();
        }

        @Override
        public String toString() {
            return "{Name: " + coreMod.name() + ", Owner: " + getOwnerId() + " @ " + getDebugSource() + "}";
        }
    }
}
