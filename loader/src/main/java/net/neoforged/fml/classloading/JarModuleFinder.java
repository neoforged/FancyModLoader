/*
 * See LICENSE-securejarhandler for licensing details.
 */

package net.neoforged.fml.classloading;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JarModuleFinder implements ModuleFinder {
    private final Map<String, ModuleReference> moduleReferenceMap;

    JarModuleFinder(SecureJar... jars) {
        this.moduleReferenceMap = Arrays.stream(jars)
                // Computing the module descriptor can be slow so do it in parallel!
                // Jars are not thread safe internally, but they are independent, so this is safe.
                .parallel()
                // Note: Collectors.toMap() works fine with parallel streams.
                .collect(Collectors.toMap(jar -> jar.moduleDataProvider().name(), jar -> new JarModuleReference(jar.moduleDataProvider()), (r1, r2) -> r1));
    }

    @Override
    public Optional<ModuleReference> find(String name) {
        return Optional.ofNullable(moduleReferenceMap.get(name));
    }

    @Override
    public Set<ModuleReference> findAll() {
        return new HashSet<>(moduleReferenceMap.values());
    }

    public static JarModuleFinder of(SecureJar... jars) {
        return new JarModuleFinder(jars);
    }

    static class JarModuleReference extends ModuleReference {
        private final SecureJar.ModuleDataProvider jar;

        JarModuleReference(SecureJar.ModuleDataProvider jar) {
            super(jar.descriptor(), jar.uri());
            this.jar = jar;
        }

        @Override
        public ModuleReader open() throws IOException {
            return new JarModuleReader(this.jar);
        }

        public SecureJar.ModuleDataProvider jar() {
            return this.jar;
        }
    }

    static class JarModuleReader implements ModuleReader {
        private final SecureJar.ModuleDataProvider jar;

        public JarModuleReader(SecureJar.ModuleDataProvider jar) {
            this.jar = jar;
        }

        @Override
        public Optional<URI> find(String name) throws IOException {
            return jar.findFile(name);
        }

        @Override
        public Optional<InputStream> open(String name) throws IOException {
            return jar.open(name);
        }

        @Override
        public Stream<String> list() throws IOException {
            return null;
        }

        @Override
        public void close() throws IOException {}

        @Override
        public String toString() {
            return this.getClass().getName() + "[jar=" + jar + "]";
        }
    }
}
