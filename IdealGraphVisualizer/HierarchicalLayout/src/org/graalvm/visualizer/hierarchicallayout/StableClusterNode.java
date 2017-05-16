package org.graalvm.visualizer.hierarchicallayout;

import org.graalvm.visualizer.layout.Cluster;
import org.graalvm.visualizer.layout.Link;
import org.graalvm.visualizer.layout.Port;
import org.graalvm.visualizer.layout.Vertex;
import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StableClusterNode implements Vertex {
    public static final int BORDER = 20;

    private Cluster cluster;
    private Port inputSlot;
    private Port outputSlot;
    private final List<Vertex> subNodes;
    private Dimension size;
    private Point position;
    private final List<Link> subEdges;
    private boolean root;
    private final String name;
    private boolean visible = true;

    public StableClusterNode(Cluster cluster, String name) {
        this.subNodes = new ArrayList<>();
        this.subEdges = new ArrayList<>();
        this.cluster = cluster;
        position = new Point(0, 0);
        this.name = name;
    }

    public void addSubNode(Vertex v) {
        subNodes.add(v);
    }

    public void addSubEdge(Link l) {
        subEdges.add(l);
    }

    public List<Link> getSubEdges() {
        return Collections.unmodifiableList(subEdges);
    }

    public void updateSize() {

        calculateSize();

        final StableClusterNode widget = this;
        inputSlot = new Port() {

            @Override
            public Point getRelativePosition() {
                return new Point(size.width / 2, 0);
            }

            @Override
            public Vertex getVertex() {
                return widget;
            }
        };

        outputSlot = new Port() {

            @Override
            public Point getRelativePosition() {
                return new Point(size.width / 2, 0);
            }

            @Override
            public Vertex getVertex() {
                return widget;
            }
        };
    }

    private void calculateSize() {

        if (subNodes.isEmpty()) {
            size = new Dimension(0, 0);
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (Vertex n : subNodes) {
            if(!n.isVisible()) {
                continue;
            }
            Point p = n.getPosition();
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x + n.getSize().width);
            maxY = Math.max(maxY, p.y + n.getSize().height);
        }

        for (Link l : subEdges) {
            if(!l.isVisible()) {
                continue;
            }
            List<Point> points = l.getControlPoints();
            for (Point p : points) {
                if (p != null) {
                    minX = Math.min(minX, p.x);
                    maxX = Math.max(maxX, p.x);
                    minY = Math.min(minY, p.y);
                    maxY = Math.max(maxY, p.y);
                }
            }
        }

        size = new Dimension(maxX - minX, maxY - minY);

        // Normalize coordinates
        for (Vertex n : subNodes) {
            n.setPosition(new Point(n.getPosition().x - minX, n.getPosition().y - minY));
        }

        for (Link l : subEdges) {
            List<Point> points = new ArrayList<>(l.getControlPoints());
            for (Point p : points) {
                if(p != null) {
                    p.x -= minX;
                    p.y -= minY;
                }
            }
            l.setControlPoints(points);

        }

        size.width += 2 * BORDER;
        size.height += 2 * BORDER;
    }

    public Port getInputSlot() {
        return inputSlot;

    }

    public Port getOutputSlot() {
        return outputSlot;
    }

    @Override
    public Dimension getSize() {
        return size;
    }

    @Override
    public Point getPosition() {
        return position;
    }

    @Override
    public void setPosition(Point pos) {

        this.position = pos;
        for (Vertex n : subNodes) {
            Point cur = new Point(n.getPosition());
            cur.translate(pos.x + BORDER, pos.y + BORDER);
            n.setPosition(cur);
        }

        for (Link e : subEdges) {
            List<Point> arr = e.getControlPoints();
            ArrayList<Point> newArr = new ArrayList<>(arr.size());
            for (Point p : arr) {
                if (p != null) {
                    Point p2 = new Point(p);
                    p2.translate(pos.x + BORDER, pos.y + BORDER);
                    newArr.add(p2);
                } else {
                    newArr.add(null);
                }
            }

            e.setControlPoints(newArr);
        }
    }

    @Override
    public Cluster getCluster() {
        return cluster;
    }

    public void setCluster(Cluster c) {
        cluster = c;
    }

    public void setRoot(boolean b) {
        root = b;
    }

    @Override
    public boolean isRoot() {
        return root;
    }

    @Override
    public int compareTo(Vertex o) {
        return toString().compareTo(o.toString());
    }

    @Override
    public String toString() {
        return name;
    }

    public List<Vertex> getSubNodes() {
        return subNodes;
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
