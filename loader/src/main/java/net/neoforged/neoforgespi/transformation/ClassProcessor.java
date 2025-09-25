/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.transformation;

import cpw.mods.modlauncher.api.CoreModTransformationContext;
import java.util.Optional;
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
public interface ClassProcessor extends ClassProcessorBehavior, ClassProcessorMetadata {
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

    String GENERATED_PACKAGE_MODULE = "net.neoforged.fml.generated";

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
     * Context available when initializing or constructing a processor.
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
    final class TransformationContext implements CoreModTransformationContext {
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
     * Capture context available to the provider generally, including a lookup for other processors and a tool to obtain
     * the bytecode of any class before this processor. Invoked once per processor, before any methods from
     * {@link ClassProcessorBehavior}.
     *
     * @param context the context for initialization
     */
    default void initialize(InitializationContext context) {}
}
