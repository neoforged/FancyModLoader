/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.LazyJarMetadata;
import cpw.mods.jarhandling.SecureJar;
import java.lang.module.ModuleDescriptor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.neoforged.neoforgespi.locating.IModFile;

public final class ModJarMetadata extends LazyJarMetadata implements JarMetadata {
    private final JarContents container;
    private IModFile modFile;

    public ModJarMetadata(JarContents container) {
        this.container = container;
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
        Set<String> packages = new HashSet<>();
        List<SecureJar.Provider> serviceProviders = new ArrayList<>();
        JarMetadata.indexJarContent(container, packages, serviceProviders);

        var bld = ModuleDescriptor.newAutomaticModule(name())
                .version(version())
                .packages(packages);
        serviceProviders.stream()
                .filter(p -> !p.providers().isEmpty())
                .forEach(p -> bld.provides(p.serviceName(), p.providers()));
        modFile.getModFileInfo().usesServices().forEach(bld::uses);
        return bld.build();
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
