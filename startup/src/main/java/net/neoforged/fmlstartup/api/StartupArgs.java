/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup.api;

import java.io.File;
import java.lang.instrument.Instrumentation;

/**
 * This class is dual-loaded both in the boot classpath and in the module-layer we use to start the game.
 * To allow the data to be comfortably constructed in Startup before it is passed over to FML,
 * we serialize and deserialize it in-memory to get it across the class-loader boundary.
 */
public record StartupArgs(Instrumentation instrumentation, File gameDirectory, String launchTarget, String[] programArgs) {}
