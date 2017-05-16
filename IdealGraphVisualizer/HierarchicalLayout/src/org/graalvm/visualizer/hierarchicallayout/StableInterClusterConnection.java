package org.graalvm.visualizer.hierarchicallayout;

import org.graalvm.visualizer.layout.Link;
import org.graalvm.visualizer.layout.Port;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

public class StableInterClusterConnection implements Link {

    private final Port inputSlot;
    private final Port outputSlot;
    private List<Point> intermediatePoints;
    private boolean visible = true;

    public StableInterClusterConnection(StableClusterOutputSlotNode outputSlotNode, StableClusterInputSlotNode inputSlotNode) {
        this.inputSlot = inputSlotNode.getInputSlot();
        this.outputSlot = outputSlotNode.getOutputSlot();
        intermediatePoints = new ArrayList<>();
    }

    @Override
    public Port getTo() {
        return inputSlot;
    }

    @Override
    public Port getFrom() {
        return outputSlot;
    }

    @Override
    public void setControlPoints(List<Point> p) {
        this.intermediatePoints = p;
    }

    @Override
    public List<Point> getControlPoints() {
        return intermediatePoints;
    }

    @Override
    public String toString() {
        return "InterClusterConnection[from=" + getFrom() + ", to=" + getTo() + "]";
    }

    @Override
    public boolean isVIP() {
        return false;
    }
    
    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }
}
