/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme.elements;

/**
 * Decorative elements are additional elements that a theme can add to the screen that
 * have no specific functionality.
 */
public abstract class ThemeDecorativeElement extends ThemeElement {
    private String id;

    @Override
    public String id() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
