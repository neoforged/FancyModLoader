/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import java.util.List;
import java.util.stream.Collectors;
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
        return "Loading errors encountered: " + this.issues.stream().map(FMLTranslations::translateIssue)
                .collect(Collectors.joining(",\n\t", "[\n\t", "\n]"));
    }
}
