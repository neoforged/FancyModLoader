/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.transformation;

import java.util.Optional;
import org.jetbrains.annotations.ApiStatus;

/**
 * Class processors, like coremods, provide an API for transforming classes as they are loaded. They are more flexible
 * than coremods, but take more care to use correctly and efficiently. The main pieces of a processor are
 * {@link #handlesClass(SelectionContext)} and {@link #processClass(TransformationContext)} (or {@link #processClass(TransformationContext)}),
 * which allow processors to say whether they want to process a given class and allow them to transform the class.
 * Processors are named and should have sensible namespaces; ordering is accomplished by specifying names that processors
 * should run before or after if present.
 */
public interface ClassProcessor extends ClassProcessorBehavior, ClassProcessorMetadata {
    interface ClassProcessorLocator {
        /**
         * {@return a processor with the given name, if it exists}
         * 
         * @param name the processor to find
         */
        Optional<ClassProcessor> find(ProcessorName name);
    }

    interface BytecodeProvider {
        /**
         * Locates the bytecode for a given class. This bytecode is provided in the form that the processor would see,
         * were it transforming that class; any transformers ordered before will have been executed on it already.
         * 
         * @param className the class to locate, in dot-separated form
         * @return the bytecode
         * @throws ClassNotFoundException if the class cannot be found
         */
        byte[] acquireClass(final String className) throws ClassNotFoundException;
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
     * Capture context available to the provider generally, including a lookup for other processors and a tool to obtain
     * the bytecode of any class before this processor. Invoked once per processor, before any methods from
     * {@link ClassProcessorBehavior}.
     *
     * @param context the context for initialization
     */
    default void initialize(InitializationContext context) {}
}
