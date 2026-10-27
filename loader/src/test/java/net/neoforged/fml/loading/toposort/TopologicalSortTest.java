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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for the topological sort and the strongly connected component detector used to report cycles.
 */
@SuppressWarnings("UnstableApiUsage") // suppress warnings from @Beta
class TopologicalSortTest {
    @Test
    void detectsSimpleCycle() {
        MutableGraph<String> graph = GraphBuilder.directed().build();
        graph.putEdge("a", "b");
        graph.putEdge("b", "c");
        graph.putEdge("c", "a");

        assertThat(cyclesOf(graph)).containsExactlyInAnyOrder(Set.of("a", "b", "c"));
    }

    @Test
    void dagHasNoCycles() {
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
     */
    @Test
    void reportsOnlyTheRealCycleOfAModGraph() {
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

    @Test
    void throwsOnCycleAndSortsDag() {
        MutableGraph<String> dag = GraphBuilder.directed().build();
        dag.putEdge("a", "b");
        dag.putEdge("a", "c");
        dag.putEdge("b", "c");
        assertThat(TopologicalSort.topologicalSort(dag, Comparator.naturalOrder()))
                .containsExactly("a", "b", "c");

        MutableGraph<String> cyclic = GraphBuilder.directed().build();
        cyclic.putEdge("a", "b");
        cyclic.putEdge("b", "a");
        assertThatThrownBy(() -> TopologicalSort.topologicalSort(cyclic, Comparator.naturalOrder()))
                .isInstanceOf(CyclePresentException.class)
                .hasMessageContaining("a")
                .hasMessageContaining("b");
    }

    /**
     * The detector must agree with a reference implementation for every graph, independently of the iteration
     * order of the graph.
     */
    @Test
    void matchesReferenceImplementationOnRandomGraphs() {
        Random random = new Random(0xC0FFEE);
        for (int iteration = 0; iteration < 300; iteration++) {
            int nodeCount = 2 + random.nextInt(9);
            MutableGraph<Integer> graph = GraphBuilder.directed().build();
            for (int node = 0; node < nodeCount; node++) {
                graph.addNode(node);
            }
            for (int from = 0; from < nodeCount; from++) {
                for (int to = 0; to < nodeCount; to++) {
                    if (from != to && random.nextInt(4) == 0) {
                        graph.putEdge(from, to);
                    }
                }
            }

            assertThat(new StronglyConnectedComponentDetector<>(graph).getComponents())
                    .as("components of %s", graph)
                    .isEqualTo(referenceComponents(graph));
        }
    }

    /**
     * The detector's iteration order depends on how nodes and edges were inserted into the graph (which, for mod
     * loading, depends on the identity hash codes of the mods). The reported components must not depend on it.
     */
    @Test
    void componentDetectionIsIndependentOfInsertionOrder() {
        Random random = new Random(1234);
        for (int iteration = 0; iteration < 100; iteration++) {
            int nodeCount = 2 + random.nextInt(6);
            List<Integer> nodes = new ArrayList<>();
            for (int node = 0; node < nodeCount; node++) {
                nodes.add(node);
            }
            List<int[]> edges = new ArrayList<>();
            for (int from = 0; from < nodeCount; from++) {
                for (int to = 0; to < nodeCount; to++) {
                    if (from != to && random.nextBoolean()) {
                        edges.add(new int[] { from, to });
                    }
                }
            }

            List<Integer> firstOrder = List.copyOf(nodes);
            Set<Set<Integer>> expected = new StronglyConnectedComponentDetector<>(
                    buildGraph(firstOrder, edges)).getComponents();
            for (int shuffle = 0; shuffle < 5; shuffle++) {
                List<Integer> order = new ArrayList<>(nodes);
                Collections.shuffle(order, random);
                List<int[]> shuffledEdges = new ArrayList<>(edges);
                Collections.shuffle(shuffledEdges, random);

                assertThat(new StronglyConnectedComponentDetector<>(buildGraph(order, shuffledEdges)).getComponents())
                        .as("components for node order %s and edges %s", order, shuffledEdges)
                        .isEqualTo(expected);
            }
        }
    }

    /**
     * Sorting must throw if and only if the graph really has a cycle, and the reported cycles must be exactly the
     * strongly connected components with more than one node.
     */
    @Test
    void sortingMatchesReferenceCyclesOnRandomGraphs() {
        Random random = new Random(42);
        for (int iteration = 0; iteration < 300; iteration++) {
            int nodeCount = 2 + random.nextInt(9);
            MutableGraph<Integer> graph = GraphBuilder.directed().build();
            for (int node = 0; node < nodeCount; node++) {
                graph.addNode(node);
            }
            for (int from = 0; from < nodeCount; from++) {
                for (int to = 0; to < nodeCount; to++) {
                    if (from != to && random.nextInt(4) == 0) {
                        graph.putEdge(from, to);
                    }
                }
            }

            Set<Set<Integer>> expectedCycles = new HashSet<>(referenceComponents(graph));
            expectedCycles.removeIf(component -> component.size() < 2);

            if (expectedCycles.isEmpty()) {
                assertThat(TopologicalSort.topologicalSort(graph, Comparator.naturalOrder()))
                        .as("sorted %s", graph)
                        .containsExactlyInAnyOrderElementsOf(graph.nodes());
            } else {
                var exception = catchThrowableOfType(CyclePresentException.class,
                        () -> TopologicalSort.topologicalSort(graph, Comparator.naturalOrder()));
                assertThat(exception).as("expected a cycle in %s", graph).isNotNull();
                assertThat(exception.<Integer>getCycles())
                        .as("reported cycles of %s", graph)
                        .isEqualTo(expectedCycles);
            }
        }
    }

    @Test
    void handlesDeepGraphsWithoutStackOverflow() {
        int nodeCount = 20_000;
        MutableGraph<Integer> graph = GraphBuilder.directed().build();
        for (int node = 0; node < nodeCount; node++) {
            graph.addNode(node);
        }
        for (int node = 0; node + 1 < nodeCount; node++) {
            graph.putEdge(node, node + 1);
        }

        assertThat(new StronglyConnectedComponentDetector<>(graph).getComponents()).hasSize(nodeCount);
    }

    private static MutableGraph<Integer> buildGraph(List<Integer> nodeOrder, List<int[]> edges) {
        MutableGraph<Integer> graph = GraphBuilder.directed().build();
        for (int node : nodeOrder) {
            graph.addNode(node);
        }
        for (int[] edge : edges) {
            graph.putEdge(edge[0], edge[1]);
        }
        return graph;
    }

    private static Set<Set<String>> cyclesOf(Graph<String> graph) {
        Set<Set<String>> cycles = new HashSet<>(new StronglyConnectedComponentDetector<>(graph).getComponents());
        cycles.removeIf(component -> component.size() < 2);
        return cycles;
    }

    /**
     * Reference implementation using Kosaraju's algorithm, deliberately written differently from the detector.
     */
    private static <T> Set<Set<T>> referenceComponents(Graph<T> graph) {
        List<T> finishOrder = new ArrayList<>();
        Set<T> visited = new HashSet<>();
        for (T start : graph.nodes()) {
            if (!visited.add(start)) {
                continue;
            }
            Deque<Iterator<T>> iterators = new ArrayDeque<>();
            Deque<T> path = new ArrayDeque<>();
            iterators.push(graph.successors(start).iterator());
            path.push(start);
            while (!iterators.isEmpty()) {
                Iterator<T> iterator = iterators.peek();
                if (iterator.hasNext()) {
                    T successor = iterator.next();
                    if (visited.add(successor)) {
                        iterators.push(graph.successors(successor).iterator());
                        path.push(successor);
                    }
                } else {
                    iterators.pop();
                    finishOrder.add(path.pop());
                }
            }
        }

        Set<Set<T>> components = new HashSet<>();
        Set<T> assigned = new HashSet<>();
        for (int i = finishOrder.size() - 1; i >= 0; i--) {
            T start = finishOrder.get(i);
            if (!assigned.add(start)) {
                continue;
            }
            Set<T> component = new HashSet<>();
            Deque<T> stack = new ArrayDeque<>();
            stack.push(start);
            while (!stack.isEmpty()) {
                T node = stack.pop();
                component.add(node);
                for (T predecessor : graph.predecessors(node)) {
                    if (assigned.add(predecessor)) {
                        stack.push(predecessor);
                    }
                }
            }
            components.add(component);
        }
        return components;
    }
}
