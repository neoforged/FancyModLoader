/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.transformation;

import java.util.function.Function;
import net.neoforged.neoforgespi.ILaunchContext;

public interface ClassProcessorProvider {
    interface ClassProcessorCollector {
        void create(ProcessorName name, Function<ClassProcessor.InitializationContext, ClassProcessor> factory);

        default void add(ClassProcessor processor) {
            create(processor.name(), ctx -> processor);
        }
    }

    void makeProcessors(ClassProcessorCollector collector, ILaunchContext launchContext);
}
