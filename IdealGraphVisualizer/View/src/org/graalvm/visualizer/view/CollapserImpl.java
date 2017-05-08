package org.graalvm.visualizer.view;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.Set;
import org.graalvm.visualizer.data.Source;
import org.graalvm.visualizer.difference.Difference;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.graph.Figure;

/**
 *
 * @author Patrik Harag
 */
class CollapserImpl implements Collapser {

    public static Color BLOCK_COLOR = new Color(79, 96, 255);

    @Override
    public void apply(Diagram diagram, Set<ClusterId> clusters) {
        for (ClusterId cluster : clusters) {
            apply(diagram, cluster);
        }
    }

    public void apply(Diagram diagram, ClusterId clusterId) {
        if (diagram.getBlocks().isEmpty()) {
            // diagram is empty | blocks are not initialized
            return;
        }

        ClusterDiffStats stats = new ClusterDiffStats();

        // collect all figures inside target cluster
        Set<Figure> figures = new LinkedHashSet<Figure>();
        Set<Source> sources = new LinkedHashSet<Source>();
        for (Figure figure : diagram.getFigures()) {
            if (ClusterId.equals(clusterId, figure.getCluster())) {
                figures.add(figure);
                sources.add(figure.getSource());
                stats.analyze(figure);
            }
        }

        if (figures.isEmpty() || sources.isEmpty()) {
            // in this diagram the target cluster is not present
            return;
        }

        // create new figure to represent entire cluster
        Figure block = createBlockFigure(diagram, clusterId.getName(), stats);

        // add source nodes to the new figure
        for (Source source : sources) {
            block.getSource().addSourceNodes(source);
        }

        mapConnections(diagram, figures, block, clusterId.getName());
        diagram.removeAllFigures(figures);  // also removes connections
    }

    private Figure createBlockFigure(Diagram diagram, String cluster, ClusterDiffStats stats) {
        Figure figure = diagram.createFigure();

        String state;
        if (stats.isNew()) {
            state = Difference.VALUE_NEW;
        } else if (stats.isDeleted()) {
            state = Difference.VALUE_DELETED;
        } else if (stats.isChanged()) {
            state = Difference.VALUE_CHANGED;
        } else {
            state = Difference.VALUE_SAME;
        }

        figure.setColor(BLOCK_COLOR);

        figure.getProperties().setProperty("name", cluster);
        figure.getProperties().setProperty("type", "block");
        figure.getProperties().setProperty(Difference.PROPERTY_STATE, state);
        return figure;
    }

    private void mapConnections(Diagram d, Set<Figure> figures, Figure replacement,
            String cluster) {

        ConnectionMapper mapper = new ConnectionMapper(cluster, figures, replacement, d);
        mapper.mapConnections();
    }

    /**
     * Diff stats for a cluster.
     */
    private static final class ClusterDiffStats {

        private int changedCount;
        private int deletedCount;
        private int newCount;

        private int total;

        private void analyze(Figure figure) {
            String state = figure.getProperties().get(Difference.PROPERTY_STATE);
            if (state != null) {
                if (state.equals(Difference.VALUE_SAME)) {
                    // nothing
                } else if (state.equals(Difference.VALUE_CHANGED)) {
                    changedCount++;
                } else if (state.equals(Difference.VALUE_DELETED)) {
                    deletedCount++;
                } else if (state.equals(Difference.VALUE_NEW)) {
                    newCount++;
                }
            }
            total++;
        }

        private boolean isEmpty() {
            return total == 0;
        }

        private boolean isNew() {
            return !isEmpty() && (total == newCount);
        }

        private boolean isDeleted() {
            return !isEmpty() && (total == deletedCount);
        }

        private boolean isChanged() {
            // one or more nodes are changed/deleted/new
            return !isEmpty() && ((changedCount + deletedCount + newCount) > 0);
        }

    }

}
