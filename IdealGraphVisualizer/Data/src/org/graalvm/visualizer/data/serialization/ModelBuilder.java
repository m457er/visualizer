/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.data.serialization;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.graalvm.visualizer.data.Folder;
import org.graalvm.visualizer.data.FolderElement;
import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.InputBlock;
import org.graalvm.visualizer.data.InputEdge;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.InputMethod;
import org.graalvm.visualizer.data.InputNode;
import org.graalvm.visualizer.data.Properties;
import org.graalvm.visualizer.data.services.GroupCallback;
import java.util.*;

/**
 * Builds a model based on SAX-like events. The expected sequence of events is:
 * <ul>
 * <li><b>startGroup</b>, [properties], <b>startGroupContent</b>, groups|graphs, <b>endGroup</b>
 * <li><b>startGraph</b>, [properties], [nodes], [blocks], [edges], <b>makeGraphEdges</b>, [blocks],
 * <b>makeBlockEdges</b>, <b>endGraph</b>
 * <li><b>startNode</b>, [properties], [edges], <b>endNode</b>
 * </ul>
 * The Builder is overridable to allow customized processing of the incoming stream, i.e. partial
 * loading.
 * <p/>
 * createXXX methods are used as factories for data objects, so that specialized implementations may
 * be created. pushXXX must be used at the object start if the object should be read and
 * constructed. Otherwise all the events up to the object close must be blocked/ignored.
 * <p/>
 * 
 */
public class ModelBuilder implements Builder {
    public static final String NO_BLOCK = "noBlock"; // NOI18N

    public static final String PROPNAME_HAS_PREDECESSOR = "hasPredecessor"; // NOI18N
    public static final String PROPNAME_IDX = "idx"; // NOI18N
    public static final String PROPNAME_SHORT_NAME = "shortName"; // NOI18N
    public static final String PROPNAME_NAME = "name"; // NOI18N
    public static final String PROPNAME_CLASS = "class"; // NOI18N
    public static final String PROPNAME_BLOCK = "block"; // NOI18N

    private static final boolean INTERN = Boolean.getBoolean("IGV.internStrings"); // NOI18N

    private static String maybeIntern(String s) {
        if (INTERN) {
            if (s == null) {
                return null;
            }
            return s.intern();
        } else {
            return s;
        }
    }

    protected static final class EdgeInfo {
        final int from;
        final int to;
        final char num;
        final String label;
        final String type;
        final boolean input;

        EdgeInfo(int from, int to) {
            this(from, to, (char) 0, null, null, false);
        }

        EdgeInfo(int from, int to, char num, String label, String type, boolean input) {
            this.from = from;
            this.to = to;
            this.label = maybeIntern(label);
            this.type = maybeIntern(type);
            this.num = num;
            this.input = input;
        }
    }

    private final GroupCallback callback;
    private final GraphDocument rootDocument;
    private final ParseMonitor monitor;
    protected final Executor modelExecutor;

    private ConstantPool pool = new ConstantPool();
    private ModelControl control;

    private Properties.Entity entity;
    private Folder folder;

    private InputGraph currentGraph;
    private List<EdgeInfo> inputEdges;
    private List<EdgeInfo> successorEdges;
    private List<EdgeInfo> nodeEdges;
    private List<EdgeInfo> blockEdges;

    private InputNode currentNode;
    private String propertyObjectKey;
    private Map<String, Object> nodeProperties;
    private InputBlock currentBlock;

    private Deque<Object> stack = new ArrayDeque<>();

    class Level {
        InputNode n = currentNode;
        String pk = propertyObjectKey;
        Map<String, Object> np = nodeProperties;

        InputGraph g = currentGraph;
        Folder f = folder;

        void apply() {
            currentNode = n;
            currentGraph = g;
            folder = f;
            propertyObjectKey = pk;
            nodeProperties = np;

            if (currentNode != null) {
                entity = currentNode;
            } else if (currentGraph != null) {
                entity = currentGraph;
            } else if (folder instanceof Properties.Entity) {
                entity = (Properties.Entity) folder;
            }
        }
    }

