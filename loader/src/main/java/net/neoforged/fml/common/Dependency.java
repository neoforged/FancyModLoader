/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.common;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Dependency {
    /**
     * @return the array of mod ids to be checked against the {@link #condition() condition}
     */
    String[] value();

    Condition condition() default Condition.ALL_PRESENT;

    enum Condition {
        ALL_PRESENT, AT_LEAST_ONE_PRESENT, NONE_PRESENT, AT_LEAST_ONE_IS_NOT_PRESENT
    }
}
