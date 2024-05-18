/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

/**
 * Content that is added to folders or jars inside a {@link SimulatedInstallation}. It can later be identified
 * again, based purely on the content of a virtual SecureJar.
 */
public record IdentifiableContent(String name, String relativePath, byte[] content) {
    IdentifiableContent(String name, String relativePath) {
        this(name, relativePath, name.getBytes());
    }

    public IdentifiableContent(String name, String relativePath, byte[] content) {
        this.name = name;
        this.relativePath = relativePath;
        this.content = content;
    }
}