    public ModelBuilder(GraphDocument rootDocument, Executor modelExecutor, GroupCallback callback, ParseMonitor monitor) {
        this.callback = callback;
        this.rootDocument = rootDocument;
        this.monitor = monitor;
        this.modelExecutor = modelExecutor;
        this.folder = rootDocument;
    }

    @Override
    public final GraphDocument rootDocument() {
        return rootDocument;
    }

    protected final Folder folder() {
        return folder;
    }

    protected final InputGraph graph() {
        return currentGraph;
    }

    protected final InputNode node() {
        return currentNode;
    }

    private Folder getParent() {
        if (currentGraph != null) {
            return folder;
        } else {
            Object o = stack.peek();
            return o instanceof Folder ? (Folder) o : null;
        }
    }

    protected void popContext() {
        if (currentNode != null) {
            currentNode = null;
            propertyObjectKey = null;
            nodeProperties = null;
            nodeEdges = null;
        } else if (currentGraph != null) {
            currentGraph = null;
            currentNode = null;
            inputEdges = null;
            successorEdges = null;
            blockEdges = null;
        }
        Object o = stack.pop();
        if (o instanceof InputGraph) {
            currentGraph = (InputGraph) o;
            entity = currentGraph;
        } else if (o instanceof InputNode) {
            currentNode = (InputNode) o;
            entity = currentNode;
            nodeProperties = (Map) stack.pop();

            Object[] oo = (Object[]) stack.pop();
            currentGraph = (InputGraph) oo[0];
            inputEdges = (List) oo[1];
            successorEdges = (List) oo[2];
        } else if (o instanceof Folder) {
            this.folder = (Folder) o;
            if (o instanceof Properties.Entity) {
                this.entity = (Properties.Entity) o;
            }
        }
    }

    private void pushContext() {
        if (currentNode != null) {
            stack.push(new Object[]{currentGraph, inputEdges, successorEdges});
            stack.push(nodeProperties);
            stack.push(currentNode);
            currentNode = null;
            currentGraph = null;
            nodeProperties = null;
        } else if (currentGraph != null) {
            stack.push(currentGraph);
            currentNode = null;
        } else if (folder != null) {
            stack.push(folder);
        }
    }

    @Override
    public void setProperty(String key, Object value) {
        getProperties().setProperty(key, value != null ? value.toString() : "null");
    }

    protected Properties getProperties() {
        return entity.getProperties();
    }

    @Override
    public void startNestedProperty(String propertyKey) {
        assert propertyObjectKey == null;
        this.propertyObjectKey = propertyKey;
    }

    protected final String getNestedProperty() {
        return propertyObjectKey;
    }

    protected final InputGraph pushGraph(InputGraph g) {
        pushContext();
        currentGraph = g;
        entity = g;
        inputEdges = new ArrayList<>();
        successorEdges = new ArrayList<>();
        return g;
    }

    protected InputGraph createGraph(String title, String name, Properties.Entity parent) {
        if (parent instanceof Group) {
            return new InputGraph(title);
        } else {
            InputGraph g = new InputGraph(title); // NOI18N
            g.getProperties().setProperty("name", name);
            // fake non-null parent
            new Group(null).addElement(g);
            return g;
        }
    }

    @Override
    public void startGraphContents(InputGraph g) {
    }

    @Override
    public InputGraph startGraph(String title) {
        if (monitor != null) {
            monitor.updateProgress();
        }
        InputNode n = currentNode;
        InputGraph g;

        if (n != null) {
            g = createGraph("", n.getId() + ":" + propertyObjectKey, n);
            propertyObjectKey = null;
        } else {
            g = createGraph(title, null, (Group) folder);
        }
        return pushGraph(g);
    }

