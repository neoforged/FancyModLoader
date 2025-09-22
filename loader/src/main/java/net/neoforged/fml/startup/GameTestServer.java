/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.startup;

import net.neoforged.api.distmarker.Dist;

/**
 * The entrypoint for starting a modded Minecraft gametest server.
 */
public class GameTestServer extends Entrypoint {
    private GameTestServer() {}

    public static void main(String[] args) {
        try (var loader = startup(args, true, Dist.DEDICATED_SERVER, false)) {
            var main = createMainMethodCallable(loader, "net.minecraft.gametest.Main");
            main.invokeExact(loader.getProgramArgs().getArguments());

            var serverThread = findThread("Server thread");
            if (serverThread == null) {
                throw new FatalStartupException("Couldn't find Minecraft server thread. Startup likely failed.");
            }

            serverThread.join();
        } catch (Throwable t) {
            FatalErrorReporting.reportFatalErrorOnConsole(t);
            System.exit(1);
        }
    }
}
