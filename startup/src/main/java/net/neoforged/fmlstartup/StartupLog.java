/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

final class StartupLog {
    private static final String[] LEVEL_TEXT = new String[] {
            "DBG",
            "INF",
            "ERR"
    };
    private static final RuntimeMXBean runtimeMgmt = ManagementFactory.getRuntimeMXBean();

    private StartupLog() {}

    public static void debug(String format, Object... args) {
        message(0, format, args);
    }

    public static void info(String format, Object... args) {
        message(1, format, args);
    }

    public static void error(String format, Object... args) {
        message(2, format, args);
    }

    private static void message(int level, String format, Object... args) {
        var finalMessage = new StringBuilder(format.length());

        finalMessage.append('[').append(runtimeMgmt.getUptime()).append("] ");
        finalMessage.append(LEVEL_TEXT[level]).append(' ');

        var idx = format.indexOf("{}");
        var lastIdx = 0;
        var argIdx = 0;
        while (idx != -1) {
            finalMessage.append(format, lastIdx, idx);
            if (argIdx < args.length) {
                finalMessage.append(args[argIdx++]);
            }
            lastIdx = idx + 2;
            idx = format.indexOf("{}", lastIdx);
        }
        finalMessage.append(format, lastIdx, format.length());

        System.out.println(finalMessage);
    }
}
