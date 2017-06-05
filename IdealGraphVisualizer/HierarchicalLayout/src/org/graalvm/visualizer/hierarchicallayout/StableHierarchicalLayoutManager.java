package org.graalvm.visualizer.hierarchicallayout;

import org.graalvm.visualizer.layout.Link;
import org.graalvm.visualizer.layout.Port;
import org.graalvm.visualizer.layout.Vertex;
import java.awt.Dimension;
import java.awt.Point;
import java.util.*;
import org.graalvm.visualizer.layout.StableLayoutGraph;
import org.graalvm.visualizer.layout.StableLayoutManager;

/**
 * Implementation of stable hierarchical layout based on Sugiyama's framework.
 * Stability of layout is guaranteed only if structure of the layout doesn't
 * change. Filtering etc. is supported by visibility of nodes, this means that
 * structure of graph is always the same, the only thing that changes is what is
 * visible. Then the algorithm guarantees that relative positions of layers and
 * relative positions of nodes withing each layer will be maintained.
 *
 */
public class StableHierarchicalLayoutManager implements StableLayoutManager {
    
    /**
     * Temporary storage for loops.
     */
    protected List<StableHierarchicalLayoutConnector> loopConnectors;
    /**
     * Temporary storage for nodes without edges.
     */
    protected List<StableHierarchicalLayoutNode> nodesWithoutEdges;
    /**
     * List of links that are longer than allowed layer difference.
     */
    protected Set<Link> tooLongLinks;
    /**
     * Mapping of layer to its height based on the biggest visible node of the
     * layer.
     */
    protected Map<Integer, Double> layerToGridColumnHeight;
    /**
     * Mapping of layer to gap which should be left before this layer (i.e. gap
     * between N-1, N layers)
     */
    protected Map<Integer, Double> layerToPreceedingLayerGap;
    /**
     * Width of grid slot.
     */
    final protected double gridColumnWidth = 20;
    /**
     * Width of volatile node.
     */
    final protected int volatileNodeWidth = 20;
    /**
     * Which strategy of combinating connectors should be used based on incoming
     * connectors.
     */
    protected InputCombination inputsCombinationRule = InputCombination.NONE;
    /**
     * Which strategy of combinating connectors should be used based on outgoing
     * connectors.
     */
    protected OutputCombination outputsCombinationRule = OutputCombination.NODE_BASED;
    /**
     * Should algorithm try to optimize positions of nodes after main algorithm
     * finished. (When instance is small enough)
     */
    protected boolean optimizeSlots = true;
    /**
     * Maximum difference of layers for which an edge will still be shown.
     */
    protected int longEdgeMaxLayers = -1;
    /**
     * Allows to control if layer height should be changed to prevent high
     * angles.
     */
    protected final boolean dynamicLayerHeight = true;
    /**
     * Minimal gap that should be left between layers.
     */
    protected final int minimalLayerGap = 40;
    /**
     * Maximum gap that should be left between layers.
     * (Cannot override input/output backedge offsets)
     */
    protected final int maxLayerGap = 300;
    
    /**
     * Specifies if tool ong edges shouldn't be drawn, otherwise short edges will be used.
     */
    private boolean ignoreTooLongEdges = true;

    @Override
    public int getSafeOffset() {
        return (int) gridColumnWidth / 3;
    }

    /**
     * Which connectors should be considered during search.
     */
    private enum SearchType {

        parentSearch, childrenSearch, bothSearch;
    }
    
    @Override
    public void doLayout(StableLayoutGraph graph, List<? extends Link> importantLinks) {
        doLayout(graph, new HashSet<Vertex>(1), new HashSet<Vertex>(1), importantLinks);
    }

    /**
     * Abstract class to help with managing offsets of backedges.
     */
    private abstract class OffsetMapping {

        int currentOffset = 0;
        private final List<Integer> offsets = new ArrayList<>();
        private final List<Set<StableHierarchicalLayoutConnector>> connectorsWithOffset = new ArrayList<>();

        public List<Integer> getOffsets() {
            return offsets;
        }

        public List<Set<StableHierarchicalLayoutConnector>> getConnectorsWithOffset() {
            return connectorsWithOffset;
        }

        /**
         * Returns offset of given connector.
         */
        public int getOffset(StableHierarchicalLayoutConnector connector) {
            for (int i = 0; i < connectorsWithOffset.size(); i++) {
                if (connectorsWithOffset.get(i).contains(connector)) {
                    return offsets.get(i);
                }
            }
            return 0;
        }

        /**
         * Adds new backedge and assigns its offset
         */
        public void placeBackEdge(StableHierarchicalLayoutConnector backEdge) {
            boolean placed = false;
            for (int i = 0; i < connectorsWithOffset.size(); i++) {
                Set<StableHierarchicalLayoutConnector> backEdgeGroup = connectorsWithOffset.get(i);
                if (isFreeFor(backEdge, backEdgeGroup)) {
                    placed = true;
                    backEdgeGroup.add(backEdge);
                    break;
                }
            }
            if (!placed) {
                addOffset();
                connectorsWithOffset.get(connectorsWithOffset.size() - 1).add(backEdge);
            }
        }

        public int getCurrentOffset() {
            return currentOffset;
        }

        /**
         * Creates new offset level.
         */
        private void addOffset() {
            currentOffset += 20;
            offsets.add(currentOffset);
            connectorsWithOffset.add(new HashSet<StableHierarchicalLayoutConnector>());
        }

