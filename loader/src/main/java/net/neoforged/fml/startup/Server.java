/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.startup;

import net.neoforged.api.distmarker.Dist;

/**
 * The entrypoint for starting a modded Minecraft server.
 */
public class Server extends Entrypoint {
    private Server() {}

    public static void main(String[] args) {
        try (var startup = startup(args, true, Dist.DEDICATED_SERVER)) {
            var main = createMainMethodCallable(startup.classLoader(), "net.minecraft.server.Main");
            main.invokeExact(startup.programArgs().getArguments());
        } catch (Throwable t) {
            FatalErrorReporting.reportFatalError(t);
            System.exit(1);
        }
    }
}
