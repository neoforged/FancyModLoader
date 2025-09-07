/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.startup;

import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.swing.JOptionPane;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;

public final class FatalErrorReporting {
    private FatalErrorReporting() {
    }

    private static Throwable unwrapException(Throwable t) {
        if (t == null) {
            return null;
        }
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
            var errorText = new StringBuilder(e.getMessage());
            if (e.getCause() != null) {
                errorText.append("\n\nTechnical Details:\n");
                appendAbbreviatedExceptionChain(unwrapException(e.getCause()), errorText);
            }

            reportFatalError(errorText.toString());
        } else {
            var exceptionText = new StringBuilder();
            // TODO Translate
            exceptionText.append("An uncaught technical error occurred:\n");
            appendAbbreviatedExceptionChain(t, exceptionText);

            reportFatalError(exceptionText.toString());
        }
    }

    private static void appendAbbreviatedExceptionChain(Throwable t, StringBuilder exceptionText) {
        boolean first = true;
        while (t != null) {
            if (first) {
                exceptionText.append("  ").append(t).append('\n');
                first = false;
            } else {
                exceptionText.append(" ↳").append(t).append('\n');
            }
            t = unwrapException(t.getCause());
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
            showErrorUsingSwing(message);
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

    private static void showErrorUsingSwing(String message) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        String html = "<html><body width='400'><strong>Fatal Startup Error</strong>"
                + "<p><pre>";
        html += escapeHtmlContent(message);
        JOptionPane.showMessageDialog(null, html, "Fatal Error", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * &    &amp;
     * <    &lt;
     * >    &gt;
     * "    &quot;
     * '    &#x27;
     */
    private static String escapeHtmlContent(String content) {
        return content
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
