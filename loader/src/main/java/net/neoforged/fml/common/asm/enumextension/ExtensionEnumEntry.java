package net.neoforged.fml.common.asm.enumextension;

import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is added by JST to mark enum entries added by mod enum extensions. This allows for consistent ordering
 * of enum entries whether they are added with JST in source, or at runtime.
 */
@ApiStatus.Internal
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface ExtensionEnumEntry {}
