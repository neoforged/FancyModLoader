/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.LazyJarMetadata;
import java.lang.module.ModuleDescriptor;
import java.util.Objects;
import net.neoforged.neoforgespi.locating.IModFile;

public final class ModJarMetadata extends LazyJarMetadata implements JarMetadata {
    private final JarContents jarContents;
    private IModFile modFile;

    public ModJarMetadata(JarContents jarContents) {
        this.jarContents = jarContents;
    }

    public void setModFile(IModFile file) {
        this.modFile = file;
    }

    @Override
    public String name() {
        return modFile.getModFileInfo().moduleName();
    }

    @Override
    public String version() {
        return modFile.getModFileInfo().versionString();
    }

    @Override
    public ModuleDescriptor computeDescriptor() {
        var bld = ModuleDescriptor.newAutomaticModule(name())
                .version(version())
                .packages(jarContents.getPackagesExcluding("assets", "data"));
        jarContents.getMetaInfServices().stream()
                .filter(p -> !p.providers().isEmpty())
                .forEach(p -> bld.provides(p.serviceName(), p.providers()));
        modFile.getModFileInfo().usesServices().forEach(bld::uses);
        return bld.build();
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
        return "ModJarMetadata[" + "modFile=" + modFile + ']';
    }
}
