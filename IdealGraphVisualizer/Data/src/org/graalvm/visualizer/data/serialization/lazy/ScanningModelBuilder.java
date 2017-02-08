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
import org.graalvm.visualizer.data.InputNode;
import org.graalvm.visualizer.data.Properties;
import org.graalvm.visualizer.data.serialization.BinarySource;
import org.graalvm.visualizer.data.serialization.ConstantPool;
import org.graalvm.visualizer.data.serialization.ModelBuilder;
import org.graalvm.visualizer.data.services.GroupCallback;

/**
 * ModelBuilder which only scans the incoming stream and creates lazy-loaded Groups
 * which implement {@link Group#LazyContent} interface. The groups are initially 
 * empty, but can be asked to load its data.
 * <p/>
 * Data may load in a separate thread defined by `fetchExecutor', but since 
 * the whole data model is single-threaded, modelExecutor is then used to attach 
 * the loaded data to the LazyContent group.
 * <p/>
 * The loaded group content may be GCed when no one holds a reference to the
 * loaded items (graphs and groups).
 * <p/>
 * This class blocks most of the {@link ModelBuilder} functionality so it
 * creates only a few objects during initial stream reading.
 */
public class ScanningModelBuilder extends ModelBuilder {
    private CachedContent streamContent;
    private BinarySource dataSource;
    private final Map<Group, GroupCompleter>   completors = new LinkedHashMap<>();
    private InputGraph graph = new InputGraph(""); // NOI18N
    private InputNode node = new InputNode(1);
    private final Executor  modelExecutor;
    private final ScheduledExecutorService  fetchExecutor;
    private StreamPool   pool;
    private final Properties dummyProperties = new Properties() {
        @Override
        protected void setPropertyInternal(String name, String value) {
            
        }
    };
    
    private int groupLevel;
    private int graphLevel;
    private GroupCompleter completer;

    public ScanningModelBuilder(
            BinarySource dataSource, 
            CachedContent content, 
            GraphDocument rootDocument, 
            GroupCallback callback, 
            Executor modelExecutor, 
            ScheduledExecutorService fetchExecutor) {
        this(dataSource, content, rootDocument, callback, modelExecutor, fetchExecutor,
                new StreamPool());
    }
    
    public ScanningModelBuilder(
            BinarySource dataSource, 
            CachedContent content, 
            GraphDocument rootDocument, 
            GroupCallback callback, 
            Executor modelExecutor, 
            ScheduledExecutorService fetchExecutor, 
            StreamPool initialPool) {
        super(rootDocument, modelExecutor, callback, null);
        this.dataSource = dataSource;
        this.streamContent = content;
        this.modelExecutor = modelExecutor;
        this.pool = initialPool;
        this.fetchExecutor = fetchExecutor;
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
    }

    @Override
    public void inputEdge(Port p, int from, int to, char num, int index) {
    }

    @Override
    public void setNodeProperty(String key, Object value) {
    }

    @Override
    public void setNodeName(NodeClass nodeClass) {
    }

    @Override
    public void setGroupName(String name, String shortName) {
        if (groupLevel == 1) {
            super.setGroupName(name, shortName);
        }
    }

    @Override
    public void endNode(int nodeId) {
    }

    @Override
    public void startNode(int nodeId, boolean hasPredecessors) {
    }

    @Override
    public void markGraphDuplicate() {
    }

    @Override
    public void endGroup() {
        if (--groupLevel == 0) {
            LazyGroup g = (LazyGroup)folder();
            completer.end(dataSource.getMark());
            super.endGroup();
            replacePool(pool = pool.forkIfNeeded());
            completer = null;
        }
    }

    @Override
    public void startGroupContent() {
        if (groupLevel == 1) {
            super.startGroupContent();
        }
    }

    @Override
    public Group startGroup() {
        if (groupLevel++ > 0) {
            return null;
        }
        assert completer == null;
        long start = dataSource.getMark() - 1;
        GroupCompleter grc = createCompleter(start);
        completer = grc;
        LazyGroup g = new LazyGroup(folder(), grc);
        completer.attachGroup(g);
        completors.put(g, grc);
        return pushGroup(g);
    }
    
    GroupCompleter createCompleter(long start) {
        return new GroupCompleter(streamContent, getConstantPool(), 
                modelExecutor, fetchExecutor, start);
    }
    
    @Override
    public InputGraph startGraph(String title) {
        graphLevel++;
        return null;
    }

    @Override
    public void end() {
        super.end();
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    public InputGraph endGraph() {
        graphLevel--;
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

    @Override
    public ConstantPool getConstantPool() {
        return pool;
    }
}
