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
package org.graalvm.visualizer.data;

import java.util.*;

public class InputGraph extends Properties.Entity implements FolderElement {

    private Folder parent;
    private Group parentGroup;
    private Set<Integer> nodeIds;

    private final GraphData data = new GraphData();

    /**
     * The graph's own data. Data is separated from the InputGraph itself so they can be eventually
     * GCed independently.
     */
    public static class GraphData {
        private final Map<Integer, InputNode> nodes = new LinkedHashMap<>();
        private final List<InputEdge> edges = new ArrayList<>();
        private final Map<String, InputBlock> blocks = new LinkedHashMap<>();;
        private final List<InputBlockEdge> blockEdges = new ArrayList<>();
        private final Map<Integer, InputBlock> nodeToBlock = new LinkedHashMap<>();

        public final Collection<InputNode> getNodes() {
            return nodes.values();
        }

        public List<InputEdge> getEdges() {
            return edges;
        }

        public Map<String, InputBlock> getBlocks() {
            return blocks;
        }

        public List<InputBlockEdge> getBlockEdges() {
            return blockEdges;
        }

        public Map<Integer, InputBlock> getNodeToBlock() {
            return nodeToBlock;
        }

        public Map<Integer, InputNode> getNodeMap() {
            return nodes;
        }
    }

    public InputGraph(String name) {
        setName(name);
    }

    protected GraphData data() {
        return data;
    }

    @Override
    public void setParent(Folder parent) {
        this.parent = parent;
        if (parent instanceof Group) {
            assert this.parentGroup == null;
            this.parentGroup = (Group) parent;
        }
    }

    public InputBlockEdge addBlockEdge(InputBlock left, InputBlock right) {
        InputBlockEdge edge = new InputBlockEdge(left, right);
        data().blockEdges.add(edge);
        left.addSuccessor(right);
        return edge;
    }

    public Set<Integer> getNodeIds() {
        if (nodeIds != null) {
            return nodeIds;
        }
        return nodeIds = Collections.unmodifiableSet(new HashSet<>(data().nodes.keySet()));
    }

    public List<InputNode> findRootNodes() {
        List<InputNode> result = new ArrayList<>();
        Set<Integer> nonRoot = new HashSet<>();
        GraphData d = data();
        for (InputEdge curEdges : d.edges) {
            nonRoot.add(curEdges.getTo());
        }

        for (InputNode node : d.getNodes()) {
            if (!nonRoot.contains(node.getId())) {
                result.add(node);
            }
        }

        return result;
    }

    public Map<InputNode, List<InputEdge>> findAllOutgoingEdges() {
        Map<InputNode, List<InputEdge>> result = new HashMap<>(getNodes().size());
        for (InputNode n : this.getNodes()) {
            result.put(n, new ArrayList<InputEdge>());
        }
        GraphData d = data();
        for (InputEdge e : d.edges) {
            int from = e.getFrom();
            InputNode fromNode = this.getNode(from);
            List<InputEdge> fromList = result.get(fromNode);
            assert fromList != null;
            fromList.add(e);
        }

        for (InputNode n : d.getNodes()) {
            List<InputEdge> list = result.get(n);
            Collections.sort(list, InputEdge.OUTGOING_COMPARATOR);
        }

        return result;
    }

    public Map<InputNode, List<InputEdge>> findAllIngoingEdges() {
        Map<InputNode, List<InputEdge>> result = new HashMap<>(getNodes().size());
        GraphData d = data();
        for (InputNode n : d.getNodes()) {
            result.put(n, new ArrayList<InputEdge>());
        }

        for (InputEdge e : d.edges) {
            int to = e.getTo();
            InputNode toNode = this.getNode(to);
            List<InputEdge> toList = result.get(toNode);
            assert toList != null;
            toList.add(e);
        }

        for (InputNode n : d.getNodes()) {
            List<InputEdge> list = result.get(n);
            Collections.sort(list, InputEdge.INGOING_COMPARATOR);
        }

        return result;
    }

    public List<InputEdge> findOutgoingEdges(InputNode n) {
        List<InputEdge> result = new ArrayList<>();

        for (InputEdge e : data().edges) {
            if (e.getFrom() == n.getId()) {
                result.add(e);
            }
        }

        Collections.sort(result, InputEdge.OUTGOING_COMPARATOR);

        return result;
    }

    public void clearBlocks() {
        data().blocks.clear();
        data().nodeToBlock.clear();
    }

