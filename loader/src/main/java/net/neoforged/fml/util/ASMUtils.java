/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.util;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.Optional;
import org.jetbrains.annotations.ApiStatus;
import sun.misc.Unsafe;

@ApiStatus.Internal
public class ASMUtils {
    private static final Unsafe UNSAFE;
    static {
        try {
            final Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(null);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException("BARF!", e);
        }
    }

    /**
     * This method exist, so that there is no need to make lambdas in asm
     * 
     * @return an Enum Comparator by name
     */
    public static Comparator<Enum<?>> nameComparator() {
        return Comparator.comparing(Enum::name);
    }

    /**
     * This method exist, so that there is no need to make lambdas in asm
     * 
     * @return an Enum Comparator by name
     */
    public static void setOrdinal(Enum<?> value, int ordinal) {
        findField(Enum.class, "ordinal").ifPresent(f -> setIntField(f, value, ordinal));
    }

    public static void setIntField(Field data, Object object, int value) {
        long offset = UNSAFE.objectFieldOffset(data);
        UNSAFE.putInt(object, offset, value);
    }

    // Make sure we don't crash if any future versions change field names
    private static Optional<Field> findField(Class<?> clazz, String name) {
        for (Field f : clazz.getDeclaredFields()) {
            if (f.getName().equals(name)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }
}
