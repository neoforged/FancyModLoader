/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.locating;

import net.neoforged.fml.ModLoadingIssue;

public sealed interface LoadResult<T> {
    record Success<T>(T value) implements LoadResult<T> {}

    record Error<T>(ModLoadingIssue error) implements LoadResult<T> {}
}
