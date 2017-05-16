package org.graalvm.visualizer.hierarchicallayout;

import org.graalvm.visualizer.layout.Link;
import org.graalvm.visualizer.layout.Port;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

public final class StableClusterIngoingConnection implements Link {

    private List<Point> controlPoints;
    private final Port inputSlot;
    private final Port outputSlot;
    private boolean visible = true;

    public StableClusterIngoingConnection(StableClusterInputSlotNode inputSlotNode, Link c) {
        this.controlPoints = new ArrayList<>();

        inputSlot = c.getTo();
        outputSlot = inputSlotNode.getOutputSlot();
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
        this.controlPoints = p;
    }

    @Override
    public List<Point> getControlPoints() {
        return controlPoints;
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
