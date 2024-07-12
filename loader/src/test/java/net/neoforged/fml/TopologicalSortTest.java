package net.neoforged.fml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.graph.ElementOrder;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import java.util.Comparator;
import net.neoforged.fml.loading.toposort.CyclePresentException;
import net.neoforged.fml.loading.toposort.TopologicalSort;
import org.junit.jupiter.api.Test;

@SuppressWarnings("UnstableApiUsage") // guava graph is @Beta
public class TopologicalSortTest {
    @Test
    public void testDefaultOrdering() {
        MutableGraph<Integer> g = GraphBuilder.directed().nodeOrder(ElementOrder.insertion()).build();
        g.putEdge(5, 0);
        g.putEdge(5, 4);
        g.putEdge(0, 1);
        g.putEdge(1, 2);
        g.putEdge(0, 4);
        g.addNode(3);

        var sorted = TopologicalSort.topologicalSort(g, Comparator.naturalOrder());
        assertThat(sorted)
                .containsExactly(5, 0, 1, 2, 3, 4);
    }

    @Test
    public void testCycle() {
        MutableGraph<Integer> g = GraphBuilder.directed().nodeOrder(ElementOrder.insertion()).build();
        g.putEdge(0, 1);
        g.putEdge(1, 3);
        g.putEdge(3, 0);
        g.addNode(2);

        assertThatThrownBy(() -> TopologicalSort.topologicalSort(g, Comparator.naturalOrder()))
                .isInstanceOf(CyclePresentException.class);
    }
}
