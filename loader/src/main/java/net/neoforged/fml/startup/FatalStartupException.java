/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.startup;

/**
 * THis exceptions {@link #getMessage()} will be shown to end-users directly in a fatal error popup.
 */
public class FatalStartupException extends RuntimeException {
    public FatalStartupException(String message) {
        super(message);
    }

    public FatalStartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
