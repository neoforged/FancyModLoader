/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.startup;

public class FatalStartupException extends RuntimeException {
    public FatalStartupException(String message) {
        super(message);
    }
}
