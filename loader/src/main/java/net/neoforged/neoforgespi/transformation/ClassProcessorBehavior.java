/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.transformation;

public interface ClassProcessorBehavior {
    /**
     * {@return whether the processor wants to recieve the class}
     *
     * @param context the context of the class to consider
     */
    boolean handlesClass(ClassProcessor.SelectionContext context);

    /**
     * Each class that the processor has opted to recieve is passed to this method for processing.
     *
     * @param context the context of the class to process
     * @return the {@link ClassProcessor.ComputeFlags} indicating how the class should be rewritten.
     */
    ClassProcessor.ComputeFlags processClass(ClassProcessor.TransformationContext context);

    /**
     * Where a class may be processed multiple times by the same processor (for example, if in addition to being loaded
     * a later processor requests the state of the given class using a {@link ClassProcessor.BytecodeProvider}), an after-processing
     * callback is guaranteed to run at most once per class, just before class load. A transformer will only see this
     * context for classes it has processed.
     *
     * @param context the context of the class that was processed
     */
    default void afterProcessing(ClassProcessor.AfterProcessingContext context) {}
}