    @Override
    public InputGraph endGraph() {
        InputGraph g = currentGraph;
        for (InputNode node : g.getNodes()) {
            node.internProperties();
        }
        popContext();
        if (currentNode != null) {
            currentNode.addSubgraph(g);
        } else {
            registerToParent(folder, g);
        }
        return g;
    }

    @Override
    public void start() {
        if (monitor != null) {
            monitor.setState("Starting parsing");
        }
    }

    @Override
    public void end() {
        if (monitor != null) {
            monitor.setState("Finished parsing");
        }
    }

    protected final Group pushGroup(Group group) {
        pushContext();
        entity = group;
        this.folder = group;
        return group;
    }

    protected Group createGroup(Folder parent) {
        return new Group(parent);
    }

    @Override
    public Group startGroup() {
        Group group = createGroup(folder);
        return pushGroup(group);
    }

    @Override
    public void startGroupContent() {
        assert folder instanceof Group;
        Folder parent = getParent();
        Group g = (Group) folder;
        if (callback == null || parent instanceof Group) {
            registerToParent(parent, (FolderElement) folder);
        }
        if (callback != null && parent instanceof GraphDocument) {
            modelExecutor.execute(() -> callback.started(g));
        }
    }

    @Override
    public void endGroup() {
        popContext();
    }

    protected final int level() {
        return stack.size();
    }

    @Override
    public void markGraphDuplicate() {
        getProperties().setProperty("_isDuplicate", "true"); // NOI18N
    }

    /**
     * Registers an item to its folder. Operation executes in {@link #modelExecutor}.
     * 
     * @param parent the folder
     * @param item the item.
     */
    protected void registerToParent(Folder parent, FolderElement item) {
        modelExecutor.execute(() -> parent.addElement(item));
    }

    protected final void pushNode(InputNode node) {
        pushContext();
        entity = currentNode = node;
        nodeProperties = new HashMap<>();
        nodeEdges = new ArrayList<>();
    }

    protected InputNode createNode(int id) {
        return new InputNode(id);
    }

    @Override
    public void startNode(int nodeId, boolean hasPredecessors) {
        assert currentGraph != null;
        InputNode node = createNode(nodeId);
        // TODO -- intern strings for the numbers
        node.getProperties().setProperty(PROPNAME_IDX, Integer.toString(nodeId)); // NOI18N
        if (hasPredecessors) {
            node.getProperties().setProperty(PROPNAME_HAS_PREDECESSOR, "true"); // NOI18N
        }
        pushNode(node);
        registerToParent(currentGraph, node);
    }

    protected void registerToParent(InputGraph g, InputNode n) {
        g.addNode(n);
    }

    @Override
    public void endNode(int nodeId) {
        popContext();
    }

    static final Set<String> SYSTEM_PROPERTIES = new HashSet<>(Arrays.asList("hasPredecessor",
                    "name",
                    "class",
                    "id",
                    "idx", PROPNAME_BLOCK));

    @Override
    public void setGroupName(String name, String shortName) {
        assert folder instanceof Group;
        setProperty("name", name);
        reportState(name);
    }

    protected final void reportState(String name) {
        if (monitor != null) {
            monitor.setState(name);
        }
    }

    protected final void reportProgress() {
        if (monitor != null) {
            monitor.updateProgress();
        }
    }

    @Override
    public void setNodeName(NodeClass nodeClass) {
        assert currentNode != null;
        getProperties().setProperty(PROPNAME_NAME, createName(nodeClass, nodeEdges, nodeClass.nameTemplate));
        getProperties().setProperty(PROPNAME_CLASS, nodeClass.className);
        switch (nodeClass.className) {
            case "BeginNode":
                getProperties().setProperty(PROPNAME_SHORT_NAME, "B");
                break;
            case "EndNode":
                getProperties().setProperty(PROPNAME_SHORT_NAME, "E");
                break;
        }
    }

