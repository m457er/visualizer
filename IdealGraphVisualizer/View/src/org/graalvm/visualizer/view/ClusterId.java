package org.graalvm.visualizer.view;

import java.util.List;
import java.util.Objects;
import org.graalvm.visualizer.data.InputBlock;
import org.graalvm.visualizer.data.InputNode;
import org.graalvm.visualizer.graph.Block;
import org.graalvm.visualizer.layout.Cluster;

/**
 *
 * @author Patrik Harag
 */
final class ClusterId {

    private final String name;
    private final int id;

    private ClusterId(String name, int id) {
        this.name = name;
        this.id = id;
    }

    String getName() {
        return name;
    }

    int getId() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        final ClusterId other = (ClusterId) obj;

        if (this.id != other.id) {
            return false;
        }

        return (this.name == null)
                ? other.name == null
                : this.name.equals(other.name);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + (this.name != null ? this.name.hashCode() : 0);
        hash = 41 * hash + this.id;
        return hash;
    }

    @Override
    public String toString() {
        return "ClusterId{" + "name=" + String.valueOf(name) + ", id=" + id + '}';
    }

    public static ClusterId of(Cluster cluster) {
        if (cluster instanceof Block) {
            Block block = (Block) cluster;
            InputBlock inputBlock = block.getInputBlock();

            String name = inputBlock.getName();
            int id = createIntId(inputBlock);

            return new ClusterId(name, id);

        } else {
            throw new AssertionError("Unknown cluster type");
        }
    }

    private static int createIntId(InputBlock inputBlock) {
        List<InputNode> nodes = inputBlock.getNodes();

        assert !nodes.isEmpty() : "There should be at least one source node";

        int id = 0;
        for (InputNode inputNode : nodes) {
            id += inputNode.getId();
        }

        return id;
    }

    public static boolean equals(ClusterId clusterId, Cluster cluster) {
        if (!Objects.equals(clusterId.getName(), getClusterName(cluster))) {
            return false;
        }

        // compare IDs only when names are equal
        return clusterId.equals(ClusterId.of(cluster));
    }

    public static String getClusterName(Cluster cluster) {
        if (cluster instanceof Block) {
            return ((Block) cluster).getInputBlock().getName();
        } else {
            assert false : "Unknown cluster type";
            return cluster.toString();
        }
    }

}
