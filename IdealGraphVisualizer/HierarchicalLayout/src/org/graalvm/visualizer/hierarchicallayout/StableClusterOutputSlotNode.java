package org.graalvm.visualizer.hierarchicallayout;

import java.awt.Dimension;
import org.graalvm.visualizer.layout.Port;
import org.graalvm.visualizer.layout.Vertex;
import java.awt.Point;
import org.graalvm.visualizer.layout.Cluster;

public final class StableClusterOutputSlotNode implements Vertex{

    private static final int SIZE = 0;
    private Point position;
    protected Port inputSlot;
    protected Port outputSlot;
    protected StableClusterNode blockNode;
    private boolean root;
    private Cluster cluster;
    private StableClusterOutgoingConnection conn;
    private String id;
    private boolean visible = false;
    
    
    public StableClusterOutputSlotNode(StableClusterNode n, String id) {
        this.blockNode = n;
        this.id = id;
        
        n.addSubNode(this);
        
        final Vertex thisNode = this;
        final StableClusterNode thisBlockNode = blockNode;

        inputSlot = new Port() {
            @Override
            public Point getRelativePosition() {
                Point referencePoint = blockNode.getPosition();
                Point thisPosition = getPosition();
                Point relativePosition = new Point(thisPosition.x - referencePoint.x, thisPosition.y - referencePoint.y);
                return relativePosition;
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
                Point referencePoint = blockNode.getPosition();
                Point thisPosition = getPosition();
                Point relativePosition = new Point(thisPosition.x - referencePoint.x, thisPosition.y - referencePoint.y);
                return relativePosition;
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
    
    public void setOutgoingConnection(StableClusterOutgoingConnection c) {
        this.conn = c;
    }

    public StableClusterOutgoingConnection getOutgoingConnection() {
        return conn;
    }

    @Override
    public String toString() {
        return id;
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
    
    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }
}
