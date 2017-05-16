package org.graalvm.visualizer.hierarchicallayout;

import org.graalvm.visualizer.layout.Link;
import org.graalvm.visualizer.layout.Port;
import java.awt.Point;
import java.util.List;

public class StableClusterEdge implements Link {

    private final StableClusterNode from;
    private final StableClusterNode to;
    private List<Point> points;
    private boolean visible = true;

    public StableClusterEdge(StableClusterNode from, StableClusterNode to) {
        assert from != null;
        assert to != null;
        this.from = from;
        this.to = to;
    }

    @Override
    public Port getTo() {
        return to.getInputSlot();
    }

    @Override
    public Port getFrom() {
        return from.getInputSlot();
    }

    @Override
    public void setControlPoints(List<Point> p) {
        this.points = p;
    }

    @Override
    public List<Point> getControlPoints() {
        return points;
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
