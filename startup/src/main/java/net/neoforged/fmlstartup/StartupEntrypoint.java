/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup;

import java.lang.instrument.Instrumentation;
import net.neoforged.fmlstartup.api.StartupArgs;

@FunctionalInterface
public interface StartupEntrypoint {
    void start(Instrumentation instrumentation, StartupArgs args);
}
