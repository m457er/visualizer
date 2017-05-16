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
package org.graalvm.visualizer.graph;

import org.graalvm.visualizer.data.InputBlock;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.InputNode;
import org.graalvm.visualizer.data.Properties;
import org.graalvm.visualizer.data.Source;
import org.graalvm.visualizer.layout.Cluster;
import org.graalvm.visualizer.layout.Vertex;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;

public class Figure extends Properties.Entity implements Source.Provider, Vertex {

    public static final int INSET = 8;
    public static int SLOT_WIDTH = 10;
    public static final int OVERLAPPING = 6;
    public static final int SLOT_START = 4;
    public static final int SLOT_OFFSET = 8;
    public static final boolean VERTICAL_LAYOUT = true;
    protected List<InputSlot> inputSlots;
    private OutputSlot singleOutput;
    private List<OutputSlot> outputSlots;
    private final Source source;
    private final Diagram diagram;
    private Point position;
    private final List<Figure> predecessors;
    private final List<Figure> successors;
    private List<InputGraph> subgraphs;
    private Color color;
    private final int id;
    private final String idString;
    private String[] lines;
    private int heightCash = -1;
    private int widthCash = -1;
    private boolean visible = true;

    public int getHeight() {
        if (heightCash == -1) {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            Graphics g = image.getGraphics();
            g.setFont(diagram.getFont().deriveFont(Font.BOLD));
            FontMetrics metrics = g.getFontMetrics();
            String nodeText = diagram.getNodeText();
            heightCash = nodeText.split("\n").length * metrics.getHeight() + INSET;
        }
        return heightCash;
    }

    public static <T> List<T> getAllBefore(List<T> inputList, T tIn) {
        List<T> result = new ArrayList<>();
        for (T t : inputList) {
            if (t.equals(tIn)) {
                break;
            }
            result.add(t);
        }
        return result;
    }

    public static int getSlotsWidth(Collection<? extends Slot> slots) {
        int result = Figure.SLOT_OFFSET;
        for (Slot s : slots) {
            result += s.getWidth() + Figure.SLOT_OFFSET;
        }
        return result;
    }

    public static int getSlotsWidth(Slot s) {
        if (s == null) {
            return Figure.SLOT_OFFSET;
        } else {
            return Figure.SLOT_OFFSET + s.getWidth() + Figure.SLOT_OFFSET;
        }
    }

    public int getWidth() {
        if (widthCash == -1) {
            int max = 0;
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            Graphics g = image.getGraphics();
            g.setFont(diagram.getFont().deriveFont(Font.BOLD));
            FontMetrics metrics = g.getFontMetrics();
            for (String s : getLines()) {
                int cur = metrics.stringWidth(s);
                if (cur > max) {
                    max = cur;
                }
            }
            widthCash = max + INSET;
            widthCash = Math.max(widthCash, Figure.getSlotsWidth(inputSlots));
            widthCash = Math.max(widthCash, outputSlots == null ? getSlotsWidth(singleOutput) : getSlotsWidth(outputSlots));
        }
        return widthCash;
    }

    protected Figure(Diagram diagram, int id) {
        this.diagram = diagram;
        this.source = new FigureSource(this);
        inputSlots = new ArrayList<>(5);
        predecessors = new ArrayList<>(6);
        successors = new ArrayList<>(6);
        this.id = id;
        idString = Integer.toString(id);

        this.position = new Point(0, 0);
        this.color = Color.WHITE;
    }

