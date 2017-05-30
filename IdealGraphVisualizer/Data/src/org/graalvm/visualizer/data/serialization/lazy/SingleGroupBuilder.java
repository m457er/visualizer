/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

package org.graalvm.visualizer.data.serialization.lazy;

import org.graalvm.visualizer.data.Folder;
import org.graalvm.visualizer.data.FolderElement;
import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.Group.Feedback;
import org.graalvm.visualizer.data.InputBlock;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.InputNode;
import org.graalvm.visualizer.data.Properties;
import org.graalvm.visualizer.data.serialization.BinarySource;
import org.graalvm.visualizer.data.serialization.Builder;
import org.graalvm.visualizer.data.serialization.ConstantPool;
import org.graalvm.visualizer.data.serialization.ModelBuilder;
import org.graalvm.visualizer.data.serialization.SkipRootException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builder to build a single Group. The Builder creates offspring Builders to load a lazy graph
 * (will ignore contents) or load full information. The delegating wrapper is responsible for
 * restoring appropriate delegate when the nested block (Group, Graph) finishes. Also maintains
 * context stack.
 */
public class SingleGroupBuilder extends DelegatingBuilder {
    private static final Logger LOG = Logger.getLogger(SingleGroupBuilder.class.getName());

    private static final int LARGE_GRAPH_THRESHOLD = StreamEntry.LARGE_ENTRY_THRESHOLD;

    /**
     * Result items to be reported
     */
    private final List<FolderElement> items = new ArrayList<>();

    /**
     * Group to complete
     */
    private final Group toComplete;

    /**
     * The current constant pool
     */
    private ConstantPool pool;
    private final Env env;
    private final GraphDocument rootDocument;
    private final StreamIndex streamIndex;
    private final BinarySource dataSource;
    private final long startOffset;
    private final boolean firstExpand;
    private final Root rootBuilder;
    private final StreamEntry rootEntry;
    private final Feedback feedback;

    private GraphMetadata gInfo;

    private long rootStartOffset;

    // diagnostics only
    private int graphIndex = -1;

    /**
     * Graph nesting level. Group children have level 1. Graphs with level > 1 are nested in outer
     * graph' node properties.
     */
    private int graphLevel;

    /**
     * Nesting group level. The expanding group has level 1. Graphs at level 1 can be lazy-loaded or
     * fully read.
     */
    private int groupLevel;

    /**
     * True, if the group's immediate current child is a lazy graph.
     */
    private boolean lazyGraph;

    /**
     * Current graph builder for the opened direct child.
     */
    private ChildGraphBuilder rootGraphBuilder;

    /**
     * Properties of individual InputNodes in the current compilation. Used to detect property
     * changes between graphs/phases
     */
    private Map<Integer, Properties> nodeProperties = new HashMap<>();

    /**
     * Saved context
     */
    private Deque<NestedData> levels = new LinkedList<>();

    /**
     * If true, counts nodes and edges in graph. Valid only for graphLevel == 1
     */
    private boolean collectCounts;
    /**
     * If true, counts nodes and edges in graph. Valid only for graphLevel == 1
     */
    private boolean collectChanges;

    private StreamEntry entry;

    /**
     * Helper class, saves one level info on creation + clears out the data. Restores data in its
     * restore. Used to save context for group/graph.
     */
    class NestedData {
        Map<Integer, Properties> props;
        GraphMetadata meta;
        StreamEntry e;
        boolean changes;
        boolean counts;
        int gIndex;

        public NestedData() {
            this.props = nodeProperties;
            this.meta = gInfo;
            this.e = entry;
            this.changes = collectChanges;
            this.counts = collectCounts;
            this.gIndex = graphIndex;

            if (graphLevel > 1) {
                nodeProperties = new HashMap<>();
            }
            gInfo = null;
            entry = null;
        }

        void restore() {
            nodeProperties = props;
            gInfo = meta;
            entry = e;
            collectChanges = changes;
            collectCounts = counts;
            graphIndex = gIndex;
        }
    }

