/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.startup;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;

/**
 * The entrypoint for starting a modded Minecraft server.
 */
public class Server extends Entrypoint {
    private Server() {
    }

    public static void main(String[] args) {
        FMLLoader loader;
        try {
            loader = startup(args, true, Dist.DEDICATED_SERVER);

            var main = createMainMethodCallable(loader, "net.minecraft.server.Main");
            main.invokeExact(loader.programArgs().getArguments());
        } catch (Throwable t) {
            FatalErrorReporting.reportFatalErrorOnConsole(t);
            System.exit(1);
            return;
        }

        // The dedicated server main method sadly just returns after spawning a non-daemon thread
        // So the only way to close the loader will be via shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(loader::close));
    }
}
