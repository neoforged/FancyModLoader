/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.classloading;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.nio.ByteBuffer;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.jarcontents.JarResource;
import org.jetbrains.annotations.Nullable;

/**
 * {@link JarMetadata} implementation for a modular jar.
 * Reads the module descriptor from the jar.
 */
public class ModuleJarMetadata implements JarMetadata {
    private final byte[] originalDescriptorBytes;
    private final ModuleDescriptor originalDescriptor;

    public ModuleJarMetadata(JarResource moduleInfo) {
        try {
            this.originalDescriptorBytes = moduleInfo.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read module-info.class from " + moduleInfo, e);
        }
        this.originalDescriptor = ModuleDescriptor.read(ByteBuffer.wrap(originalDescriptorBytes));
    }

    @Override
    public ModuleDescriptor descriptor(JarContents contents) {
        var fullDescriptor = ModuleDescriptor.read(ByteBuffer.wrap(originalDescriptorBytes), () -> ModuleDescriptorFactory.scanModulePackages(contents));

        // We do inherit the name and version, as well as the package list.
        var builder = ModuleDescriptor.newAutomaticModule(fullDescriptor.name());
        fullDescriptor.rawVersion().ifPresent(builder::version);
        builder.packages(fullDescriptor.packages());
        fullDescriptor.provides().forEach(builder::provides);

        return builder.build();
    }

    @Override
    public String name() {
        return originalDescriptor.name();
    }

    @Override
    @Nullable
    public String version() {
        return originalDescriptor.rawVersion().orElse(null);
    }
}
