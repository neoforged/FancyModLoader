/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup;

enum NativeArchitectureOS {
    WINDOWS,
    MACOSX,
    LINUX;

    static NativeArchitectureOS current() {
        return Holder.current;
    }

    private static class Holder {
        private static final NativeArchitectureOS current;

        static {
            var osName = System.getProperty("os.name");
            // The following matches the logic in Apache Commons Lang 3 SystemUtils
            if (osName.startsWith("Linux") || osName.startsWith("LINUX")) {
                current = LINUX;
            } else if (osName.startsWith("Mac OS X")) {
                current = MACOSX;
            } else if (osName.startsWith("Windows")) {
                current = WINDOWS;
            } else {
                throw new IllegalStateException("Unsupported operating system: " + osName);
            }
        }
    }
}
