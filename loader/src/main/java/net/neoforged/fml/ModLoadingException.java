/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import java.util.List;
import java.util.Objects;

import net.neoforged.fml.i18n.FMLTranslations;

public class ModLoadingException extends RuntimeException {
    private final List<ModLoadingIssue> issues;

    public ModLoadingException(ModLoadingIssue issue) {
        this(List.of(issue));
    }

    public ModLoadingException(List<ModLoadingIssue> issues) {
        this.issues = List.copyOf(issues);
    }

    public List<ModLoadingIssue> getIssues() {
        return this.issues;
    }

    @Override
    public String getMessage() {
        var result = new StringBuilder();
        var errors = this.issues.stream().filter(i -> i.severity() == ModLoadingIssue.Severity.ERROR).toList();
        if (!errors.isEmpty()) {
            result.append("Loading errors encountered:\n");
            for (var error : errors) {
                appendIssue(error, result);
            }
        }
        var warnings = this.issues.stream().filter(i -> i.severity() == ModLoadingIssue.Severity.WARNING).toList();
        if (!warnings.isEmpty()) {
            result.append("Loading warnings encountered:\n");
            for (var warning : warnings) {
                appendIssue(warning, result);
            }
        }
        return result.toString();
    }

    private void appendIssue(ModLoadingIssue issue, StringBuilder result) {
        String translation;
        try {
            translation = FMLTranslations.stripControlCodes(FMLTranslations.translateIssueEnglish(issue));
        } catch (Exception e) {
            // Fall back to *something* readable in case the translation fails
            translation = issue.toString();
        }

        // Poor mans indentation
        translation = translation.replace("\n", "\n\t  ");

        result.append("\t- ").append(translation).append("\n");
    }

    @Override
    public synchronized Throwable getCause() {
        //First get all issues which are errored, and get their first cause.
        for (ModLoadingIssue i : issues) {
            if (i.severity() == ModLoadingIssue.Severity.ERROR) {
                Throwable cause = i.cause();
                if (cause != null) {
                    return cause;
                }
            }
        }

        //If we have no errors then check the warnings.
        for (ModLoadingIssue i : issues) {
            if (i.severity() == ModLoadingIssue.Severity.WARNING) {
                Throwable cause = i.cause();
                if (cause != null) {
                    return cause;
                }
            }
        }

        //No cause known.
        return null;
    }
}
