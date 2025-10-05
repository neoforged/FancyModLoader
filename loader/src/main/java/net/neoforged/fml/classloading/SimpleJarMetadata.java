/*
 * See LICENSE-securejarhandler for licensing details.
 */

package net.neoforged.fml.classloading;

import java.lang.module.ModuleDescriptor;
import net.neoforged.fml.jarcontents.JarContents;
import org.jetbrains.annotations.Nullable;

/**
 * {@link JarMetadata} implementation for a non-modular jar, turning it into an automatic module.
 */
public class SimpleJarMetadata implements JarMetadata {
    private final String name;
    private final String version;
    private final JarContents jar;

    public SimpleJarMetadata(String name, String version, JarContents jar) {
        this.name = name;
        this.version = version;
        this.jar = jar;
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
    public ModuleDescriptor descriptor() {
        var bld = ModuleDescriptor.newAutomaticModule(name());
        if (version() != null) {
            bld.version(version());
        }

        ModuleDescriptorFactory.scanAutomaticModule(jar, bld);

        return bld.build();
    }
}
