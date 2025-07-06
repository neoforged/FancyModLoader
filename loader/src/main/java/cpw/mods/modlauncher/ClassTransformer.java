/*
 * ModLauncher - for launching Java programs with in-flight transformation ability.
 * Copyright (C) 2017-2019 cpw
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package cpw.mods.modlauncher;

import static cpw.mods.modlauncher.LogMarkers.MODLAUNCHER;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.neoforged.neoforgespi.transformation.IClassProcessor;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

/**
 * Transforms classes using the supplied launcher services
 */
public class ClassTransformer {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Marker CLASSDUMP = MarkerManager.getMarker("CLASSDUMP");
    private final TransformStore transformers;
    private final TransformingClassLoader transformingClassLoader;
    private final TransformerAuditTrail auditTrail;

    ClassTransformer(final TransformStore transformStore, final TransformingClassLoader transformingClassLoader, final TransformerAuditTrail auditTrail) {
        this.transformers = transformStore;
        this.transformingClassLoader = transformingClassLoader;
        this.transformers.initializeBytecodeProvider(name -> className -> transformingClassLoader.buildTransformedClassNodeFor(className, name));
        this.auditTrail = auditTrail;
    }

    byte[] transform(byte[] inputClass, String className, final String upToTransformer) {
        final String internalName = className.replace('.', '/');
        final Type classDesc = Type.getObjectType(internalName);
        
        var transformersToUse = this.transformers.transformersFor(classDesc, inputClass.length == 0, upToTransformer);
        if (transformersToUse.isEmpty()) {
            return inputClass;
        }

        ClassNode clazz = new ClassNode(Opcodes.ASM9);
        if (inputClass.length > 0) {
            final ClassReader classReader = new ClassReader(inputClass);
            classReader.accept(clazz, ClassReader.EXPAND_FRAMES);
        } else {
            clazz.name = classDesc.getInternalName();
            clazz.version = Opcodes.V1_8;
            clazz.superName = Type.getInternalName(Object.class);
        }
        
        boolean allowsComputeFrames = false;
        
        int flags = 0;
        for (var transformer : transformersToUse) {
            if (IClassProcessor.COMPUTING_FRAMES.equals(transformer.name())) {
                allowsComputeFrames = true;
            } else {
                auditTrail.addClassProcessor(classDesc.getClassName(), transformer);
            }
            flags |= transformer.processClassWithFlags(clazz, classDesc);
            if (!allowsComputeFrames) {
                if ((flags & IClassProcessor.ComputeFlags.COMPUTE_FRAMES) != 0) {
                    LOGGER.error(MODLAUNCHER, "Transformer {} requested COMPUTE_FRAMES but is not allowed to do so as it runs before transformer "+ IClassProcessor.COMPUTING_FRAMES, transformer.name());
                    throw new IllegalStateException("Transformer " + transformer.name() + " requested COMPUTE_FRAMES but is not allowed to do so as it runs before frame information is available"+ IClassProcessor.COMPUTING_FRAMES);
                }
            }
        }
        
        if (flags == 0) {
            return inputClass; // No changes were made, return the original class
        }

        final ClassWriter cw = TransformerClassWriter.createClassWriter(flags, this, clazz);
        clazz.accept(cw);
        // if upToTransformer is null, we are doing this for classloading purposes
        if (LOGGER.isEnabled(Level.TRACE) && upToTransformer == null && LOGGER.isEnabled(Level.TRACE, CLASSDUMP)) {
            dumpClass(cw.toByteArray(), className);
        }
        return cw.toByteArray();
    }

    private static Path tempDir;

    private void dumpClass(final byte[] clazz, String className) {
        if (tempDir == null) {
            synchronized (ClassTransformer.class) {
                if (tempDir == null) {
                    try {
                        tempDir = Files.createTempDirectory("classDump");
                    } catch (IOException e) {
                        LOGGER.error(MODLAUNCHER, "Failed to create temporary directory");
                        return;
                    }
                }
            }
        }
        try {
            final Path tempFile = tempDir.resolve(className + ".class");
            Files.write(tempFile, clazz);
            LOGGER.info(MODLAUNCHER, "Wrote {} byte class file {} to {}", clazz.length, className, tempFile);
        } catch (IOException e) {
            LOGGER.error(MODLAUNCHER, "Failed to write class file {}", className, e);
        }
    }

    TransformingClassLoader getTransformingClassLoader() {
        return transformingClassLoader;
    }
}
