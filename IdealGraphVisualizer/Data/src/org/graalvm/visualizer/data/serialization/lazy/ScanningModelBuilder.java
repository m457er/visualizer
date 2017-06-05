/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.data.serialization.lazy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import org.graalvm.visualizer.data.Folder;
import org.graalvm.visualizer.data.FolderElement;
import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.InputBlock;
import org.graalvm.visualizer.data.InputEdge;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.Properties;
import org.graalvm.visualizer.data.serialization.BinarySource;
import org.graalvm.visualizer.data.serialization.ConstantPool;
import org.graalvm.visualizer.data.serialization.ModelBuilder;
import org.graalvm.visualizer.data.serialization.ParseMonitor;
import org.graalvm.visualizer.data.services.GroupCallback;
import java.util.Deque;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ModelBuilder which only scans the incoming stream and creates lazy-loaded Groups which implement
 * {@link Group#LazyContent} interface. The groups are initially empty, but can be asked to load its
 * data.
 * <p/>
 * Data may load in a separate thread defined by `fetchExecutor', but since the whole data model is
 * single-threaded, modelExecutor is then used to attach the loaded data to the LazyContent group.
 * <p/>
 * This class blocks most of the {@link ModelBuilder} functionality so it creates only a few objects
 * during initial stream reading. It loads just toplevel groups.
 */
public class ScanningModelBuilder extends ModelBuilder {
    private static final Logger LOG = Logger.getLogger(ScanningModelBuilder.class.getName());

    private final CachedContent streamContent;
    private final BinarySource dataSource;
    private final Map<Group, GroupCompleter> completors = new LinkedHashMap<>();
    private final ScheduledExecutorService fetchExecutor;
    private final Properties dummyProperties = new Properties() {
        @Override
        protected void setPropertyInternal(String name, String value) {

        }
    };

    // for testing
    int groupLevel;
    private int graphLevel;
    private GroupCompleter completer;
    
    /**
     * Index information for Groups and Graphs in the stream.
     */
    protected final StreamIndex index = new StreamIndex();

    private final Deque<StreamEntry> entryStack = new LinkedList<>();

    private StreamEntry entry;

    public ScanningModelBuilder(
                    BinarySource dataSource,
                    CachedContent content,
                    GraphDocument rootDocument,
                    ParseMonitor monitor,
                    Executor modelExecutor,
                    ScheduledExecutorService fetchExecutor) {
        this(dataSource, content, rootDocument, null, monitor, modelExecutor, fetchExecutor,
                        new StreamPool());
    }

    public ScanningModelBuilder(
                    BinarySource dataSource,
                    CachedContent content,
                    GraphDocument rootDocument,
                    GroupCallback callback,
                    ParseMonitor monitor,
                    Executor modelExecutor,
                    ScheduledExecutorService fetchExecutor) {
        this(dataSource, content, rootDocument, callback, monitor, modelExecutor, fetchExecutor, new StreamPool());
    }

        @SuppressWarnings("OverridableMethodCallInConstructor")
    ScanningModelBuilder(
                    BinarySource dataSource,
                    CachedContent content,
                    GraphDocument rootDocument,
                    GroupCallback callback,
                    ParseMonitor monitor,
                    Executor modelExecutor,
                    ScheduledExecutorService fetchExecutor,
                    StreamPool initialPool) {
        super(rootDocument, modelExecutor, callback, monitor);
        this.dataSource = dataSource;
        this.streamContent = content;
        replacePool(initialPool);
        this.fetchExecutor = fetchExecutor;
    }
    
    protected StreamPool pool() {
        return (StreamPool)getConstantPool();
    }
    
    @Override
    protected void registerToParent(Folder parent, FolderElement graph) {
        if (parent instanceof GraphDocument) {
            super.registerToParent(parent, graph);
        }
    }

    @Override
    public void setMethod(String name, String shortName, int bci) {
    }

    @Override
    public void makeBlockEdges() {
        // no op
    }

    @Override
    public void addBlockEdge(int from, int to) {
        // no op
    }

    @Override
    public void addNodeToBlock(int nodeId) {
        // no op
    }

    @Override
    public Properties getNodeProperties(int nodeId) {
        return dummyProperties;
    }

    @Override
    public InputBlock startBlock(int id) {
        return null;
    }

    @Override
    public void makeGraphEdges() {
    }

    @Override
    public InputEdge immutableEdge(char fromIndex, char toIndex, int from, int to, String label, String type) {
        return null;
    }

    @Override
    public void successorEdge(Port p, int from, int to, char num, int index) {
        if (scanGraph) {
            entry.getGraphMeta().addEdge(from, to);
        }
    }

    @Override
    public void inputEdge(Port p, int from, int to, char num, int index) {
        if (scanGraph) {
            entry.getGraphMeta().addEdge(from, to);
        }
    }

    @Override
    public void setNodeProperty(String key, Object value) {
    }

    @Override
    public void setNodeName(NodeClass nodeClass) {
    }

    private String currentGroupName;

    @Override
    public void setGroupName(String name, String shortName) {
        if (groupLevel == 1) {
            super.setGroupName(name, shortName);
            completer.attachTo((LazyGroup) folder(), name);
        }
        currentGroupName = name;
        reportState(name);
    }

    @Override
    public void endNode(int nodeId) {
    }

    private long rootStartPos;

    @Override
    public void startRoot() {
        super.startRoot();
        rootStartPos = dataSource.getMark();
    }

    @Override
    public void startNode(int nodeId, boolean hasPredecessors) {
        if (scanGraph) {
            entry.getGraphMeta().addNode(nodeId);
        }
    }

    @Override
    public void markGraphDuplicate() {
        entry.getGraphMeta().markDuplicate();
    }

    protected void registerEntry(StreamEntry en, long pos) {
        StreamPool n = pool().forkIfNeeded();
        if (LOG.isLoggable(Level.FINER)) {
            if (n != pool()) {
                LOG.log(Level.FINER, "Replaced pool {0} for {1}", new Object[] { 
                    Integer.toHexString(System.identityHashCode(pool())),
                    Integer.toHexString(System.identityHashCode(n))
                });
            }
        }
        replacePool(n);
        en.end(pos, n);
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, "{0} Entry {1}:{2}", new Object[] { logIndent(), en.getStart(), en.getEnd()});
        }
    }
    
    @Override
    public void endGroup() {
        // register end as rootStartPos, which is the offset of the 'close group'
        registerEntry(entry, rootStartPos);
        if (--groupLevel == 0) {
            LazyGroup g = (LazyGroup) folder();
            completer.end(entry.getEnd());
            super.endGroup();
            completer = null;
        }
        entry = entryStack.pop();
    }

    @Override
    public void startGroupContent() {
        if (groupLevel == 1) {
            super.startGroupContent();
            if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, "{2} Group {0}, start = {1}", new Object[]{currentGroupName, rootStartPos, logIndent()});
            }
        }
    }
    
    @Override
    public Group startGroup() {
        checkConstantPool();
        entryStack.push(entry);
        entry = addEntry(new StreamEntry(
            dataSource.getMajorVersion(), dataSource.getMinorVersion(),
            rootStartPos, forkPool()
        ));
        if (groupLevel++ > 0) {
            return null;
        }
        assert completer == null;
        GroupCompleter grc = createCompleter(rootStartPos);
        completer = grc;
        LazyGroup g = new LazyGroup(folder(), grc);
        completer.attachTo(g, null);
        completors.put(g, grc);
        return pushGroup(g);
    }
    
    private String tlGraphName;

    private boolean scanGraph;

    @Override
    public InputGraph startGraph(String title) {
        checkConstantPool();
        entryStack.push(entry);
        entry = addEntry(new StreamEntry(
            dataSource.getMajorVersion(), dataSource.getMinorVersion(),
            rootStartPos, forkPool()
        ).setMetadata(new GraphMetadata()));
        graphLevel++;
        scanGraph = false;
        if (graphLevel == 1) {
            tlGraphName = title;
            LOG.log(Level.FINER, "Starting graph {0} at {1}", new Object[]{title, rootStartPos});

            scanGraph = true;
        }
        reportProgress();
        return null;
    }

    @Override
    public void end() {
        super.end();
        index.close();
        LOG.log(Level.FINE, "Scan terminated");
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    public InputGraph endGraph() {
        registerEntry(entry, dataSource.getMark());
        graphLevel--;
        if (graphLevel == 0) {
            LOG.log(Level.FINER, "Graph {0} ends at {1}, contains {2} nodes", new Object[]{
                            tlGraphName, dataSource.getMark(), entry.getGraphMeta().getNodeCount()
            });
        }
        scanGraph = graphLevel == 1;
        entry = entryStack.pop();
        return null;
    }

    @Override
    public void startNestedProperty(String propertyKey) {
    }

    @Override
    public void setProperty(String key, Object value) {
        if (folder() instanceof LazyGroup && graphLevel == 0) {
            super.setProperty(key, value);
        }
    }

    private void checkConstantPool() {
        assert getReaderPool() == getConstantPool();
    }

    // user by tests
    private String logIndent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < groupLevel; i++) {
            sb.append("  ");
        }
        return sb.toString();
    }
    
    public ConstantPool forkPool() {
        StreamPool sp = pool();
        ConstantPool np = sp.forkIfNeeded();
        replacePool(np);
        return np;
    }
    
    protected StreamEntry addEntry(StreamEntry m) {
        StreamEntry e = index.addEntry(m);
        return e;
    }

    GroupCompleter getCompleter(Group g) {
        return (GroupCompleter)completors.get(g);
    }

    GroupCompleter createCompleter(long start) {
        return new GroupCompleter(
                        new Env(streamContent, modelExecutor, fetchExecutor),
                        index, entry);
    }
}
