/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.backend;

public enum TextureFormat {
    RGBA8_UNORM(ComponentType.UNORM_8, 4),
    RED8_UNORM(ComponentType.UNORM_8, 1),
    RG32_FLOAT(ComponentType.FLOAT_32, 2),
    ;

    private final ComponentType componentType;
    private final int components;
    private final int size;

    TextureFormat(ComponentType componentType, int components) {
        this.componentType = componentType;
        this.components = components;
        this.size = componentType.size * components;
    }

    public ComponentType getComponentType() {
        return componentType;
    }

    public int getComponents() {
        return components;
    }

    public int getSize() {
        return size;
    }

    public enum ComponentType {
        UNORM_8(1, true),
        FLOAT_32(4, false),
        ;

        private final int size;
        private final boolean normalized;

        ComponentType(int size, boolean normalized) {
            this.size = size;
            this.normalized = normalized;
        }

        public int getSize() {
            return size;
        }

        public boolean isNormalized() {
            return normalized;
        }
    }
}
