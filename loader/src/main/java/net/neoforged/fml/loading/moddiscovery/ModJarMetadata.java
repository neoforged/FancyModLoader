/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.LazyJarMetadata;
import cpw.mods.jarhandling.impl.ModuleDescriptorFactory;
import java.lang.module.ModuleDescriptor;
import java.util.Objects;
import net.neoforged.neoforgespi.locating.IModFile;

public final class ModJarMetadata extends LazyJarMetadata implements JarMetadata {
    private final JarContents jar;
    private IModFile modFile;

    public ModJarMetadata(JarContents jar) {
        this.jar = jar;
    }

    public void setModFile(IModFile file) {
        this.modFile = file;
    }

    @Override
    public String name() {
        return modFile.getId();
    }

    @Override
    public String version() {
        return modFile.getModFileInfo().versionString();
    }

    @Override
    public ModuleDescriptor computeDescriptor() {
        var bld = ModuleDescriptor.newAutomaticModule(name())
                .version(version());

        ModuleDescriptorFactory.scanAutomaticModule(jar, bld, "assets", "data");

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
