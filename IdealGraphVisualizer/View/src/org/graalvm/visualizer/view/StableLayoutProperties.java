package org.graalvm.visualizer.view;

import org.graalvm.visualizer.hierarchicallayout.StableHierarchicalLayoutManager;

/**
 *
 * @author Patrik Harag
 */
public class StableLayoutProperties {

    private StableHierarchicalLayoutManager activeLayoutManager;
    private boolean alwaysOptimizeSlots = false;

    public void initActiveLayoutManager(StableHierarchicalLayoutManager activeLayoutManager) {
        this.activeLayoutManager = activeLayoutManager;
        
        if (activeLayoutManager != null) {
            activeLayoutManager.setAlwaysOptimizeSlots(alwaysOptimizeSlots);
        }
    }

    public boolean isAlwaysOptimizeSlots() {
        return alwaysOptimizeSlots;
    }

    public void setAlwaysOptimizeSlots(boolean alwaysOptimizeSlots) {
        this.alwaysOptimizeSlots = alwaysOptimizeSlots;
        
        if (activeLayoutManager != null) {
            activeLayoutManager.setAlwaysOptimizeSlots(alwaysOptimizeSlots);
        }
    }

}
