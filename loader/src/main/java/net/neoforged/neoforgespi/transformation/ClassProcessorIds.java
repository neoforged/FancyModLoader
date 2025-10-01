/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.transformation;

import net.neoforged.fml.coremod.CoreMod;

/**
 * Standard names for built-in processors.
 */
public final class ClassProcessorIds {
    /**
     * A dummy processor used to order processors relative to frame computation; anything that requires frame
     * re-computation should run after this, and anything providing information that should be available for frame
     * computation should run before this. Thus, any processor that returns {@link ClassProcessorBehavior.ComputeFlags#COMPUTE_FRAMES}
     * <em>must</em> run after this processor.
     */
    public static final ProcessorName COMPUTING_FRAMES = new ProcessorName("neoforge", "computing_frames");
    /**
     * A dummy processor acting as a default group for processors provided by an {@link CoreMod}.
     */
    public static final ProcessorName COREMODS_GROUP = new ProcessorName("neoforge", "coremods_default");
    public static final ProcessorName RUNTIME_ENUM_EXTENDER = new ProcessorName("neoforge", "runtime_enum_extender");
    public static final ProcessorName ACCESS_TRANSFORMERS = new ProcessorName("neoforge", "access_transformer");
    public static final ProcessorName MIXIN = new ProcessorName("neoforge", "mixin");
    public static final ProcessorName DIST_CLEANER = new ProcessorName("neoforge", "neoforge_dev_dist_cleaner");

    private ClassProcessorIds() {}
}
