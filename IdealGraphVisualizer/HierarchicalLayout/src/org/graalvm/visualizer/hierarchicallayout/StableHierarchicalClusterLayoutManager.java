package org.graalvm.visualizer.hierarchicallayout;

import org.graalvm.visualizer.layout.StableLayoutGraph;
import org.graalvm.visualizer.layout.*;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.*;

public class StableHierarchicalClusterLayoutManager {

    private StableLayoutManager subManager;
    private StableLayoutManager manager;
    
    public static List<? extends Link> testEdges;
    public static List<Cluster> testClusters;
    public static HashMap<Cluster, List<? extends Cluster>> testMap = new HashMap<>();
 
    public void doLayout(StableLayoutGraph graph) {
        doLayout(graph, new HashSet<Vertex>(), new HashSet<Vertex>(), new HashSet<Link>());
    }

    public void setSubManager(StableLayoutManager manager) {
        this.subManager = manager;
    }

    public void setManager(StableLayoutManager manager) {
        this.manager = manager;
    }

    public void doLayout(StableLayoutGraph graph, Set<? extends Vertex> firstLayerHint, Set<? extends Vertex> lastLayerHint, Set<? extends Link> importantLinks) {
        HashMap<Cluster, HashMap<Port, StableClusterInputSlotNode>> clusterInputSlotHash = new HashMap<>();
        HashMap<Cluster, HashMap<Port, StableClusterOutputSlotNode>> clusterOutputSlotHash = new HashMap<>();

        HashMap<Cluster, StableClusterNode> clusterNodes = new HashMap<>();
        HashMap<Cluster, Set<StableClusterInputSlotNode>> clusterInputSlotSet = new HashMap<>();
        HashMap<Cluster, Set<StableClusterOutputSlotNode>> clusterOutputSlotSet = new HashMap<>();
        List<Link> clusterEdges = new ArrayList<>();
        List<Link> interClusterEdges = new ArrayList<>();
        HashMap<Link, StableClusterOutgoingConnection> linkClusterOutgoingConnection = new HashMap<>();
        HashMap<Link, StableInterClusterConnection> linkInterClusterConnection = new HashMap<>();
        HashMap<Link, StableClusterIngoingConnection> linkClusterIngoingConnection = new HashMap<>();
        List<StableClusterNode> clusterNodeList = new ArrayList<>();

        List<Cluster> cluster = graph.getClusters();
        int z = 0;
        for (Cluster c : cluster) {
            clusterInputSlotHash.put(c, new HashMap<Port, StableClusterInputSlotNode>());
            clusterOutputSlotHash.put(c, new HashMap<Port, StableClusterOutputSlotNode>());
            clusterOutputSlotSet.put(c, new TreeSet<StableClusterOutputSlotNode>());
            clusterInputSlotSet.put(c, new TreeSet<StableClusterInputSlotNode>());
            StableClusterNode cn = new StableClusterNode(c, "" + z);
            clusterNodes.put(c, cn);
            clusterNodeList.add(cn);
            z++;
        }
        
        // Add cluster edges
        for (Cluster c : cluster) {

            StableClusterNode start = clusterNodes.get(c);
            
            List<? extends Cluster> successors = c.getStableSuccessors();
            
            for (Cluster succ : successors) {
                StableClusterNode end = clusterNodes.get(succ);
                if (end != null && start != end) {
                    StableClusterEdge e = new StableClusterEdge(start, end);
                    clusterEdges.add(e);
                    interClusterEdges.add(e);
                }
            }
        }
        
        for (Vertex v : graph.getVertices()) {
            Cluster c = v.getCluster();
            assert c != null : "Cluster of vertex " + v + " is null!";
            clusterNodes.get(c).addSubNode(v);
        }

        for (Link l : graph.getLinks()) {

            Port fromPort = l.getFrom();
            Port toPort = l.getTo();
            Vertex fromVertex = fromPort.getVertex();
            Vertex toVertex = toPort.getVertex();
            Cluster fromCluster = fromVertex.getCluster();
            Cluster toCluster = toVertex.getCluster();

            Port samePort = fromPort;

            if (fromCluster == toCluster) {
                clusterNodes.get(fromCluster).addSubEdge(l);
            } else {
                StableClusterInputSlotNode inputSlotNode = clusterInputSlotHash.get(toCluster).get(samePort);
                StableClusterOutputSlotNode outputSlotNode = clusterOutputSlotHash.get(fromCluster).get(samePort);

                if (outputSlotNode == null) {
                    outputSlotNode = new StableClusterOutputSlotNode(clusterNodes.get(fromCluster), "Out " + fromCluster.toString() + " " + samePort.toString());
                    clusterOutputSlotSet.get(fromCluster).add(outputSlotNode);
                    StableClusterOutgoingConnection conn = new StableClusterOutgoingConnection(outputSlotNode, l);
                    conn.setVisible(l.isVisible());
                    outputSlotNode.setOutgoingConnection(conn);
                    clusterNodes.get(fromCluster).addSubEdge(conn);
                    clusterOutputSlotHash.get(fromCluster).put(samePort, outputSlotNode);

                    linkClusterOutgoingConnection.put(l, conn);
                } else {
                    linkClusterOutgoingConnection.put(l, outputSlotNode.getOutgoingConnection());
                    outputSlotNode.getOutgoingConnection().setVisible(outputSlotNode.getOutgoingConnection().isVisible() || l.isVisible());
                }

                if (inputSlotNode == null) {
                    inputSlotNode = new StableClusterInputSlotNode(clusterNodes.get(toCluster), "In " + toCluster.toString() + " " + samePort.toString());
                    clusterInputSlotSet.get(toCluster).add(inputSlotNode);
                }

                outputSlotNode.setVisible(outputSlotNode.isVisible() || l.isVisible());
                inputSlotNode.setVisible(inputSlotNode.isVisible() || l.isVisible());

                StableClusterIngoingConnection conn = new StableClusterIngoingConnection(inputSlotNode, l);
                conn.setVisible(l.isVisible());
                inputSlotNode.setIngoingConnection(conn);
                clusterNodes.get(toCluster).addSubEdge(conn);
                
                clusterInputSlotHash.get(toCluster).put(samePort, inputSlotNode);

                linkClusterIngoingConnection.put(l, conn);

                StableInterClusterConnection interConn = new StableInterClusterConnection(outputSlotNode, inputSlotNode);
                interConn.setVisible(l.isVisible());
                linkInterClusterConnection.put(l, interConn);
                clusterEdges.add(interConn);
            }
        }
        
        for (Cluster c : cluster) {
            StableClusterNode n = clusterNodes.get(c);

            subManager.doLayout(new StableLayoutGraph(n.getSubEdges(), n.getSubNodes()), clusterInputSlotSet.get(c), clusterOutputSlotSet.get(c), new ArrayList<Link>(1));
            n.updateSize();
        }

        for (StableClusterNode clusterNode : clusterNodeList) {
            clusterNode.setVisible(false);
            for (Vertex vertex : clusterNode.getSubNodes()) {
                if (vertex.isVisible() && !(vertex instanceof StableClusterInputSlotNode || vertex instanceof StableClusterOutputSlotNode)) {
                    clusterNode.setVisible(true);
                    break;
                }
            }
        }
        
        manager.doLayout(new StableLayoutGraph(clusterEdges, clusterNodeList), new HashSet<Vertex>(), new HashSet<Vertex>(), interClusterEdges);

        for (Cluster c : cluster) {
            StableClusterNode n = clusterNodes.get(c);
            c.setBounds(new Rectangle(n.getPosition(), n.getSize()));
        }

        for (Link l : graph.getLinks()) {

            if (linkInterClusterConnection.containsKey(l)) {
                //inside source cluster
                StableClusterOutgoingConnection conn1 = linkClusterOutgoingConnection.get(l);
                //inter cluster
                StableInterClusterConnection conn2 = linkInterClusterConnection.get(l);
                // inside target cluster
                StableClusterIngoingConnection conn3 = linkClusterIngoingConnection.get(l);

                assert conn1 != null;
                assert conn2 != null;
                assert conn3 != null;

                List<Point> points = new ArrayList<>();

                points.addAll(conn1.getControlPoints());

                // if connection between clusters doesnt exist, but it existed inside source cluster then add link to the border of source Cluster
                if (conn2.getControlPoints().isEmpty() && !conn1.getControlPoints().isEmpty()) {
                    Point lastPointOfCluster = conn1.getControlPoints().get(conn1.getControlPoints().size() - 1);
                    
                    points.add(new Point(lastPointOfCluster.x + subManager.getSafeOffset(), lastPointOfCluster.y));
                    points.add(new Point(lastPointOfCluster.x + subManager.getSafeOffset(), lastPointOfCluster.y + ClusterNode.BORDER));
                    //link separator
                    points.add(null);
                }


                points.addAll(conn2.getControlPoints());

                // if connection between clusters doesnt exist, but it exists inside target cluster then add link from the border of target Cluster
                if (conn2.getControlPoints().isEmpty() && !conn3.getControlPoints().isEmpty()) {
                    //link separator
                    points.add(null);
                    Point firstPointOfCluster = conn3.getControlPoints().get(0);
                    points.add(new Point(firstPointOfCluster.x, firstPointOfCluster.y - ClusterNode.BORDER));
                }

                points.addAll(conn3.getControlPoints());

                l.setControlPoints(points);
            }
        }
    }

}
