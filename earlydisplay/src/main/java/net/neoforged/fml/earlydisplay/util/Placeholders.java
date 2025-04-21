/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.util;

import java.util.Map;
import java.util.regex.Pattern;

public final class Placeholders {
    private static final Pattern PATTERN = Pattern.compile("\\$\\{(\\w+)}");

    private Placeholders() {}

    public static String resolve(String text, Map<String, String> placeholders) {
        return PATTERN.matcher(text).replaceAll(matchResult -> {
            var placeholder = matchResult.group(1);
            return placeholders.getOrDefault(placeholder, "${" + placeholder + "}");
        });
    }
}
