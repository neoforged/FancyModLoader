/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup.api;

import java.io.File;
import java.util.Set;

public record StartupArgs(
        File gameDirectory,
        String launchTarget,
        String[] programArgs,
        Set<File> claimedFiles) {}
