package org.graalvm.visualizer.layout;

import java.util.List;
import java.util.Set;

public interface StableLayoutManager {
    
    public void doLayout(StableLayoutGraph graph);

    public void doLayout(StableLayoutGraph graph, List<? extends Link> importantLinks);

    public void doLayout(StableLayoutGraph graph, Set<? extends Vertex> firstLayerHint, Set<? extends Vertex> lastLayerHint, List<? extends Link> importantLinks);
    
    public int getSafeOffset();
}