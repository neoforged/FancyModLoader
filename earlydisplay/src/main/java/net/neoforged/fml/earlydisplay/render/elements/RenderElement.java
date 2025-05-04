/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.elements;

import net.neoforged.fml.earlydisplay.render.MaterializedTheme;
import net.neoforged.fml.earlydisplay.render.RenderContext;
import net.neoforged.fml.earlydisplay.render.SimpleFont;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeElement;
import net.neoforged.fml.earlydisplay.util.Bounds;
import net.neoforged.fml.earlydisplay.util.StyleLength;
import org.jetbrains.annotations.Nullable;

public abstract class RenderElement implements AutoCloseable {
    protected final MaterializedTheme theme;

    @Nullable
    private String id;
    protected SimpleFont font;
    private final ThemeElement element;

    public RenderElement(ThemeElement element, MaterializedTheme theme) {
        this.theme = theme;
        this.font = theme.getFont(element.font());
        this.element = element;
    }

    @Nullable
    public String id() {
        return this.id;
    }

    public void setId(@Nullable String id) {
        this.id = id;
    }

    public abstract void render(RenderContext context);

    public Bounds resolveBounds(float availableWidth, float availableHeight, float intrinsicWidth, float intrinsicHeight) {
        var left = resolve(element.left(), availableWidth);
        var right = availableWidth - resolve(element.right(), availableWidth);
        var top = resolve(element.top(), availableHeight);
        var bottom = availableHeight - resolve(element.bottom(), availableHeight);

        var width = right - left;
        var height = bottom - top;

        boolean widthDefined = !Float.isNaN(width);
        boolean heightDefined = !Float.isNaN(height);

        // Handle aspect ratio
        if (widthDefined != heightDefined) {
            if (element.maintainAspectRatio()) {
                float ar = intrinsicWidth / intrinsicHeight;
                if (widthDefined) {
                    height = width / ar;
                } else {
                    width = height * ar;
                }
            } else if (widthDefined) {
                height = intrinsicHeight;
            } else if (heightDefined) {
                width = intrinsicWidth;
            }
        } else if (!widthDefined && !heightDefined) {
            width = intrinsicWidth;
            height = intrinsicHeight;
        }

        // Fill out the unspecified size based on width/height
        if (Float.isNaN(left) && Float.isNaN(right)) {
            left = 0;
            right = width;
        } else if (Float.isNaN(right)) {
            right = left + width;
        } else if (Float.isNaN(left)) {
            left = right - width;
        }
        if (Float.isNaN(top) && Float.isNaN(bottom)) {
            top = 0;
            bottom = height;
        } else if (Float.isNaN(bottom)) {
            bottom = top + height;
        } else if (Float.isNaN(top)) {
            top = bottom - height;
        }

        // Apply centering with the assumption any offset on the corresponding edge is to be interpreted as an
        // offset from the center instead
        if (element.centerHorizontally()) {
            width = right - left;
            left = availableWidth / 2 - width / 2 + left;
            right = left + width;
        }
        if (element.centerVertically()) {
            height = bottom - top;
            top = availableHeight / 2 - height / 2 + top;
            bottom = top + height;
        }

        return new Bounds(left, top, right, bottom);
    }

    private float resolve(StyleLength length, float availableSpace) {
        return switch (length.unit()) {
            case UNDEFINED -> Float.NaN;
            case POINT -> length.value();
            case REM -> length.value() * font.lineSpacing();
            case PERCENT -> (length.value() * availableSpace) / 100.0f;
        };
    }

    public static float clamp(float num, float min, float max) {
        if (num < min) {
            return min;
        } else {
            return Math.min(num, max);
        }
    }

    public static int clamp(int num, int min, int max) {
        if (num < min) {
            return min;
        } else {
            return Math.min(num, max);
        }
    }

    @Override
    public void close() {
    }

    @Override
    public String toString() {
        return id;
    }
}
