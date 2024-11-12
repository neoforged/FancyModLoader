/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.common;

public @interface ChainDependency {
    Dependency value();

    Operator operator() default Operator.AND;

    enum Operator {
        AND, OR
    }
}