    public void setEdge(int fromIndex, int toIndex, int from, int to) {
        assert fromIndex == ((char) fromIndex) : "Downcast must be safe";
        assert toIndex == ((char) toIndex) : "Downcast must be safe";

        InputEdge edge = new InputEdge((char) fromIndex, (char) toIndex, from, to);
        if (!this.getEdges().contains(edge)) {
            this.addEdge(edge);
        }
    }

    public void ensureNodesInBlocks() {
        InputBlock noBlock = null;
        GraphData d = data();
        for (InputNode n : d.getNodes()) {
            assert d.nodes.get(n.getId()) == n;
            if (!d.nodeToBlock.containsKey(n.getId())) {
                if (noBlock == null) {
                    noBlock = this.addBlock("(no block)");
                }
                noBlock.addNode(n.getId());
            }
            assert this.getBlock(n) != null;
        }
    }

    public void setBlock(InputNode node, InputBlock block) {
        data().nodeToBlock.put(node.getId(), block);
    }

    public InputBlock getBlock(int nodeId) {
        return data().nodeToBlock.get(nodeId);
    }

    public InputBlock getBlock(InputNode node) {
        assert data().nodes.containsKey(node.getId());
        assert data().nodes.get(node.getId()).equals(node);
        return getBlock(node.getId());
    }

    public InputGraph getNext() {
        return parentGroup.getNext(this);
    }

    public InputGraph getPrev() {
        return parentGroup.getPrev(this);
    }

    /**
     * Determines whether a node changed compared to state in its predecessor graph. If the node is
     * newly introduced, or discarded in this graph, it is considered changed. Otherwise node is
     * changed iff its properties are not equal.
     * <p/>
     * Note: if the graph is a duplicate, then none of its nodes can be changed.
     * 
     * @param nodeId node to check
     * @return true, if the node in this graph has changed
     */
    public boolean isNodeChanged(int nodeId) {
        InputGraph prev = getPrev();
        if (prev == null || !containsNode(nodeId)) {
            return true;
        }
        if (!prev.containsNode(nodeId)) {
            return true;
        }
        if (isDuplicate()) {
            return false;
        }
        InputNode our = getNode(nodeId);
        InputNode their = prev.getNode(nodeId);
        return our.getProperties().equals(their.getProperties());
    }

    private void setName(String name) {
        this.getProperties().setProperty("name", name);
    }

    @Override
    public String getName() {
        return getProperties().get("name");
    }

    public int getNodeCount() {
        return data().nodes.size();
    }

    public int getEdgeCount() {
        return data().edges.size();
    }

    public Collection<InputNode> getNodes() {
        return Collections.unmodifiableCollection(data().nodes.values());
    }

    public Set<Integer> getNodesAsSet() {
        return Collections.unmodifiableSet(data().nodes.keySet());
    }

    public Collection<InputBlock> getBlocks() {
        return Collections.unmodifiableCollection(data().blocks.values());
    }

    public void addNode(InputNode node) {
        data().nodes.put(node.getId(), node);
        nodeIds = null;
    }

    public InputNode getNode(int id) {
        return data().nodes.get(id);
    }

    public InputNode removeNode(int id) {
        InputNode n = data().nodes.remove(id);
        if (n != null) {
            nodeIds = null;
        }
        return n;
    }

    public Collection<InputEdge> getEdges() {
        return Collections.unmodifiableList(data().edges);
    }

    public void removeEdge(InputEdge c) {
        boolean removed = data().edges.remove(c);
        assert removed;
    }

    public void addEdge(InputEdge c) {
        data().edges.add(c);
    }

    public Group getGroup() {
        return parentGroup;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Graph ").append(getName()).append(" ").append(getProperties().toString()).append("\n");
        for (InputNode n : data().nodes.values()) {
            sb.append(n.toString());
            sb.append("\n");
        }

        for (InputEdge c : data().edges) {
            sb.append(c.toString());
            sb.append("\n");
        }

        for (InputBlock b : getBlocks()) {
            sb.append(b.toString());
            sb.append("\n");
        }

        return sb.toString();
    }

    public InputBlock addBlock(String name) {
        final InputBlock b = new InputBlock(this, name);
        data().blocks.put(b.getName(), b);
        return b;
    }

    public InputBlock getBlock(String s) {
        return data().blocks.get(s);
    }

    public Collection<InputBlockEdge> getBlockEdges() {
        return Collections.unmodifiableList(data().blockEdges);
    }

    @Override
    public Folder getParent() {
        return parent;
    }

    protected void complete() {
    }

    public boolean containsNode(int id) {
        return getNode(id) != null;
    }

    public boolean isDuplicate() {
        return getProperties().get("_duplicate") != null; // NOI18N
    }
}
