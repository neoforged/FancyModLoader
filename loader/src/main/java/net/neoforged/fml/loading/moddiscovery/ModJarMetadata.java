/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.LazyJarMetadata;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.jarhandling.impl.ModuleJarMetadata;
import net.neoforged.neoforgespi.locating.IModFile;

import java.lang.module.ModuleDescriptor;
import java.util.Objects;
import java.util.Optional;

public final class ModJarMetadata extends LazyJarMetadata implements JarMetadata {
    private static final String MODULE_INFO = "module-info.class";     
    private static final String AUTOMATIC_MODULE_NAME = "Automatic-Module-Name";

    private final JarContents jarContents;
    private IModFile modFile;

    ModJarMetadata(JarContents jarContents) {
        this.jarContents = jarContents;
    }

    public void setModFile(IModFile file) {
        this.modFile = file;
    }

    @Override
    public String name() {
        return descriptor().name();
    }

    @Override
    public String version() {
        return modFile.getModFileInfo().versionString();
    }

    @Override
    public ModuleDescriptor computeDescriptor() {
        SecureJar secureJar = modFile.getSecureJar();
        // Try reading descriptor from module-info.class first
        SecureJar.ModuleDataProvider provider = secureJar.moduleDataProvider();
        return provider.findFile(MODULE_INFO)
            .map(uri -> {
                // Use ModuleJarMetadata to read the descriptor
                JarMetadata metadata = new ModuleJarMetadata(uri, () -> jarContents.getPackagesExcluding("assets", "data"));
                ModuleDescriptor jarDescriptor = metadata.descriptor();
                // Convert descriptor to builder
                var builder = wrapDescriptor(jarDescriptor)
                    // Use version from mod metadata
                    .version(version());
                return builder.build();
            })
            // If the jar does not contain a module descriptor, we build one ourselves 
            .orElseGet(() -> {
                // Use Automatic-Module-Name first, fallback to modid
                var name = Optional.ofNullable(provider.getManifest().getMainAttributes().getValue(AUTOMATIC_MODULE_NAME))
                    .orElseGet(() -> modFile.getModFileInfo().moduleName());
                // Build module descriptor as automatic module. This ensures the module is open, exports all packages and mutually reads all other modules.
                var bld = ModuleDescriptor.newAutomaticModule(name)
                    // Use version from mod metadata
                    .version(version())
                    // Add all packages
                    .packages(jarContents.getPackagesExcluding("assets", "data"));
                // Add service providers
                jarContents.getMetaInfServices().stream()
                    .filter(p -> !p.providers().isEmpty())
                    .forEach(p -> bld.provides(p.serviceName(), p.providers()));
                // Add used services from mod metadata 
                modFile.getModFileInfo().usesServices().forEach(bld::uses);
                return bld.build();
            });
    }

    public IModFile modFile() {
        return modFile;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ModJarMetadata) obj;
        return Objects.equals(this.modFile, that.modFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modFile);
    }

    @Override
    public String toString() {
        return "ModJarMetadata[" +"modFile=" + modFile + ']';
    }

    // Create a module descriptor builder from an existing descriptor, allowing us to modify and re-build it
    private ModuleDescriptor.Builder wrapDescriptor(ModuleDescriptor descriptor) {
        var builder = ModuleDescriptor.newModule(descriptor.name(), descriptor.modifiers());
        builder.packages(descriptor.packages());
        descriptor.version().ifPresent(builder::version);
        descriptor.requires().forEach(builder::requires);
        descriptor.exports().forEach(builder::exports);
        descriptor.opens().forEach(builder::opens);
        descriptor.uses().forEach(builder::uses);
        descriptor.provides().forEach(builder::provides);
        return builder;
    } 
}
