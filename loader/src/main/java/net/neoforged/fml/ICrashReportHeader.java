/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import java.util.function.Supplier;

/**
 * Supplies a header to add to crash reports.
 * @see CrashReportCallables#registerHeader(Supplier)
 */
@FunctionalInterface
public interface ICrashReportHeader {
    /**
     * {@return the header to be displayed at the top of the crash report}
     */
    String getHeader();
}