    public SingleGroupBuilder(Group toComplete,
                    Env env,
                    BinarySource dataSource,
                    StreamIndex streamIndex,
                    StreamEntry entry,
                    Feedback feedback,
                    boolean firstExpand) {
        this.env = env;
        this.toComplete = toComplete;
        this.pool = entry.getInitialPool().clone();
        this.rootDocument = new GraphDocument();
        this.streamIndex = streamIndex;
        this.dataSource = dataSource;
        this.startOffset = entry.getStart();
        this.firstExpand = firstExpand;
        this.feedback = feedback;
        this.rootEntry = entry;

        this.entry = rootEntry;
        rootBuilder = new Root();
        delegateTo(rootBuilder);
    }

    public List<? extends FolderElement> getItems() {
        return items;
    }

    @Override
    public void endGroup() {
        levels.pop().restore();
        groupLevel--;
        super.endGroup();
    }

    @Override
    public Group startGroup() {
        levels.push(new NestedData());
        lazyGraph = false;
        groupLevel++;
        return super.startGroup();
    }

    boolean isRootChildGraph() {
        return groupLevel == 1 && graphLevel == 1;
    }

    @Override
    public InputGraph startGraph(String title) {
        graphIndex++;
        levels.push(new NestedData());
        graphLevel++;
        return super.startGraph(title);
    }

    private void registerNodeProperties(int nodeId, Properties props) {
        Properties oldProps = nodeProperties.get(nodeId);
        if (oldProps != null && !oldProps.equals(props)) {
            gInfo.nodeChanged(nodeId);
        }
        nodeProperties.put(nodeId, props);
    }

    @Override
    public InputGraph endGraph() {
        levels.pop().restore();
        graphLevel--;

        switch (graphLevel) {
            case 0:
                LOG.log(Level.FINE, "Switch to root builder");
                delegateTo(rootBuilder);
                break;
            case 1:
                // time to switch from a subgraph to the group's child builder
                LOG.log(Level.FINE, "Switch to lazy builder");
                delegateTo(rootGraphBuilder);
                rootGraphBuilder = null;
        }
        return super.endGraph();
    }

    @Override
    public void startRoot() {
        rootStartOffset = dataSource.getMark();
        super.startRoot();
    }

    long absPosition(long p) {
        return startOffset + p;
    }

    long relPosition(long p) {
        return p - startOffset;
    }

    /**
     * Root builder, builds full model. Identifies large graphs and loads them as lazy. PENDING -
     * maybe identify also large groups and load them as lazy.
     */
    class Root extends ModelBuilder {
        private boolean newEntry;

        public Root() {
            super(rootDocument, env.getModelExecutor(), null, new ParseMonitorBridge(entry, feedback, dataSource));
        }

        @Override
        protected Group createGroup(Folder parent) {
            if (parent == rootDocument) {
                // do not create a Group for the completed instance, use the actual object
                return toComplete;
            } else {
                return super.createGroup(parent);
            }
        }

        @Override
        protected void registerToParent(Folder parent, FolderElement item) {
            if (item == toComplete) {
                return;
            }
            if (parent == toComplete) {
                // do not put items into the completed group, fill them all at once at the end
                items.add(item);
            } else {
                // ignore threading, the item should have no listeners yet.
                parent.addElement(item);
            }
        }

        @Override
        protected InputGraph createGraph(String title, String name, Properties.Entity parent) {
            if (rootEntry.size() < LARGE_GRAPH_THRESHOLD && (entry == null || entry.size() < LARGE_GRAPH_THRESHOLD)) {
                // let Graph to keep entire group data in memory
                return new LazyGroup.LoadedGraph(title, graphLevel == 1 ? gInfo : null);
            }
            GraphCompleter completer = new GraphCompleter(env, entry);
            LazyGraph g = new LazyGraph(title, gInfo, completer);
            completer.attachTo(g, title);
            LOG.log(Level.FINE, "Started lazy graph {0}, positions {1}-{2}",
                            new Object[]{g.getName(), entry.getStart(), entry.getEnd()});
            // switch to lazygraph builder
            ChildGraphBuilder gb = new ChildGraphBuilder(g);
            rootGraphBuilder = gb;
            delegateTo(gb);
            return g;
        }

