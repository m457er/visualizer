package org.graalvm.visualizer.hierarchicallayout;

import org.graalvm.visualizer.layout.Link;

/**
 * Connector between two layout nodes. Connector leads from parent to child and
 * represents real link in graph. There can be N connectors representing 1 link.
 */
public class StableHierarchicalLayoutConnector {

    private StableHierarchicalLayoutNode parent;
    private StableHierarchicalLayoutNode child;
    private final Link representedLink;
    private ConnectorType type;
    private boolean visible = true;

    /**
     * Specifies if connector leads from parent to child, or if it should be
     * considered going from child to parent in certain situations while
     * computing graph layout.
     */
    public enum ConnectorType {

        normal, reversed;
    }

    public void setChild(StableHierarchicalLayoutNode child) {
        this.child = child;
    }

    public void setParent(StableHierarchicalLayoutNode parent) {
        this.parent = parent;
    }

    public StableHierarchicalLayoutNode getChild() {
        return child;
    }

    public StableHierarchicalLayoutNode getParent() {
        return parent;
    }

    public void setType(ConnectorType type) {
        this.type = type;
    }

    public ConnectorType getType() {
        return type;
    }

    public Link getRepresentedLink() {
        return representedLink;
    }

    public StableHierarchicalLayoutConnector(StableHierarchicalLayoutNode parent, StableHierarchicalLayoutNode child, ConnectorType type, Link representedLink) {
        this.parent = parent;
        this.child = child;
        this.type = type;
        this.representedLink = representedLink;
        this.visible = true;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof StableHierarchicalLayoutConnector) {
            StableHierarchicalLayoutConnector other = (StableHierarchicalLayoutConnector) obj;
            return (this == other) || (this.child.equals(other.child) && this.parent.equals(other.parent) && this.representedLink.equals(other.representedLink));
        }
        return false;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible;
    }
}
