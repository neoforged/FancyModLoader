/*
 * See LICENSE-securejarhandler for licensing details.
 */

package net.neoforged.fml.classloading;

import net.neoforged.fml.jarcontents.JarContents;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

public record JarMetadata(String moduleName, @Nullable String version, Supplier<ModuleDescriptor> descriptor) {
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
            byte[] originalDescriptorBytes;
            try {
                originalDescriptorBytes = moduleInfoResource.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read module-info.class from " + jar, e);
            }
            var originalDescriptor = ModuleDescriptor.read(ByteBuffer.wrap(originalDescriptorBytes));
            var name = originalDescriptor.name();
            var version = originalDescriptor.rawVersion().orElse(null);
            return new JarMetadata(name, version, () -> {
                var fullDescriptor = ModuleDescriptor.read(ByteBuffer.wrap(originalDescriptorBytes), () -> ModuleDescriptorFactory.scanModulePackages(jar));

                // We do inherit the name and version, as well as the package list.
                var builder = ModuleDescriptor.newAutomaticModule(fullDescriptor.name());
                fullDescriptor.rawVersion().ifPresent(builder::version);
                builder.packages(fullDescriptor.packages());
                fullDescriptor.provides().forEach(builder::provides);

                return builder.build();
            });
        } else {
            var nav = ModuleDescriptorFactory.computeNameAndVersion(jar.getPrimaryPath());
            String name = nav.name();
            String version = nav.version();

            String automaticModuleName = jar.getManifest().getMainAttributes().getValue("Automatic-Module-Name");
            if (automaticModuleName != null) {
                name = automaticModuleName;
            }

            var effectiveName = name;
            return new JarMetadata(name, version, () -> {
                var bld = ModuleDescriptor.newAutomaticModule(effectiveName);
                if (version != null) {
                    bld.version(version);
                }

                ModuleDescriptorFactory.scanAutomaticModule(jar, bld);
                return bld.build();
            });
        }
    }

}
