/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.transformation;

import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

/**
 * Class processors, like coremods, provide an API for transforming classes as they are loaded. They are more flexible
 * than coremods, but take more care to use correctly and efficiently. The main pieces of a processor are
 * {@link #handlesClass(SelectionContext)} and {@link #processClass(TransformationContext)} (or {@link #processClass(TransformationContext)}),
 * which allow processors to say whether they want to process a given class and allow them to transform the class.
 * Processors are named and should have sensible namespaces; ordering is accomplished by specifying names that processors
 * should run before or after if present.
 */
public interface ClassProcessor {
    enum ComputeFlags {
        /**
         * This plugin did not change the class and therefor requires no rewrite of the class.
         * This is the fastest option
         */
        NO_REWRITE,
        /**
         * The plugin did change the class and requires a rewrite, but does not require any additional computation
         * as frames and maxs in the class did not change of have been corrected by the plugin.
         */
        SIMPLE_REWRITE,
        /**
         * The plugin did change the class and requires a rewrite, and requires max re-computation,
         * but frames are unchanged or corrected by the plugin
         */
        COMPUTE_MAXS,
        /**
         * The plugin did change the class and requires a rewrite, and requires frame re-computation.
         * This is the slowest, but also the safest method if you don't know what level is required.
         * This implies {@link #COMPUTE_MAXS}, so maxs will also be recomputed.
         */
        COMPUTE_FRAMES
    }

    /**
     * A dummy processor used to order processors relative to frame computation; anything that requires frame
     * re-computation should run after this, and anything providing information that should be available for frame
     * computation should run before this. Thus, any processor that returns {@link ComputeFlags#COMPUTE_FRAMES}
     * <em>must</em> run after this processor.
     */
    ProcessorName COMPUTING_FRAMES = new ProcessorName("neoforge", "computing_frames");

    /**
     * {@return a unique identifier for this processor}
     */
    ProcessorName name();

    /**
     * {@return processors that this processor must run before}
     */
    default Set<ProcessorName> runsBefore() {
        return Set.of();
    }

    /**
     * {@return processors that this processor must run after} This should include
     * {@link ClassProcessor#COMPUTING_FRAMES} if the processor returns a result requiring frame re-computation.
     */
    default Set<ProcessorName> runsAfter() {
        return Set.of(COMPUTING_FRAMES);
    }

    String GENERATED_PACKAGE_MODULE = "net.neoforged.fml.generated";

    /**
     * {@return packages that this processor generates classes for, that do not already exist on the game layer}
     * Generated packages in the game layer will be in the module {@value #GENERATED_PACKAGE_MODULE}.
     */
    default Set<String> generatesPackages() {
        return Set.of();
    }

    /**
     * Context available when determining whether a processor wants to handle a class
     * 
     * @param type  the class to consider
     * @param empty if the class is empty at present (indicates no backing file found)
     */
    record SelectionContext(Type type, boolean empty) {
        @ApiStatus.Internal
        public SelectionContext {}
    }

    interface ClassProcessorLocator {
        Optional<ClassProcessor> find(ProcessorName name);
    }

    interface BytecodeProvider {
        byte[] acquireTransformedClassBefore(final String className) throws ClassNotFoundException;
    }

    /**
     * Context available when initializing or constructing a processor. The {@param bytecodeProvider} and the
     * {@param locator} are not live during initialization, but will work as expected as soon as any other part of the
     * {@link ClassProcessor} API is invoked except for {@link #name()} or {@link #generatesPackages()}.
     * 
     * @param bytecodeProvider allows querying class bytes' states before this processor
     * @param locator          allows locating other class processors
     */
    record InitializationContext(BytecodeProvider bytecodeProvider, ClassProcessorLocator locator) {
        @ApiStatus.Internal
        public InitializationContext {}
    }

    /**
     * Context available when processing a class
     */
    final class TransformationContext {
        private final Type type;
        private final ClassNode node;
        private final boolean empty;
        private final BiConsumer<String, String[]> auditTrail;
        private final Supplier<byte[]> initialSha256;

        @ApiStatus.Internal
        public TransformationContext(Type type, ClassNode node, boolean empty, BiConsumer<String, String[]> auditTrail, Supplier<byte[]> initialSha256) {
            this.type = type;
            this.node = node;
            this.empty = empty;
            this.auditTrail = auditTrail;
            this.initialSha256 = initialSha256;
        }

        public Type type() {
            return type;
        }

        public ClassNode node() {
            return node;
        }

        public boolean empty() {
            return empty;
        }

        public void audit(String activity, String... context) {
            auditTrail.accept(activity, context);
        }

        public byte[] initialSha256() {
            return initialSha256.get();
        }
    }

    /**
     * Context available in post-processing single-instance callbacks
     * 
     * @param type the class that was processed
     */
    record AfterProcessingContext(Type type) {
        @ApiStatus.Internal
        public AfterProcessingContext {}
    }

    /**
     * {@return whether the processor wants to recieve the class}
     * 
     * @param context the context of the class to consider
     */
    boolean handlesClass(SelectionContext context);

    /**
     * Each class that the processor has opted to recieve is passed to this method for processing.
     *
     * @param context the context of the class to process
     * @return the {@link ComputeFlags} indicating how the class should be rewritten.
     */
    ComputeFlags processClass(TransformationContext context);

    /**
     * Where a class may be processed multiple times by the same processor (for example, if in addition to being loaded
     * a later processor requests the state of the given class using a {@link BytecodeProvider}), an after-processing
     * callback is guaranteed to run at most once per class, just before class load. A transformer will only see this
     * context for classes it has processed.
     * 
     * @param context the context of the class that was processed
     */
    default void afterProcessing(AfterProcessingContext context) {}

    /**
     * Capture context available to the provider generally, including a lookup for other processors and a tool to obtain
     * the bytecode of any class before this processor.
     *
     * @param context the context for initialization
     */
    default void initialize(InitializationContext context) {}
}