    static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{(p|i)#([a-zA-Z0-9$_]+)(/(l|m|s))?\\}");

    private String createName(NodeClass nodeClass, List<EdgeInfo> edges, String template) {
        if (template.isEmpty()) {
            int lastDot = nodeClass.className.lastIndexOf('.');
            String localShortName = nodeClass.className.substring(lastDot + 1);
            if (localShortName.endsWith("Node") && !localShortName.equals("StartNode") && !localShortName.equals("EndNode")) {
                return localShortName.substring(0, localShortName.length() - 4);
            } else {
                return localShortName;
            }
        }
        Matcher m = TEMPLATE_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(2);
            String type = m.group(1);
            String result;
            switch (type) {
                case "i":
                    StringBuilder inputString = new StringBuilder();
                    for (EdgeInfo edge : edges) {
                        if (edge.label.startsWith(name) && (name.length() == edge.label.length() || edge.label.charAt(name.length()) == '[')) {
                            if (inputString.length() > 0) {
                                inputString.append(", ");
                            }
                            inputString.append(edge.from);
                        }
                    }
                    result = inputString.toString();
                    break;
                case "p":
                    Object prop = nodeProperties.get(name);
                    String length = m.group(4);
                    if (prop == null) {
                        result = "?";
                    } else if (length != null && prop instanceof LengthToString) {
                        LengthToString lengthProp = (LengthToString) prop;
                        switch (length) {
                            default:
                            case "l":
                                result = lengthProp.toString(Length.L);
                                break;
                            case "m":
                                result = lengthProp.toString(Length.M);
                                break;
                            case "s":
                                result = lengthProp.toString(Length.S);
                                break;
                        }
                    } else {
                        result = prop.toString();
                    }
                    break;
                default:
                    result = "#?#";
                    break;
            }

            // Escape '\' and '$' to not interfere with the regular expression.
            StringBuilder newResult = new StringBuilder();
            for (int i = 0; i < result.length(); ++i) {
                char c = result.charAt(i);
                if (c == '\\') {
                    newResult.append("\\\\");
                } else if (c == '$') {
                    newResult.append("\\$");
                } else {
                    newResult.append(c);
                }
            }
            result = newResult.toString();
            m.appendReplacement(sb, result);
        }
        m.appendTail(sb);
        return maybeIntern(sb.toString());
    }

    @Override
    public void setNodeProperty(String key, Object value) {
        assert currentNode != null;
        if (SYSTEM_PROPERTIES.contains(key)) {
            key = "!data." + key;
        }
        nodeProperties.put(key, value);
        setProperty(key, value);
    }

    @Override
    public void inputEdge(Port p, int from, int to, char num, int index) {
        assert currentNode != null;
        String label = (p.isList && index >= 0) ? p.name + "[" + index + "]" : p.name;
        EdgeInfo ei = new EdgeInfo(from, to, num, label, ((TypedPort) p).type.toString(Length.S), true);
        inputEdges.add(ei);
        nodeEdges.add(ei);
    }

    @Override
    public void successorEdge(Port p, int from, int to, char num, int index) {
        assert currentNode != null;
        String label = (p.isList && index >= 0) ? p.name + "[" + index + "]" : p.name;
        EdgeInfo ei = new EdgeInfo(to, from, num, label, "Successor", false);
        successorEdges.add(ei);
        nodeEdges.add(ei);
    }

    protected InputEdge immutableEdge(char fromIndex, char toIndex, int from, int to, String label, String type) {
        return InputEdge.createImmutable(fromIndex, toIndex, from, to, label, type);
    }

