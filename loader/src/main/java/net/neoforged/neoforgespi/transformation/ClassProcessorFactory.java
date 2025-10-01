/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.transformation;

@FunctionalInterface
public interface ClassProcessorFactory {
    ClassProcessor create(ClassProcessorMetadata metadata, BytecodeProvider provider);
}
