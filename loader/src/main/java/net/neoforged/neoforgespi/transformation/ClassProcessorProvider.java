/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.transformation;

import java.util.function.Function;

public interface ClassProcessorProvider {
    interface ClassProcessorCollector {
        void add(ClassProcessorMetadata metadata, Function<ClassProcessor.InitializationContext, ClassProcessorBehavior> factory);

        void add(ClassProcessor processor);
    }

    void makeProcessors(ClassProcessorCollector collector);
}
