/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.transformation;

import java.util.Map;
import org.jetbrains.annotations.ApiStatus;

/**
 * The context provided to {@linkplain ClassProcessor class processors}
 * when they are linked with a bytecode source.
 */
@ApiStatus.NonExtendable
public interface ClassProcessorLinkContext {
    /**
     * {@return an immutable map of the processors that are being linked. The maps iteration order is the order in which the processors will be applied}
     */
    Map<ProcessorName, ClassProcessor> processors();

    /**
     * {@return a provider to access class bytecode for other classes than the class that is being transformed}
     */
    BytecodeProvider bytecodeProvider();
}
