/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

/**
 * @param progressBarBackground           The background image being rendered as the base for a progress bar.
 * @param progressBarForeground           The image that will be rendered on top of the background for progress bars that are being filled normally
 *                                        from the left.
 * @param progressBarIndeterminate        The image that will be rendered on top of the background for progress bars that are actively animating
 *                                        as an indeterminate progress bar.
 * @param progressBarIndeterminateBounces Indicates that indeterminate progress bars use a fixed width sprite that bounces back and forth,
 *                                        instead of using a sprite that seems like it scrolls through the background (which causes the
 *                                        sprite to squish until it is 0 pixels wide at the edges).
 */
public record ThemeSprites(
        ThemeTexture progressBarBackground,
        ThemeTexture progressBarForeground,
        ThemeTexture progressBarIndeterminate,
        boolean progressBarIndeterminateBounces) {}
