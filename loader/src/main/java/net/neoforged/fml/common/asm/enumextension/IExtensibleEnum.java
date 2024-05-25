package net.neoforged.fml.common.asm.enumextension;

/**
 * To be implemented on vanilla enums that should be enhanced with ASM to be
 * extensible. If this is implemented on a class, the class must define a static
 * method called {@code isExtended()} which takes zero args and returns a boolean.
 * By default, the method should throw to make sure the enum was handled by the transformer.
 * <p>
 *
 * <pre>
 * public static boolean isExtended() {
 *     throw new IllegalStateException("Enum not transformed");
 * }
 * </pre>
 *
 * The method contents will be replaced with ASM at runtime to return whether any
 * modded were added to the enum
 */
public interface IExtensibleEnum {}
