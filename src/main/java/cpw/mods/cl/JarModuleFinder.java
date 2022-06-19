package cpw.mods.cl;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.jarhandling.impl.Jar;
import cpw.mods.util.LambdaExceptionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JarModuleFinder implements ModuleFinder {
    private final Map<String, ModuleReference> moduleReferenceMap;

    JarModuleFinder(final SecureJar... jars) {
        record ref(SecureJar.ModuleDataProvider jar, ModuleReference ref) {}
        this.moduleReferenceMap = Arrays.stream(jars)
                .map(jar->new ref(jar.moduleDataProvider(), new JarModuleReference(jar.moduleDataProvider())))
                .collect(Collectors.toMap(r->r.jar.name(), r->r.ref, (r1, r2)->r1));
    }

    @Override
    public Optional<ModuleReference> find(final String name) {
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

        JarModuleReference(final SecureJar.ModuleDataProvider jar) {
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

        public JarModuleReader(final SecureJar.ModuleDataProvider jar) {
            this.jar = jar;
        }

        @Override
        public Optional<URI> find(final String name) throws IOException {
            return jar.findFile(name);
        }

        @Override
        public Optional<InputStream> open(final String name) throws IOException {
            return jar.open(name);
        }

        @Override
        public Stream<String> list() throws IOException {
            return null;
        }

        @Override
        public void close() throws IOException {

        }

        @Override
        public String toString() {
            return this.getClass().getName() + "[jar=" + jar + "]";
        }
    }
}
