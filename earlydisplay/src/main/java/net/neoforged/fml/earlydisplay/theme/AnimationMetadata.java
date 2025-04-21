/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

/**
 * Optional metadata for a {@link ImageLoader} to animate an image.
 *
 * @param frameCount The number of frames vertically stacked in the source image.
 */
public record AnimationMetadata(int frameCount) {}
