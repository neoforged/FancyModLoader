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

import cpw.mods.modlauncher.api.IEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Supplier;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

/**
 * Transforms classes using the supplied launcher services
 */
@ApiStatus.Internal
public class ClassTransformer {
    private static final byte[] EMPTY = new byte[0];
    private static final Logger LOGGER = LogManager.getLogger();
    private final Marker CLASSDUMP = MarkerManager.getMarker("CLASSDUMP");
    private final TransformStore transformers;
    private final TransformingClassLoader transformingClassLoader;
    private final TransformerAuditTrail auditTrail;

    public ClassTransformer(final TransformStore transformStore, final TransformingClassLoader transformingClassLoader, IEnvironment environment) {
        this(transformStore, transformingClassLoader, new TransformerAuditTrail(), environment);
    }

    public ClassTransformer(final TransformStore transformStore, final TransformingClassLoader transformingClassLoader, final TransformerAuditTrail auditTrail, IEnvironment environment) {
        this.transformers = transformStore;
        this.transformingClassLoader = transformingClassLoader;
        this.transformers.initializeBytecodeProvider(name -> className -> transformingClassLoader.buildTransformedClassNodeFor(className, name), environment);
        this.auditTrail = auditTrail;
    }

    public byte[] transform(byte[] inputClass, String className, final ProcessorName upToTransformer) {
        final String internalName = className.replace('.', '/');
        final Type classDesc = Type.getObjectType(internalName);

        ClassTransformStatistics.incrementLoadedClasses();

        var transformersToUse = this.transformers.transformersFor(classDesc, inputClass.length == 0, upToTransformer);
        if (transformersToUse.isEmpty()) {
            return inputClass;
        }

        ClassTransformStatistics.incrementTransformedClasses();

        Supplier<byte[]> digest;
        ClassNode clazz = new ClassNode(Opcodes.ASM9);
        boolean isEmpty = inputClass.length == 0;
        if (inputClass.length > 0) {
            final ClassReader classReader = new ClassReader(inputClass);
            classReader.accept(clazz, ClassReader.EXPAND_FRAMES);
            digest = () -> getSha256().digest(inputClass);
        } else {
            clazz.name = classDesc.getInternalName();
            clazz.version = Opcodes.V1_8;
            clazz.superName = Type.getInternalName(Object.class);
            digest = () -> getSha256().digest(EMPTY);
        }

        boolean allowsComputeFrames = false;

        int flags = 0;
        for (var transformer : transformersToUse) {
            if (ClassProcessor.COMPUTING_FRAMES.equals(transformer.name())) {
                allowsComputeFrames = true;
            }
            var trail = auditTrail.forClassProcessor(classDesc.getClassName(), transformer);
            var context = new ClassProcessor.TransformationContext(
                    classDesc,
                    clazz,
                    isEmpty,
                    trail,
                    digest);
            var newFlags = transformer.processClassWithFlags(context);
            if (newFlags != ClassProcessor.ComputeFlags.NO_REWRITE) {
                trail.rewrites();
            }
            flags |= newFlags;
            if (flags != 0) {
                isEmpty = false; // If a transformer makes changes, we are no longer empty
            }
            if (!allowsComputeFrames) {
                if ((flags & ClassProcessor.ComputeFlags.COMPUTE_FRAMES) != 0) {
                    LOGGER.error(MODLAUNCHER, "Transformer {} requested COMPUTE_FRAMES but is not allowed to do so as it runs before transformer {}", transformer.name(), ClassProcessor.COMPUTING_FRAMES);
                    throw new IllegalStateException("Transformer " + transformer.name() + " requested COMPUTE_FRAMES but is not allowed to do so as it runs before transformer " + ClassProcessor.COMPUTING_FRAMES);
                }
            }
        }
        if (upToTransformer == null) {
            // run post-result callbacks
            var context = new ClassProcessor.AfterProcessingContext(
                    classDesc);
            for (var transformer : transformersToUse) {
                transformer.afterProcessing(context);
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

    private MessageDigest getSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("HUH");
        }
    }

    TransformingClassLoader getTransformingClassLoader() {
        return transformingClassLoader;
    }
}
