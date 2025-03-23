package net.neoforged.fml.earlydisplay.theme;

/**
 * Optional metadata for a {@link ImageLoader} to animate an image.
 *
 * @param frameCount The number of frames vertically stacked in the source image.
 */
public record AnimationMetadata(int frameCount) {}
