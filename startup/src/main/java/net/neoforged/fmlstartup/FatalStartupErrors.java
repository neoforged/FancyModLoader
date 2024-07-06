/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup;

import java.io.File;
import java.util.Collection;

public final class FatalStartupErrors {
    private FatalStartupErrors() {}

    public static FatalStartupException missingRequiredModules(Collection<String> missingModules) {
        var message = new StringBuilder("Failed to start because some required modules were missing:\n");
        for (String missingModule : missingModules) {
            message.append(" - ").append(missingModule).append("\n");
        }
        return new FatalStartupException(message.toString());
    }

    public static FatalStartupException failedToReadMetadata(File file) {
        var message = new StringBuilder("Failed to start because the file metadata of the following file could not be read:\n");
        message.append(file.getAbsolutePath());
        return new FatalStartupException(message.toString());
    }
}
