/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.language;

import java.util.List;
import java.util.Optional;

/**
 * This is an interface for querying configuration elements
 */
public interface IConfigurable {
    <T> Optional<T> getConfigElement(final String... key);

    public List<? extends IConfigurable> getConfigList(final String... key);
}
