/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import com.google.common.collect.Streams;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import org.jetbrains.annotations.Nullable;

public record ModLoadingIssue(
        Severity severity,
        String translationKey,
        List<Object> translationArgs,
        @Nullable Throwable cause,
        @Nullable Path affectedPath,
        @Nullable IModFile affectedModFile,
        @Nullable IModInfo affectedMod) {

    public ModLoadingIssue(Severity severity, String translationKey, List<Object> translationArgs) {
        this(severity, translationKey, translationArgs, null, null, null, null);
    }

    public static ModLoadingIssue error(String translationKey, Object... args) {
        return new ModLoadingIssue(Severity.ERROR, translationKey, List.of(args));
    }

    public static ModLoadingIssue warning(String translationKey, Object... args) {
        return new ModLoadingIssue(Severity.WARNING, translationKey, List.of(args));
    }

    public ModLoadingIssue withAffectedPath(Path affectedPath) {
        return new ModLoadingIssue(severity, translationKey, translationArgs, cause, affectedPath, null, null);
    }

    public ModLoadingIssue withAffectedModFile(IModFile affectedModFile) {
        var affectedPath = affectedModFile.getFilePath();
        return new ModLoadingIssue(severity, translationKey, translationArgs, cause, affectedPath, affectedModFile, null);
    }

    public ModLoadingIssue withAffectedMod(IModInfo affectedMod) {
        var affectedModFile = affectedMod.getOwningFile().getFile();
        var affectedPath = affectedModFile.getFilePath();
        return new ModLoadingIssue(severity, translationKey, translationArgs, cause, affectedPath, affectedModFile, affectedMod);
    }

    public ModLoadingIssue withCause(Throwable cause) {
        return new ModLoadingIssue(severity, translationKey, translationArgs, cause, affectedPath, affectedModFile, affectedMod);
    }

    public String getTranslatedMessage() {
        Object[] formattingArgs;
        // TODO: cleanup null here - this requires moving all indices in the translations
        if (severity == Severity.ERROR) {
            // Error translations included a "cause" in position 2
            formattingArgs = Streams.concat(Stream.of(affectedMod, null, cause), translationArgs.stream()).toArray();
        } else {
            formattingArgs = Streams.concat(Stream.of(affectedMod, null), translationArgs.stream()).toArray();
        }

        return Bindings.parseMessage(translationKey, formattingArgs);
    }

    public String toString() {
        var result = new StringBuilder(severity + ": " + translationKey);

        for (var arg : translationArgs) {
            result.append(", ");
            result.append(arg);
        }

        return result.toString();
    }
    public enum Severity {
        WARNING,
        ERROR
    }
}
