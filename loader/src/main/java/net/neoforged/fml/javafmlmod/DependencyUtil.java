/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.javafmlmod;

import java.util.Arrays;
import java.util.Collection;
import net.neoforged.fml.common.DependsOn;

public final class DependencyUtil {
    public static Boolean evaluateChain(DependsOn.List chain, Collection<String> loadedMods) {
        if (chain == null) return true;

        return evaluateChain(chain.value(), loadedMods);
    }

    public static Boolean evaluateChain(DependsOn[] chain, Collection<String> loadedMods) {
        var matches = true;
        DependsOn.Operation previousOperation = null;
        for (DependsOn dep : chain) {
            if (previousOperation == null) {
                matches = evaluateDependency(dep, loadedMods);
                previousOperation = dep.operation();
                continue;
            }

            switch (previousOperation) {
                case AND -> matches = matches && evaluateDependency(dep, loadedMods);
                case OR -> matches = matches || evaluateDependency(dep, loadedMods);
            }

            previousOperation = dep.operation();
        }

        return matches;
    }

    public static Boolean evaluateDependency(DependsOn dep, Collection<String> loadedMods) {
        var condition = switch (dep.condition()) {
            case AND -> Arrays.stream(dep.value()).allMatch(loadedMods::contains);
            case OR -> Arrays.stream(dep.value()).anyMatch(loadedMods::contains);
        };

        if (dep.negateCondition()) {
            return !condition;
        } else return condition;
    }
}
