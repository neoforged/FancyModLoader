/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.backend;

public interface ELSTexture extends AutoCloseable {
    int width();

    int height();

    TextureFormat format();

    @Override
    void close();
}
