/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup;

import java.util.Set;

final class JlsConstants {
    // https://docs.oracle.com/javase/specs/jls/se22/html/jls-3.html#jls-3.9
    static final Set<String> RESERVED_KEYWORDS = Set.of(
            "abstract",
            "assert",
            "boolean",
            "break",
            "byte",
            "case",
            "catch",
            "char",
            "class",
            "const",
            "continue",
            "default",
            "do",
            "double",
            "else",
            "enum",
            "extends",
            "final",
            "finally",
            "float",
            "for",
            "goto",
            "if",
            "implements",
            "import",
            "instanceof",
            "int",
            "interface",
            "long",
            "native",
            "new",
            "package",
            "private",
            "protected",
            "public",
            "return",
            "short",
            "static",
            "strictfp",
            "super",
            "switch",
            "synchronized",
            "this",
            "throw",
            "throws",
            "transient",
            "try",
            "void",
            "volatile",
            "while",
            // Not really keywords, but "boolean literals"
            "true",
            "false",
            // Not really a keyword, but the "null literal"
            "null",
            "_");

    // Same as jdk.internal.module.Checks.isClassName
    // A string is a type name, if each of the segments delimited by '.' are valid identifiers
    public static boolean isTypeName(String str) {
        var lastIdx = 0;
        for (var idx = str.indexOf('.'); idx != -1; idx = str.indexOf('.', lastIdx)) {
            if (!isJavaIdentifier(str.substring(lastIdx, idx))) {
                return false;
            }
            lastIdx = idx + 1;
        }
        return isJavaIdentifier(str.substring(lastIdx));
    }

    // Same as jdk.internal.module.Checks.isJavaIdentifier
    public static boolean isJavaIdentifier(String str) {
        if (str.isEmpty() || RESERVED_KEYWORDS.contains(str)) {
            return false;
        }

        // This iterates over the Unicode code points instead of UTF-16 characters
        for (var i = 0; i < str.length();) {
            int codePoint = Character.codePointAt(str, i);

            if (i == 0 && !Character.isJavaIdentifierStart(codePoint)
                    || !Character.isJavaIdentifierPart(codePoint)) {
                return false;
            }

            i += Character.charCount(codePoint);
        }

        return true;
    }

    private JlsConstants() {}

    public static String packageName(String line) {
        var idx = line.lastIndexOf('.');
        if (idx == -1) {
            return "";
        } else {
            return line.substring(0, idx);
        }
    }
}
