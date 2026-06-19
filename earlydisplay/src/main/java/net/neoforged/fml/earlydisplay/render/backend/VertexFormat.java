package net.neoforged.fml.earlydisplay.render.backend;

import java.util.Arrays;

public enum VertexFormat {
    POS(Element.POS),
    POS_TEX(Element.POS, Element.TEX),
    POS_COLOR(Element.POS, Element.COLOR),
    POS_TEX_COLOR(Element.POS, Element.TEX, Element.COLOR);

    private final Element[] elements;
    public final int stride;

    VertexFormat(Element... elements) {
        this.elements = elements;
        this.stride = Arrays.stream(elements).mapToInt(e -> e.width).sum();
    }

    public Element element(int idx) {
        return this.elements[idx];
    }

    public int elementCount() {
        return this.elements.length;
    }

    public enum Element {
        POS(2, 2 * 4),
        TEX(2, 2 * 4),
        COLOR(4, 4);

        public final int count;
        public final int width;

        Element(int count, int width) {
            this.count = count;
            this.width = width;
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
