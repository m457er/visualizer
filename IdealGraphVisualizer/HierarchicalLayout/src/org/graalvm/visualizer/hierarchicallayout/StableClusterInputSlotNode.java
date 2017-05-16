package org.graalvm.visualizer.hierarchicallayout;

import java.awt.Dimension;
import org.graalvm.visualizer.layout.Port;
import org.graalvm.visualizer.layout.Vertex;
import java.awt.Point;
import org.graalvm.visualizer.layout.Cluster;

public final class StableClusterInputSlotNode implements Vertex {

    private String id;
    private static final int SIZE = 0;
    private Point position;
    protected Port inputSlot;
    protected Port outputSlot;
    protected StableClusterNode blockNode;
    private InterClusterConnection interBlockConnection;
    private Cluster cluster;
    private StableClusterIngoingConnection conn;
    private boolean visible = false;
    
    public StableClusterInputSlotNode(StableClusterNode n, String id) {

        this.blockNode = n;
        this.id = id;

        n.addSubNode(this);
        final StableClusterInputSlotNode thisNode = this;
        final StableClusterNode thisBlockNode = blockNode;

        outputSlot = new Port() {
            public Point getRelativePosition() {
                Point referencePoint = blockNode.getPosition();
                Point thisPosition = getPosition();
                Point relativePosition = new Point(thisPosition.x - referencePoint.x, thisPosition.y - referencePoint.y);
                return relativePosition;
            }

            public Vertex getVertex() {
                return thisNode;
            }

            @Override
            public String toString() {
                return "OutPort of " + thisNode.toString();
            }
        };

        inputSlot = new Port() {
            public Point getRelativePosition() {
                Point referencePoint = blockNode.getPosition();
                Point thisPosition = getPosition();
                Point relativePosition = new Point(thisPosition.x - referencePoint.x, thisPosition.y - referencePoint.y);
                return relativePosition;
            }

            public Vertex getVertex() {
                return thisBlockNode;
            }

            @Override
            public String toString() {
                return "InPort of " + thisBlockNode.toString();
            }
        };
    }

    public Port getInputSlot() {
        return inputSlot;
    }

    public Port getOutputSlot() {
        return outputSlot;
    }
    
    public void setIngoingConnection(StableClusterIngoingConnection c) {
        conn = c;
    }

    public StableClusterIngoingConnection getIngoingConnection() {
        return conn;
    }

    @Override
    public String toString() {
        return id;
    }

    public InterClusterConnection getInterBlockConnection() {
        return interBlockConnection;
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

    public void setInterBlockConnection(InterClusterConnection interBlockConnection) {
        this.interBlockConnection = interBlockConnection;
    }

    @Override
    public Cluster getCluster() {
        return cluster;
    }

    @Override
    public boolean isRoot() {
        return true;
    }

    @Override
    public int compareTo(Vertex o) {
        return toString().compareTo(o.toString());
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
