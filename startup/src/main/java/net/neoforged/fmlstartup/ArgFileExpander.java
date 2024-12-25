/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.StreamTokenizer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Expands "@"-files in a given command line.
 */
public final class ArgFileExpander {
    private ArgFileExpander() {}

    public static String[] expandArgFiles(String[] args) {
        List<String> newArgs = new ArrayList<>(args.length);

        for (String arg : args) {
            addOrExpand(arg, newArgs);
        }

        return newArgs.toArray(new String[0]);
    }

    // The functions below are copied and adapted from picocli.
    private static void addOrExpand(String arg, List<String> arguments) {
        if (!arg.equals("@") && arg.startsWith("@")) {
            arg = arg.substring(1);
            if (!arg.startsWith("@")) {
                expandArgumentFile(arg, arguments);
                return;
            }
        }
        arguments.add(arg);
    }

    private static void expandArgumentFile(String fileName, List<String> arguments) {
        File file = new File(fileName);
        if (!file.canRead()) {
            arguments.add("@" + fileName);
        } else {
            expandValidArgumentFile(fileName, file, arguments);
        }
    }

    private static void expandValidArgumentFile(String fileName, File file, List<String> arguments) {
        try (var reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            StreamTokenizer tok = new StreamTokenizer(reader);
            tok.resetSyntax();
            tok.wordChars(' ', 255);
            tok.whitespaceChars(0, ' ');
            tok.quoteChar('"');
            tok.quoteChar('\'');
            tok.commentChar('#');
            while (tok.nextToken() != StreamTokenizer.TT_EOF) {
                arguments.add(tok.sval);
            }
        } catch (Exception ex) {
            throw new FatalStartupException("Could not read argument file @" + fileName + ": " + ex);
        }
    }
}
