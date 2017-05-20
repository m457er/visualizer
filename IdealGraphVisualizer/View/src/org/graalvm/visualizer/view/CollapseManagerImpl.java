package org.graalvm.visualizer.view;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.graalvm.visualizer.data.InputBlock;
import org.graalvm.visualizer.filter.FilterChain;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.layout.Cluster;
import org.openide.util.lookup.ServiceProvider;

/**
 * Manages block collapsing.
 *
 * @author Patrik Harag
 */
@ServiceProvider(service = CollapseManager.class)
public class CollapseManagerImpl implements CollapseManager {

    private final Set<ClusterId> collapsed;
    private final Collapser collapser;

    public CollapseManagerImpl() {
        this.collapsed = new LinkedHashSet<>();
        this.collapser = new CollapserImpl();
    }

    @Override
    public boolean isActive() {
        return !collapsed.isEmpty();
    }

    private void collapse(DiagramViewModel model, Collection<? extends Cluster> clusters) {
        for (Cluster cluster : clusters) {
            collapsed.add(ClusterId.of(cluster));
        }

        update(model);
    }

    @Override
    public void collapseSelected(DiagramViewModel model) {
        collapse(model, collectSelectedClusters(model));
    }

    @Override
    public void collapseAll(DiagramViewModel model) {
        collapse(model, model.getDiagramToView().getBlocks());
    }

    @Override
    public void expandSelected(DiagramViewModel model) {
        Set<Cluster> clusters = collectSelectedClusters(model);

        for (Cluster cluster : clusters) {
            collapsed.remove(ClusterId.of(cluster));
        }

        update(model);
    }

    @Override
    public void expandAll(DiagramViewModel model) {
        collapsed.clear();
        update(model);
    }

    @Override
    public void apply(Diagram diagram) {
        collapser.apply(diagram, collapsed);
    }

    private static void update(DiagramViewModel model) {
        // enforce update
        // TODO: is a better way?

        FilterChain filterChain = model.getFilterChain();
        filterChain.getChangedEvent().fire();
    }

    private static Set<Cluster> collectSelectedClusters(DiagramViewModel model) {
        Diagram diagram = model.getDiagramToView();
        Set<Cluster> clusters = new LinkedHashSet<>();

        // selected figures
        for (Figure figure : model.getSelectedFigures()) {
            clusters.add(figure.getCluster());
        }

        for (InputBlock inputBlock : model.getSelectedBlocks()) {
            try {
                clusters.add(diagram.getBlock(inputBlock));
            } catch (AssertionError e) {
                // selected block is not present in this diagram
            }
        }

        return clusters;
    }

}
