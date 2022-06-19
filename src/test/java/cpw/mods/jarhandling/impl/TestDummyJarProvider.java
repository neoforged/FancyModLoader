package cpw.mods.jarhandling.impl;

import cpw.mods.cl.JarModuleFinder;
import cpw.mods.cl.ModuleClassLoader;
import cpw.mods.jarhandling.SecureJar;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

public class TestDummyJarProvider {
    @Test
    public void testDummySecureJar() {
        final var jmf = JarModuleFinder.of(new DummyJar());
        final var cf = Configuration.resolveAndBind(jmf, List.of(ModuleLayer.boot().configuration()), ModuleFinder.of(), List.of("testdummy"));
        final var mcl = new ModuleClassLoader("TEST", cf, List.of(ModuleLayer.boot()));
        final var ml = ModuleLayer.defineModules(cf, List.of(ModuleLayer.boot()), f->mcl);
        final var mod = ml.layer().findModule("testdummy").orElseThrow();
        final var rmod = cf.findModule("testdummy").orElseThrow();
        final var clz = Class.forName(mod, "test.dummy.Fish");
        assertEquals(mod, clz.getModule());
        assertEquals("testdummy", clz.getModule().getName());
    }

    private static byte[] makeClass(final String cname) {
        var cn = new ClassNode(Opcodes.ASM9);
        cn.visit(60, Opcodes.ACC_PUBLIC, cname, null, "java/lang/Object", null);
        var cw = new ClassWriter(Opcodes.ASM9);
        cn.accept(cw);
        return cw.toByteArray();
    }
    record TestModuleProvider() implements SecureJar.ModuleDataProvider {
        @Override
        public String name() {
            return "testdummy";
        }

        @Override
        public ModuleDescriptor descriptor() {
            return ModuleDescriptor.newOpenModule("testdummy").version("1.0").packages(Set.of("test.dummy")).build();
        }

        @Override
        public URI uri() {
            return URI.create("http://pants/");
        }

        @Override
        public Optional<URI> findFile(final String name) {
            return Optional.empty();
        }

        @Override
        public Optional<InputStream> open(final String name) {
            return Optional.of(new ByteArrayInputStream(makeClass(name.substring(0, name.length()-6))));
        }

        @Override
        public Manifest getManifest() {
            return null;
        }

        @Override
        public CodeSigner[] verifyAndGetSigners(final String cname, final byte[] bytes) {
            return new CodeSigner[0];
        }
    }
    record DummyJar() implements SecureJar {
        @Override
        public ModuleDataProvider moduleDataProvider() {
            return new TestModuleProvider();
        }

        @Override
        public Path getPrimaryPath() {
            return null;
        }

        @Override
        public CodeSigner[] getManifestSigners() {
            return new CodeSigner[0];
        }

        @Override
        public Status verifyPath(final Path path) {
            return null;
        }

        @Override
        public Status getFileStatus(final String name) {
            return null;
        }

        @Override
        public Attributes getTrustedManifestEntries(final String name) {
            return null;
        }

        @Override
        public boolean hasSecurityData() {
            return false;
        }

        @Override
        public Set<String> getPackages() {
            return null;
        }

        @Override
        public List<Provider> getProviders() {
            return null;
        }

        @Override
        public String name() {
            return null;
        }

        @Override
        public Path getPath(final String first, final String... rest) {
            return null;
        }

        @Override
        public Path getRootPath() {
            return null;
        }
    }
}
