/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.backend;

import java.util.Arrays;

public enum VertexFormat {
    POS_TEX_COLOR(Element.POS, Element.TEX, Element.COLOR);

    private final Element[] elements;
    public final int stride;

    VertexFormat(Element... elements) {
        this.elements = elements;
        this.stride = Arrays.stream(elements).mapToInt(e -> e.format.getSize()).sum();
    }

    public Element element(int idx) {
        return this.elements[idx];
    }

    public int elementCount() {
        return this.elements.length;
    }

    public int findElement(Element target) {
        for (int i = 0; i < this.elements.length; i++) {
            if (this.elements[i] == target) {
                return i;
            }
        }
        return -1;
    }

    public enum Element {
        POS("position", TextureFormat.RG32_FLOAT),
        TEX("uv", TextureFormat.RG32_FLOAT),
        COLOR("color", TextureFormat.RGBA8_UNORM);

        public final String name;
        public final TextureFormat format;

        Element(String name, TextureFormat format) {
            this.name = name;
            this.format = format;
        }
    }

    public enum Mode {
        TRIANGLES(3),
        QUADS(4),
        ;

        public final int vertices;

        Mode(int vertices) {
            this.vertices = vertices;
        }
    }
}
