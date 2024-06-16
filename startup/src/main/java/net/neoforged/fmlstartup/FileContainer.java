/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup;

import java.io.File;
import java.util.List;

public record FileContainer(List<FileContainerLayer> layers) {}

record FileContainerLayer(File location, boolean directory, int size, long lastModified) {}
