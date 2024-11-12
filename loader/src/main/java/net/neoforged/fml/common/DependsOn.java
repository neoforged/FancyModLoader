/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.common;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Repeatable(DependsOn.List.class)
@Retention(RetentionPolicy.RUNTIME)
public @interface DependsOn {
    /**
     * @return the array of mod ids to be checked against the {@link #condition() condition}
     */
    String[] value();

    /**
     * @return The condition for this dependency to be met, either {@link Operation#AND Operation.AND} (All mod ids must be present) or {@link Operation#OR Operation.OR} (At least one mod id must be present)
     */
    Operation condition() default Operation.AND;

    /**
     * @return Whether to negate the {@link #condition() condition} or not
     */
    boolean negateCondition() default false;

    /**
     * @return The operation to be used to combine this dependency and the next, either {@link Operation#AND Operation.AND} (Both dependencies must be met) or {@link Operation#OR Operation.OR} (At least one of the dependencies must be met). The last operation is ignored
     */
    Operation operation() default Operation.AND;

    enum Operation {
        AND, OR
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        DependsOn[] value();
    }
}
