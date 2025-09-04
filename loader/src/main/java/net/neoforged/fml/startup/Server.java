/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.startup;

import net.neoforged.api.distmarker.Dist;
import org.jetbrains.annotations.Nullable;

/**
 * The entrypoint for starting a modded Minecraft server.
 */
public class Server extends Entrypoint {
    private Server() {}

    public static void main(String[] args) {
        try (var loader = startup(args, true, Dist.DEDICATED_SERVER)) {
            var main = createMainMethodCallable(loader, "net.minecraft.server.Main");
            main.invokeExact(loader.programArgs().getArguments());

            var serverThread = findServerThread();
            if (serverThread == null) {
                throw new FatalStartupException("Couldn't find Minecraft server thread. Startup likely failed.");
            }

            serverThread.join();
        } catch (Throwable t) {
            FatalErrorReporting.reportFatalErrorOnConsole(t);
            System.exit(1);
        }
    }

    private static @Nullable Thread findServerThread() {
        Thread serverThread = null;
        for (var thread : Thread.getAllStackTraces().keySet()) {
            if ("Server thread".equals(thread.getName())) {
                // While there's no guarantee for thread ids to be monotonically increasing
                // if there's ever a conflict between threads named "Server thread" because a mod spawned one
                // with that name, we'll pick the lower.
                if (serverThread == null || thread.threadId() <= serverThread.threadId()) {
                    serverThread = thread;
                }
            }
        }
        return serverThread;
    }
}
