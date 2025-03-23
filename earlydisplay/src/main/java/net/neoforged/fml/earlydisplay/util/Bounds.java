package net.neoforged.fml.earlydisplay.util;

public record Bounds(float left, float top, float right, float bottom) {
    public Bounds(float x, float y, Size size) {
        this(x, y, x + size.width(), y + size.height());
    }

    public float width() {
        return right - left;
    }

    public float height() {
        return bottom - top;
    }

    public Bounds union(Bounds other) {
        return new Bounds(
                Math.min(left, other.left),
                Math.min(top, other.top),
                Math.max(right, other.right),
                Math.max(bottom, other.bottom));
    }
}
