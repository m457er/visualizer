/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */
package org.graalvm.visualizer.hierarchicallayout;

import org.graalvm.visualizer.layout.Cluster;
import org.graalvm.visualizer.layout.Link;
import org.graalvm.visualizer.layout.Port;
import org.graalvm.visualizer.layout.Vertex;
import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClusterNode implements Vertex {
    public static final int BORDER = 20;

    private Cluster cluster;
    private Port inputSlot;
    private Port outputSlot;
    private final Set<Vertex> subNodes;
    private Dimension size;
    private Point position;
    private final Set<Link> subEdges;
    private boolean root;
    private final String name;

    public ClusterNode(Cluster cluster, String name) {
        this.subNodes = new HashSet<>();
        this.subEdges = new HashSet<>();
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

    public Set<Link> getSubEdges() {
        return Collections.unmodifiableSet(subEdges);
    }

    public void updateSize() {


        calculateSize();

        final ClusterNode widget = this;
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
            Point p = n.getPosition();
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x + n.getSize().width);
            maxY = Math.max(maxY, p.y + n.getSize().height);
        }

        for (Link l : subEdges) {
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
                p.x -= minX;
                p.y -= minY;
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

    public Set<? extends Vertex> getSubNodes() {
        return subNodes;
    }
}
