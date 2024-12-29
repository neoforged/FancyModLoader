/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.startup;

import net.neoforged.api.distmarker.Dist;

public class DataClient extends Entrypoint {
    private DataClient() {}

    public static void main(String[] args) {
        try (var startup = startup(args, true, Dist.CLIENT)) {
            var main = createMainMethodCallable(startup.classLoader(), "net.minecraft.client.data.Main");
            main.invokeExact(startup.programArgs());
        } catch (Throwable t) {
            FatalErrorReporting.reportFatalError(t);
            System.exit(1);
        }
    }
}
