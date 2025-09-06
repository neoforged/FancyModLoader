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

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerActivity;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Transforms classes using the supplied launcher services
 */
@ApiStatus.Internal
public class ClassTransformer {
    private static final byte[] EMPTY = new byte[0];
    private static final Logger LOGGER = LogManager.getLogger();
    private final Marker CLASSDUMP = MarkerManager.getMarker("CLASSDUMP");
    private final TransformStore transformers;
    private final LaunchPluginHandler pluginHandler;
    private final TransformerAuditTrail auditTrail;
    private final AtomicLong transformedClasses = new AtomicLong();
    private final AtomicLong classParsingTime = new AtomicLong();
    private final AtomicLong classTransformingTime = new AtomicLong();

    public ClassTransformer(TransformStore transformStore, LaunchPluginHandler pluginHandler) {
        this(transformStore, pluginHandler, new TransformerAuditTrail());
    }

    public ClassTransformer(final TransformStore transformStore, final LaunchPluginHandler pluginHandler, final TransformerAuditTrail auditTrail) {
        this.transformers = Objects.requireNonNull(transformStore, "transformStore");
        this.pluginHandler = Objects.requireNonNull(pluginHandler, "pluginHandler");
        this.auditTrail = Objects.requireNonNull(auditTrail, "auditTrail");
    }

