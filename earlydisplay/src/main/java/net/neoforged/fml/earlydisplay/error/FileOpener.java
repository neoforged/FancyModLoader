/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.error;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

abstract sealed class FileOpener {
    private static final Logger LOGGER = LogManager.getLogger();

    static FileOpener get() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return new Windows();
        } else if (osName.contains("mac")) {
            return new Mac();
        } else {
            return new Default();
        }
    }

    final void open(@Nullable Path path) {
        if (path == null) return;

        URI uri = path.toUri();
        try {
            Runtime.getRuntime().exec(getOpenParameters(uri));
        } catch (IOException e) {
            LOGGER.error("Failed to open URI '{}'", uri, e);
        }
    }

    protected abstract String[] getOpenParameters(URI uri);

    private static final class Windows extends FileOpener {
        @Override
        protected String[] getOpenParameters(URI uri) {
            return new String[] { "rundll32", "url.dll,FileProtocolHandler", uri.toString() };
        }
    }

    private static final class Mac extends FileOpener {
        @Override
        protected String[] getOpenParameters(URI uri) {
            return new String[] { "open", uri.toString() };
        }
    }

    private static final class Default extends FileOpener {
        @Override
        protected String[] getOpenParameters(URI uri) {
            String uriString = uri.toString();
            if ("file".equals(uri.getScheme())) {
                uriString = uriString.replace("file:", "file://");
            }
            return new String[] { "xdg-open", uriString };
        }
    }
}
