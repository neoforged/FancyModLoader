/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.common.asm.enumextension;

/**
 * To be implemented on vanilla enums that should be enhanced with ASM to be
 * extensible. If this is implemented on a class, the class must define a static
 * method called {@code getExtensionInfo()} which takes zero args and returns an {@link ExtensionInfo}.
 * By default, the method should throw to make sure the enum was handled by the transformer.
 *
 * {@snippet :
 * public static ExtensionInfo getExtensionInfo() {
 *     throw new IllegalStateException("Enum not transformed");
 * }
 * }
 *
 * The method contents will be replaced with ASM at runtime to return information about the extension of the enum
 */
public interface IExtensibleEnum {}
