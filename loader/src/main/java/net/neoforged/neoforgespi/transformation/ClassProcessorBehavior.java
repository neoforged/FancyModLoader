/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.transformation;

import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.neoforged.fml.coremod.CoreModTransformationContext;
import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

public interface ClassProcessorBehavior {
    /**
     * A dummy processor used to order processors relative to frame computation; anything that requires frame
     * re-computation should run after this, and anything providing information that should be available for frame
     * computation should run before this. Thus, any processor that returns {@link ComputeFlags#COMPUTE_FRAMES}
     * <em>must</em> run after this processor.
     */
    ProcessorName COMPUTING_FRAMES = new ProcessorName("neoforge", "computing_frames");

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
     * @return the {@link ClassProcessor.ComputeFlags} indicating how the class should be rewritten.
     */
    ComputeFlags processClass(TransformationContext context);

    /**
     * Where a class may be processed multiple times by the same processor (for example, if in addition to being loaded
     * a later processor requests the state of the given class using a {@link ClassProcessor.BytecodeProvider}), an after-processing
     * callback is guaranteed to run at most once per class, just before class load. A transformer will only see this
     * context for classes it has processed.
     *
     * @param context the context of the class that was processed
     */
    default void afterProcessing(AfterProcessingContext context) {}

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
     * Context available when determining whether a processor wants to handle a class
     * 
     * @param type  the class to consider
     * @param empty if the class is empty at present (indicates no backing file found)
     */
    record SelectionContext(Type type, boolean empty) {
        @ApiStatus.Internal
        public SelectionContext {}
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
}
