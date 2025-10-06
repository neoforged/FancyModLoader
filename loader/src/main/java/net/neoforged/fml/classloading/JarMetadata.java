/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.classloading;

import java.lang.module.ModuleDescriptor;
import net.neoforged.fml.jarcontents.JarContents;
import org.jetbrains.annotations.Nullable;

/**
 * Describes the modular properties of a Jar file, with direct access to the module name and version,
 * as well as the ability to create a module descriptor for a given {@link JarContents}, which may
 * involve scanning the Jar file for packages.
 */
public interface JarMetadata {
    String name();

    @Nullable
    String version();

    ModuleDescriptor descriptor(JarContents contents);

    /**
     * Builds the jar metadata for a jar following the normal rules for Java jars.
     *
     * <p>If the jar has a {@code module-info.class} file, the module info is read from there.
     * Otherwise, the jar is an automatic module, whose name is optionally derived
     * from {@code Automatic-Module-Name} in the manifest.
     */
    static JarMetadata from(JarContents jar) {
        var moduleInfoResource = jar.get("module-info.class");
        if (moduleInfoResource != null) {
            return new ModuleJarMetadata(moduleInfoResource);
        } else {
            return AutomaticModuleJarMetadata.from(jar);
        }
    }
}
