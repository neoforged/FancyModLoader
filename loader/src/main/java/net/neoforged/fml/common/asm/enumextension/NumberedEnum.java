package net.neoforged.fml.common.asm.enumextension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
public @interface NumberedEnum {
    /**
     * {@return the parameter index of the ID parameter}
     */
    int value() default 0;
}
