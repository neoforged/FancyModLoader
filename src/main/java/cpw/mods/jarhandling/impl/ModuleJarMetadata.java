package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.JarMetadata;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


public class ModuleJarMetadata implements JarMetadata {
    private final ModuleDescriptor descriptor;

    public ModuleJarMetadata(final URI uri, final Set<String> packages) {
        try (var is = Files.newInputStream(Path.of(uri))) {
            ClassReader cr = new ClassReader(is);
            var mcv = new ModuleClassVisitor();
            cr.accept(mcv, ClassReader.SKIP_CODE);
            mcv.mfv().packages().addAll(packages);
            mcv.mfv().builder().packages(mcv.mfv.packages());
            descriptor = mcv.mfv().builder().build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private class ModuleClassVisitor extends ClassVisitor {
        private ModFileVisitor mfv;

        ModuleClassVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public ModuleVisitor visitModule(final String name, final int access, final String version) {
            this.mfv = new ModFileVisitor(name, access, version);
            return this.mfv;
        }

        public ModFileVisitor mfv() {
            return mfv;
        }
    }
    private class ModFileVisitor extends ModuleVisitor {
        private final ModuleDescriptor.Builder builder;
        private final Set<String> packages = new HashSet<>();

        public ModFileVisitor(final String name, final int access, final String version) {
            super(Opcodes.ASM9);
            builder = ModuleDescriptor.newOpenModule(name);
            if (version != null) builder.version(version);
        }

        @Override
        public void visitExport(final String packaze, final int access, final String... modules) {
            if (modules != null) {
                builder.exports(packaze.replace('/','.'), Set.of(modules));
            } else {
                builder.exports(packaze.replace('/','.'));
            }
        }

        @Override
        public void visitMainClass(final String mainClass) {
            builder.mainClass(mainClass.replace('/','.'));
        }

        @Override
        public void visitOpen(final String packaze, final int access, final String... modules) {
        }

        @Override
        public void visitPackage(final String packaze) {
            packages.add(packaze.replace('/', '.'));
        }

        @Override
        public void visitProvide(final String service, final String... providers) {
            builder.provides(service.replace('/','.'), Arrays.stream(providers).map(s->s.replace('/','.')).toList());
        }

        @Override
        public void visitRequire(final String module, final int access, final String version) {
            Set<ModuleDescriptor.Requires.Modifier> mods;
            if (access == 0)
                mods = Set.of();
            else {
                mods = new HashSet<>();
                if ((access & Opcodes.ACC_TRANSITIVE) != 0)
                    mods.add(ModuleDescriptor.Requires.Modifier.TRANSITIVE);
                if ((access & Opcodes.ACC_STATIC_PHASE) != 0)
                    mods.add(ModuleDescriptor.Requires.Modifier.STATIC);
                if ((access & Opcodes.ACC_SYNTHETIC) != 0)
                    mods.add(ModuleDescriptor.Requires.Modifier.SYNTHETIC);
                if ((access & Opcodes.ACC_MANDATED) != 0)
                    mods.add(ModuleDescriptor.Requires.Modifier.MANDATED);
            }
            if (version!=null) {
                builder.requires(mods, module, ModuleDescriptor.Version.parse(version));
            } else {
                builder.requires(mods, module);
            }
        }

        @Override
        public void visitUse(final String service) {
            builder.uses(service.replace('/','.'));
        }

        ModuleDescriptor.Builder builder() {
            return builder;
        }

        public Set<String> packages() {
            return packages;
        }
    }
    @Override
    public String name() {
        return descriptor.name();
    }

    @Override
    public String version() {
        return descriptor.version().toString();
    }

    @Override
    public ModuleDescriptor descriptor() {
        return descriptor;
    }
}
