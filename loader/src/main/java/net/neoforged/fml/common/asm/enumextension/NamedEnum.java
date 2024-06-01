package net.neoforged.fml.common.asm.enumextension;

public @interface NamedEnum {
    /**
     * {@return the parameter index of the name parameter}
     */
    int value() default 0;
}