    public int getId() {
        return id;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public Color getColor() {
        return color;
    }

    public List<Figure> getPredecessors() {
        return Collections.unmodifiableList(predecessors);
    }

    public Set<Figure> getPredecessorSet() {
        Set<Figure> result = new HashSet<>();
        for (Figure f : getPredecessors()) {
            result.add(f);
        }
        return Collections.unmodifiableSet(result);
    }

    public Set<Figure> getSuccessorSet() {
        Set<Figure> result = new HashSet<>();
        for (Figure f : getSuccessors()) {
            result.add(f);
        }
        return Collections.unmodifiableSet(result);
    }

    public List<Figure> getSuccessors() {
        return Collections.unmodifiableList(successors);
    }

    protected void addPredecessor(Figure f) {
        this.predecessors.add(f);
    }

    protected void addSuccessor(Figure f) {
        this.successors.add(f);
    }

    protected void removePredecessor(Figure f) {
        assert predecessors.contains(f);
        predecessors.remove(f);
    }

    protected void removeSuccessor(Figure f) {
        assert successors.contains(f);
        successors.remove(f);
    }

    public List<InputGraph> getSubgraphs() {
        return subgraphs;
    }

    public void setSubgraphs(List<InputGraph> subgraphs) {
        this.subgraphs = subgraphs;
    }

    @Override
    public void setPosition(Point p) {
        this.position = p;
    }

    @Override
    public Point getPosition() {
        return position;
    }

    public Diagram getDiagram() {
        return diagram;
    }

    @Override
    public Source getSource() {
        return source;
    }

    public InputSlot createInputSlot() {
        InputSlot slot = new InputSlot(this, -1);
        inputSlots.add(slot);
        return slot;
    }

    public InputSlot createInputSlot(int index) {
        InputSlot slot = new InputSlot(this, index);
        inputSlots.add(slot);
        Collections.sort(inputSlots, Slot.slotIndexComparator);
        return slot;
    }

    public void removeSlot(Slot s) {

        assert inputSlots.contains(s) || (singleOutput == s || (outputSlots != null && outputSlots.contains(s)));

        List<Connection> connections = new ArrayList<>(s.getConnections());
        for (Connection c : connections) {
            c.remove();
        }

        if (inputSlots.contains(s)) {
            inputSlots.remove(s);
        } else if (!doRemoveOutputSlot(s)) {
            return; // ?
        }
        sourcesChanged(s.getSource());
    }

    private boolean doRemoveOutputSlot(Slot s) {
        if (outputSlots != null) {
            boolean ret = outputSlots.remove(s);
            if (outputSlots.isEmpty()) {
                outputSlots = null;
                singleOutput = null;
            }
            return ret;
        } else if (singleOutput == s) {
            singleOutput = null;
            return true;
        }
        return false;
    }

    public OutputSlot createOutputSlot() {
        OutputSlot slot = new OutputSlot(this, -1);
        addOutputSlot(slot);
        return slot;
    }

    private boolean addOutputSlot(OutputSlot slot) {
        boolean res;
        if (outputSlots != null) {
            outputSlots.add(slot);
            res = true;
        } else if (singleOutput == null) {
            singleOutput = slot;
            res = false;
        } else {
            outputSlots = new ArrayList<>(2);
            outputSlots.add(singleOutput);
            outputSlots.add(slot);
            singleOutput = null;
            res = true;
        }
        if (res) {
            sourcesChanged(slot.getSource());
        }
        return res;
    }

    public OutputSlot createOutputSlot(int index) {
        OutputSlot slot = new OutputSlot(this, index);
        if (addOutputSlot(slot)) {
            Collections.sort(outputSlots, Slot.slotIndexComparator);
        }
        return slot;
    }

    public List<InputSlot> getInputSlots() {
        return Collections.unmodifiableList(inputSlots);
    }

    public Set<Slot> getSlots() {
        Set<Slot> result = new HashSet<>();
        result.addAll(getInputSlots());
        result.addAll(getOutputSlots());
        return result;
    }

    public List<OutputSlot> getOutputSlots() {
        if (outputSlots != null) {
            return Collections.unmodifiableList(outputSlots);
        } else if (singleOutput != null) {
            return Collections.singletonList(singleOutput);
        } else {
            return Collections.emptyList();
        }
    }

    void removeInputSlot(InputSlot s) {
        s.removeAllConnections();
        inputSlots.remove(s);
    }

    void removeOutputSlot(OutputSlot s) {
        s.removeAllConnections();
        doRemoveOutputSlot(s);
    }

    public String[] getLines() {
        if (lines == null) {
            updateLines();
        }
        return lines;
    }

    public void updateLines() {
        String[] strings = diagram.getNodeText().split("\n");
        String[] result = new String[strings.length];

        for (int i = 0; i < strings.length; i++) {
            result[i] = resolveString(strings[i], getProperties());
        }

        lines = result;
    }

    public static final String resolveString(String string, Properties properties) {

        StringBuilder sb = new StringBuilder();
        boolean inBrackets = false;
        StringBuilder curIdent = new StringBuilder();

        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            if (inBrackets) {
                if (c == ']') {
                    String value = properties.get(curIdent.toString());
                    if (value == null) {
                        value = "";
                    }
                    sb.append(value);
                    inBrackets = false;
                } else {
                    curIdent.append(c);
                }
            } else {
                if (c == '[') {
                    inBrackets = true;
                    curIdent = new StringBuilder();
                } else {
                    sb.append(c);
                }
            }
        }

        return sb.toString();
    }

    private int outputSlotCount() {
        return outputSlots == null ? (singleOutput != null ? 1 : 0) : outputSlots.size();
    }

    @Override
    public Dimension getSize() {
        int osSize = outputSlotCount();
        if (VERTICAL_LAYOUT) {
            int width = Math.max(getWidth(), Figure.SLOT_WIDTH * (Math.max(inputSlots.size(), osSize) + 1));
            int height = getHeight() + 2 * Figure.SLOT_WIDTH - 2 * Figure.OVERLAPPING;

            return new Dimension(width, height);
        } else {
            int width = getWidth() + 2 * Figure.SLOT_WIDTH - 2 * Figure.OVERLAPPING;
            int height = Figure.SLOT_WIDTH * (Math.max(inputSlots.size(), osSize) + 1);
            return new Dimension(width, height);
        }
    }

    @Override
    public String toString() {
        return idString;
    }

    public Cluster getCluster() {
        if (getSource().getSourceNodes().isEmpty()) {
            assert false : "Should never reach here, every figure must have at least one source node!";
            return null;
        } else {
            final InputBlock inputBlock = diagram.getGraph().getBlock(getSource().first());
            assert inputBlock != null;
            Cluster result = diagram.getBlock(inputBlock);
            assert result != null;
            return result;
        }
    }

    @Override
    public boolean isRoot() {

        List<InputNode> sourceNodes = source.getSourceNodes();
        if (sourceNodes.size() > 0 && sourceNodes.get(0).getProperties().get("name") != null) {
            return source.getSourceNodes().get(0).getProperties().get("name").equals("Root");
        } else {
            return false;
        }
    }

    @Override
    public int compareTo(Vertex f) {
        return toString().compareTo(f.toString());
    }

    public Rectangle getBounds() {
        return new Rectangle(this.getPosition(), new Dimension(this.getWidth(), this.getHeight()));
    }

    void sourcesChanged(Source s) {
        diagram.invalidateSlotMap();
    }
    
    @Override
    public boolean isVisible()
    {
        return visible;
}
    
    @Override
    public void setVisible(boolean visible)
    {
        this.visible = visible;
    }
}
