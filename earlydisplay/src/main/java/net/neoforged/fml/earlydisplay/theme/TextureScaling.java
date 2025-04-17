package net.neoforged.fml.earlydisplay.theme;

public sealed interface TextureScaling {
    /**
     * The intrinsic layout width of this image.
     * This is required to support images that are larger in physical pixels for High DPI.
     */
    int width();

    /**
     * The intrinsic layout height of this image.
     * This is required to support images that are larger in physical pixels for High DPI.
     */
    int height();

    record Stretch(int width, int height) implements TextureScaling {}

    record Tile(int width, int height) implements TextureScaling {}

    record NineSlice(int width, int height, int left, int top, int right, int bottom, boolean stretchHorizontalFill, boolean stretchVerticalFill) implements TextureScaling {}
}
