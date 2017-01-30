/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.graalvm.visualizer.connection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
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
 *
 */
public class ScanningModelBuilder extends ModelBuilder {
    private CachedContent streamContent;
    private BinarySource dataSource;
    private final Map<Group, GroupCompleter>   completors = new LinkedHashMap<>();
    private InputGraph graph = new InputGraph("");
    private InputNode node = new InputNode(1);
    private final Executor  modelExecutor;
    private Supplier<ConstantPool>  poolSource;

    private Properties dummyProperties = new Properties() {
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
            Supplier<ConstantPool> poolSource) {
        super(rootDocument, modelExecutor, callback, null);
        this.dataSource = dataSource;
        this.streamContent = content;
        this.modelExecutor = modelExecutor;
        this.poolSource = poolSource;
    }
    
    public void poolSource(Supplier<ConstantPool> poolSource) {
        this.poolSource = poolSource;
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
    public void createBlockEdges() {
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
    public void createGraphEdges(List<EdgeInfo> inputEdges, List<EdgeInfo> succEdges) {
    }

    @Override
    public InputEdge immutableEdge(char fromIndex, char toIndex, int from, int to, String label, String type) {
        return null;
    }

    @Override
    public EdgeInfo successorEdge(Port p, int from, int to, char num, int index) {
        return null;
    }

    @Override
    public EdgeInfo inputEdge(Port p, int from, int to, char num, int index) {
        return null;
    }

    @Override
    public void setNodeProperty(String key, Object value) {
    }

    @Override
    public void setNodeName(List<EdgeInfo> edges, NodeClass nodeClass) {
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
            ConstantPool pool = poolSource.get();
            ((StreamPool)pool).forkIfNeeded();
            completer.attachGroup(g, pool);
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
        GroupCompleter grc = new GroupCompleter(streamContent, modelExecutor, start);

        completer = grc;
        LazyGroup g = new LazyGroup(folder(), grc);
        completors.put(g, grc);
        return pushGroup(g);
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
    
}