    @Override
    public void makeGraphEdges() {
        assert currentGraph != null;
        InputGraph graph = currentGraph;
        assert (inputEdges != null && successorEdges != null) || (graph.getNodes().isEmpty());

        Set<InputNode> nodesWithSuccessor = new HashSet<>();
        for (EdgeInfo e : successorEdges) {
            assert !e.input;
            char fromIndex = e.num;
            nodesWithSuccessor.add(graph.getNode(e.from));
            char toIndex = 0;
            graph.addEdge(immutableEdge(fromIndex, toIndex, e.from, e.to, e.label, e.type));
        }
        for (EdgeInfo e : inputEdges) {
            assert e.input;
            char fromIndex = (char) (nodesWithSuccessor.contains(graph.getNode(e.from)) ? 1 : 0);
            char toIndex = e.num;
            graph.addEdge(immutableEdge(fromIndex, toIndex, e.from, e.to, e.label, e.type));
        }
    }

    private String blockName(int id) {
        return id >= 0 ? Integer.toString(id) : NO_BLOCK;
    }

    @Override
    public InputBlock startBlock(int id) {
        return startBlock(blockName(id));
    }

    @Override
    public InputBlock startBlock(String name) {
        assert currentGraph != null;
        assert currentBlock == null;
        if (blockEdges == null) {
            // initialized lazily since if initialized in Graph, must be saved when
            // switching from Node to subgraph. Blocks do not nest any other structures.
            blockEdges = new ArrayList<>();
        }
        return currentBlock = currentGraph.addBlock(name);
    }

    @Override
    public void endBlock(int id) {
        currentBlock = null;
    }

    @Override
    public Properties getNodeProperties(int nodeId) {
        assert currentGraph != null;
        return currentGraph.getNode(nodeId).getProperties();
    }
    
    protected boolean updateNodeBlock(int nodeId, String name) {
        final Properties properties = getNodeProperties(nodeId);
        final String oldBlock = properties.get(PROPNAME_BLOCK);
        if (oldBlock != null) {
            properties.setProperty(PROPNAME_BLOCK, oldBlock + ", " + name);
            return false;
        } else {
            properties.setProperty(PROPNAME_BLOCK, name);
            return true;
        }
    }

    @Override
    public void addNodeToBlock(int nodeId) {
        assert currentBlock != null;
        String name = currentBlock.getName();
        if (updateNodeBlock(nodeId, name)) {
            currentBlock.addNode(nodeId);
        }
    }

    @Override
    public void addBlockEdge(int from, int to) {
        blockEdges.add(new EdgeInfo(from, to));
    }

    @Override
    public void makeBlockEdges() {
        assert currentGraph != null;
        if (blockEdges != null) {
            for (EdgeInfo e : blockEdges) {
                String fromName = blockName(e.from);
                String toName = blockName(e.to);
                currentGraph.addBlockEdge(currentGraph.getBlock(fromName), currentGraph.getBlock(toName));
            }
        }
        currentGraph.ensureNodesInBlocks();
    }

    @Override
    public void setMethod(String name, String shortName, int bci) {
        assert currentNode == null;
        assert currentGraph == null;
        assert folder instanceof Group;

        Group g = (Group) folder;

        InputMethod inMethod = new InputMethod(g, name, shortName, bci);
        inMethod.setBytecodes(""); // NO18N
        g.setMethod(inMethod);
    }

    /**
     * Called during reading when the reader encounters beginning of a new stream. All pending data
     * should be reset.
     */
    @Override
    public void resetStreamData() {
        replacePool(getConstantPool().restart());
    }

    @Override
    public ConstantPool getConstantPool() {
        return pool;
    }

    protected void replacePool(ConstantPool newPool) {
        this.pool = newPool;
        if (control != null) {
            control.setConstantPool(newPool);
        }
    }

    @Override
    public void setModelControl(ModelControl target) {
        this.control = target;
    }
    
    protected ConstantPool getReaderPool() {
        return control.getConstantPool();
    }

    @Override
    public void startRoot() {
        // no op
    }

    protected List<EdgeInfo> getInputEdges() {
        return inputEdges;
    }

    protected List<EdgeInfo> getSuccessorEdges() {
        return successorEdges;
    }

    protected List<EdgeInfo> getNodeEdges() {
        return nodeEdges;
    }
}
