/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme.elements;

import java.util.Objects;

public class ThemeLabelElement extends ThemeDecorativeElement {
    private String text = "";

    public String text() {
        return text;
    }

    public void setText(String text) {
        this.text = Objects.requireNonNull(text);
    }
}
