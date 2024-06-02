/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery;

import java.nio.file.Path;

public record CoreModFile(String name, Path path, ModFile file) {}
