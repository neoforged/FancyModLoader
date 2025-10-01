/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.transformation;

import java.util.Set;

public interface ClassProcessorMetadata {
    String GENERATED_PACKAGE_MODULE = "net.neoforged.fml.generated";

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
     * {@link ClassProcessorIds#COMPUTING_FRAMES} if the processor returns a result requiring frame re-computation.
     */
    default Set<ProcessorName> runsAfter() {
        return Set.of(ClassProcessorIds.COMPUTING_FRAMES);
    }

    /**
     * {@return packages that this processor generates classes for, that do not already exist on the game layer}
     * Generated packages in the game layer will be in the module {@value ClassProcessorMetadata#GENERATED_PACKAGE_MODULE}.
     */
    default Set<String> generatesPackages() {
        return Set.of();
    }
}
