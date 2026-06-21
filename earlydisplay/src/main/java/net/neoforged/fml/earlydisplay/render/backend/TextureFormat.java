/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.backend;

public enum TextureFormat {
    RGBA(4),
    RED(1),
    ;

    private final int components;

    TextureFormat(int components) {
        this.components = components;
    }

    public int getComponents() {
        return components;
    }
}
