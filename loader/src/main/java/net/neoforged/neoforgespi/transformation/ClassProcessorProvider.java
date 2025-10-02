/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.transformation;

import java.util.ServiceLoader;
import org.jetbrains.annotations.ApiStatus;

/**
 * A provider can be implemented and exposed using the Java {@link ServiceLoader} to contribute
 * {@linkplain ClassProcessor class processors} to the launching game. You can also provide {@link ClassProcessor}
 * implementations using service loader directly, but this interface allows the launch environment to be inspected
 * and processors to be added conditionally.
 */
public interface ClassProcessorProvider {
    @ApiStatus.NonExtendable
    interface Collector {
        void add(ClassProcessor processor);
    }

    /**
     * Context about the currently launching game that is passed when processors are collected.
     */
    @ApiStatus.NonExtendable
    interface Context {}

    void makeProcessors(Context context, Collector collector);
}
