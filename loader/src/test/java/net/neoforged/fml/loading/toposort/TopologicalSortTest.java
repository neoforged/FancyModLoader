/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.toposort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TopologicalSort} and {@link StronglyConnectedComponentDetector}
 */
@SuppressWarnings("UnstableApiUsage") // suppress warnings from @Beta
class TopologicalSortTest {
    @Test
    void resultProperlySorted() {
        MutableGraph<String> graph = GraphBuilder.directed().build();
        graph.putEdge("a", "b");
        graph.putEdge("a", "c");
        graph.putEdge("b", "c");
        assertThat(TopologicalSort.topologicalSort(graph, Comparator.naturalOrder()))
                .isEqualTo(List.of("a", "b", "c"));
    }

    @Test
    void throwsOnCycle() {
        MutableGraph<String> graph = GraphBuilder.directed().build();
        graph.putEdge("a", "b");
        graph.putEdge("b", "a");
        assertThatThrownBy(() -> TopologicalSort.topologicalSort(graph, Comparator.naturalOrder()))
                .isInstanceOf(CyclePresentException.class)
                .hasMessageContaining("a")
                .hasMessageContaining("b");
    }

    @Test
    void simpleCycle() {
        MutableGraph<String> graph = GraphBuilder.directed().build();
        graph.putEdge("a", "b");
        graph.putEdge("b", "c");
        graph.putEdge("c", "a");

        assertThat(cyclesOf(graph)).containsExactlyInAnyOrder(Set.of("a", "b", "c"));
    }

    @Test
    void noCycle() {
        MutableGraph<String> graph = GraphBuilder.directed().build();
        graph.putEdge("a", "b");
        graph.putEdge("a", "c");
        graph.putEdge("b", "d");
        graph.putEdge("c", "d");
        graph.putEdge("d", "e");

        assertThat(cyclesOf(graph)).isEmpty();
    }

    /**
     * Regression test: the previous implementation lowered the lowlink of a node from every already-visited
     * successor, including successors whose component was already completed. That merged nodes into the wrong
     * component, so a member of the real cycle ({@code c}) was missing from the reported cycle.
     */
    @Test
    void reportsAllMembersOfCycle() {
        MutableGraph<String> graph = GraphBuilder.directed().build();
        graph.addNode("a");
        graph.addNode("b");
        graph.addNode("c");
        graph.putEdge("a", "b");
        graph.putEdge("a", "c");
        graph.putEdge("b", "a");
        graph.putEdge("c", "b");

        assertThat(cyclesOf(graph)).containsExactlyInAnyOrder(Set.of("a", "b", "c"));
    }

    /**
     * Regression test: the previous implementation invented a cycle out of {@code c -> d -> a}, which is a plain
     * path, because it compared against the DFS depth instead of a discovery counter and ignored whether a
     * successor was still on the stack.
     */
    @Test
    void doesNotInventCycles() {
        MutableGraph<String> graph = GraphBuilder.directed().build();
        graph.addNode("a");
        graph.addNode("b");
        graph.addNode("c");
        graph.addNode("d");
        graph.putEdge("a", "b");
        graph.putEdge("b", "a");
        graph.putEdge("c", "d");
        graph.putEdge("d", "a");

        assertThat(cyclesOf(graph)).containsExactlyInAnyOrder(Set.of("a", "b"));
    }

    /**
     * Reproduces the shape of the mod graphs that reported bogus cycles in the wild: one real three-node cycle,
     * with nodes that merely depend on members of the cycle and a node on a plain path into it.
     *
     * <p>Every reported component must be the real cycle itself; nodes such as {@code dependent_a} only depend on
     * the cycle and must not be reported.
     *
     * @see <a href="https://github.com/neoforged/FancyModLoader/issues/471">related GitHub issue</a>
     */
    @Test
    void reportsOnlyRealCycles() {
        MutableGraph<String> graph = GraphBuilder.directed().build();
        // Mirrors the insertion order of ModSorter: every mod is added as a node first, edges are added afterwards.
        for (String mod : List.of("upstream", "cycle_a", "cycle_b", "dependent_a", "cycle_c", "dependent_b")) {
            graph.addNode(mod);
        }
        graph.putEdge("cycle_a", "cycle_b");
        graph.putEdge("cycle_b", "cycle_a");
        graph.putEdge("cycle_a", "cycle_c");
        graph.putEdge("cycle_c", "cycle_b");
        graph.putEdge("dependent_a", "cycle_b");
        graph.putEdge("dependent_b", "cycle_b");
        graph.putEdge("upstream", "cycle_a");

        assertThat(cyclesOf(graph))
                .containsExactlyInAnyOrder(Set.of("cycle_a", "cycle_b", "cycle_c"));

        var exception = catchThrowableOfType(CyclePresentException.class,
                () -> TopologicalSort.topologicalSort(graph, Comparator.naturalOrder()));
        assertThat(exception).isNotNull();
        assertThat(exception.<String>getCycles())
                .containsExactlyInAnyOrder(Set.of("cycle_a", "cycle_b", "cycle_c"));
    }

    private static Set<Set<String>> cyclesOf(Graph<String> graph) {
        Set<Set<String>> cycles = new HashSet<>(new StronglyConnectedComponentDetector<>(graph).getComponents());
        cycles.removeIf(component -> component.size() < 2);
        return cycles;
    }
}
