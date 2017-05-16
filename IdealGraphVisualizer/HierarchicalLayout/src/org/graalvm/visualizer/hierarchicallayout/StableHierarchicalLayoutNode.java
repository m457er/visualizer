package org.graalvm.visualizer.hierarchicallayout;

import org.graalvm.visualizer.layout.Link;
import org.graalvm.visualizer.layout.Vertex;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/**
 * Layout description of graph node/cluster.
 *
 * Each node has assigned layer in layout hierarchy and position. Position
 * expresses relative position of nodes within one layer. Coordinates x,y are
 * relative since node has no knowledge of its cluster. Dimension of is
 * readonly, changing it will change the way how it is handled by layout, but
 * won't change dimensions of displayed node. Fixed layer is indication that
 * node cannot be moved across layers e.g. ClusterInputSlotNode. UsedSlots
 * specifies width in slots (actual slot size is maintained by layout algorithm)
 * StartingSlot is number of a leftmost slot. Visible is indication if node will
 * be visible in final layout.
 */
public class StableHierarchicalLayoutNode {

    private Vertex representedVertex;
    private int layer;
    private double position;
    private List<StableHierarchicalLayoutConnector> children = new ArrayList<>();
    private List<StableHierarchicalLayoutConnector> parents = new ArrayList<>();
    private boolean volatileNode;
    private int x, y;
    private Dimension dimension;
    private boolean fixedLayer;
    private int usedSlots;
    private int startingSlot;
    private boolean visible = true;

    public void setVolatileNode(boolean volatileNode) {
        this.volatileNode = volatileNode;
    }

    public boolean isVolatileNode() {
        return volatileNode;
    }

    /**
     * Creates new connector between this node and parent node. Connector
     * represents given Link.
     *
     * @param parent Parent (source) node from which connector goes.
     * @param representedLink Link of real graph which is represented by this
     * connector.
     * @return Created connector
     */
    public StableHierarchicalLayoutConnector addParent(StableHierarchicalLayoutNode parent, Link representedLink) {
        StableHierarchicalLayoutConnector connector = new StableHierarchicalLayoutConnector(parent, this, StableHierarchicalLayoutConnector.ConnectorType.normal, representedLink);
        parents.add(connector);
        parent.addChild(connector);
        return connector;
    }

    /**
     * Adds connector to parents of this node. Method doesn't change connector
     * itself or any other node.
     *
     * @param connector
     */
    public void addParent(StableHierarchicalLayoutConnector connector) {
        parents.add(connector);
    }

    /**
     * Creates new connector between this node and child node. Connector
     * represents given Link.
     *
     * @param child Child (target) node to which connector goes.
     * @param representedLink Link of real graph which is represented by this
     * connector.
     * @return Created connector
     */
    public StableHierarchicalLayoutConnector addChild(StableHierarchicalLayoutNode child, Link representedLink) {
        StableHierarchicalLayoutConnector connector = new StableHierarchicalLayoutConnector(this, child, StableHierarchicalLayoutConnector.ConnectorType.normal, representedLink);
        children.add(connector);
        child.addParent(connector);
        return connector;
    }

    /**
     * Adds connector to parents of this node. Method doesn't change connector
     * itself or any other node.
     *
     * @param connector
     */
    public void addChild(StableHierarchicalLayoutConnector connector) {
        children.add(connector);
    }

    public StableHierarchicalLayoutNode(Vertex representedVertex, int layer, boolean volatileNode, boolean visible, Dimension dimension) {
        this.representedVertex = representedVertex;
        this.layer = layer;
        this.volatileNode = volatileNode;
        this.visible = visible;
        this.dimension = dimension;
    }

    public StableHierarchicalLayoutNode(boolean volatileNode, int layer) {
        this.volatileNode = volatileNode;
        this.layer = layer;
    }

    public StableHierarchicalLayoutNode() {
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }

    public int getLayer() {
        return layer;
    }

    public List<StableHierarchicalLayoutConnector> getParents() {
        return parents;
    }

    public List<StableHierarchicalLayoutConnector> getChildren() {
        return children;
    }

    public void setPosition(double position) {
        this.position = position;
    }

    public double getPosition() {
        return position;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Dimension getDimension() {
        return dimension;
    }

    public void setDimension(Dimension dimension) {
        this.dimension = dimension;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof StableHierarchicalLayoutNode) {
            if (this == obj) {
                return true;
            }
            if (representedVertex == null) {
                return false;
            }
            if (representedVertex.equals(((StableHierarchicalLayoutNode) obj).representedVertex)) {
                return true;
            }
        }
        return false;
    }

    public Vertex getRepresentedVertex() {
        return representedVertex;
    }

    public void setRepresentedVertex(Vertex representedVertex) {
        this.representedVertex = representedVertex;
    }

    public void setFixedLayer(boolean fixedLayer) {
        this.fixedLayer = fixedLayer;
    }

    public boolean isFixedLayer() {
        return fixedLayer;
    }

    public void setUsedSlots(int usedSlots) {
        this.usedSlots = usedSlots;
    }

    public int getUsedSlots() {
        return usedSlots;
    }

    public void setStartingSlot(int startingSlot) {
        this.startingSlot = startingSlot;
    }

    public int getStartingSlot() {
        return startingSlot;
    }

    public int centralSlotPosition() {
        return startingSlot + usedSlots / 2;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setChildren(List<StableHierarchicalLayoutConnector> children) {
        this.children = children;
    }

    public void setParents(List<StableHierarchicalLayoutConnector> parents) {
        this.parents = parents;
    }
}