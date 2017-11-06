package org.graalvm.visualizer.view;

import org.graalvm.visualizer.hierarchicallayout.StableHierarchicalLayoutManager;

/**
 *
 * @author Patrik Harag
 */
public class StableLayoutProperties {

    public static final int DEFAULT_MAX_LAYER_GAP = StableHierarchicalLayoutManager.DEFAULT_MAX_LAYER_GAP;
    public static final boolean DEFAULT_DYNAMIC_LAYER_HEIGHT = StableHierarchicalLayoutManager.DEFAULT_DYNAMIC_LAYER_HEIGHT;
    public static final boolean DEFAULT_ALWAYS_OPTIMIZE_SLOTS = StableHierarchicalLayoutManager.DEFAULT_ALWAYS_OPTIMIZE_SLOTS;

    private StableHierarchicalLayoutManager activeLayoutManager;
    private boolean alwaysOptimizeSlots = false;
    private boolean dynamicLayerHeight = true;
    private int maxLayerGap = StableHierarchicalLayoutManager.DEFAULT_MAX_LAYER_GAP;

    public void initActiveLayoutManager(StableHierarchicalLayoutManager activeLayoutManager) {
        this.activeLayoutManager = activeLayoutManager;
        
        if (activeLayoutManager != null) {
            activeLayoutManager.setAlwaysOptimizeSlots(alwaysOptimizeSlots);
            activeLayoutManager.setDynamicLayerHeight(dynamicLayerHeight);
            activeLayoutManager.setMaxLayerGap(maxLayerGap);
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

    public boolean isDynamicLayerHeight() {
        return dynamicLayerHeight;
    }
    
    public void setDynamicLayerHeight(boolean dynamicLayerHeight) {
        this.dynamicLayerHeight = dynamicLayerHeight;
        
        if (activeLayoutManager != null) {
            activeLayoutManager.setDynamicLayerHeight(dynamicLayerHeight);
        }
    }

    public void setMaxLayerGap(int maxLayerGap) {
        this.maxLayerGap = maxLayerGap;
        if (activeLayoutManager != null) {
            activeLayoutManager.setMaxLayerGap(maxLayerGap);
        }
    }

    public int getMaxLayerGap() {
        return maxLayerGap;
    }

}
