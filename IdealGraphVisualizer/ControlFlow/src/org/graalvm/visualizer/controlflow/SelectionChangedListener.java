package org.graalvm.visualizer.controlflow;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.Set;
import org.graalvm.visualizer.data.ChangedListener;
import org.graalvm.visualizer.data.InputBlock;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.InputNode;
import org.graalvm.visualizer.selectioncoordinator.SelectionCoordinator;

/**
 *
 * @author Patrik Harag
 */
class SelectionChangedListener implements ChangedListener<SelectionCoordinator> {

    static final Color DEFAULT_COLOR = Color.WHITE;
    static final Color SELECTED_COLOR = Color.ORANGE;
    
    private final ControlFlowScene scene;

    SelectionChangedListener(final ControlFlowScene outer) {
        this.scene = outer;
    }

    @Override
    public void changed(SelectionCoordinator source) {
        InputGraph graph = scene.getGraph();
        if (graph == null)
            return;

        Set<BlockWidget> remainingWidgets = collectAllBlockWidgets(scene);
        Set<BlockWidget> selectedWidgets = new LinkedHashSet<BlockWidget>();

        // collect block widgets (control flow) according to selected nodes
        for (Object object : source.getSelectedObjects()) {
            InputBlock block = getInputBlock(object, graph);

            if (block == null) {
                // this can happen for example when more diagram views are
                //   opened at the same time
                continue;
            }

            BlockWidget widget = fingWidget(block, remainingWidgets);
            if (widget != null) {
                remainingWidgets.remove(widget);
                selectedWidgets.add(widget);
            }
        }
        
        // TODO: highlight selected blocks? - requires changes in the API

        apply(selectedWidgets, remainingWidgets);
    }
    
    private InputBlock getInputBlock(Object object, InputGraph graph) {
        if (object instanceof Integer) {
            Integer nodeID = (Integer) object;
            return graph.getBlock(nodeID);
        }

        if (object instanceof InputNode) {
            InputNode node = (InputNode) object;
            return graph.getBlock(node.getId());
        }

        return null;
    }

    private Set<BlockWidget> collectAllBlockWidgets(ControlFlowScene scene) {
        Set<BlockWidget> allWidgets = new LinkedHashSet<BlockWidget>();

        for (InputBlock block : scene.getNodes()) {
            BlockWidget widget = (BlockWidget) scene.findWidget(block);
            allWidgets.add(widget);
        }

        return allWidgets;
    }

    private BlockWidget fingWidget(InputBlock block, Set<BlockWidget> allWidgets) {
        for (BlockWidget widget : allWidgets) {
            if (block.equals(widget.getBlock())) {
                return widget;
            }
        }
        return null;
    }

    private void apply(Set<BlockWidget> selected, Set<BlockWidget> rest) {
        for (BlockWidget widget : selected) {
            widget.setBackground(SELECTED_COLOR);
        }

        for (BlockWidget widget : rest) {
            widget.setBackground(DEFAULT_COLOR);
        }

        scene.validate();
    }
}
