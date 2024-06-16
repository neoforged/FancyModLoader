package net.neoforged.fmlstartup;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class BootModules {
    private static final List<String> BOOT_MODULES = List.of(
            // Math library
            "org.joml",
            // OpenGL Access and various Natives
            "org.lwjgl",
            "org.lwjgl.*",
            // Logging, Commons
            "org.apache.*",
            "org.slf4j",
            // This is Mojangs logging library
            "logging",
            // Argument parsing
            "jopt.simple",
            // Arbitrary natives access
            "com.sun.jna",
            "com.sun.jna.*",
            // GSON
            "com.google.gson"
            // TODO: ASM? Shade it?
    );

    private static final Set<String> DIRECT_MATCHES = new HashSet<>();
    private static final List<String> PREFIX_MATCHES = new ArrayList<>();

    static {
        for (String bootModule : BOOT_MODULES) {
            if (bootModule.endsWith("*")) {
                PREFIX_MATCHES.add(bootModule.substring(0, bootModule.length() - 1));
            } else {
                DIRECT_MATCHES.add(bootModule);
            }
        }
    }

    public static boolean isBootModule(@Nullable String moduleName) {
        if (moduleName == null) {
            return false;
        }
        if (DIRECT_MATCHES.contains(moduleName)) {
            return true;
        }
        for (String prefixMatch : PREFIX_MATCHES) {
            if (moduleName.startsWith(prefixMatch)) {
                return true;
            }
        }
        return false;
    }
}