    public byte[] transform(TransformingClassLoader loader, byte[] inputClass, String className, String reason) {
        final String internalName = className.replace('.', '/');
        final Type classDesc = Type.getObjectType(internalName);

        var launchPluginTransformerSet = pluginHandler.computeLaunchPluginTransformerSet(classDesc, inputClass.length == 0, reason, this.auditTrail);

        var classTransforms = transformers.getClassTransforms(className);
        if (classTransforms == null && launchPluginTransformerSet.isEmpty()) {
            return inputClass;
        }

        transformedClasses.incrementAndGet();

        ClassNode clazz;
        Supplier<byte[]> digest;
        boolean empty;
        long classParseStart = System.nanoTime();
        try {
            clazz = new ClassNode(Opcodes.ASM9);
            if (inputClass.length > 0) {
                final ClassReader classReader = new ClassReader(inputClass);
                classReader.accept(clazz, ClassReader.EXPAND_FRAMES);
                digest = () -> getSha256().digest(inputClass);
                empty = false;
            } else {
                clazz.name = classDesc.getInternalName();
                clazz.version = 52;
                clazz.superName = "java/lang/Object";
                digest = () -> getSha256().digest(EMPTY);
                empty = true;
            }
        } finally {
            classParsingTime.addAndGet(System.nanoTime() - classParseStart);
        }
        auditTrail.addReason(classDesc.getClassName(), reason);

        long transformStart = System.nanoTime();
        try {

            final int preFlags = pluginHandler.offerClassNodeToPlugins(ILaunchPluginService.Phase.BEFORE, launchPluginTransformerSet.getOrDefault(ILaunchPluginService.Phase.BEFORE, Collections.emptyList()), clazz, classDesc, auditTrail, reason);
            if (preFlags == ILaunchPluginService.ComputeFlags.NO_REWRITE && classTransforms == null && launchPluginTransformerSet.getOrDefault(ILaunchPluginService.Phase.AFTER, Collections.emptyList()).isEmpty()) {
                // Shortcut if there's no further work to do
                return inputClass;
            }

            if (classTransforms != null) {
                VotingContext context = new VotingContext(className, empty, digest, auditTrail.getActivityFor(className), reason);

                clazz = this.performVote(classTransforms.preTransformers, clazz, context);

                List<FieldNode> fieldList = new ArrayList<>(clazz.fields.size());
                // it's probably possible to inject "dummy" fields into this list for spawning new fields without class transform
                for (FieldNode field : clazz.fields) {
                    var fieldTransformers = classTransforms.getForField(field.name, field.desc);
                    fieldList.add(this.performVote(fieldTransformers, field, context));
                }

                // it's probably possible to inject "dummy" methods into this list for spawning new methods without class transform
                List<MethodNode> methodList = new ArrayList<>(clazz.methods.size());
                for (MethodNode method : clazz.methods) {
                    var methodTransformers = classTransforms.getForMethod(method.name, method.desc);
                    methodList.add(this.performVote(methodTransformers, method, context));
                }

                clazz.fields = fieldList;
                clazz.methods = methodList;
                clazz = this.performVote(classTransforms.postTransformers, clazz, context);
            }

            final int postFlags = pluginHandler.offerClassNodeToPlugins(ILaunchPluginService.Phase.AFTER, launchPluginTransformerSet.getOrDefault(ILaunchPluginService.Phase.AFTER, Collections.emptyList()), clazz, classDesc, auditTrail, reason);
            if (preFlags == ILaunchPluginService.ComputeFlags.NO_REWRITE && postFlags == ILaunchPluginService.ComputeFlags.NO_REWRITE && classTransforms == null) {
                return inputClass;
            }

            //Transformers always get compute_frames
            int mergedFlags = classTransforms != null ? ILaunchPluginService.ComputeFlags.COMPUTE_FRAMES : (postFlags | preFlags);

            //Don't compute frames when loading for frame computation to avoid cycles. The byte data will only be used for computing frames anyway
            if (reason.equals(ITransformerActivity.COMPUTING_FRAMES_REASON))
                mergedFlags &= ~ILaunchPluginService.ComputeFlags.COMPUTE_FRAMES;

            final ClassWriter cw = TransformerClassWriter.createClassWriter(mergedFlags, loader, clazz);
            clazz.accept(cw);
            if (LOGGER.isEnabled(Level.TRACE) && ITransformerActivity.CLASSLOADING_REASON.equals(reason) && LOGGER.isEnabled(Level.TRACE, CLASSDUMP)) {
                dumpClass(cw.toByteArray(), className);
            }
            return cw.toByteArray();
        } finally {
            classTransformingTime.addAndGet(System.nanoTime() - transformStart);
        }
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

    private <T> T performVote(List<ITransformer<T>> transformers, T node, VotingContext context) {
        if (transformers.isEmpty()) {
            return node;
        }

        transformers = new ArrayList<>(transformers);

        context.setNode(node);
        do {
            final Stream<TransformerVote<T>> voteResultStream = transformers.stream().map(t -> gatherVote(t, context));
            final Map<TransformerVoteResult, List<TransformerVote<T>>> results = voteResultStream.collect(Collectors.groupingBy(TransformerVote::getResult));
            // Someone rejected the current state. We're done here, and cannot proceed.
            if (results.containsKey(TransformerVoteResult.REJECT)) {
                throw new VoteRejectedException(results.get(TransformerVoteResult.REJECT), node.getClass());
            }
            // Remove all the "NO" voters - they don't wish to participate in further voting rounds
            if (results.containsKey(TransformerVoteResult.NO)) {
                transformers.removeAll(results.get(TransformerVoteResult.NO).stream().map(TransformerVote::getTransformer).collect(Collectors.toList()));
            }
            // If there's at least one YES voter, let's apply the first one we find, remove them, and continue.
            if (results.containsKey(TransformerVoteResult.YES)) {
                var transformer = results.get(TransformerVoteResult.YES).get(0).getTransformer();
                node = transformer.transform(node, context);
                var owner = Objects.requireNonNullElse(this.transformers.getOwner(transformer), "unknown");
                auditTrail.addTransformerAuditTrail(context.getClassName(), owner, transformer);
                transformers.remove(transformer);
                continue;
            }
            // If we get here and find a DEFER, it means everyone just voted to DEFER. That's an untenable state and we cannot proceed.
            if (results.containsKey(TransformerVoteResult.DEFER)) {
                throw new VoteDeadlockException(results.get(TransformerVoteResult.DEFER), node.getClass());
            }
        } while (!transformers.isEmpty());
        return node;
    }

    private <T> TransformerVote<T> gatherVote(ITransformer<T> transformer, VotingContext context) {
        TransformerVoteResult vr = transformer.castVote(context);
        return new TransformerVote<>(vr, transformer);
    }

    private MessageDigest getSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("HUH");
        }
    }

    public long getTransformedClasses() {
        return transformedClasses.get();
    }

    public long getClassParsingTime() {
        return classParsingTime.get();
    }

    public long getClassTransformingTime() {
        return classTransformingTime.get();
    }

    public TransformerAuditTrail getAuditTrail() {
        return auditTrail;
    }

    public List<ITransformer<?>> getTransformers() {
        return List.copyOf(transformers.getTransformers());
    }
}
