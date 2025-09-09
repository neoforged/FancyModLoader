/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.startup;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class FatalErrorReporting {
    private FatalErrorReporting() {}

    private static Throwable unwrapException(Throwable t) {
        var cause = t.getCause();
        if (cause == null) {
            return t; // Cannot unwrap without a cause.
        }

        if (t instanceof InvocationTargetException) {
            return cause;
        }
        return t;
    }

    public static void reportFatalError(Throwable t) {
        t = unwrapException(t);

        if (t instanceof FatalStartupException e) {
            reportFatalError(e.getMessage());
        } else {
            reportFatalError(t.toString());
        }
    }

    public static void reportFatalErrorOnConsole(Throwable t) {
        t = unwrapException(t);
        t.printStackTrace();
    }

    /**
     * Report a fatal startup error.
     * At this point, it doesn't matter if we double-classload from the system classloader, since we're about to exit.
     */
    public static void reportFatalError(String message) {
        System.setProperty("java.awt.headless", "false"); // Overriding what MC set
        if (!GraphicsEnvironment.isHeadless()) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            String html = "<html><body width='400'><strong>Fatal Startup Error</strong>"
                    + "<p>";
            html += message.replace("<", "&lt;").replace("\n", "<br>");
            JOptionPane.showMessageDialog(null, html, "Fatal Error", JOptionPane.ERROR_MESSAGE);
        } else {
            // TinyFD refuses to let us use quotes
            message = message.replace('"', '`');
            message = message.replace('\'', '`');

            TinyFileDialogs.tinyfd_messageBox(
                    "NeoForge - Fatal Startup Error",
                    message,
                    "ok",
                    "error",
                    false);
        }
        System.exit(1);
    }
}