        @Override
        public void startGraphContents(InputGraph g) {
            if (graphLevel == 1 && !collectChanges && !(g instanceof LazyGroup.LoadedGraph)) {
                // the graph was already seen, we can safely skip it if it is lazy-loaded, metadata was already collected
                replacePool(entry.getSkipPool().clone());
                throw new SkipRootException(relPosition(entry.getStart()), relPosition(entry.getEnd()));
            }
            super.startGraphContents(g);
        }

        public InputGraph startGraph(String title) {
            entry = streamIndex.get(absPosition(rootStartOffset));
            if (entry != null) {
                if (entry.size() > 1024 * 1024) {
                    reportState(title);
                }
                gInfo = entry.getGraphMeta();
                collectCounts = false;
                collectChanges = firstExpand;
                newEntry = false;
            } else {
                gInfo = new GraphMetadata();
                if (rootEntry.size() >= LARGE_GRAPH_THRESHOLD) {
                    entry = new StreamEntry(dataSource.getMajorVersion(), dataSource.getMinorVersion(), startOffset, getConstantPool());
                    newEntry = true;
                    entry.setMetadata(gInfo);
                }
                // will not be lazy expanded, no constant pool info
                collectCounts = true;
                collectChanges = true;
            }
            return super.startGraph(title);
        }

        @Override
        public InputGraph endGraph() {
            InputGraph g = graph();
            if (g instanceof LazyGraph) {
                if (newEntry) {
                    entry.end(dataSource.getMark(), pool);
                    replacePool(pool = ((StreamPool) pool).forkIfNeeded());
                    streamIndex.addEntry(entry);
                }
                // avoid call to getNodes() in super
                popContext();
                registerToParent(folder(), g);
            } else {
                g = super.endGraph();
            }
            if (rootGraphBuilder != null) {
                g = rootGraphBuilder.g;
                LOG.log(Level.FINE, "Finished lazy graph {0}", g.getName());
                reportState(((Group) folder()).getName());
            }
            rootGraphBuilder = null;
            return g;
        }

        @Override
        protected void replacePool(ConstantPool newPool) {
            pool = newPool;
            super.replacePool(newPool);
        }

        @Override
        public ConstantPool getConstantPool() {
            return pool;
        }

        @Override
        public void markGraphDuplicate() {
            gInfo.markDuplicate();
        }

        @Override
        public void makeBlockEdges() {
            super.makeBlockEdges();
            if (collectChanges) {
                // hook to register graph node's properties, after they were
                // updated with node-to-block properties
                InputGraph g = graph();
                assert !(g instanceof Group.LazyContent);
                for (InputNode n : g.getNodes()) {
                    int nodeId = n.getId();
                    gInfo.addNode(nodeId);
                    registerNodeProperties(nodeId, n.getProperties());
                }
            }
        }
    }

    class ChildGraphBuilder extends ModelBuilder {
        private InputGraph nested;
        private final LazyGraph g;
        private Map<Integer, Properties> stageProperties = new HashMap<>();
        private String blockName;

        public ChildGraphBuilder(LazyGraph g) {
            super(rootDocument, env.getModelExecutor(), null, null);
            this.g = g;

            pushGroup(toComplete);
            pushGraph(g);
        }

        @Override
        public Properties getNodeProperties(int nodeId) {
            return stageProperties.get(nodeId);
        }

        @Override
        protected void registerToParent(InputGraph g, InputNode n) {
            stageProperties.put(n.getId(), n.getProperties());
        }

        @Override
        public void addNodeToBlock(int nodeId) {
            updateNodeBlock(nodeId, blockName);
        }

        @Override
        public void addBlockEdge(int from, int to) {
        }

