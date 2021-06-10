package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;

import java.lang.module.ModuleDescriptor;
import java.util.List;
import java.util.Set;

public record SimpleJarMetadata(String name, String version, Set<String> pkgs, List<SecureJar.Provider> providers) implements JarMetadata {
    @Override
    public ModuleDescriptor descriptor() {
        var bld = ModuleDescriptor.newAutomaticModule(name());
        if (version()!=null)
            bld.version(version());
        bld.packages(pkgs());
        providers.stream().filter(p->!p.providers().isEmpty()).forEach(p->bld.provides(p.serviceName(), p.providers()));
        return bld.build();
    }
}
