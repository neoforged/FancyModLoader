package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.LazyJarMetadata;
import cpw.mods.jarhandling.SecureJar;
import org.jetbrains.annotations.Nullable;

import java.lang.module.ModuleDescriptor;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * {@link JarMetadata} implementation for a non-modular jar, turning it into an automatic module.
 */
public class SimpleJarMetadata extends LazyJarMetadata implements JarMetadata {
    private final String name;
    private final String version;
    private final Supplier<Set<String>> packagesSupplier;
    private final List<SecureJar.Provider> providers;

    public SimpleJarMetadata(String name, String version, Supplier<Set<String>> packagesSupplier, List<SecureJar.Provider> providers) {
        this.name = name;
        this.version = version;
        this.packagesSupplier = packagesSupplier;
        this.providers = providers.stream().filter(p -> !p.providers().isEmpty()).toList();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    @Nullable
    public String version() {
        return version;
    }

    @Override
    public ModuleDescriptor computeDescriptor() {
        var bld = ModuleDescriptor.newAutomaticModule(name());
        if (version()!=null)
            bld.version(version());
        bld.packages(packagesSupplier.get());
        providers.forEach(p->bld.provides(p.serviceName(), p.providers()));
        return bld.build();
    }

    @Override
    public List<SecureJar.Provider> providers() {
        return Collections.unmodifiableList(providers);
    }
}
