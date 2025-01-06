package cpw.mods.jarhandling;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Manifest;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of {@link SecureJar} that does not actually contain any files,
 * but still defines packages.
 *
 * <p>This can be used by frameworks that generate classes at runtime, in specific packages,
 * and need to make a {@link SecureJar}-based module system implementation aware of these packages.
 */
public final class VirtualJar implements SecureJar {
    /**
     * Creates a new virtual jar.
     *
     * @param name     the name of the virtual jar; will be used as the module name
     * @param packages the list of packages in this virtual jar
     */
    public VirtualJar(String name, String... packages) {
        this.moduleDescriptor = ModuleDescriptor.newAutomaticModule(name)
                .packages(Set.of(packages))
                .build();
    }

    private final ModuleDescriptor moduleDescriptor;
    private final ModuleDataProvider moduleData = new VirtualJarModuleDataProvider();
    private final Manifest manifest = new Manifest();

    @Override
    public ModuleDataProvider moduleDataProvider() {
        return moduleData;
    }

    @Override
    public Path getPrimaryPath() {
        return Paths.get(name()); // TODO
    }

    @Override
    public JarContents container() {
        return JarContents.empty(Paths.get(""));
    }

    @Override
    public String name() {
        return moduleDescriptor.name();
    }

    @Override
    public Path getPath(String first, String... rest) {
        return Paths.get(first, rest);
    }

    @Override
    public Path getRootPath() {
        throw new RuntimeException(); // TODO
    }

    @Override
    public void close() throws IOException {}

    private class VirtualJarModuleDataProvider implements ModuleDataProvider {
        @Override
        public String name() {
            return VirtualJar.this.name();
        }

        @Override
        public ModuleDescriptor descriptor() {
            return moduleDescriptor;
        }

        @Override
        @Nullable
        public URI uri() {
            return null;
        }

        @Override
        public Optional<URI> findFile(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<InputStream> open(String name) {
            return Optional.empty();
        }

        @Override
        public Manifest getManifest() {
            return manifest;
        }
    }
}
