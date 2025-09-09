/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.startup;

import net.neoforged.api.distmarker.Dist;

public class DataServer extends Entrypoint {
    private DataServer() {}

    public static void main(String[] args) {
        try (var loader = startup(args, true, Dist.DEDICATED_SERVER, false)) {
            var main = createMainMethodCallable(loader, "net.minecraft.data.Main");
            main.invokeExact(loader.programArgs().getArguments());
        } catch (Throwable t) {
            FatalErrorReporting.reportFatalErrorOnConsole(t);
            System.exit(1);
        }
    }
}
