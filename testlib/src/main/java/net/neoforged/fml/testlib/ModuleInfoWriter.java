package net.neoforged.fml.testlib;

import java.lang.module.ModuleDescriptor;
import java.lang.reflect.AccessFlag;
import java.nio.ByteBuffer;
import java.util.Set;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;

public final class ModuleInfoWriter {
    private ModuleInfoWriter() {}

    public static byte[] toByteArray(ModuleDescriptor descriptor) {
        ClassWriter cw = new ClassWriter(0);
        String internalName = "module-info";
        cw.visit(Opcodes.V11, Opcodes.ACC_MODULE, internalName, null, null, null);

        ModuleVisitor mv = cw.visitModule(descriptor.name(), mask(descriptor.accessFlags()), descriptor.rawVersion().orElse(null));

        descriptor.requires().forEach(req -> {
            mv.visitRequire(req.name(), mask(req.accessFlags()), req.compiledVersion().map(Object::toString).orElse(null));
        });

        descriptor.exports().forEach(exp -> mv.visitExport(exp.source(), mask(exp.accessFlags()), exp.targets() == null ? null : exp.targets().toArray(new String[0])));

        descriptor.opens().forEach(open -> mv.visitOpen(open.source(), mask(open.accessFlags()), open.targets() == null ? null : open.targets().toArray(new String[0])));

        descriptor.uses().forEach(use -> mv.visitUse(use.replace('.', '/')));

        descriptor.provides().forEach(prov -> mv.visitProvide(prov.service().replace('.', '/'),
                prov.providers().stream().map(p -> p.replace('.', '/')).toArray(String[]::new)));

        mv.visitEnd();
        cw.visitEnd();
        byte[] data = cw.toByteArray();

        // Validate it actually didn't lose any data
        var writtenDescriptor = ModuleDescriptor.read(ByteBuffer.wrap(data));
        if (!writtenDescriptor.equals(descriptor)) {
            throw new RuntimeException("The written module-descriptor " + writtenDescriptor + " is not equal to the original one " + descriptor);
        }

        return data;
    }

    private static int mask(Set<AccessFlag> flags) {
        return flags.stream().mapToInt(AccessFlag::mask).reduce(0, (a, b) -> a | b);
    }
}
