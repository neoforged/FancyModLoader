/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

@ApiStatus.OverrideOnly
public interface ISystemReportExtender extends Supplier<String> {
    /**
     * {@return the label of the crash report value to add}
     */
    @Nullable
    default String getLabel() {
        return null;
    }

    /**
     * {@return the value to add to the crash report}
     */
    @Nullable
    @Override
    default String get() {
        return null;
    }

    /**
     * {@return a header to add to the crash report}
     */
    @Nullable
    default String getHeader() {
        return null;
    }

    default boolean isActive() {
        return true;
    }
}
