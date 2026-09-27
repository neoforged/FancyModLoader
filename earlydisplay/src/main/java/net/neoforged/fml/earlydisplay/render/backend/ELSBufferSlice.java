/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.backend;

public interface ELSBufferSlice {
    ELSBuffer buffer();

    long offset();

    long length();
}
