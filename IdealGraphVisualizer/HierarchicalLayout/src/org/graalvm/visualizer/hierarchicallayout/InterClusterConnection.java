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

import org.graalvm.visualizer.layout.Link;
import org.graalvm.visualizer.layout.Port;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

public class InterClusterConnection implements Link {

    private final Port inputSlot;
    private final Port outputSlot;
    private List<Point> intermediatePoints;
    private final ClusterInputSlotNode inputSlotNode;
    private final ClusterOutputSlotNode outputSlotNode;

    public InterClusterConnection(ClusterOutputSlotNode outputSlotNode, ClusterInputSlotNode inputSlotNode) {
        this.outputSlotNode = outputSlotNode;
        this.inputSlotNode = inputSlotNode;
        this.inputSlot = inputSlotNode.getInputSlot();
        this.outputSlot = outputSlotNode.getOutputSlot();
        intermediatePoints = new ArrayList<>();
    }

    public ClusterOutputSlotNode getOutputSlotNode() {
        return outputSlotNode;
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
}
