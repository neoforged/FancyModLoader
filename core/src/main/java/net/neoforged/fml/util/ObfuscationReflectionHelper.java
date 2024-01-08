/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.util;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import cpw.mods.modlauncher.api.INameMappingService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated Since name remapping is no longer present, use {@link  ReflectionHelper} or any other
 * reflection utility instead.
 */
@SuppressWarnings({"unused"})
@Deprecated(forRemoval = true)
public class ObfuscationReflectionHelper
{
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Marker REFLECTION = MarkerManager.getMarker("REFLECTION");

    /**
     * @deprecated Remapping has been removed, use {@code name} directly.
     */
    @NotNull
    public static String remapName(INameMappingService.Domain domain, String name)
    {
        return name;
    }

    /**
     * @deprecated Use {@link  ReflectionHelper#getPrivateValue}.
     */
    @Nullable
    public static <T, E> T getPrivateValue(Class<? super E> classToAccess, E instance, String fieldName)
    {
        return ReflectionHelper.getPrivateValue(classToAccess, instance, fieldName);
    }

    /**
     * @deprecated Use {@link ReflectionHelper#setPrivateValue}.
     */
    public static <T, E> void setPrivateValue(@NotNull final Class<? super T> classToAccess, @NotNull final T instance, @Nullable final E value, @NotNull final String fieldName)
    {
        ReflectionHelper.setPrivateValue(classToAccess, instance, value, fieldName);
    }

    /**
     * @deprecated Use {@link ReflectionHelper#findMethod}.
     */
    @NotNull
    public static Method findMethod(@NotNull final Class<?> clazz, @NotNull final String methodName, @NotNull final Class<?>... parameterTypes)
    {
        return ReflectionHelper.findMethod(clazz, methodName, parameterTypes);
    }

    /**
     * @deprecated Use {@link ReflectionHelper#findConstructor} instead.
     */
    @NotNull
    public static <T> Constructor<T> findConstructor(@NotNull final Class<T> clazz, @NotNull final Class<?>... parameterTypes)
    {
        return ReflectionHelper.findConstructor(clazz, parameterTypes);
    }

    /**
     * @deprecated Use {@link ReflectionHelper#findField}
     */
    @NotNull
    public static <T> Field findField(@NotNull final Class<? super T> clazz, @NotNull final String fieldName)
    {
        return ReflectionHelper.findField(clazz, fieldName);
    }

    /**
     * @deprecated Use {@link net.neoforged.fml.util.ReflectionHelper.UnableToAccessFieldException} instead.
     */
    @Deprecated(forRemoval = true)
    public static class UnableToAccessFieldException extends RuntimeException
    {
        private UnableToAccessFieldException(Exception e)
        {
            super(e);
        }
    }

    /**
     * @deprecated Use {@link net.neoforged.fml.util.ReflectionHelper.UnableToFindFieldException} instead.
     */
    @Deprecated(forRemoval = true)
    public static class UnableToFindFieldException extends RuntimeException
    {
        private UnableToFindFieldException(Exception e)
        {
            super(e);
        }
    }

    /**
     * @deprecated Use {@link net.neoforged.fml.util.ReflectionHelper.UnableToFindMethodException} instead.
     */
    @Deprecated(forRemoval = true)
    public static class UnableToFindMethodException extends RuntimeException
    {
        public UnableToFindMethodException(Throwable failed)
        {
            super(failed);
        }
    }

    /**
     * @deprecated Use {@link net.neoforged.fml.util.ReflectionHelper.UnknownConstructorException} instead.
     */
    @Deprecated(forRemoval = true)
    public static class UnknownConstructorException extends RuntimeException
    {
        public UnknownConstructorException(final String message)
        {
            super(message);
        }
    }
}
