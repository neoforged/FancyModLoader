/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.transformation;

public interface ClassProcessorProvider {
    interface ClassProcessorCollector {
        void add(ClassProcessorMetadata metadata, ClassProcessorFactory factory);

        void add(ClassProcessor processor);
    }

    void makeProcessors(ClassProcessorCollector collector);
}
