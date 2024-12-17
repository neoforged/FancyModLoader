/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup;

/**
 * This mirrors the detection logic found in LWJGL.
 */
enum NativeArchitectureCPU {
    X86,
    X64,
    ARM32,
    ARM64;

    static NativeArchitectureCPU current() {
        return Holder.current;
    }

    private static class Holder {
        private static final NativeArchitectureCPU current;

        static {
            // Actual source of these values:
            // https://github.com/openjdk/jdk/blob/master/src/java.base/windows/native/libjava/java_props_md.c#L551
            var arch = System.getProperty("os.arch");
            if (arch.startsWith("arm") || arch.startsWith("aarch")) {
                current = arch.contains("64") ? ARM64 : ARM32;
            } else if (arch.equals("amd64")) {
                current = X64;
            } else if (arch.equals("x86")) {
                current = X86;
            } else {
                throw new RuntimeException("Invalid architecture: " + arch);
            }
        }
    }
}
