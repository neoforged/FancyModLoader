/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import java.util.List;
import net.neoforged.fml.i18n.FMLTranslations;

public class ModLoadingException extends RuntimeException {
    private final List<ModLoadingIssue> issues;

    public ModLoadingException(ModLoadingIssue issue) {
        this(List.of(issue));
    }

    public ModLoadingException(List<ModLoadingIssue> issues) {
        this.issues = issues;
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
            for (var warning : errors) {
                result.append("\t").append(translateIssue(warning)).append("\n");
            }
        }
        var warnings = this.issues.stream().filter(i -> i.severity() == ModLoadingIssue.Severity.WARNING).toList();
        if (!warnings.isEmpty()) {
            result.append("Loading warnings encountered:\n");
            for (var warning : warnings) {
                result.append("\t").append(translateIssue(warning)).append("\n");
            }
        }
        return result.toString();
    }

    private String translateIssue(ModLoadingIssue issue) {
        try {
            return FMLTranslations.stripControlCodes(FMLTranslations.translateIssueEnglish(issue));
        } catch (Exception e) {
            // Fall back to *something* readable in case the translation fails
            return issue.toString();
        }
    }
}
