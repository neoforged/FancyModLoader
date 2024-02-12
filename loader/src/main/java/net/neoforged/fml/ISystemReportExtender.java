/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.Supplier;

@ApiStatus.OverrideOnly
public interface ISystemReportExtender extends Supplier<String>
{
    String getLabel();

    default boolean isActive() {
        return true;
    }
}