        /**
         * Checks if backEdge can be placed in same offset group as given group
         * of backEdges.
         *
         * @return True if backEdge doesn't overlap with any backedge from
         * group, unless connectors are mergeable.
         */
        private boolean isFreeFor(StableHierarchicalLayoutConnector backEdge, Set<StableHierarchicalLayoutConnector> backEdgeGroup) {
            for (StableHierarchicalLayoutConnector backEdgeOfGroup : backEdgeGroup) {
                if (inConflict(backEdgeOfGroup, backEdge)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Checks if two connectors have illegal overlap.
         */
        abstract protected boolean inConflict(StableHierarchicalLayoutConnector connector1, StableHierarchicalLayoutConnector connector2);
    }

    /**
     * Manages output offsets of backedges to minimize additional height.
     */
    private class OffsetMappingOutputs extends OffsetMapping {

        @Override
        protected boolean inConflict(StableHierarchicalLayoutConnector connector1, StableHierarchicalLayoutConnector connector2) {
            StableHierarchicalLayoutNode c1First = connector1.getChild(); //child is volatile
            StableHierarchicalLayoutNode c1Second = connector1.getParent();
            StableHierarchicalLayoutNode c2First = connector2.getChild(); //child is volatile
            StableHierarchicalLayoutNode c2Second = connector2.getParent();

            StableHierarchicalLayoutNode c1Smaller = c1First.getPosition() < c1Second.getPosition() ? c1First : c1Second;
            StableHierarchicalLayoutNode c1Bigger = c1First.getPosition() > c1Second.getPosition() ? c1First : c1Second;
            StableHierarchicalLayoutNode c2Smaller = c2First.getPosition() < c2Second.getPosition() ? c2First : c2Second;
            StableHierarchicalLayoutNode c2Bigger = c2First.getPosition() > c2Second.getPosition() ? c2First : c2Second;

            // overlap doesn't exist
            if (c1Smaller.getPosition() > c2Bigger.getPosition() || c1Bigger.getPosition() < c2Smaller.getPosition()) {
                return false;
            }

            // if both connectors lead to same volatile node, then it's allowed to merge them
            if (c1First == c2First) {
                return false;
            }
            // if both connection come from same node, then they have to either represent same link, or don't overlap
            if (c1Second == c2Second) {
                if (connector1.getRepresentedLink().getFrom().equals(connector2.getRepresentedLink().getFrom())) {
                    return false;

                } else if (connector1.getRepresentedLink().getFrom().getRelativePosition().x < connector2.getRepresentedLink().getFrom().getRelativePosition().x && c1First.getPosition() < c1Second.getPosition() && c2First.getPosition() > c2Second.getPosition()) {
                    return false;
                } else if (connector1.getRepresentedLink().getFrom().getRelativePosition().x > connector2.getRepresentedLink().getFrom().getRelativePosition().x && c1First.getPosition() > c1Second.getPosition() && c2First.getPosition() < c2Second.getPosition()) {
                    return false;
                }
            }

            return true;
        }
    }

    /**
     * Manages input offsets of backedges to minimize additional height.
     */
    private class OffsetMappingInputs extends OffsetMapping {

        @Override
        protected boolean inConflict(StableHierarchicalLayoutConnector connector1, StableHierarchicalLayoutConnector connector2) {
            StableHierarchicalLayoutNode firstNodeC1 = connector1.getParent();
            StableHierarchicalLayoutNode secondNodeC1 = connector1.getChild();

            StableHierarchicalLayoutNode firstNodeC2 = connector2.getParent();
            StableHierarchicalLayoutNode secondNodeC2 = connector2.getChild();

            StableHierarchicalLayoutNode firstSmaller = firstNodeC1.getPosition() < secondNodeC1.getPosition() ? firstNodeC1 : secondNodeC1;
            StableHierarchicalLayoutNode firstBigger = firstNodeC1.getPosition() > secondNodeC1.getPosition() ? firstNodeC1 : secondNodeC1;

            StableHierarchicalLayoutNode secondSmaller = firstNodeC2.getPosition() < secondNodeC2.getPosition() ? firstNodeC2 : secondNodeC2;
            StableHierarchicalLayoutNode secondBigger = firstNodeC2.getPosition() > secondNodeC2.getPosition() ? firstNodeC2 : secondNodeC2;

            // overlap doesn't exist
            if (secondBigger.getPosition() < firstSmaller.getPosition() || secondSmaller.getPosition() > firstBigger.getPosition()) {
                return false;
            }
            // if lines meet, its ok, if they either have same from/to port
            // => this means, that they share same volatilenode, or they have same destination port
            StableHierarchicalLayoutNode firstVolatile;
            StableHierarchicalLayoutNode secondVolatile;
            StableHierarchicalLayoutNode middleNode;
            Port c1Port;
            Port c2Port;
            if (firstSmaller.isVolatileNode()) {
                firstVolatile = firstSmaller;
                middleNode = firstBigger;
                Vertex nodeVertex = firstBigger.getRepresentedVertex();
                if (connector1.getRepresentedLink().getFrom().getVertex().equals(nodeVertex)) {
                    c1Port = connector1.getRepresentedLink().getFrom();
                } else {
                    c1Port = connector1.getRepresentedLink().getTo();
                }
            } else {
                firstVolatile = firstBigger;
                middleNode = firstSmaller;
                Vertex nodeVertex = firstSmaller.getRepresentedVertex();
                if (connector1.getRepresentedLink().getFrom().getVertex().equals(nodeVertex)) {
                    c1Port = connector1.getRepresentedLink().getFrom();
                } else {
                    c1Port = connector1.getRepresentedLink().getTo();
                }
            }
            if (secondSmaller.isVolatileNode()) {
                secondVolatile = secondSmaller;
                Vertex nodeVertex = secondBigger.getRepresentedVertex();
                if (connector2.getRepresentedLink().getFrom().getVertex().equals(nodeVertex)) {
                    c2Port = connector2.getRepresentedLink().getFrom();
                } else {
                    c2Port = connector2.getRepresentedLink().getTo();
                }
            } else {
                secondVolatile = secondBigger;
                Vertex nodeVertex = secondSmaller.getRepresentedVertex();
                if (connector2.getRepresentedLink().getFrom().getVertex().equals(nodeVertex)) {
                    c2Port = connector2.getRepresentedLink().getFrom();
                } else {
                    c2Port = connector2.getRepresentedLink().getTo();
                }
            }
            if (firstVolatile.equals(secondVolatile) || c1Port.equals(c2Port)) {
                return false;
            }
            // if connectors are between different ports, then connector comming from left wants left port and connector comming from right wants right port
            return !((firstVolatile.getPosition() < middleNode.getPosition() && secondVolatile.getPosition() > middleNode.getPosition() && c1Port.getRelativePosition().x < c2Port.getRelativePosition().x) || (firstVolatile.getPosition() > middleNode.getPosition() && secondVolatile.getPosition() < middleNode.getPosition() && c1Port.getRelativePosition().x > c2Port.getRelativePosition().x));
        }
    }

    public StableHierarchicalLayoutManager() {
    }

    public StableHierarchicalLayoutManager(InputCombination inputsCombinationRule, OutputCombination outputsCombinationRule) {
        this.inputsCombinationRule = inputsCombinationRule;
        this.outputsCombinationRule = outputsCombinationRule;
    }

    @Override
    public void doLayout(StableLayoutGraph graph) {
        doLayout(graph, new HashSet<Vertex>(), new HashSet<Vertex>(), new ArrayList<ClusterEdge>());
    }

    @Override
    public void doLayout(StableLayoutGraph graph, Set<? extends Vertex> firstLayerHint, Set<? extends Vertex> lastLayerHint, List<? extends Link> importantLinks) {
        loopConnectors = new ArrayList<>();
        nodesWithoutEdges = new ArrayList<>();
        tooLongLinks = new HashSet<>();
        Graph layoutGraph = new Graph();
        layerToGridColumnHeight = new HashMap<>();
        layerToPreceedingLayerGap = new HashMap<>();
        List<StableHierarchicalLayoutNode> nodes = new ArrayList<>(graph.getVertices().size());
        Map<Vertex, StableHierarchicalLayoutNode> vertexToNodeMapping = new HashMap<>(graph.getVertices().size());

        // Create inner representation of vertices
        List<? extends Vertex> vertices = graph.getVertices();
        for (Vertex vertex : vertices) {
            StableHierarchicalLayoutNode node = new StableHierarchicalLayoutNode(vertex, -1, false, vertex.isVisible(), vertex.getSize());
            nodes.add(node);
            vertexToNodeMapping.put(vertex, node);
        }

        // Create inner representation of links
        for (Link link : graph.getLinks()) {
            StableHierarchicalLayoutNode nodeFrom = vertexToNodeMapping.get(link.getFrom().getVertex());

            StableHierarchicalLayoutNode nodeTo = vertexToNodeMapping.get(link.getTo().getVertex());

            StableHierarchicalLayoutConnector connector = new StableHierarchicalLayoutConnector(nodeFrom, nodeTo, StableHierarchicalLayoutConnector.ConnectorType.normal, link);
            connector.setVisible(link.isVisible());

            nodeFrom.addChild(connector);
            nodeTo.addParent(connector);
        }

        for (Link link : importantLinks) {
            StableHierarchicalLayoutNode nodeFrom = vertexToNodeMapping.get(link.getFrom().getVertex());

            StableHierarchicalLayoutNode nodeTo = vertexToNodeMapping.get(link.getTo().getVertex());

            StableHierarchicalLayoutConnector connector = new StableHierarchicalLayoutConnector(nodeFrom, nodeTo, StableHierarchicalLayoutConnector.ConnectorType.normal, link);
            connector.setVisible(link.isVisible());

            nodeFrom.addChild(connector);
            nodeTo.addParent(connector);
        }

        /**
         * Create first and last layer, clusterInput/Output slots
         */
        Set<StableHierarchicalLayoutNode> firstLayer = new HashSet();
        Set<StableHierarchicalLayoutNode> lastLayer = new HashSet();

        for (Vertex vertex : firstLayerHint) {
            firstLayer.add(vertexToNodeMapping.get(vertex));
        }
        layoutGraph.setFirstLayer(firstLayer);

        for (Vertex vertex : lastLayerHint) {
            lastLayer.add(vertexToNodeMapping.get(vertex));
        }
        layoutGraph.setLastLayer(lastLayer);
        layoutGraph.setNodes(nodes);
        layoutGraph.realNodeCount = nodes.size();
        layoutGraph.realEdgeCount = graph.getLinks().size();

        // create layout and update original graph
        applyLayout(layoutGraph);
        updateGraph(layoutGraph, graph, vertexToNodeMapping);
    }

    /**
     * Creates y offsets between layers for incoming backedges.
     *
     * @param graph
     * @return
     */
    private Map<Integer, OffsetMappingInputs> createIncomingBackedgeMapping(Graph graph) {
        // Gather backedges ending in each layer
        Map<Integer, List<StableHierarchicalLayoutConnector>> layerToIncoming = new HashMap<>();
        for (StableHierarchicalLayoutNode node : graph.nodes) {
            if (!layerToIncoming.containsKey(node.getLayer())) {
                layerToIncoming.put(node.getLayer(), new ArrayList<StableHierarchicalLayoutConnector>());
            }
            if (!node.isVolatileNode()) {
                // won't have potential ending backedges
                continue;
            }

            // if node is not visible then even connectors won't be == no need for offset
            if (!node.isVisible()) {
                continue;
            }

            for (StableHierarchicalLayoutConnector potentialBackedge : node.getChildren()) {
                // if edge is beginning of backedge
                if (potentialBackedge.getChild().getLayer() == potentialBackedge.getParent().getLayer()) {
                    //is ending  of incoming backedge => need to get offset
                    layerToIncoming.get(node.getLayer()).add(potentialBackedge);
                }
            }
        }

        Map<Integer, OffsetMappingInputs> layerToOffsetMappingOfInputs = new HashMap<>();
        // for each layer and its managed backedges, create offset mapping and assign connectors to it
        for (int layer = graph.minLayer; layer <= graph.maxLayer; layer++) {
            OffsetMappingInputs offsetMapping = new OffsetMappingInputs();
            layerToOffsetMappingOfInputs.put(layer, offsetMapping);
            List<StableHierarchicalLayoutConnector> incomingBackedges = layerToIncoming.get(layer);
            Collections.sort(incomingBackedges, new Comparator<StableHierarchicalLayoutConnector>() {
                @Override
                public int compare(StableHierarchicalLayoutConnector t, StableHierarchicalLayoutConnector t1) {
                    double tAbstractLength = Math.abs(t.getChild().getPosition() - t.getParent().getPosition());
                    double t1AbstractLength = Math.abs(t1.getChild().getPosition() - t1.getParent().getPosition());
                    return Double.compare(tAbstractLength, t1AbstractLength);
                }
            });

            //incoming backedges are now sorted
            for (StableHierarchicalLayoutConnector backedge : incomingBackedges) {
                offsetMapping.placeBackEdge(backedge);
            }
        }
        return layerToOffsetMappingOfInputs;
    }

    /**
     * Creates y offsets between layers for outgoing backedges.
     *
     * @param graph
     * @return
     */
    private Map<Integer, OffsetMappingOutputs> createOutgoingBackedgeMapping(Graph graph) {
        // Gather backedges starting in layer
        Map<Integer, List<StableHierarchicalLayoutConnector>> layerToOutgoing = new HashMap<>();
        for (StableHierarchicalLayoutNode node : graph.nodes) {
            if (!layerToOutgoing.containsKey(node.getLayer())) {
                layerToOutgoing.put(node.getLayer(), new ArrayList<StableHierarchicalLayoutConnector>());
            }
            if (node.isVolatileNode()) {
                // won't have potential startign backedges
                continue;
            }

            //invisible node won't need output offsets
            if (!node.isVisible()) {
                continue;
            }

            for (StableHierarchicalLayoutConnector potentialBackedge : node.getChildren()) {
                if (potentialBackedge.getChild().getLayer() == potentialBackedge.getParent().getLayer()) {
                    //is beginning of outgoing backedge => need to get offset
                    layerToOutgoing.get(node.getLayer()).add(potentialBackedge);
                }
            }
        }

        //
        Map<Integer, OffsetMappingOutputs> layerToOffsetMappingOfOutputs = new HashMap<>();
        for (int layer = graph.minLayer; layer <= graph.maxLayer; layer++) {
            OffsetMappingOutputs offsetMapping = new OffsetMappingOutputs();
            layerToOffsetMappingOfOutputs.put(layer, offsetMapping);
            List<StableHierarchicalLayoutConnector> outgoingBackedges = layerToOutgoing.get(layer);
            Collections.sort(outgoingBackedges, new Comparator<StableHierarchicalLayoutConnector>() {
                @Override
                public int compare(StableHierarchicalLayoutConnector t, StableHierarchicalLayoutConnector t1) {
                    double tAbstractLength = Math.abs(t.getChild().getPosition() - t.getParent().getPosition());
                    double t1AbstractLength = Math.abs(t1.getChild().getPosition() - t1.getParent().getPosition());
                    return Double.compare(tAbstractLength, t1AbstractLength);
                }
            });

            //outgoing backedges are now sorted
            for (StableHierarchicalLayoutConnector backedge : outgoingBackedges) {
                offsetMapping.placeBackEdge(backedge);
            }
        }
        return layerToOffsetMappingOfOutputs;
    }

    /**
     * Creates mapping of layer (N) and needed offset between layer N and N+1 to
     * prevent high angles of connectors.
     */
    private Map<Integer, Integer> createDynamicYOffsetsToPreventHighAnglesOfConnectors(Graph graph) {
        Map<Integer, Integer> layerToExtraYOffset = new HashMap<>();
        if (dynamicLayerHeight) {
            for (int layer = graph.minLayer; layer <= graph.maxLayer; layer++) {
                double offset = 0;

                for (StableHierarchicalLayoutNode node : graph.layerMapping.get(layer)) {
                    if (!node.isVisible()) {
                        continue;
                    }

                    for (StableHierarchicalLayoutConnector childConnector : node.getChildren()) {
                        StableHierarchicalLayoutNode childNode = childConnector.getChild();
                        if (!childNode.isVisible() || !childConnector.isVisible()) {
                            continue;
                        }

                        if (node.getLayer() >= childConnector.getChild().getLayer()) {
                            continue;
                        }

                        //X coord of left edge
                        int nodeX = node.getX();
                        //if is volatile node, then connector will go through middle
                        if (node.isVolatileNode()) {
                            nodeX += node.getDimension().width / 2;
                        } else {
                            // else if is normal node, then add relative position of slot (ClusterInputSlotNodes doesn't need this since they represent Slot itself)
                            if (!(node.getRepresentedVertex() instanceof StableClusterInputSlotNode)) {
                                nodeX += childConnector.getRepresentedLink().getFrom().getRelativePosition().x;
                            }
                        }
                        //X coord of left edge
                        int chNodeX = childNode.getX();
                        //if is volatile node, then connector will go through middle
                        if (childNode.isVolatileNode()) {
                            chNodeX += childNode.getDimension().width / 2;
                        } else {
                            // else if is normal node, then add relative position of slot (ClusterOutputSlotNodes doesn't need this since they represent Slot itself)
                            if (!(childNode.getRepresentedVertex() instanceof StableClusterOutputSlotNode)) {
                                chNodeX += childConnector.getRepresentedLink().getTo().getRelativePosition().x;
                            }
                        }

                        double xDiff = Math.abs(nodeX - chNodeX);
                        double minAngle = Math.toRadians(30);
                        double tan = Math.tan(minAngle);
                        double minY = xDiff * tan;
                        if (minY > offset) {
                            offset = minY;
                        }
                    }

                    // backedges
                    for (StableHierarchicalLayoutConnector parentConnector : node.getParents()) {
                        StableHierarchicalLayoutNode parentNode = parentConnector.getParent();
                        if (!parentNode.isVisible() || !parentConnector.isVisible()) {
                            continue;
                        }

                        if (node.getLayer() >= parentConnector.getParent().getLayer()) {
                            continue;
                        }

                        //X coord of left edge
                        int nodeX = node.getX();
                        //if is volatile node, then connector will go through middle
                        if (node.isVolatileNode()) {
                            nodeX += node.getDimension().width / 2;
                        } else {
                            // else if is normal node, then add relative position of slot (ClusterInputSlotNodes doesn't need this since they represent Slot itself)
                            if (!(node.getRepresentedVertex() instanceof StableClusterInputSlotNode)) {
                                nodeX += parentConnector.getRepresentedLink().getFrom().getRelativePosition().x;
                            }
                        }
                        //X coord of left edge
                        int pNodeX = parentNode.getX();
                        //if is volatile node, then connector will go through middle
                        if (parentNode.isVolatileNode()) {
                            pNodeX += parentNode.getDimension().width / 2;
                        } else {
                            // else if is normal node, then add relative position of slot (ClusterOutputSlotNodes doesn't need this since they represent Slot itself)
                            if (!(parentNode.getRepresentedVertex() instanceof StableClusterOutputSlotNode)) {
                                pNodeX += parentConnector.getRepresentedLink().getTo().getRelativePosition().x;
                            }
                        }

                        double xDiff = Math.abs(nodeX - pNodeX);
                        double minAngle = Math.toRadians(30);
                        double tan = Math.tan(minAngle);
                        double minY = xDiff * tan;
                        if (minY > offset) {
                            offset = minY;
                        }
                    }
                }
                layerToExtraYOffset.put(layer, Double.valueOf(offset).intValue());
            }
        } else {
            for (int i = graph.minLayer; i <= graph.maxLayer; i++) {
                layerToExtraYOffset.put(i, 0);
            }
        }
        return layerToExtraYOffset;
    }

    /**
     * Write computed values from created layout back to original graph.
     *
     * @param graph Hierarchical layout representation of graph.
     * @param layoutGraph Original input graph
     * @param vertexToNodeMapping Mapping of original vertices to nodes in
     * layout.
     */
    protected void updateGraph(Graph graph, StableLayoutGraph layoutGraph, Map<Vertex, StableHierarchicalLayoutNode> vertexToNodeMapping) {
        // Sets initial positions of vertices, y-Coords are not final
        for (StableHierarchicalLayoutNode node : graph.getNodes()) {
            Vertex vertex = node.getRepresentedVertex();
            if (vertex == null || vertex instanceof StableClusterNode) {
                //nothing
            } else {
                vertex.setPosition(new Point(node.getX(), node.getY()));
            }
        }

        // offsets of backedges on outputs and inputs
        Map<Integer, OffsetMappingInputs> layerToOffsetMappingOfInputs = createIncomingBackedgeMapping(graph);
        Map<Integer, OffsetMappingOutputs> layerToOffsetMappingOfOutputs = createOutgoingBackedgeMapping(graph);
        // dynamic y offset to avoid high angles of connectors between layers
        Map<Integer, Integer> layerToExtraYOffset = createDynamicYOffsetsToPreventHighAnglesOfConnectors(graph);

        // adding y coords to vertices
        // has to be added to both nodes and vertices
        int previouslyVisibleLayer = -1;
        int yPos = 0;
        for (int i = graph.minLayer; i <= graph.maxLayer; i++) {
            List<StableHierarchicalLayoutNode> layer = graph.layerMapping.get(i);

            boolean invisibleLayer = true;

            for (StableHierarchicalLayoutNode node : layer) {
                if (node.isVisible()) {
                    invisibleLayer = false;
                    break;
                }
            }
            //if layer is visible, assign coords
            if (!invisibleLayer) {
                int inputsOffset = layerToOffsetMappingOfInputs.get(i).getCurrentOffset();
                int outputsOffset = 0;
                if (layerToOffsetMappingOfOutputs.containsKey(previouslyVisibleLayer)) {
                    outputsOffset = layerToOffsetMappingOfOutputs.get(previouslyVisibleLayer).getCurrentOffset();
                }
                int extraYOffset = 0;
                if (layerToExtraYOffset.containsKey(previouslyVisibleLayer)) {
                    extraYOffset = layerToExtraYOffset.get(previouslyVisibleLayer);
                }

                int finalOffset = Math.max(Math.min(Math.max(minimalLayerGap, extraYOffset), maxLayerGap), inputsOffset + outputsOffset + 30 );

                for (StableHierarchicalLayoutNode node : layer) {
                    if (!node.isVisible()) {
                        continue;
                    }
                    int currentLayerHeight = layerToGridColumnHeight.get(i).intValue();
                    int dueToConnectorOffset = finalOffset;
                    int yOffset = (currentLayerHeight - node.getDimension().height) / 2;
                    node.setY(yPos + yOffset + dueToConnectorOffset);
                    if (node.getRepresentedVertex() != null) {
                        Vertex vertex = node.getRepresentedVertex();
                        vertex.setPosition(new Point(node.getX(), node.getY()));
                    }
                }
                yPos += finalOffset + layerToGridColumnHeight.get(i);
                previouslyVisibleLayer = i;
            }
        }

        //edge beginning/ending for long edges should be drawn
        if(!ignoreTooLongEdges) {
            Map<Vertex, List<Port>> vertexToPortMap = new HashMap<>();
            Map<Port, List<Link>> portToLinkMap = new HashMap<>();
            
            for (Link tooLongLink : tooLongLinks) {
                //Structure for incoming long edges
                Port targetPort = tooLongLink.getTo();
                Vertex targetVertex = targetPort.getVertex();
                
                if(!portToLinkMap.containsKey(targetPort))
                {
                    portToLinkMap.put(targetPort, new ArrayList<Link>());
                    
                    if(!vertexToPortMap.containsKey(targetVertex))
                    {
                        vertexToPortMap.put(targetVertex, new ArrayList<Port>());
                    }
                    vertexToPortMap.get(targetVertex).add(targetPort);
                }
                portToLinkMap.get(targetPort).add(tooLongLink);
            }
            
            for (Vertex targetVertex : vertexToPortMap.keySet()) {
                List<Port> ports = vertexToPortMap.get(targetVertex);
                Collections.sort(ports, new Comparator<Port>() {
                    @Override
                    public int compare(Port o1, Port o2) {
                        return o1.getRelativePosition().x - o2.getRelativePosition().x;
                    }
                });
                
                int availableSpace = targetVertex.getSize().width;
                int connectorCount = 0;
                
                for (Port port : ports) {
                    connectorCount += portToLinkMap.get(port).size();
                }
                int spacing = availableSpace / (connectorCount + 1);
                int placed = 1;
                int startingXCoord = targetVertex.getPosition().x - 3;
                int startingYCoord = targetVertex.getPosition().y - minimalLayerGap / 3;
                
                for (Port port : ports) {
                    List<Link> links = portToLinkMap.get(port);
                    
                    for (Link link : links) {
                        List<Point> controlPoints = new ArrayList<>();
                        //outgoing edge part
                        Port fromPort = link.getFrom();
                        
                        Point startPoint = new Point(fromPort.getVertex().getPosition());
                        if(fromPort.getVertex() instanceof StableClusterInputSlotNode) {
                            startPoint.translate(0, fromPort.getVertex().getSize().height);
                        }
                        else {
                            startPoint.translate(fromPort.getRelativePosition().x, fromPort.getRelativePosition().y + fromPort.getVertex().getSize().height);
                        }
                        
                        controlPoints.add(startPoint);
                        Point midPoint = new Point(startPoint);
                        midPoint.y += minimalLayerGap/4;
                        controlPoints.add(midPoint);
                        Point secondMidPoint = new Point(midPoint);
                        secondMidPoint.x += getSafeOffset();
                        controlPoints.add(secondMidPoint);
                        Point endPoint = new Point(secondMidPoint);
                        endPoint.y += minimalLayerGap/4;
                        controlPoints.add(endPoint);
                        
                        //edge split
                        controlPoints.add(null);
                        
                        //incoming edge part
                        Point targetStartPoint = new Point(startingXCoord + spacing * placed, startingYCoord);
                        controlPoints.add(targetStartPoint);
                        Point targetMidPoint = new Point(startingXCoord + spacing * placed, startingYCoord + minimalLayerGap / 6);
                        controlPoints.add(targetMidPoint);
                        Point targetEndPoint = new Point(startingXCoord, startingYCoord + minimalLayerGap / 3);
                        if(!(targetVertex instanceof StableClusterOutputSlotNode))
                        {
                             targetEndPoint.x += port.getRelativePosition().x;
                        }
                        controlPoints.add(targetEndPoint);
                        
                        placed++;
                        link.setControlPoints(controlPoints);
                    }
                }
            }
        }

        // creating connector paths for links
        for (Link link : layoutGraph.getLinks()) {
            // if link is not visible or has been cut
            if (!link.isVisible()) {
                link.setControlPoints(new ArrayList<Point>(1));
                continue;
            }
            if(tooLongLinks.contains(link))
            {
                if(ignoreTooLongEdges)
                {
                    link.setControlPoints(new ArrayList<Point>(1));
                }
                continue;
            }

            Vertex fromVertex = link.getFrom().getVertex();
            Vertex toVertex = link.getTo().getVertex();

            StableHierarchicalLayoutNode fromNode = vertexToNodeMapping.get(fromVertex);
            StableHierarchicalLayoutNode toNode = vertexToNodeMapping.get(toVertex);

            //find connector representing link
            for (StableHierarchicalLayoutConnector connector : fromNode.getChildren()) {
                if (connector.getRepresentedLink().equals(link)) {
                    //points of line
                    List<Point> linePoints = new ArrayList<>();


                    Point relativeToNode = link.getFrom().getRelativePosition();
                    Dimension fromNodeDimension = fromNode.getDimension();

                    int fromNodeYOffset = (int) ((layerToGridColumnHeight.get(fromNode.getLayer()) - fromNodeDimension.height) / 2) + fromNode.getDimension().height;

                    Point fromNodePosition = new Point(fromNode.getX() + relativeToNode.x, fromNode.getY() + relativeToNode.y + fromNodeYOffset);

                    // In case of ClusterInputSlotNode node X coord already contains relative offset of port
                    if (link.getFrom().getVertex() instanceof StableClusterInputSlotNode) {
                        fromNodePosition.x = fromNode.getX();
                        fromNodePosition.y = fromNode.getY() + fromNodeYOffset;
                    }

                    // Cluster nodes have border, this prevents line from starting lower than it should.
                    if (link.getTo().getVertex() instanceof StableClusterNode) {
                        fromNodePosition.y = fromNode.getY() + fromNodeYOffset;
                    }

                    //add initial point
                    linePoints.add(fromNodePosition);


                    StableHierarchicalLayoutNode childNode = connector.getChild();
                    StableHierarchicalLayoutConnector currentConnectorPart = connector;

                    //if is starting of backedge -> adding offset and creating first part of connector (the part of node->down->horizontal (left/right) part of connector
                    if (fromNode.getLayer() == childNode.getLayer()) {
                        int yBackEdgeOffset = layerToOffsetMappingOfOutputs.get(fromNode.getLayer()).getOffset(currentConnectorPart);
                        Point underFromNodeOffsetPoint = new Point(fromNodePosition);
                        underFromNodeOffsetPoint.y += yBackEdgeOffset;
                        linePoints.add(underFromNodeOffsetPoint);
                        Point underVolatileNodeOffsetPoint = new Point(underFromNodeOffsetPoint);
                        underVolatileNodeOffsetPoint.x = childNode.getX() + volatileNodeWidth / 2;
                        linePoints.add(underVolatileNodeOffsetPoint);
                    }

                    //While link didn't reach endNode
                    while (true) {
                        Dimension childNodeDimension = childNode.getDimension();

                        int childNodeYOffset = (int) ((layerToGridColumnHeight.get(childNode.getLayer()) - childNodeDimension.height) / 2);
                        // connectors go through middle of volatile nodes
                        int relativeOffset = volatileNodeWidth / 2;

                        // if is not volatile node then use relative position of links slot
                        if (!childNode.isVolatileNode()) {
                            relativeOffset = link.getTo().getRelativePosition().x;
                        }

                        // if is backedge (but not last link of it)
                        if (toNode.getLayer() <= fromNode.getLayer() && !childNode.equals(toNode)) {
                            Point toNodePosition = new Point(childNode.getX() + relativeOffset, childNode.getY() + layerToGridColumnHeight.get(childNode.getLayer()).intValue());
                            linePoints.add(toNodePosition);
                            linePoints.add(new Point(childNode.getX() + relativeOffset, childNode.getY() + childNodeYOffset));
                        } else {
                            if (link.getTo().getVertex() instanceof StableClusterOutputSlotNode && !childNode.isVolatileNode()) {
                                // extra point at the top of the layer if node is small, to prevent connector going through neighbouring node/cluster 
                                if ((double) childNode.getDimension().height / layerToGridColumnHeight.get(childNode.getLayer()) < 0.9) {
                                    linePoints.add(new Point(childNode.getX(), childNode.getY() - (int) (((layerToGridColumnHeight.get(childNode.getLayer()) - childNodeDimension.height) / 2) * 0.8)));
                                }
                                linePoints.add(new Point(childNode.getX(), childNode.getY()));
                            } else {
                                //if last point of link and was backedge
                                if (childNode.equals(toNode) && toNode.getLayer() <= fromNode.getLayer()) {
                                    int backedgeOffset = layerToOffsetMappingOfInputs.get(childNode.getLayer()).getOffset(currentConnectorPart);
                                    Point lastPoint = linePoints.get(linePoints.size() - 1);
                                    Point firstOrtogonalPoint = new Point(lastPoint.x, lastPoint.y - backedgeOffset);
                                    linePoints.add(firstOrtogonalPoint);
                                    linePoints.add(new Point(childNode.getX() + relativeOffset, firstOrtogonalPoint.y));

                                }
                                // extra point at the top of the layer if node is small, to prevent connector going through neighbouring node/cluster 
                                if ((double) childNode.getDimension().height / layerToGridColumnHeight.get(childNode.getLayer()) < 0.9) {
                                    linePoints.add(new Point(childNode.getX() + relativeOffset, childNode.getY() - (int) (((layerToGridColumnHeight.get(childNode.getLayer()) - childNodeDimension.height) / 2))));
                                    linePoints.add(new Point(childNode.getX() + relativeOffset, childNode.getY()));
                                } else {
                                    linePoints.add(new Point(childNode.getX() + relativeOffset, childNode.getY() + childNodeYOffset));
                                }

                            }

                            // end if creating link reached last segment
                            if (childNode.equals(toNode)) {
                                break;
                            }

                            // line through volatile node
                            if (childNode.isVolatileNode()) {
                                linePoints.add(new Point(childNode.getX() + relativeOffset, childNode.getY() + childNodeDimension.height + childNodeYOffset));
                            }
                        }

                        //Find next node (can be volatile or final node) of connector path
                        for (StableHierarchicalLayoutConnector childConnector : childNode.getChildren()) {
                            if (childConnector.getRepresentedLink().equals(connector.getRepresentedLink())) {
                                childNode = childConnector.getChild();
                                currentConnectorPart = childConnector;
                                break;
                            }
                        }
                    }
                    //set path of points to link
                    link.setControlPoints(linePoints);
                    break;
                }
            }
        }

        // set paths for loops
        for (StableHierarchicalLayoutConnector loopConnector : loopConnectors) {
            if (!loopConnector.getRepresentedLink().isVisible()) {
                continue;
            }
            Link loopLink = loopConnector.getRepresentedLink();
            Vertex loopVertex = loopLink.getTo().getVertex();
            StableHierarchicalLayoutNode loopNode = vertexToNodeMapping.get(loopVertex);

            Dimension nodeDimension = loopNode.getDimension();

            int xOffset = 0;
            int yOffset = (int) ((layerToGridColumnHeight.get(loopNode.getLayer()) - nodeDimension.height) / 2) + nodeDimension.height;

            Point initialPoint = new Point(loopNode.getX() + loopLink.getFrom().getRelativePosition().x + xOffset, loopNode.getY() + loopLink.getFrom().getRelativePosition().y + yOffset);
            Point finalPoint = new Point(loopNode.getX() + loopLink.getTo().getRelativePosition().x + xOffset, loopNode.getY() + loopLink.getTo().getRelativePosition().y + yOffset - nodeDimension.height);

            List<Point> controlPoints = new ArrayList<>();

            controlPoints.add(initialPoint);

            Point secondP = new Point(initialPoint.x - 5 - nodeDimension.width / 2, initialPoint.y);
            controlPoints.add(secondP);

            Point thirdP = new Point(initialPoint.x - 5 - nodeDimension.width / 2, finalPoint.y);
            controlPoints.add(thirdP);

            controlPoints.add(finalPoint);

            loopLink.setControlPoints(controlPoints);
        }

    }

    /**
     * Calculates in which layer child Node of given connector can be, to
     * satisfy condition, that connectors go from lower layers to higher.
     * Current layer of child Node is taken into consideration. No change is
     * done to any node or connector itself.
     *
     * @param connector
     * @return Minimum layer child Node of given connector should be to achieve
     * lower to higher layer heading of connectors.
     */
    public int calculateChildMinLayer(StableHierarchicalLayoutConnector connector) {
        return Math.max(connector.getChild().getLayer(), connector.getParent().getLayer() + 1);
    }

    /**
     * Graph description object to be used to create hierarchical layout.
     */
    protected class Graph {

        private List<StableHierarchicalLayoutNode> nodes;
        private Map<Integer, ArrayList<StableHierarchicalLayoutNode>> layerMapping;
        private Set<StableHierarchicalLayoutNode> firstLayer;
        private Set<StableHierarchicalLayoutNode> lastLayer;
        private int xMax, yMax;
        private int minLayer, maxLayer;
        private int realNodeCount;
        private int realEdgeCount;

        public Graph() {
            nodes = new ArrayList<>();
        }

        public void addNode(StableHierarchicalLayoutNode node) {
            nodes.add(node);
        }

        public void setNodes(List<StableHierarchicalLayoutNode> nodes) {
            this.nodes = nodes;
        }

        public List<StableHierarchicalLayoutNode> getNodes() {
            return nodes;
        }

        public void setxMax(int xMax) {
            this.xMax = xMax;
        }

        public void setyMax(int yMax) {
            this.yMax = yMax;
        }

        public void setLayerMapping(Map<Integer, ArrayList<StableHierarchicalLayoutNode>> layerMapping) {
            this.layerMapping = layerMapping;
        }

        public Map<Integer, ArrayList<StableHierarchicalLayoutNode>> getLayerMapping() {
            return layerMapping;
        }

        public int getxMax() {
            return xMax;
        }

        public int getyMax() {
            return yMax;
        }

        public Set<StableHierarchicalLayoutNode> getFirstLayer() {
            return firstLayer;
        }

        public Set<StableHierarchicalLayoutNode> getLastLayer() {
            return lastLayer;
        }

        public void setFirstLayer(Set<StableHierarchicalLayoutNode> firstLayer) {
            this.firstLayer = firstLayer;
        }

        public void setLastLayer(Set<StableHierarchicalLayoutNode> lastLayer) {
            this.lastLayer = lastLayer;
        }

        public void setMaxLayer(int maxLayer) {
            this.maxLayer = maxLayer;
        }

        public void setMinLayer(int minLayer) {
            this.minLayer = minLayer;
        }

        public int getMaxLayer() {
            return maxLayer;
        }

        public int getMinLayer() {
            return minLayer;
        }
    }

    /**
     * Performs layouting of graph.
     */
    public void applyLayout(Graph graph) {
        if (graph.getNodes().isEmpty()) {
            return;
        } 
        //Removes cycles in graph, loops are stored in loopConnectors
        removeCycles(graph);
        //Sets layers to nodes
        initLayers(graph);
        //Creates mapping of nodes to layers
        createLayerMapping(graph);
        
        //Checks which connectors are too long
        if (longEdgeMaxLayers >= 0) {
            processTooLongEdges(graph);
        }        
        //sets height of layers
        setHeightOfLayers(graph);
        //Replaces long edges with new path using volatile nodes to in each layer between nodes
        replaceLongEdges(graph);
        //Aggregation rules for incoming edges
        switch (inputsCombinationRule) {
            case PORT_BASED:
                reduceConnectorsBasedOnInputPort(graph);
                reduceReversedConnectorsBasedOnInputPort(graph);
                break;
            case NODE_BASED:
                reduceConnectorsBasedOnInputNode(graph);
                reduceReversedConnectorsBasedOnNode(graph);
                break;
            case NONE:
            default:
                break;
        }
        removeNodesWithoutEdges(graph);
        //Creates initial ordering of nodes
        createOrdering(graph);
        for (int i = 0; i < 3; i++) 
        {
            positionSwapping(graph);
        }
        //removes volatile nodes on empty (invisible) layers so they dont get drawn
        removeUnneededVolatileNodes(graph);

        //assign coordinates to nodes based on node layer and position.
        assignCoords(graph);
    }

    /**
     * Disables visibility of connectors, that are longer than allowed maximum
     * length of edges. Takes into consideration that some layers may not be
     * visible in this view.
     */
    protected void processTooLongEdges(Graph graph) {
        int[] visibleLayers = visibleLayers(graph);

        for (StableHierarchicalLayoutNode node : graph.nodes) {
            for (StableHierarchicalLayoutConnector connector : node.getChildren()) {
                StableHierarchicalLayoutNode childNode = connector.getChild();
                // normal edge
                if (node.getLayer() < childNode.getLayer()) {
                    int countOfVisibleLayers = 0;
                    for (int i = node.getLayer() + 1; i < childNode.getLayer(); i++) {
                        countOfVisibleLayers += visibleLayers[i];
                    }
                    if (countOfVisibleLayers > longEdgeMaxLayers) {
                        tooLongLinks.add(connector.getRepresentedLink());
                        connector.setVisible(false);
                    }
                } // back edge
                else {
                    int countOfVisibleLayers = 0;
                    for (int i = childNode.getLayer(); i < node.getLayer(); i++) {
                        countOfVisibleLayers += visibleLayers[i];
                    }

                    if (countOfVisibleLayers > longEdgeMaxLayers) {
                        tooLongLinks.add(connector.getRepresentedLink());
                        connector.setVisible(false);
                    }
                }
            }
        }
    }

    /**
     * Returns array containing 1 if layer is visible and 0 if not
     */
    private int[] visibleLayers(Graph graph) {
        int[] visibleLayers = new int[graph.maxLayer];

        for (int i = graph.minLayer; i < graph.maxLayer; i++) {
            if (isLayerVisible(graph.layerMapping.get(i))) {
                visibleLayers[i] = 1;
            }
        }
        return visibleLayers;
    }

    /**
     * Returns True if layer contains atleast one visible node. Volatile nodes
     * are ignored, as they can be removed as needed.
     */
    private boolean isLayerVisible(List<StableHierarchicalLayoutNode> layer) {
        for (StableHierarchicalLayoutNode node : layer) {
            if (!node.isVolatileNode() && node.isVisible()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets height of layers based on biggest node of layer.
     */
    private void setHeightOfLayers(Graph graph) {
        for (StableHierarchicalLayoutNode node : graph.nodes) {
            if (layerToGridColumnHeight.get(node.getLayer()) == null) {
                layerToGridColumnHeight.put(node.getLayer(), node.getDimension().getHeight());
            } else if (layerToGridColumnHeight.get(node.getLayer()) < node.getDimension().height) {
                layerToGridColumnHeight.put(node.getLayer(), 1.0 * node.getDimension().height);
            }
        }
    }

    /**
     * Removes volatile nodes in layers, that are not visible in current view.
     */
    private void removeUnneededVolatileNodes(Graph graph) {
        Set<StableHierarchicalLayoutNode> graphNodes = new HashSet<>(graph.nodes);
        
        for (int layer = graph.minLayer; layer <= graph.maxLayer; layer++) {
            List<StableHierarchicalLayoutNode> layerList = graph.layerMapping.get(layer);
            boolean layerVisible = isLayerVisible(layerList);
            
            //if layer is not visible remove volatile nodes and reconnect their connectors
            if (!layerVisible) {
                ArrayList<StableHierarchicalLayoutNode> maintainedNodes = new ArrayList<>();

                for (StableHierarchicalLayoutNode potentialVolatileNode : layerList) {
                    //if volatile node then can be reduced
                    if (potentialVolatileNode.isVolatileNode() && potentialVolatileNode.isVisible()) {
                        // for each parent connector find child connector
                        // from child of same link remove volatile -> child and add parent -> child instead
                        Map<Link, StableHierarchicalLayoutConnector> childConnectorMapping = new HashMap<>();
                        
                        for (StableHierarchicalLayoutConnector childConnector : potentialVolatileNode.getChildren()) {
                            childConnectorMapping.put(childConnector.getRepresentedLink(), childConnector);
                        }
                        
                        for (StableHierarchicalLayoutConnector parentConnector : potentialVolatileNode.getParents()) {
                            Link representedLink = parentConnector.getRepresentedLink();
                            
                            
                            StableHierarchicalLayoutConnector childConnector = childConnectorMapping.get(representedLink);
                            StableHierarchicalLayoutNode child = childConnector.getChild();
                            child.getParents().remove(childConnector);
                            //set parent connector to parent -> child
                            parentConnector.setChild(child);
                            child.addParent(parentConnector);
                        }
                        graphNodes.remove(potentialVolatileNode);
                    }
                    //leave it in layer
                    else {
                        maintainedNodes.add(potentialVolatileNode);
                    }
                }
                // set new nodes
                graph.layerMapping.put(layer, maintainedNodes);
            }
        }
        ArrayList<StableHierarchicalLayoutNode> nodes = new ArrayList<>(graph.nodes.size());
        
        for (StableHierarchicalLayoutNode node : graph.nodes) {
            if(graphNodes.contains(node)) {
                nodes.add(node);
            }
        }
        graph.nodes = nodes;
        
    }

    /**
     * Creates mapping between layer and its nodes
     */
    protected void createLayerMapping(Graph graph) {
        // Creation of layer mapping
        int maxLayer = -1;
        for (StableHierarchicalLayoutNode node : graph.getNodes()) {
            if (maxLayer < node.getLayer()) {
                maxLayer = node.getLayer();
            }
        }

        Map<Integer, ArrayList<StableHierarchicalLayoutNode>> layerMapping = new HashMap<>();

        for (int i = 1; i <= maxLayer; i++) {
            layerMapping.put(i, new ArrayList<StableHierarchicalLayoutNode>());
        }

        for (StableHierarchicalLayoutNode node : graph.getNodes()) {
            layerMapping.get(node.getLayer()).add(node);
        }
        graph.layerMapping = layerMapping;
    }

    protected void reduceReversedConnectorsBasedOnInputPort(Graph graph) {
        int maxLayer = graph.maxLayer;
        int minLayer = graph.minLayer;

        for (int i = minLayer; i <= maxLayer; i++) {
            List<StableHierarchicalLayoutNode> layer = graph.layerMapping.get(i);

            for (int j = 0; j < layer.size(); j++) {
                //Check node if there are atleast 2 parent volatile nodes, that can be reduced to 1
                StableHierarchicalLayoutNode node = layer.get(j);

                Map<Port, Set<StableHierarchicalLayoutNode>> portToNodeMapping = new HashMap<>();

                for (StableHierarchicalLayoutConnector parentConnector : node.getParents()) {
                    StableHierarchicalLayoutNode parent = parentConnector.getParent();

                    if (parentConnector.getType() != StableHierarchicalLayoutConnector.ConnectorType.normal) {
                        continue;
                    }

                    if (!parent.isVolatileNode()) {
                        continue;
                    }
                    if (parent.getLayer() < node.getLayer()) {
                        continue;
                    }

                    //if is volatile node and has only 1 child or all children go to same node
                    boolean viableToReduction = false;
                    if (parent.getChildren().size() == 1) {
                        viableToReduction = true;
                    }
                    if (!viableToReduction) {
                        StableHierarchicalLayoutConnector parentsChildRepresentant = parent.getChildren().get(0);
                        Port representantPort = parentsChildRepresentant.getRepresentedLink().getTo();

                        boolean allGoToSameNode = true;
                        for (StableHierarchicalLayoutConnector parentsChildConnector : parent.getChildren()) {
                            Port parentsChildConnectorPort = parentsChildConnector.getRepresentedLink().getTo();
                            if (!representantPort.equals(parentsChildConnectorPort)) {
                                allGoToSameNode = false;
                                break;
                            }
                        }
                        viableToReduction = allGoToSameNode;
                    }

                    if (viableToReduction) {
                        if (!portToNodeMapping.containsKey(parentConnector.getRepresentedLink().getTo())) {
                            portToNodeMapping.put(parentConnector.getRepresentedLink().getTo(), new HashSet<StableHierarchicalLayoutNode>());
                        }
                        portToNodeMapping.get(parentConnector.getRepresentedLink().getTo()).add(parent);
                    }
                }
                for (Port port : portToNodeMapping.keySet()) {
                    Set<StableHierarchicalLayoutNode> reducibleNodes = portToNodeMapping.get(port);
                    //if there are more than 1 node that can be reduced
                    if (reducibleNodes.size() > 1) {
                        //create new volatile node
                        StableHierarchicalLayoutNode newVolatileNode = new StableHierarchicalLayoutNode(true, reducibleNodes.iterator().next().getLayer());
                        newVolatileNode.setVisible(false);
                        newVolatileNode.setDimension(new Dimension(volatileNodeWidth, (layerToGridColumnHeight.get(newVolatileNode.getLayer()).intValue())));
                        graph.getNodes().add(newVolatileNode);
                        graph.layerMapping.get(newVolatileNode.getLayer()).add(newVolatileNode);
                        //transfer connectors and remove original volatile nodes
                        for (StableHierarchicalLayoutNode replacedNode : reducibleNodes) {
                            newVolatileNode.setVolatileNode(newVolatileNode.isVisible() || replacedNode.isVisible());
                            for (StableHierarchicalLayoutConnector parentConnector : replacedNode.getParents()) {
                                parentConnector.setChild(newVolatileNode);
                                newVolatileNode.addParent(parentConnector);
                            }
                            for (StableHierarchicalLayoutConnector childrenConnector : replacedNode.getChildren()) {
                                childrenConnector.setParent(newVolatileNode);
                                newVolatileNode.addChild(childrenConnector);
                            }
                            graph.getNodes().remove(replacedNode);
                            graph.layerMapping.get(replacedNode.getLayer()).remove(replacedNode);
                        }
                    }
                }
            }
        }
    }

    protected void reduceReversedConnectorsBasedOnNode(Graph graph) {
        int maxLayer = graph.maxLayer;
        int minLayer = graph.minLayer;

        for (int i = minLayer; i <= maxLayer; i++) {
            List<StableHierarchicalLayoutNode> layer = graph.layerMapping.get(i);

            for (int j = 0; j < layer.size(); j++) {
                //Check node if there are atleast 2 parent volatile nodes, that can be reduced to 1
                StableHierarchicalLayoutNode node = layer.get(j);

                Set<StableHierarchicalLayoutNode> suitableNodes = new HashSet<>();

                for (StableHierarchicalLayoutConnector parentConnector : node.getParents()) {
                    StableHierarchicalLayoutNode parent = parentConnector.getParent();

                    if (parentConnector.getType() != StableHierarchicalLayoutConnector.ConnectorType.normal) {
                        continue;
                    }

                    if (!parent.isVolatileNode()) {
                        continue;
                    }
                    if (parent.getLayer() < node.getLayer()) {
                        continue;
                    }

                    //if is volatile node and has only 1 child or all children go to same node
                    boolean viableToReduction = false;
                    if (parent.getChildren().size() == 1) {
                        viableToReduction = true;
                    }
                    if (!viableToReduction) {
                        StableHierarchicalLayoutConnector parentsChildRepresentant = parent.getChildren().get(0);
                        Vertex representantVertex = parentsChildRepresentant.getRepresentedLink().getTo().getVertex();

                        boolean allGoToSameNode = true;
                        for (StableHierarchicalLayoutConnector parentsChildConnector : parent.getChildren()) {
                            Vertex parentsChildConnectorVertex = parentsChildConnector.getRepresentedLink().getTo().getVertex();
                            if (!representantVertex.equals(parentsChildConnectorVertex)) {
                                allGoToSameNode = false;
                                break;
                            }
                        }
                        viableToReduction = allGoToSameNode;
                    }
                    // collect reduced parent
                    if (viableToReduction) {
                        suitableNodes.add(parent);
                    }
                }
                //if there are more than 1 node that can be reduced
                if (suitableNodes.size() > 1) {
                    //create new volatile node
                    StableHierarchicalLayoutNode newVolatileNode = new StableHierarchicalLayoutNode(true, suitableNodes.iterator().next().getLayer());
                    newVolatileNode.setVisible(false);
                    newVolatileNode.setDimension(new Dimension(volatileNodeWidth, (layerToGridColumnHeight.get(newVolatileNode.getLayer()).intValue())));
                    graph.getNodes().add(newVolatileNode);
                    graph.layerMapping.get(newVolatileNode.getLayer()).add(newVolatileNode);

                    //transfer connectors and remove original volatile nodes
                    for (StableHierarchicalLayoutNode replacedNode : suitableNodes) {
                        newVolatileNode.setVisible(newVolatileNode.isVisible() || replacedNode.isVisible());
                        for (StableHierarchicalLayoutConnector parentConnector : replacedNode.getParents()) {
                            parentConnector.setChild(newVolatileNode);
                            newVolatileNode.addParent(parentConnector);
                        }
                        for (StableHierarchicalLayoutConnector childrenConnector : replacedNode.getChildren()) {
                            childrenConnector.setParent(newVolatileNode);
                            newVolatileNode.addChild(childrenConnector);
                        }
                        graph.getNodes().remove(replacedNode);
                        graph.layerMapping.get(replacedNode.getLayer()).remove(replacedNode);

                        int indexOfReplacedNode = graph.layerMapping.get(replacedNode.getLayer()).indexOf(replacedNode);
                        graph.layerMapping.get(replacedNode.getLayer()).remove(replacedNode);
                        graph.nodes.remove(replacedNode);

                        if (indexOfReplacedNode < j) {
                            j--;
                        } else {
                            // nothing
                        }
                    }
                }
            }
        }
    }

    protected void reduceConnectorsBasedOnInputPort(Graph graph) {
        int maxLayer = graph.maxLayer;
        int minLayer = graph.minLayer;

        for (int i = maxLayer; i >= minLayer; i--) {
            List<StableHierarchicalLayoutNode> layer = graph.layerMapping.get(i);

            for (int j = 0; j < layer.size(); j++) {
                //Check node if there are atleast 2 parent volatile nodes, that can be reduced to 1
                StableHierarchicalLayoutNode node = layer.get(j);

                Map<Port, Set<StableHierarchicalLayoutNode>> portToNodeMapping = new HashMap<>();

                for (StableHierarchicalLayoutConnector parentConnector : node.getParents()) {
                    StableHierarchicalLayoutNode parent = parentConnector.getParent();

                    if (parentConnector.getType() != StableHierarchicalLayoutConnector.ConnectorType.normal) {
                        continue;
                    }

                    if (!parent.isVolatileNode()) {
                        continue;
                    }
                    if (parent.getLayer() >= node.getLayer()) {
                        continue;
                    }

                    //if is volatile node and has only 1 child or all children go to same port
                    boolean viableToReduction = false;
                    if (parent.getChildren().size() == 1) {
                        viableToReduction = true;
                    }
                    if (!viableToReduction) {
                        Port parentsChildPortRepresentant = parent.getChildren().get(0).getRepresentedLink().getTo();
                        boolean allGoToSameNode = true;
                        for (StableHierarchicalLayoutConnector parentsChildConnector : parent.getChildren()) {
                            Port parentsChildConnectorPort = parentsChildConnector.getRepresentedLink().getTo();
                            if (!parentsChildConnectorPort.equals(parentsChildPortRepresentant)) {
                                allGoToSameNode = false;
                                break;
                            }
                        }
                        viableToReduction = allGoToSameNode;
                    }

                    //collect reduced parent
                    if (viableToReduction) {
                        if (!portToNodeMapping.containsKey(parentConnector.getRepresentedLink().getTo())) {
                            portToNodeMapping.put(parentConnector.getRepresentedLink().getTo(), new HashSet<StableHierarchicalLayoutNode>());
                        }
                        portToNodeMapping.get(parentConnector.getRepresentedLink().getTo()).add(parent);
                    }
                }

                for (Port port : portToNodeMapping.keySet()) {
                    //if there are more than 1 node that can be reduced
                    Set<StableHierarchicalLayoutNode> reducibleNodes = portToNodeMapping.get(port);
                    if (reducibleNodes.size() > 1) {
                        //create new volatile node
                        StableHierarchicalLayoutNode newVolatileNode = new StableHierarchicalLayoutNode(true, node.getLayer() - 1);
                        newVolatileNode.setVisible(false);
                        newVolatileNode.setDimension(new Dimension(volatileNodeWidth, (layerToGridColumnHeight.get(newVolatileNode.getLayer()).intValue())));
                        graph.getNodes().add(newVolatileNode);
                        graph.layerMapping.get(newVolatileNode.getLayer()).add(newVolatileNode);
                        //transfer connectors and remove original volatile nodes
                        for (StableHierarchicalLayoutNode replacedNode : reducibleNodes) {
                            newVolatileNode.setVisible(newVolatileNode.isVisible() || replacedNode.isVisible());
                            for (StableHierarchicalLayoutConnector parentConnector : replacedNode.getParents()) {
                                parentConnector.setChild(newVolatileNode);
                                newVolatileNode.addParent(parentConnector);
                            }
                            for (StableHierarchicalLayoutConnector childrenConnector : replacedNode.getChildren()) {
                                childrenConnector.setParent(newVolatileNode);
                                newVolatileNode.addChild(childrenConnector);
                            }
                            graph.layerMapping.get(replacedNode.getLayer()).remove(replacedNode);
                            graph.nodes.remove(replacedNode);
                        }
                    }
                }
            }
        }
    }

    protected void reduceConnectorsBasedOnInputNode(Graph graph) {
        int maxLayer = graph.maxLayer;
        int minLayer = graph.minLayer;

        for (int i = maxLayer; i >= minLayer; i--) {
            List<StableHierarchicalLayoutNode> layer = graph.layerMapping.get(i);

            for (int j = 0; j < layer.size(); j++) {
                //Check node if there are atleast 2 parent volatile nodes, that can be reduced to 1
                StableHierarchicalLayoutNode node = layer.get(j);

                Set<StableHierarchicalLayoutNode> suitableNodes = new HashSet<>();

                for (StableHierarchicalLayoutConnector parentConnector : node.getParents()) {
                    if (parentConnector.getType() != StableHierarchicalLayoutConnector.ConnectorType.normal) {
                        continue;
                    }
                    StableHierarchicalLayoutNode parent = parentConnector.getParent();
                    if (!parent.isVolatileNode()) {
                        continue;
                    }
                    if (parent.getLayer() >= node.getLayer()) {
                        continue;
                    }

                    //if is volatile node and has only 1 child or all children go to same node
                    boolean viableToReduction = false;
                    if (parent.getChildren().size() == 1) {
                        viableToReduction = true;
                    }
                    if (!viableToReduction) {
                        StableHierarchicalLayoutConnector parentsChildRepresentant = parent.getChildren().get(0);
                        Vertex representantVertex = parentsChildRepresentant.getRepresentedLink().getTo().getVertex();

                        boolean allGoToSameNode = true;
                        for (StableHierarchicalLayoutConnector parentsChildConnector : parent.getChildren()) {
                            Vertex parentsChildConnectorVertex = parentsChildConnector.getRepresentedLink().getTo().getVertex();
                            if (!representantVertex.equals(parentsChildConnectorVertex)) {
                                allGoToSameNode = false;
                                break;
                            }
                        }
                        viableToReduction = allGoToSameNode;
                    }

                    //collect reduced parent
                    if (viableToReduction) {
                        suitableNodes.add(parent);
                    }
                }
                //if there are more than 1 node that can be reduced
                if (suitableNodes.size() > 1) {
                    //create new volatile node
                    StableHierarchicalLayoutNode newVolatileNode = new StableHierarchicalLayoutNode(true, node.getLayer() - 1);
                    newVolatileNode.setVisible(false);
                    newVolatileNode.setDimension(new Dimension(volatileNodeWidth, (layerToGridColumnHeight.get(newVolatileNode.getLayer()).intValue())));
                    graph.getNodes().add(newVolatileNode);
                    graph.layerMapping.get(newVolatileNode.getLayer()).add(newVolatileNode);
                    //transfer connectors and remove original volatile nodes
                    for (StableHierarchicalLayoutNode replacedNode : suitableNodes) {
                        newVolatileNode.setVisible(newVolatileNode.isVisible() || replacedNode.isVisible());
                        for (StableHierarchicalLayoutConnector parentConnector : replacedNode.getParents()) {
                            parentConnector.setChild(newVolatileNode);
                            newVolatileNode.addParent(parentConnector);
                        }
                        for (StableHierarchicalLayoutConnector childrenConnector : replacedNode.getChildren()) {
                            childrenConnector.setParent(newVolatileNode);
                            newVolatileNode.addChild(childrenConnector);
                        }
                        int indexOfReplacedNode = graph.layerMapping.get(replacedNode.getLayer()).indexOf(replacedNode);
                        graph.layerMapping.get(replacedNode.getLayer()).remove(replacedNode);
                        graph.nodes.remove(replacedNode);

                        if (indexOfReplacedNode < j) {
                            j--;
                        } else {
                            // nothing
                        }
                    }
                }
            }
        }
    }

    /**
     * Breaks cycles in graph. Loops are removed from graph, other cycles have
     * their connector type switched to ParentArrow.
     */
    protected void removeCycles(Graph graph) {
        List<StableHierarchicalLayoutNode> nodes = graph.getNodes();

        //removing loops
        for (StableHierarchicalLayoutNode node : nodes) {
            List<StableHierarchicalLayoutConnector> connectors = node.getChildren();
            for (int i = 0; i < connectors.size(); i++) {
                StableHierarchicalLayoutConnector connector = connectors.get(i);
                if (connector.getChild() == node) {
                    loopConnectors.add(connector);
                    node.getChildren().remove(connector);
                    node.getParents().remove(connector);
                    --i;
                }
            }
        }

        //removing cycles
        Set<StableHierarchicalLayoutNode> globalUnvisited = new LinkedHashSet<>(graph.nodes);
        
        Stack<StableHierarchicalLayoutNode> nodeStack = new Stack<>();
        
        while(!globalUnvisited.isEmpty())
        {
            StableHierarchicalLayoutNode newRoot = globalUnvisited.iterator().next();
            nodeStack.add(newRoot);
            Set<StableHierarchicalLayoutNode> pathVisited = new HashSet<>();
            
            while(!nodeStack.empty())
            {
                StableHierarchicalLayoutNode currentNode = nodeStack.pop();
                
                if(!globalUnvisited.contains(currentNode))
                {
                    pathVisited.remove(currentNode);
                    continue;
                }
                
                globalUnvisited.remove(currentNode);
                pathVisited.add(currentNode);
                nodeStack.push(currentNode);

                for (StableHierarchicalLayoutConnector childConnector : currentNode.getChildren()) 
                {
                    StableHierarchicalLayoutNode child = childConnector.getChild();
                
                    if (pathVisited.contains(child)) {
                        //connector is not reversed in structure, but reversing is indicated by parentArrow
                        childConnector.setType(StableHierarchicalLayoutConnector.ConnectorType.reversed);
                    } else if(globalUnvisited.contains(child)){
                        nodeStack.push(child);
                    }
                }
            }
        }
    }

    /**
     * Creates ordering of graph nodes by placing nodes on average position of
     * their parents.
     */
    protected void createOrdering(Graph graph) {
        /*
         * Algorithm goes through each layer and tries to place best position for 
         * each node based on its parents. If position is free, then place node 
         * there, if position is occupied, compare not rounded positions
         * new < old - new node is placed on position, old node and nodes on right
         * of position are shifted to the right till there are no collisions
         * new >= old - new node is placed on position, old node and nods on left
         * of position are shifted to the left till there are no collisions
         */

        //Initial position of nodes (leaving some space between to reduce initial collisions)
        Map<Integer, ArrayList<StableHierarchicalLayoutNode>> layerMapping = graph.layerMapping;
        
        Set<StableHierarchicalLayoutNode> visited = new HashSet<>();
        
        for (int layer = graph.minLayer; layer <= graph.maxLayer; layer++) {
            int position = 1;
            for (StableHierarchicalLayoutNode node : layerMapping.get(layer)) {
                node.setPosition(position);
                position += 200;
            }
        }
        
        // dynamic iteration limit
        int iterationCount = 10;
        final int minIterations = 2;
        
        int edgeCountModifier = graph.realEdgeCount % 200;
        iterationCount = Math.max(iterationCount - edgeCountModifier, minIterations);

        // iteratively go through layers and compute ideal positions
        for (int repeat = 0; repeat < iterationCount; repeat++) {
            //downsweep
            for (int layer = graph.minLayer; layer <= graph.maxLayer; layer++) {
                //layer is sorted throughout iteration, to iterate over all nodes
                ArrayList<StableHierarchicalLayoutNode> layerList = layerMapping.get((Integer) layer);

                for (int i = 0; i < layerList.size(); i++) {
                    //Compute ideal position of node
                    StableHierarchicalLayoutNode node = layerList.get(i);   
                    
                    
                    if (visited.contains(node)) {
                        continue;
                    }
                    visited.add(node);
                    
                    double positionValue = computePositionOneLayer(node);

                    double positionValueRounded = Math.round(positionValue);

                    // node is on ideal position
                    if (node.getPosition() == positionValueRounded) {
                        continue;
                    }

                    // Node is not optimally placed, place it while checking for collisions
                    //!! layerList contains nodes sorted by position (ASC)
                    // ==> binary search
                    int left = 0;
                    int right = layerList.size()-1;
                    int collisionIndex = 0;
                    boolean found = false;
                    
                    if (positionValueRounded > node.getPosition()) {
                        left = i + 1;
                    } else {
                        right = i - 1;
                    }
                    
                    while (left <= right) {
                        collisionIndex = (left + right) / 2;

                        int usedPosition = (int) layerList.get(collisionIndex).getPosition();
                        if (usedPosition > positionValueRounded) {
                            right = collisionIndex - 1;
                        } else if (usedPosition < positionValueRounded) {
                            left = collisionIndex + 1;
                        } else {
                            found = true;
                            break;
                        }
                    }
                    
                    if (found) {
                        StableHierarchicalLayoutNode controlled = layerList.get(collisionIndex);
                        
                        if (controlled.getPosition() == positionValueRounded) {
                            double exactPosition = computePositionOneLayer(controlled);
                            // If controlled node wants higher position than node
                            // shift controlled position (and possibly other nodes) to the right and place node
                            if (exactPosition >= positionValue) {
                                node.setPosition(positionValueRounded);
                                
                                if (i < collisionIndex) {
                                    leftShift(i, collisionIndex - 1, layerList);
                                    controlled.setPosition(controlled.getPosition() + 1);
                                
                                    //shift only positions of other nodes
                                    for (int k = collisionIndex + 1; k < layerList.size(); k++) {
                                        StableHierarchicalLayoutNode n = layerList.get(k);
                                        StableHierarchicalLayoutNode m = layerList.get(k - 1);
                                        if (m.getPosition() == n.getPosition()) {
                                            n.setPosition(n.getPosition() + 1);
                                        }
                                        else {
                                            break;
                                        }
                                    }
                                } else {
                                    rightRotationOfElements(collisionIndex, i, layerList);
                                }
                                
                            } // If controlled wants lower position than node
                            // Place node on position and rebuild sortedPosition
                            else {
                                node.setPosition(positionValueRounded);
                                
                                if (i < collisionIndex) {
                                    leftRotationOfElements(i, collisionIndex, layerList);
                                }
                                else {
                                    rightShift(collisionIndex + 1, i, layerList);
                                    controlled.setPosition(controlled.getPosition() - 1);
                                    
                                    // Cascade position shift
                                    for (int k = collisionIndex - 1; k >= 0; k--) {
                                        StableHierarchicalLayoutNode n = layerList.get(k);
                                        StableHierarchicalLayoutNode m = layerList.get(k + 1);
                                        if (m.getPosition() == n.getPosition()) {
                                            n.setPosition(n.getPosition() - 1);
                                        } else {
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else {
                        if (i > left) {
                            rightShift(left, i, layerList);
                        } else {
                            leftShift(i, right, layerList);
                        }
                        node.setPosition(positionValueRounded);
                    }

                    if (i < right) {
                        i--;
                    }
                }
                
                visited.clear();
            }
        }
        graph.setLayerMapping(layerMapping);
    }
    
    /**
     * Rotates elements of List 1 position to the left WITHOUT updating positions.
     * @param from Index of first rotated item (inclusive)
     * @param to Index of last rotated item (inclusive)
     * @param nodeList List with nodes
     */
    private void leftShift(int from, int to, ArrayList<StableHierarchicalLayoutNode> nodeList) {
        StableHierarchicalLayoutNode replacing = nodeList.get(from);
        
        int curr = to;
        
        while(curr >= from) {
            
            StableHierarchicalLayoutNode replaced = nodeList.set(curr, replacing);
            
            replacing = replaced;
            curr--;
        }
    }
    
    /**
     * Rotates elements of List 1 position to the right WITHOUT updating positions.
     * @param from Index of first rotated item (inclusive)
     * @param to Index of last rotated item (inclusive)
     * @param nodeList List with nodes
     */
    private void rightShift(int from, int to, ArrayList<StableHierarchicalLayoutNode> nodeList) {
        StableHierarchicalLayoutNode replacing = nodeList.get(to);
        
        int curr = from;

        while (curr <= to) {
            StableHierarchicalLayoutNode replaced = nodeList.set(curr, replacing);

            replacing = replaced;
            curr++;
        }
    }
    
    /**
     * Rotates elements of List 1 position to the right while updating positions.
     * Last element have to have its new position already set.
     * @param from Index of first rotated item (inclusive)
     * @param to Index of last rotated item (inclusive)
     * @param nodeList List with nodes
     */
    private void rightRotationOfElements(int from, int to, ArrayList<StableHierarchicalLayoutNode> nodeList) {
        int curr = from;
        StableHierarchicalLayoutNode replacing = nodeList.get(to);

        while (curr <= to) {
            StableHierarchicalLayoutNode replaced = nodeList.set(curr, replacing);
            if (replaced.getPosition() == replacing.getPosition()) {
                replaced.setPosition(replaced.getPosition() + 1);
            }

            replacing = replaced;
            curr++;
        }
    }
    
    /**
     * Rotates elements of List 1 position to the left while updating positions.
     * First element have to have its new position already set.
     * @param from Index of first rotated item (inclusive)
     * @param to Index of last rotated item (inclusive)
     * @param nodeList List with nodes
     */
    private void leftRotationOfElements(int from, int to, ArrayList<StableHierarchicalLayoutNode> nodeList) {
        int curr = to;
        
        StableHierarchicalLayoutNode replacing = nodeList.get(from);

        while (curr >= from) {
            StableHierarchicalLayoutNode replaced = nodeList.set(curr, replacing);
            if (replaced.getPosition() == replacing.getPosition()) {
                replaced.setPosition(replaced.getPosition() - 1);
            }

            replacing = replaced;
            curr--;
        }
    }
    
    /**
     * Computes ideal position based on both parents and children.
     */
    protected double computePositionBothWay(StableHierarchicalLayoutNode node) {
        double avg = 0, count = 0;

        for (StableHierarchicalLayoutConnector childConnector : node.getChildren()) {
            StableHierarchicalLayoutNode child = childConnector.getChild();
            double value = child.getPosition();

            avg += value;
            count++;
        }
        for (StableHierarchicalLayoutConnector parentConnector : node.getParents()) {
            StableHierarchicalLayoutNode parent = parentConnector.getParent();
            double value = parent.getPosition();
            avg += value;
            count++;
        }
        avg /= count;
        return avg;
    }

    /**
     * Computes ideal position based on parents of node, if node has no parents,
     * then children are used instead.
     */
    protected double computePositionOneLayer(StableHierarchicalLayoutNode node) {
        double avg = 0, count = 0;

        if (node.getParents().isEmpty()) {
            for (StableHierarchicalLayoutConnector childConnector : node.getChildren()) {
                StableHierarchicalLayoutNode child = childConnector.getChild();
                double value = child.getPosition();

                avg += value;
                count++;
            }
        }
        for (StableHierarchicalLayoutConnector parentConnector : node.getParents()) {
            StableHierarchicalLayoutNode parent = parentConnector.getParent();
            double value = parent.getPosition();
            avg += value;
            count++;
        }
        avg /= count;
        return avg;
    }

    /**
     * Computes ideal position based on position of parents and grandparents. If
     * node has none, children and grandchildren are used instead.
     */
    protected double computePosition(StableHierarchicalLayoutNode node) {
        double avg = 0, count = 0;

        // if node has no parents, then use atleast suboptimal solution from children
        if (node.getParents().isEmpty()) {
            for (StableHierarchicalLayoutConnector childConnector : node.getChildren()) {
                StableHierarchicalLayoutNode child = childConnector.getChild();
                double value = child.getPosition();

                avg += value;
                count++;

                for (StableHierarchicalLayoutConnector grandChildConnector : child.getChildren()) {
                    value = grandChildConnector.getChild().getPosition();
                    avg += value;
                    count++;
                }
            }
        }

        for (StableHierarchicalLayoutConnector parentConnector : node.getParents()) {
            StableHierarchicalLayoutNode parent = parentConnector.getParent();
            double value = parent.getPosition();
            avg += value;
            count++;

            for (StableHierarchicalLayoutConnector grandParentConnector : parent.getParents()) {
                value = grandParentConnector.getParent().getPosition();

                avg += value;
                count++;
            }
        }

        avg /= count;
        return avg;
    }

    /**
     * Replaces edges connecting nodes on immediately neighbouring layers with
     * series of volatile(auxiliary) nodes and connectors in whole graph.
     */
    protected void replaceLongEdges(Graph graph) {
        List<StableHierarchicalLayoutNode> nodes = new ArrayList<>(graph.getNodes());
        for (StableHierarchicalLayoutNode node : nodes) {
            List<StableHierarchicalLayoutConnector> connectors = node.getChildren();
            int rank = node.getLayer();
            switch (outputsCombinationRule) {
                case NODE_BASED:
                    List<StableHierarchicalLayoutConnector> managedConnectors = new ArrayList<>();
                    List<StableHierarchicalLayoutConnector> managedBackEdgeConnectors = new ArrayList<>();
                    // long edges are grouped together to one volatile node per layer
                    for (StableHierarchicalLayoutConnector connector : connectors) {
                        StableHierarchicalLayoutNode child = connector.getChild();
                        int childRank = child.getLayer();

                        if (childRank - rank > 1) {
                            managedConnectors.add(connector);
                        } else if (childRank - rank <= 0) {
                            managedBackEdgeConnectors.add(connector);
                        }
                    }

                    transformLongEdge(graph, managedConnectors, rank + 1);
                    transformBackEdge(graph, managedBackEdgeConnectors, rank);
                    break;
                case PORT_BASED:
                    Map<Port, List<StableHierarchicalLayoutConnector>> managedPortToConnectors = new HashMap<>();
                    Map<Port, List<StableHierarchicalLayoutConnector>> managedPortToBackEdgeConnectors = new HashMap<>();
                    
                    //to maintain ordering
                    List<Port> managedPortToCon = new ArrayList<>();
                    List<Port> managedPortToBac = new ArrayList<>();
                    
                    //long edges of same port are grouped together to one volatile node  per layer
                    for (StableHierarchicalLayoutConnector connector : connectors) {
                        StableHierarchicalLayoutNode child = connector.getChild();
                        int childRank = child.getLayer();

                        if (childRank - rank > 1) {
                            Port fromPort = connector.getRepresentedLink().getFrom();
                            
                            if (!managedPortToConnectors.containsKey(fromPort)) {
                                managedPortToCon.add(fromPort);
                                managedPortToConnectors.put(fromPort, new ArrayList<StableHierarchicalLayoutConnector>());
                            }
                            managedPortToConnectors.get(fromPort).add(connector);
                        } else if (childRank - rank <= 0) {
                            Port fromPort = connector.getRepresentedLink().getFrom();
                            
                            if (!managedPortToBackEdgeConnectors.containsKey(fromPort)) {
                                managedPortToBac.add(fromPort);
                                managedPortToBackEdgeConnectors.put(fromPort, new ArrayList<StableHierarchicalLayoutConnector>());
                            }
                            managedPortToBackEdgeConnectors.get(fromPort).add(connector);
                        }
                    }

                    for (Port port : managedPortToCon) {
                        transformLongEdge(graph, managedPortToConnectors.get(port), rank + 1);
                    }
                    for (Port port : managedPortToBac) {
                        transformBackEdge(graph, managedPortToBackEdgeConnectors.get(port), rank);
                    }
                    break;
                case NONE:
                default:
                    // Version, where each replaced Long edge has its own volatile node path
                    List<StableHierarchicalLayoutConnector> iterableConnectors = new ArrayList<>(connectors);
                    for (StableHierarchicalLayoutConnector connector : iterableConnectors) {
                        StableHierarchicalLayoutNode child = connector.getChild();
                        int childRank = child.getLayer();

                        if (childRank - rank > 1) {
                            transformLongEdge(graph, connector);
                        } // is backedge
                        else if (childRank - rank <= 0) {
                            transformBackEdge(graph, connector);
                        }
                    }
            }
        }
    }

    /**
     * Transforms list of long edges to short ones using volatile nodes. All
     * connectors use same volatile node per layer.
     *
     * @param connectors long edges
     * @param layer starting layer
     */
    private void transformLongEdge(Graph graph, List<StableHierarchicalLayoutConnector> connectors, int layer) {
        for (StableHierarchicalLayoutConnector connector : connectors) {
            connector.getParent().getChildren().remove(connector);
            connector.getChild().getParents().remove(connector);
        }

        StableHierarchicalLayoutNode previousLayer = null;
        while (!connectors.isEmpty()) {
            StableHierarchicalLayoutNode volatileNode = new StableHierarchicalLayoutNode(true, layer);
            volatileNode.setVisible(false);
            volatileNode.setDimension(new Dimension(volatileNodeWidth, (layerToGridColumnHeight.get(layer).intValue())));
            graph.addNode(volatileNode);
            graph.layerMapping.get(volatileNode.getLayer()).add(volatileNode);

            for (StableHierarchicalLayoutConnector connector : connectors) {
                StableHierarchicalLayoutNode parent;
                if (layer - 1 == connector.getParent().getLayer()) {
                    parent = connector.getParent();
                } else {
                    parent = previousLayer;
                }
                StableHierarchicalLayoutConnector newConnector = volatileNode.addParent(parent, connector.getRepresentedLink());
                newConnector.setVisible(connector.isVisible());
                volatileNode.setVisible(volatileNode.isVisible() || newConnector.isVisible());
            }
            //move to next layer
            layer++;

            
            Set<StableHierarchicalLayoutConnector> toBeRemoved = new HashSet<>();
            
            for (int i = 0; i < connectors.size(); i++) {
                StableHierarchicalLayoutConnector connector = connectors.get(i);
                if (connector.getChild().getLayer() == layer) {
                    toBeRemoved.add(connector);
                    StableHierarchicalLayoutConnector newConnector = volatileNode.addChild(connector.getChild(), connector.getRepresentedLink());
                    newConnector.setVisible(connector.isVisible());
                    volatileNode.setVisible(volatileNode.isVisible() || newConnector.isVisible());
                }
            }
            
            List<StableHierarchicalLayoutConnector> newConnectors = new ArrayList<>(connectors.size()/2);
            for (StableHierarchicalLayoutConnector newConnector : connectors) {
                if(!toBeRemoved.contains(newConnector)) {
                    newConnectors.add(newConnector);
                }
            }
            connectors = newConnectors;
            
            previousLayer = volatileNode;
        }
    }

    /**
     * Transforms list of back edges to links using volatile nodes. All
     * connectors use same volatile node per layer.
     *
     * @param connectors long edges
     * @param layer starting layer
     */
    private void transformBackEdge(Graph graph, List<StableHierarchicalLayoutConnector> connectors, int layer) {
        for (StableHierarchicalLayoutConnector connector : connectors) {
            connector.getParent().getChildren().remove(connector);
            connector.getChild().getParents().remove(connector);
        }

        StableHierarchicalLayoutNode previousLayer = null;
        while (!connectors.isEmpty()) {
            StableHierarchicalLayoutNode volatileNode = new StableHierarchicalLayoutNode(true, layer);
            volatileNode.setVisible(false);
            volatileNode.setDimension(new Dimension(volatileNodeWidth, (layerToGridColumnHeight.get(layer).intValue())));
            graph.addNode(volatileNode);
            graph.layerMapping.get(volatileNode.getLayer()).add(volatileNode);

            for (StableHierarchicalLayoutConnector connector : connectors) {
                StableHierarchicalLayoutNode parent;
                if (layer == connector.getParent().getLayer()) {
                    parent = connector.getParent();
                } else {
                    parent = previousLayer;
                }
                StableHierarchicalLayoutConnector newConnector = volatileNode.addParent(parent, connector.getRepresentedLink());
                newConnector.setVisible(connector.isVisible());
                volatileNode.setVisible(volatileNode.isVisible() || connector.isVisible());
            }
            //move to next layer
            layer--;

            for (int i = 0; i < connectors.size(); i++) {
                StableHierarchicalLayoutConnector connector = connectors.get(i);
                if (connector.getChild().getLayer() == layer + 1) {
                    connectors.remove(i);
                    StableHierarchicalLayoutConnector newConnector = volatileNode.addChild(connector.getChild(), connector.getRepresentedLink());
                    newConnector.setVisible(connector.isVisible());
                    i--;
                }
            }
            previousLayer = volatileNode;
        }
    }

    /**
     * Transforms backedge to link of volatile nodes and connectors.
     *
     * @param connector edge to be transformed
     */
    private void transformBackEdge(Graph graph, StableHierarchicalLayoutConnector connector) {
        StableHierarchicalLayoutNode parent = connector.getParent();
        StableHierarchicalLayoutNode child = connector.getChild();

        parent.getChildren().remove(connector);
        child.getParents().remove(connector);

        StableHierarchicalLayoutNode parentNode = parent;
        StableHierarchicalLayoutNode voidNode;

        for (int i = parent.getLayer(); i >= child.getLayer(); i--) {
            voidNode = new StableHierarchicalLayoutNode(true, i);
            voidNode.setDimension(new Dimension(volatileNodeWidth, (layerToGridColumnHeight.get(i).intValue())));
            StableHierarchicalLayoutConnector newConnector = voidNode.addParent(parentNode, connector.getRepresentedLink());

            newConnector.setVisible(connector.isVisible());
            voidNode.setVisible(connector.isVisible());

            graph.addNode(voidNode);
            graph.layerMapping.get(voidNode.getLayer()).add(voidNode);
            parentNode = voidNode;
        }
        parentNode.addChild(child, connector.getRepresentedLink());

    }

    /**
     * Transforms edge to link of volatile nodes and connectors.
     *
     * @param connector edge to be transformed
     */
    private void transformLongEdge(Graph graph, StableHierarchicalLayoutConnector connector) {
        StableHierarchicalLayoutNode parent = connector.getParent();
        StableHierarchicalLayoutNode child = connector.getChild();

        parent.getChildren().remove(connector);
        child.getParents().remove(connector);

        StableHierarchicalLayoutNode parentNode = parent;
        StableHierarchicalLayoutNode voidNode;

        for (int i = parent.getLayer() + 1; i < child.getLayer(); i++) {
            voidNode = new StableHierarchicalLayoutNode(true, i);
            voidNode.setDimension(new Dimension(volatileNodeWidth, (layerToGridColumnHeight.get(i).intValue())));
            StableHierarchicalLayoutConnector newConnector = voidNode.addParent(parentNode, connector.getRepresentedLink());

            newConnector.setVisible(connector.isVisible());
            voidNode.setVisible(connector.isVisible());

            graph.addNode(voidNode);
            graph.layerMapping.get(voidNode.getLayer()).add(voidNode);
            parentNode = voidNode;
        }
        parentNode.addChild(child, connector.getRepresentedLink());

    }

    /**
     * Places nodes to layers based on their edges to minimize backedges if
     * possible.
     */
    protected void initLayers(Graph graph) {     
        for (StableHierarchicalLayoutNode node : graph.getFirstLayer()) {
            {
                node.setLayer(1); //cluster inputslot
            }
            node.setFixedLayer(true);
            graph.setMinLayer(1);
        }

        Map<StableHierarchicalLayoutNode, Integer> balance = new HashMap<>(graph.nodes.size());
        Queue<StableHierarchicalLayoutConnector> assignQueue = new ArrayDeque<>();
        Stack<StableHierarchicalLayoutNode> reversedAssign = new Stack<>();
        
        for (int i = 0; i < graph.nodes.size(); i++) {
            StableHierarchicalLayoutNode node = graph.nodes.get(i);
            int reversed = 0;
            
            boolean withoutParents = true;
            
            for (StableHierarchicalLayoutConnector parent : node.getParents()) {
                if(parent.getType() == StableHierarchicalLayoutConnector.ConnectorType.reversed)
                {
                    reversed++;
                }
                else
                {
                    withoutParents = false;
                }
            }
            
            balance.put(node, node.getParents().size() - reversed);
            
            if(withoutParents)
            {
                if(!node.isFixedLayer())
                {
                    if(graph.firstLayer.isEmpty())
                    {
                        node.setLayer(1);
                    }
                    else
                    {
                        node.setLayer(2);
                    }
                    reversedAssign.push(node);
                }
                
                for (StableHierarchicalLayoutConnector childConnector : node.getChildren()) {
                    if(childConnector.getType() == StableHierarchicalLayoutConnector.ConnectorType.normal)
                    {
                        assignQueue.add(childConnector);
                    }
                }
            }
        }
        
        while(!assignQueue.isEmpty())
        {
            StableHierarchicalLayoutConnector connector = assignQueue.poll();
            
            StableHierarchicalLayoutNode parent = connector.getParent();
            StableHierarchicalLayoutNode child = connector.getChild();
            
            int suggestedLayer = parent.getLayer() + 1;
            
            if(suggestedLayer > child.getLayer())
            {
                child.setLayer(suggestedLayer);
            }
            int childBalance = balance.get(child) - 1;
            
            if(childBalance == 0)
            {
                reversedAssign.push(child);
                
                for (StableHierarchicalLayoutConnector childsChildConnector : child.getChildren()) {
                    if(childsChildConnector.getType() != StableHierarchicalLayoutConnector.ConnectorType.reversed)
                    {
                        assignQueue.add(childsChildConnector);
                    }
                }
            }
            else
            {
                balance.put(child, childBalance);
            }
        }

        boolean dirtyLastLayer = false;
        int lastLayer = Integer.MIN_VALUE;
        //Last layer has to have only nodes that are supposed to be there (cluster output slot nodes)
        for (StableHierarchicalLayoutNode node : graph.getNodes()) {
            if (node.getLayer() > lastLayer) {
                lastLayer = node.getLayer();
                dirtyLastLayer = false;
            }
            if (node.getLayer() == lastLayer && !graph.getLastLayer().contains(node)) {
                dirtyLastLayer = true;
            }
        }

        if (dirtyLastLayer) {
            lastLayer++;
        }

        for (StableHierarchicalLayoutNode node : graph.getLastLayer()) {
            node.setLayer(lastLayer);
            node.setFixedLayer(true);
        }
        
        //move nodes from lower layers to higher, if possible
        while(!reversedAssign.isEmpty())
        {
            StableHierarchicalLayoutNode node = reversedAssign.pop();
            if(node.isFixedLayer())
            {
                continue;
            }
            
            int possibleToShiftToLayer = Integer.MAX_VALUE;
            
            for (StableHierarchicalLayoutConnector connector : node.getChildren()) {
                if(connector.getType() == StableHierarchicalLayoutConnector.ConnectorType.reversed)
                {
                    continue;
                }
                
                possibleToShiftToLayer = Math.min(possibleToShiftToLayer, connector.getChild().getLayer() - 1);
            }
            
            if(possibleToShiftToLayer > node.getLayer() && possibleToShiftToLayer != Integer.MAX_VALUE)
            {
                node.setLayer(possibleToShiftToLayer);
            }
        }

        // set min and max layer of graph
        updateLayerBounds(graph);
    }

    private void updateLayerBounds(Graph graph) {
        int minLayer = Integer.MAX_VALUE;
        int maxLayer = Integer.MIN_VALUE;
        for (StableHierarchicalLayoutNode node : graph.nodes) {
            if (minLayer > node.getLayer()) {
                minLayer = node.getLayer();
            }
            if (maxLayer < node.getLayer()) {
                maxLayer = node.getLayer();
            }
        }
        graph.minLayer = minLayer;
        graph.maxLayer = maxLayer;
    }

    private int getNodeGroupWidth(List<StableHierarchicalLayoutNode> group) {
        int layerWidth = 0;
        boolean containedVisible = false;
        for (StableHierarchicalLayoutNode node : group) {
            if (node.isVisible()) {
                layerWidth += node.getUsedSlots() + 1;
                containedVisible = true;
            }
        }
        if(containedVisible) {
            layerWidth--;
        }
        return layerWidth;
    }

    /**
     * Places given layer acording to searchType. Parent search should be used
     * when placing layers down from middle layer. Child when up from middle
     * layer.
     */
    private void placeLayer(List<StableHierarchicalLayoutNode> layer, List<StableHierarchicalLayoutNode> previousLayer, SearchType searchType) {
        //list of node groups that want same slot
        List<List<StableHierarchicalLayoutNode>> listOfNodeGroups = new ArrayList<>();

        listOfNodeGroups.add(new ArrayList<StableHierarchicalLayoutNode>());
        //add first node to the first group
        listOfNodeGroups.get(0).add(layer.get(0));
        for (int i = 1; i < layer.size(); i++) {
            StableHierarchicalLayoutNode node = layer.get(i);

            //if this Node wants same position as current group, place it there, create new group otherwise
            int idealSlotPosition = getIdealSlotPosition(node, searchType, true);
            if (getIdealSlotPosition(listOfNodeGroups.get(listOfNodeGroups.size() - 1).get(0), searchType, true) == idealSlotPosition) {
                listOfNodeGroups.get(listOfNodeGroups.size() - 1).add(node);
            } else {
                listOfNodeGroups.add(new ArrayList<StableHierarchicalLayoutNode>());
                listOfNodeGroups.get(listOfNodeGroups.size() - 1).add(node);
            }
        }

        //find start, end and middle of previous layer

        int previousStartingSlot = previousLayer.get(0).getStartingSlot();
        int previousEndingSlot = previousLayer.get(previousLayer.size() - 1).getStartingSlot() + previousLayer.get(previousLayer.size() - 1).getUsedSlots() - 1;
        int previousMiddleSlot = previousStartingSlot + (previousEndingSlot - previousStartingSlot) / 2;

        //Find group, that is as close to the middle as possible, then find its middle and place nodes of group around
        int idealGroupIndex = Integer.MIN_VALUE;
        int distance = Integer.MAX_VALUE;

        for (int i = 0; i < listOfNodeGroups.size(); i++) {
            List<StableHierarchicalLayoutNode> group = listOfNodeGroups.get(i);
            StableHierarchicalLayoutNode groupRepresentant = group.get(0);

            int groupIdealSlot = getIdealSlotPosition(groupRepresentant, searchType, true);
            //Group doesn't have any connection from previous layer
            if (groupIdealSlot == Integer.MIN_VALUE) {
                continue;
            }
            if (Math.abs(previousMiddleSlot - groupIdealSlot) < distance) {
                distance = Math.abs(previousMiddleSlot - groupIdealSlot);
                idealGroupIndex = i;
            } else {
                break;
            }
        }
        // would mean, that no node in previous layer has any connection to next layer, which would mean, that application couldn't even get to instructions in lower layers
        if (idealGroupIndex == Integer.MIN_VALUE) {
            idealGroupIndex = listOfNodeGroups.size()/2;
        }

        //get group of nodes which want slots in the middle
        List<StableHierarchicalLayoutNode> middleNodes = listOfNodeGroups.get(idealGroupIndex);

        // get width of this group
        int middleWidth = getNodeGroupWidth(middleNodes);

        //ideal start for middle group
        int middleIdealPosition = getIdealSlotPosition(middleNodes.get(0), searchType, true);
        final int startOfMiddleSection = middleIdealPosition - middleWidth / 2;

        // placing middle group around ideal slot
        int placingIndex = placeGroup(middleNodes, startOfMiddleSection);

        int leftPlacingEdge = startOfMiddleSection; // everything placed on left of middle has to be < slot
        int rightPlacingEdge = placingIndex + 1; // everything placed on right of middle has to be >= slot

        //going to the right from middle group
        for (int i = idealGroupIndex + 1; i < listOfNodeGroups.size(); i++) {
            List<StableHierarchicalLayoutNode> placedGroup = listOfNodeGroups.get(i);
            StableHierarchicalLayoutNode representant = placedGroup.get(0);
            int representantSlot = getIdealSlotPosition(representant, searchType, true);

            //if group has no connection to previous layer, place it as close to previous as possible
            if (representantSlot == Integer.MIN_VALUE) {
                rightPlacingEdge = placeGroup(placedGroup, rightPlacingEdge);
                rightPlacingEdge++;
            } else {
                int groupWidth = getNodeGroupWidth(placedGroup);
                //get ideal starting slot of placed group and check it against right end of previously placed group (to prevent overlap)
                int groupStartSlot = representantSlot - groupWidth / 2;
                rightPlacingEdge = Math.max(groupStartSlot, rightPlacingEdge);

                //place this group
                rightPlacingEdge = placeGroup(placedGroup, rightPlacingEdge);
                rightPlacingEdge++;
            }
        }

        // go to the left from middle group
        for (int i = idealGroupIndex - 1; i >= 0; i--) {
            List<StableHierarchicalLayoutNode> placedGroup = listOfNodeGroups.get(i);
            StableHierarchicalLayoutNode representant = placedGroup.get(0);
            int representantSlot = getIdealSlotPosition(representant, searchType, true);

            if (representantSlot == Integer.MIN_VALUE) {
                //group doesn't have any connection to previous layer place it as close to the middle group on right that was already placed
                for (int nodeIndex = placedGroup.size() - 1; nodeIndex >= 0; nodeIndex--) {
                    StableHierarchicalLayoutNode placedNode = placedGroup.get(nodeIndex);
                    placedNode.setStartingSlot(leftPlacingEdge - 1 - placedNode.getUsedSlots());
                    if (placedNode.isVisible()) {
                        leftPlacingEdge = leftPlacingEdge - 1 - placedNode.getUsedSlots();
                    }
                }
            } else {
                int groupWidth = getNodeGroupWidth(placedGroup);
                //extra width acts as space
                groupWidth++;

                int groupStartSlot = representantSlot - groupWidth / 2;
                int groupEndSlot = groupStartSlot + groupWidth; // extra space already added
                leftPlacingEdge -= 1;

                leftPlacingEdge = Math.min(groupEndSlot, leftPlacingEdge);

                for (int j = placedGroup.size() - 1; j >= 0; j--) {
                    StableHierarchicalLayoutNode placedNode = placedGroup.get(j);
                    if (placedNode.isVisible()) {
                        placedNode.setStartingSlot(leftPlacingEdge - placedNode.getUsedSlots());
                        leftPlacingEdge = leftPlacingEdge - placedNode.getUsedSlots() - 1;
                    } else {
                        placedNode.setStartingSlot(leftPlacingEdge);
                    }
                }
            }
        }
    }
    
    private int computeNodeUsedSlots(StableHierarchicalLayoutNode node)
    {
        int result = (int) Math.ceil(node.getDimension().width / gridColumnWidth);
        if(result == 0)
        {
            result = 1;
        }
        return result;
    }
    

    /**
     * Assigns coords to nodes.
     *
     * Method first finds widest layer, places it and then goes from this layer
     * up and down. Coords for nodes of each such layer are created by splitting
     * nodes to groups. Each group contains nodes, that want same position when
     * looking at direction of already placed layers (parent/child depending on
     * direction to the widest layer) From these groups the one wanting position
     * as close to the center of previous layer is placed first around position
     * the group wants. Then from this placed group new groups are placed on the
     * left or right, to either their ideal position (if no collision happens),
     * or are shifted to prevent collision.
     *
     * It's also important to maintain atleast 1 slot space between all nodes.
     */
    protected void assignCoords(Graph graph) {
        Map<Integer, ArrayList<StableHierarchicalLayoutNode>> layerMapping = graph.getLayerMapping();

        int widestLayer = 0;
        int widestLayerMinWidth = 0;

        //sets needed slots per node (each node needs atleast 1 slot)
        for (StableHierarchicalLayoutNode node : graph.nodes) {
            node.setUsedSlots(computeNodeUsedSlots(node));
        }

        // go through all layers and find the widest one 
        for (int layer = graph.minLayer; layer <= graph.maxLayer; layer++) {
            int layerWidth = getNodeGroupWidth(layerMapping.get(layer));

            if (layerWidth > widestLayerMinWidth) {
                widestLayerMinWidth = layerWidth;
                widestLayer = layer;
            }
        }

        if (widestLayer != 0) {
            int minLayer = graph.minLayer;
            int maxLayer = graph.maxLayer;

            // place the widest layer
            List<StableHierarchicalLayoutNode> widestLayerNodes = layerMapping.get(widestLayer);
            placeGroup(widestLayerNodes, 0);

            // going up from widest layer
            for (int layerIndex = widestLayer - 1; layerIndex >= minLayer; layerIndex--) {
                List<StableHierarchicalLayoutNode> layer = layerMapping.get(layerIndex);
                placeLayer(layer, layerMapping.get(layerIndex + 1), SearchType.childrenSearch);
            }

            // going down from widest layer
            for (int layerIndex = widestLayer + 1; layerIndex <= maxLayer; layerIndex++) {
                List<StableHierarchicalLayoutNode> layer = layerMapping.get(layerIndex);
                placeLayer(layer, layerMapping.get(layerIndex - 1), SearchType.parentSearch);
            
            }
            
            boolean optimization = optimizeSlots;
            if(graph.realEdgeCount >= 300)
            {
                optimization = false;
            }
            
            // optimizing slot positions by shifting whole groups while maintaining relative positions of groups
            if (optimization) {
                for (int i = 0; i < 3; i++) {
                    for (int layer = graph.minLayer; layer <= graph.maxLayer; layer++) {
                        //create groups
                        List<List<StableHierarchicalLayoutNode>> nodeGroups = new ArrayList<>();
                        Collections.sort(graph.layerMapping.get(layer), new Comparator<StableHierarchicalLayoutNode>() {
                            @Override
                            public int compare(StableHierarchicalLayoutNode t, StableHierarchicalLayoutNode t1) {
                                double pos1 = t.getStartingSlot();
                            double pos2 = t1.getStartingSlot();
                                return pos1 < pos2 ? -1 : pos1 == pos2 ? 0 : 1;
                            }
                        });
                        for (StableHierarchicalLayoutNode node : graph.layerMapping.get(layer)) {
                            if (nodeGroups.isEmpty()) {
                                nodeGroups.add(new ArrayList<StableHierarchicalLayoutNode>());
                                nodeGroups.get(0).add(node);
                                continue;
                            }

                            List<StableHierarchicalLayoutNode> groupList = nodeGroups.get(nodeGroups.size() - 1);
                            StableHierarchicalLayoutNode groupRepresentant = groupList.get(0);
                            if (getIdealSlotPosition(node, SearchType.bothSearch, false) == getIdealSlotPosition(groupRepresentant, SearchType.bothSearch, false)) {
                                groupList.add(node);
                            } else {
                                nodeGroups.add(new ArrayList<StableHierarchicalLayoutNode>());
                                nodeGroups.get(nodeGroups.size() - 1).add(node);
                            }
                        }

                        // For each group check if it can get to its ideal position

                        List<List<StableHierarchicalLayoutNode>> iteratedGroups = nodeGroups;

                        for (int j = 0; j < iteratedGroups.size(); j++) {
                            List<StableHierarchicalLayoutNode> group = iteratedGroups.get(j);

                            StableHierarchicalLayoutNode firstNodeOfGroup = group.get(0);
                            int startingSlot = firstNodeOfGroup.getStartingSlot();
                            StableHierarchicalLayoutNode lastNodeOfGroup = group.get(group.size() - 1);
                            int endingSlot = lastNodeOfGroup.getStartingSlot();
                            if (lastNodeOfGroup.isVisible()) {
                                endingSlot += lastNodeOfGroup.getUsedSlots() - 1;
                            }
                            int middleSlot = startingSlot + (endingSlot - startingSlot) / 2;
                            //initial slots already exist, so searching by both parent and children can be used
                            int idealSlot = getIdealSlotPosition(group.get(0), SearchType.bothSearch, false);
                        
                            //treshold to prevent staircase effect
                            if (Math.abs(middleSlot - idealSlot) <= 5) {
                                //nothing
                            } else if (idealSlot < middleSlot) {
                                // if wants to the left and is mostleft group, then it can be moved without problem
                                int idealStartingSlot = idealSlot - (middleSlot - startingSlot);
                                if (j == 0) {
                                    placeGroup(group, idealStartingSlot);
                                } else //if wants to left, but there is other group, then move it to the ideal location or as much to the left as left group allows
                                {
                                    List<StableHierarchicalLayoutNode> groupOnTheLeft = iteratedGroups.get(j - 1);
                                    StableHierarchicalLayoutNode lastOfLeftGroup = groupOnTheLeft.get(groupOnTheLeft.size() - 1);
                                    int firstEmptySlotAfterLeftGroup = lastOfLeftGroup.getStartingSlot(); //if not visible, there is space already from placing node before the last one
                                    if (lastOfLeftGroup.isVisible()) {
                                        firstEmptySlotAfterLeftGroup += lastOfLeftGroup.getUsedSlots();
                                    }
                                    else
                                    {
                                        firstEmptySlotAfterLeftGroup--;
                                    }
                                
                                    if (firstEmptySlotAfterLeftGroup < idealStartingSlot) {
                                        placeGroup(group, idealStartingSlot);
                                    } else {
                                        idealStartingSlot = firstEmptySlotAfterLeftGroup + 1;
                                        if(idealStartingSlot < startingSlot)
                                        {
                                            placeGroup(group, idealStartingSlot);
                                        }
                                    }
                                }
                            } else {
                                // if wants to the right
                                int idealStartingSlot = idealSlot - (middleSlot - startingSlot);

                                //if is last group then can be placed without problem
                                if (j == iteratedGroups.size() - 1) {
                                    placeGroup(group, idealStartingSlot);
                                } else //if there is some other group on the right, move this group to the ideal slot or as much to the right as next group allows
                                {
                                    List<StableHierarchicalLayoutNode> groupOnTheRight = iteratedGroups.get(j + 1);
                                    StableHierarchicalLayoutNode firstOfRightGroup = groupOnTheRight.get(0);
                                    int lastEmptySlotBeforeRightGroup = firstOfRightGroup.getStartingSlot();
                                    if(firstOfRightGroup.isVisible())
                                    {
                                        lastEmptySlotBeforeRightGroup = firstOfRightGroup.getStartingSlot() - 1;
                                    }
                                    else
                                    {
                                        boolean breakSearch = false;
                                        for (int k = j + 1; k < iteratedGroups.size(); k++) {
                                            List<StableHierarchicalLayoutNode> checkedGroup = iteratedGroups.get(k);
                                            for (int l = 0; l < checkedGroup.size(); l++) {
                                                StableHierarchicalLayoutNode checkedNode = checkedGroup.get(l);
                                                assert checkedNode.getStartingSlot() >= lastEmptySlotBeforeRightGroup;
                                                if(checkedNode.getStartingSlot() != lastEmptySlotBeforeRightGroup)
                                                {
                                                    //slot was used only by invisible nodes
                                                    breakSearch = true;
                                                    break;
                                                }
                                                else if(checkedNode.isVisible())
                                                {
                                                    //slot is used by visible node, last unused 1 back
                                                    lastEmptySlotBeforeRightGroup--;
                                                    breakSearch = true;
                                                    break;
                                                }
                                            }
                                            if(breakSearch)
                                            {
                                                break;
                                            }
                                        }
                                    }
                                    int idealEnding = (endingSlot - startingSlot) + idealStartingSlot;
                                
                                    if (idealEnding + 1 <= lastEmptySlotBeforeRightGroup) {
                                        placeGroup(group, idealStartingSlot);
                                    } else {
                                        if(!group.get(group.size()-1).isVisible())
                                        {
                                            idealStartingSlot = lastEmptySlotBeforeRightGroup - (endingSlot - startingSlot);
                                        }
                                        else
                                        {
                                            idealStartingSlot = lastEmptySlotBeforeRightGroup - 1 - (endingSlot - startingSlot);
                                        }
                                        // move only if is really moving to the right
                                        if(idealStartingSlot > startingSlot)
                                        {
                                            placeGroup(group, idealStartingSlot);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        //returning nodes without edges back to structure
        if(!nodesWithoutEdges.isEmpty())
        {
            int workingLayer = nodesWithoutEdges.get(0).getLayer();
        
            List<StableHierarchicalLayoutNode> layerForNodesWithoutEdges = graph.layerMapping.get(workingLayer);
            int lastSlot;
            if(layerForNodesWithoutEdges.isEmpty())
            {
                lastSlot = 0;
            }
            else
            {
                StableHierarchicalLayoutNode lastNode = layerForNodesWithoutEdges.get(layerForNodesWithoutEdges.size()-1);
                if(lastNode.isVisible())
                {
                    lastSlot = lastNode.getStartingSlot() + lastNode.getUsedSlots();
                }
                else
                {
                    lastSlot = lastNode.getStartingSlot();
                }
            }
        
            for (StableHierarchicalLayoutNode node : nodesWithoutEdges)
            {
                int usedSlots = computeNodeUsedSlots(node);
                node.setUsedSlots(usedSlots);
                node.setStartingSlot(lastSlot);
                lastSlot += usedSlots;
            
                layerForNodesWithoutEdges.add(node);
                graph.nodes.add(node);
            }
        }
        
        // assign X coords to all nodes
        // Y coords are only temporary as they don't consider place needed for edges
        int yPos = 0;
        for (int i = graph.minLayer; i <= graph.maxLayer; i++) {
            List<StableHierarchicalLayoutNode> layer = layerMapping.get(i);

            boolean invisibleLayer = true;

            for (StableHierarchicalLayoutNode node : layer) {
                if (node.isVisible()) {
                    invisibleLayer = false;
                    break;
                }
            }
            //if layer is not visible, no need to assign coords
            if (!invisibleLayer) {
                for (StableHierarchicalLayoutNode node : layer) {
                    if (!node.isVisible()) {
                        continue;
                    }
                    int xOffset = (int) ((node.getUsedSlots() * gridColumnWidth) - node.getDimension().width) / 2;
                    node.setX(node.getStartingSlot() * (int) gridColumnWidth + xOffset);
                    int currentLayerHeight = layerToGridColumnHeight.get(i).intValue();
                    int yOffset = (currentLayerHeight - node.getDimension().height) / 2;
                    node.setY(yPos + yOffset);
                }

                yPos += layerToGridColumnHeight.get(i);
            }
        }

    }

    /**
     * Places one group from starting slot with standard 1 slot gap.
     * @return Last used slot or startingSlot-1 if no node was placed.
     */
    private int placeGroup(List<StableHierarchicalLayoutNode> group, int startingSlot) {
        int currentSlot = startingSlot;
        for (StableHierarchicalLayoutNode node : group) {
            node.setStartingSlot(currentSlot);
            if (node.isVisible()) {
                currentSlot += node.getUsedSlots() + 1;
            }
        }
        currentSlot--;
        return currentSlot;
    }

    /**
     * Computes ideal slot for node, while considering nodes specified by
     * searchType with possibility to ignore backEdges
     *
     * @return Ideal slot position
     */
    private int getIdealSlotPosition(StableHierarchicalLayoutNode node, SearchType searchType, boolean ignoreBackEdges) {
        int idealSlot = 0;
        int nodeCount = 0;
        
        if (searchType == SearchType.childrenSearch || searchType == SearchType.bothSearch) {
            for (StableHierarchicalLayoutConnector connector : node.getChildren()) {
                StableHierarchicalLayoutNode childNode = connector.getChild();

                if (ignoreBackEdges && childNode.getLayer() <= node.getLayer()) {
                    continue;
                }
                idealSlot += childNode.centralSlotPosition();
                nodeCount++;
            }
            
            for (StableHierarchicalLayoutConnector connector : node.getParents()) {
                StableHierarchicalLayoutNode parentNode = connector.getParent();
                
                if(parentNode.getLayer() >= node.getLayer())
                {
                    idealSlot += parentNode.centralSlotPosition();
                    nodeCount++;
                }
            }
        }
        if (searchType == SearchType.parentSearch || searchType == SearchType.bothSearch) {
            for (StableHierarchicalLayoutConnector connector : node.getParents()) {
                StableHierarchicalLayoutNode parentNode = connector.getParent();

                if (ignoreBackEdges && parentNode.getLayer() >= node.getLayer()) {
                    continue;
                }
                idealSlot += parentNode.centralSlotPosition();
                nodeCount++;
            }
            
            for (StableHierarchicalLayoutConnector connector : node.getChildren()) {
                StableHierarchicalLayoutNode childNode = connector.getChild();

                if (childNode.getLayer() <= node.getLayer()) {
                    idealSlot += childNode.centralSlotPosition();
                    nodeCount++;
                }
            }
        }
        // unknown ideal slot
        if (nodeCount == 0) {
            return Integer.MIN_VALUE;
        }
        return idealSlot / nodeCount;
    }

    /**
     * Swaps positions of neighbouring nodes in each layer of graph, if nodes
     * would get better positions considering both parents and children.
     */
    private void positionSwapping(Graph graph) {
        Map<Integer, ArrayList<StableHierarchicalLayoutNode>> layerMapping = graph.layerMapping;
        for (int layer = graph.minLayer; layer <= graph.maxLayer; layer++) {
            List<StableHierarchicalLayoutNode> layerNodes = layerMapping.get(layer);
            for (int i = 0; i < layerNodes.size() - 1; i++) {
                StableHierarchicalLayoutNode node = layerNodes.get(i);
                StableHierarchicalLayoutNode next = layerNodes.get(i + 1);
                if (computePositionBothWay(next) < computePositionBothWay(node)) {
                    next.setPosition(node.getPosition());
                    node.setPosition(next.getPosition());
                    layerNodes.set(i, next);
                    layerNodes.set(i + 1, node);
                    i++;
                }
            }
        }
    }
    
    private void removeNodesWithoutEdges(Graph graph)
    {
        for (StableHierarchicalLayoutNode node : graph.nodes)
        {
            if(node.getChildren().isEmpty() && node.getParents().isEmpty())
            {
                nodesWithoutEdges.add(node);
                graph.layerMapping.get(node.getLayer()).remove(node);
            }
        }
        graph.nodes.removeAll(nodesWithoutEdges);
    }

    public void setCombineSameInputs(InputCombination inputsCombinationRule) {
        this.inputsCombinationRule = inputsCombinationRule;
    }

    public void setCombineSameOutputs(OutputCombination outputsCombinationRule) {
        this.outputsCombinationRule = outputsCombinationRule;
    }

    public enum InputCombination {

        PORT_BASED, NODE_BASED, NONE;
    }

    public enum OutputCombination {

        NONE, NODE_BASED, PORT_BASED;
    }

    /**
     * Sets maximum length of edge which will be still displayed as whole. 
     * @param longEdgeMaxLayers 0 means that no lines will be drawn. Negative value means that all edges will be drawn.
     */
    public void setLongEdgeMaxLayers(int longEdgeMaxLayers) {
        if (longEdgeMaxLayers < 0) {
            this.longEdgeMaxLayers = -1;
        } else {
            this.longEdgeMaxLayers = longEdgeMaxLayers;
        }
    }

    public boolean isDynamicLayerHeight() {
        return dynamicLayerHeight;
    }

    public void setIgnoreTooLongEdges(boolean ignoreTooLongEdges) {
        this.ignoreTooLongEdges = ignoreTooLongEdges;
    }
}