        @Override
        public void startRoot() {
            throw new IllegalStateException();
        }

        @Override
        protected void replacePool(ConstantPool newPool) {
            pool = newPool;
            super.replacePool(newPool);
        }

        @Override
        public ConstantPool getConstantPool() {
            return pool;
        }

        @Override
        public Group startGroup() {
            throw new IllegalStateException("Unexpected group inside graph");
        }

        // ignored data

        @Override
        public void makeBlockEdges() {
            if (collectChanges) {
                // update after node-to-block assignment
                for (Map.Entry<Integer, Properties> en : stageProperties.entrySet()) {
                    int nodeId = en.getKey();
                    Properties props = en.getValue();
                    registerNodeProperties(nodeId, props);
                }
            }
        }

        @Override
        public void endBlock(int id) {
        }

        @Override
        public InputBlock startBlock(String name) {
            blockName = name;
            return null;
        }

        @Override
        public void makeGraphEdges() {
        }

        public void startNode(int nodeId, boolean hasPredecessors) {
            if (collectCounts) {
                gInfo.addNode(nodeId);
            }
            super.startNode(nodeId, hasPredecessors);
        }

        @Override
        public void markGraphDuplicate() {
            if (collectCounts) {
                gInfo.markDuplicate();
            }
        }

        @Override
        public InputGraph endGraph() {
            assert nested != null;
            InputGraph n = nested;
            nested = null;
            LOG.log(Level.FINE, "Resuming operation after property {0}", getNestedProperty());
            return nested;
        }

        @Override
        public InputGraph startGraph(String title) {
            assert getNestedProperty() != null;
            LOG.log(Level.FINE, "Ignoring nested graph in property {0}", getNestedProperty());
            nested = super.startGraph(title);
            Builder sub = new Ignore();
            delegateTo(sub);
            return nested;
        }
    }

    /**
     * Builder which throws everything away
     */
    final class Ignore implements Builder {
        @Override
        public void addBlockEdge(int from, int to) {
        }

        @Override
        public void addNodeToBlock(int nodeId) {
        }

        @Override
        public void end() {
        }

        @Override
        public void endBlock(int id) {
        }

        @Override
        public InputGraph endGraph() {
            return null;
        }

        @Override
        public void endGroup() {
        }

        @Override
        public void endNode(int nodeId) {
        }

        @Override
        public ConstantPool getConstantPool() {
            return null;
        }

        @Override
        public Properties getNodeProperties(int nodeId) {
            return null;
        }

        @Override
        public void inputEdge(Builder.Port p, int from, int to, char num, int index) {
        }

        @Override
        public void makeBlockEdges() {
        }

        @Override
        public void makeGraphEdges() {
        }

        @Override
        public void markGraphDuplicate() {
        }

        @Override
        public void resetStreamData() {
        }

        @Override
        public GraphDocument rootDocument() {
            return null;
        }

        @Override
        public void setGroupName(String name, String shortName) {
        }

        @Override
        public void setMethod(String name, String shortName, int bci) {
        }

        @Override
        public void setNodeName(Builder.NodeClass nodeClass) {
        }

        @Override
        public void setNodeProperty(String key, Object value) {
        }

        @Override
        public void setProperty(String key, Object value) {
        }

        @Override
        public void start() {
        }

        @Override
        public InputBlock startBlock(int id) {
            return null;
        }

        @Override
        public InputBlock startBlock(String name) {
            return null;
        }

        @Override
        public InputGraph startGraph(String title) {
            return null;
        }

        @Override
        public void startGraphContents(InputGraph g) {
        }

        @Override
        public Group startGroup() {
            return null;
        }

        @Override
        public void startGroupContent() {
        }

        @Override
        public void startNestedProperty(String propertyKey) {
        }

        @Override
        public void startNode(int nodeId, boolean hasPredecessors) {
        }

        @Override
        public void startRoot() {
        }

        @Override
        public void successorEdge(Builder.Port p, int from, int to, char num, int index) {
        }
    }
}
