/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.language;

public interface ILifecycleEvent<R extends ILifecycleEvent<?>> {
    @SuppressWarnings("unchecked")
    default R concrete() {
        return (R) this;
    }
}

