/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.toposort;

import com.google.common.graph.Graph;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * An object that splits a graph into strongly connected components lazily with
 * Tarjan's Strongly Connected Components Algorithm.
 *
 * <p>This algorithm allows to detect all cycles in dependencies that prevent topological
 * sorting.
 *
 * <p>This detector evaluates the graph lazily and won't reflect the modifications in the
 * graph after initial evaluation.
 */
public class StronglyConnectedComponentDetector<T> {
    private final Graph<T> graph;
    private @Nullable Set<Set<T>> components;

    public StronglyConnectedComponentDetector(Graph<T> graph) {
        this.graph = graph;
    }

    public Set<Set<T>> getComponents() {
        var components = this.components;
        if (components == null) {
            components = calculate();
            this.components = components;
        }
        return components;
    }

    /**
     * an iterative version of Tarjan's algorithm.
     */
    @SuppressWarnings("unchecked")
    private Set<Set<T>> calculate() {
        Set<T> nodes = graph.nodes();
        int n = nodes.size();

        // Map every node to a dense index in [0, n), and remember the reverse mapping.
        Map<T, Integer> ids = new HashMap<>(Math.max(1, n * 2));
        T[] elements = (T[]) new Object[n];
        int nextId = 0;
        for (T node : nodes) {
            ids.put(node, nextId);
            elements[nextId] = node;
            nextId++;
        }

        // Snapshot the successors as dense indices, so the DFS below only works with ints.
        int[][] successors = new int[n][];
        for (int node = 0; node < n; node++) {
            Set<T> nodeSuccessors = graph.successors(elements[node]);
            int[] nodeSuccessorsById = new int[nodeSuccessors.size()];
            int i = 0;
            for (T successor : nodeSuccessors) {
                nodeSuccessorsById[i++] = ids.get(successor);
            }
            successors[node] = nodeSuccessorsById;
        }

        int[] low = new int[n]; // lowest discovery index reachable from a node
        int[] discoveryIndex = new int[n]; // discovery index, or -1 if not visited yet
        Arrays.fill(discoveryIndex, -1);
        int nextDiscoveryIndex = 0;

        int[] componentStack = new int[n];
        int componentStackTop = 0;
        BitSet onStack = new BitSet(n);

        // Explicit DFS stacks, to avoid overflowing the JVM stack on large graphs.
        int[] dfsStack = new int[n];
        int[] dfsCursor = new int[n];
        int dfsDepth = 0;

        Set<Set<T>> components = new HashSet<>();
        for (int root = 0; root < n; root++) {
            if (discoveryIndex[root] != -1) {
                continue;
            }

            discoveryIndex[root] = low[root] = nextDiscoveryIndex++;
            componentStack[componentStackTop++] = root;
            onStack.set(root);
            dfsStack[dfsDepth] = root;
            dfsCursor[dfsDepth] = 0;
            dfsDepth++;

            while (dfsDepth > 0) {
                int node = dfsStack[dfsDepth - 1];
                int[] nodeSuccessors = successors[node];
                int cursor = dfsCursor[dfsDepth - 1];

                boolean descended = false;
                while (cursor < nodeSuccessors.length) {
                    int successor = nodeSuccessors[cursor++];
                    if (discoveryIndex[successor] == -1) {
                        // Tree edge: descend into the successor.
                        dfsCursor[dfsDepth - 1] = cursor;
                        discoveryIndex[successor] = low[successor] = nextDiscoveryIndex++;
                        componentStack[componentStackTop++] = successor;
                        onStack.set(successor);
                        dfsStack[dfsDepth] = successor;
                        dfsCursor[dfsDepth] = 0;
                        dfsDepth++;
                        descended = true;
                        break;
                    } else if (onStack.get(successor)) {
                        // Back or cross edge within the current component: only nodes still on the stack count.
                        low[node] = Math.min(low[node], discoveryIndex[successor]);
                    }
                }
                if (descended) {
                    continue;
                }

                // All successors of `node` were visited: `node` is done.
                dfsCursor[dfsDepth - 1] = cursor;
                dfsDepth--;
                if (dfsDepth > 0) {
                    int parent = dfsStack[dfsDepth - 1];
                    low[parent] = Math.min(low[parent], low[node]);
                }

                if (low[node] == discoveryIndex[node]) {
                    Set<T> component = new HashSet<>();
                    int member;
                    do {
                        member = componentStack[--componentStackTop];
                        onStack.clear(member);
                        component.add(elements[member]);
                    } while (member != node);
                    components.add(component);
                }
            }
        }

        return components;
    }
}
