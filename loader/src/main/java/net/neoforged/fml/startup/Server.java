/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.startup;

/**
 * The entrypoint for starting a modded Minecraft server.
 */
public class Server extends Entrypoint {
    private Server() {}

    public static void main(String[] args) {
        try (var startup = startup(args)) {
            var main = createMainMethodCallable(startup.classLoader(), "net.minecraft.server.Main");
            main.invokeExact(startup.programArgs());
        } catch (Throwable t) {
            FatalErrorReporting.reportFatalError(t);
            System.exit(1);
        }
    }
}