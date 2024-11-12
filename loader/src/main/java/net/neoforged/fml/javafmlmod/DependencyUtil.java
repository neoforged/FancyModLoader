/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.javafmlmod;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import net.neoforged.fml.common.ChainDependency;
import net.neoforged.fml.common.Dependency;

public final class DependencyUtil {
    public static Boolean evaluateChain(List<ChainDependency> chain, Collection<String> loadedMods) {
        var matches = true;
        ChainDependency.Operator previousOperator = null;
        for (ChainDependency chainDep : chain) {
            if (previousOperator == null) {
                matches = evaluateDependency(chainDep.value(), loadedMods);
                previousOperator = chainDep.operator();
                continue;
            }

            switch (previousOperator) {
                case AND -> matches = matches && evaluateDependency(chainDep.value(), loadedMods);
                case OR -> matches = matches || evaluateDependency(chainDep.value(), loadedMods);
            }

            previousOperator = chainDep.operator();
        }

        return matches;
    }

    public static Boolean evaluateDependency(Dependency dep, Collection<String> loadedMods) {
        return switch (dep.condition()) {
            case ALL_PRESENT -> Arrays.stream(dep.value()).allMatch(loadedMods::contains);
            case AT_LEAST_ONE_PRESENT -> Arrays.stream(dep.value()).anyMatch(loadedMods::contains);
            case NONE_PRESENT -> Arrays.stream(dep.value()).noneMatch(loadedMods::contains);
            case AT_LEAST_ONE_IS_NOT_PRESENT -> Arrays.stream(dep.value()).anyMatch((modId) -> !loadedMods.contains(modId));
        };
    }
}
