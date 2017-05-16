package org.graalvm.visualizer.hierarchicallayout;

import org.graalvm.visualizer.layout.Link;
import org.graalvm.visualizer.layout.Port;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

public class StableClusterOutgoingConnection implements Link {

    private List<Point> intermediatePoints;
    private final Port inputSlot;
    private final Port outputSlot;
    private boolean visible = true;

    public StableClusterOutgoingConnection(StableClusterOutputSlotNode outputSlotNode, Link c) {
        this.intermediatePoints = new ArrayList<>();

        outputSlot = c.getFrom();
        inputSlot = outputSlotNode.getInputSlot();
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
