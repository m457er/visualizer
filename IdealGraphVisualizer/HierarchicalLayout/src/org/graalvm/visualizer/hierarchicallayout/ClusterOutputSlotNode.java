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
import org.graalvm.visualizer.layout.Port;
import org.graalvm.visualizer.layout.Vertex;
import java.awt.Dimension;
import java.awt.Point;

public class ClusterOutputSlotNode implements Vertex {

    private static final int SIZE = 0;
    private Point position;
    private Port inputSlot;
    private final Port outputSlot;
    private ClusterNode blockNode;
    private boolean root;
    private Cluster cluster;
    private ClusterOutgoingConnection conn;
    private String id;

    public void setOutgoingConnection(ClusterOutgoingConnection c) {
        this.conn = c;
    }

    public ClusterOutgoingConnection getOutgoingConnection() {
        return conn;
    }

    @Override
    public String toString() {
        return id;
    }

    public ClusterOutputSlotNode(ClusterNode n, String id) {
        this.blockNode = n;
        this.id = id;

        n.addSubNode(this);

        final Vertex thisNode = this;
        final ClusterNode thisBlockNode = blockNode;

        inputSlot = new Port() {

            @Override
            public Point getRelativePosition() {
                return new Point(0, 0);
            }

            @Override
            public Vertex getVertex() {
                return thisNode;
            }

            @Override
            public String toString() {
                return "InPort of " + thisNode.toString();
            }
        };

        outputSlot = new Port() {

            @Override
            public Point getRelativePosition() {
                Point p = new Point(thisNode.getPosition());
                p.x += ClusterNode.BORDER;
                p.y = 0;
                return p;
            }

            @Override
            public Vertex getVertex() {
                return thisBlockNode;
            }

            @Override
            public String toString() {
                return "OutPort of " + thisNode.toString();
            }
        };
    }

    @Override
    public Dimension getSize() {
        return new Dimension(SIZE, SIZE);
    }

    @Override
    public void setPosition(Point p) {
        this.position = p;
    }

    @Override
    public Point getPosition() {
        return position;
    }

    public Port getInputSlot() {
        return inputSlot;
    }

    public Port getOutputSlot() {
        return outputSlot;
    }

    public void setCluster(Cluster c) {
        cluster = c;
    }

    public void setRoot(boolean b) {
        root = b;
    }

    @Override
    public Cluster getCluster() {
        return cluster;
    }

    @Override
    public boolean isRoot() {
        return root;
    }

    @Override
    public int compareTo(Vertex o) {
        return toString().compareTo(o.toString());
    }
}
