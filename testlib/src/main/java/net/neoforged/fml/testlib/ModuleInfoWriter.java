/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

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

    /**
     * Writes a module-info.class, but omits the package list attribute (which is optional), to test
     * our code for reconstructing it.
     */
    public static byte[] toByteArrayWithoutPackages(ModuleDescriptor descriptor) {
        return toByteArray(descriptor, false);
    }

    public static byte[] toByteArray(ModuleDescriptor descriptor) {
        return toByteArray(descriptor, true);
    }

    private static byte[] toByteArray(ModuleDescriptor descriptor, boolean withPackages) {
        ClassWriter cw = new ClassWriter(0);
        String internalName = "module-info";
        cw.visit(Opcodes.V11, Opcodes.ACC_MODULE, internalName, null, null, null);

        ModuleVisitor mv = cw.visitModule(descriptor.name(), mask(descriptor.accessFlags()), descriptor.rawVersion().orElse(null));

        descriptor.requires().forEach(req -> {
            mv.visitRequire(req.name(), mask(req.accessFlags()), req.compiledVersion().map(Object::toString).orElse(null));
        });

        descriptor.exports().forEach(exp -> mv.visitExport(toBinaryName(exp.source()), mask(exp.accessFlags()), exp.targets() == null ? null : exp.targets().toArray(new String[0])));

        descriptor.opens().forEach(open -> mv.visitOpen(toBinaryName(open.source()), mask(open.accessFlags()), open.targets() == null ? null : open.targets().toArray(new String[0])));

        descriptor.uses().forEach(use -> mv.visitUse(toBinaryName(use)));

        descriptor.provides().forEach(prov -> mv.visitProvide(toBinaryName(prov.service()),
                prov.providers().stream().map(ModuleInfoWriter::toBinaryName).toArray(String[]::new)));

        if (withPackages) {
            descriptor.packages().forEach(p -> mv.visitPackage(toBinaryName(p)));
        }

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

    private static String toBinaryName(String name) {
        return name.replace('.', '/');
    }

    private static int mask(Set<AccessFlag> flags) {
        return flags.stream().mapToInt(AccessFlag::mask).reduce(0, (a, b) -> a | b);
    }
}
