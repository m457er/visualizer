/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
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

/**
 *
 */
public class ModelBuilder {
    public static final String NO_BLOCK = "noBlock";
    
    public enum Length {
        S,
        M,
        L
    }

    private static final boolean INTERN = Boolean.getBoolean("IGV.internStrings");
    
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

    public static final class EdgeInfo {
        final int from;
        final int to;
        final char num;
        final String label;
        final String type;
        final boolean input;
        
        public EdgeInfo(int from, int to) {
            this(from, to, (char) 0, null, null, false);
        }
        public EdgeInfo(int from, int to, char num, String label, String type, boolean input) {
            this.from = from;
            this.to = to;
            this.label = maybeIntern(label);
            this.type = maybeIntern(type);
            this.num = num;
            this.input = input;
        }
    }
    
    public interface LengthToString {
        String toString(Length l);
    }

    public static class Port {
        public final boolean isList;
        public final String name;
        
        Port(boolean isList, String name) {
            this.isList = isList;
            this.name = name;
        }
    }

    public static final class TypedPort extends Port {
        public final LengthToString type;
        
        TypedPort(boolean isList, String name, LengthToString type) {
            super(isList, name);
            this.type = type;
        }
    }

    public final static class NodeClass {
        public final String className;
        public final String nameTemplate;
        public final List<TypedPort> inputs;
        public final List<Port> sux;
        
        NodeClass(String className, String nameTemplate, List<TypedPort> inputs, List<Port> sux) {
            this.className = className;
            this.nameTemplate = nameTemplate;
            this.inputs = inputs;
            this.sux = sux;
        }
        @Override
        public String toString() {
            return className;
        }
    }
    
    private final GroupCallback callback;
    private final GraphDocument rootDocument;
    private final ParseMonitor monitor;
    private final Executor modelExecutor;

    private Properties.Entity   entity;
    private InputNode   currentNode;
    private InputGraph  currentGraph;
    private Folder      folder;
    
    private Deque<Object>   stack = new ArrayDeque<>();
    
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
                entity = (Properties.Entity)folder;
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
            return o instanceof Folder ? (Folder)o : null;
        }
    }
    
    private void popContext() {
        if (currentNode != null) {
            currentNode = null;
            propertyObjectKey = null;
            nodeProperties = null;
        } else if (currentGraph != null) {
            currentGraph = null;
        }
        Object o = stack.pop();
        if (o instanceof InputGraph) {
            currentGraph = (InputGraph)o;
            entity = currentGraph;
        } else if (o instanceof InputNode) {
            currentNode = (InputNode)o;
            nodeProperties = (Map)stack.pop();
            currentGraph = (InputGraph)stack.pop();
            entity = currentNode;
        } else if (o instanceof Folder) {
            this.folder = (Folder)o;
            if (o instanceof Properties.Entity) {
                this.entity = (Properties.Entity)o;
            }
        }
    }
    
    private void pushContext() {
        if (currentNode != null) {
            stack.push(currentGraph);
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
    
    public void setProperty(String key, Object value) {
        getProperties().setProperty(key, value != null ? value.toString() : "null");
    }
    
    Properties getProperties() {
        return entity.getProperties();
    }
    
    private String propertyObjectKey;
    
    public void startNestedProperty(String propertyKey) {
        assert propertyObjectKey == null;
        this.propertyObjectKey = propertyKey;
    }
    
    protected final InputGraph pushGraph(InputGraph g) {
        pushContext();
        currentGraph = g;
        entity = g;
        return g;
    }

    public InputGraph startGraph(String title) {
        if (monitor != null) {
            monitor.updateProgress();
        }
        InputNode n = currentNode;
        InputGraph g;
        
        if (n != null) {
            g = new InputGraph(""); // NOI18N
            g.getProperties().setProperty("name", n.getId() + ":" + propertyObjectKey);
            // fake non-null parent
            new Group(null).addElement(g);
            propertyObjectKey = null;
        } else {
            g = new InputGraph(title);
        }
        return pushGraph(g);
    }
    
    public InputGraph endGraph() {
        InputGraph g = currentGraph;
        for (InputNode node : g.getNodes()) {
            node.internProperties();
        }
        blockEdges = null;
        popContext();
        if (currentNode != null) {
            currentNode.addSubgraph(g);
        } else {
            registerToParent(folder, g);
        }
        return g;
    }
    
    protected final Group pushGroup(Group group) {
        pushContext();
        entity = group;
        this.folder = group;
        return group;
    }
    
    public void start() {
        if (monitor != null) {
            monitor.setState("Starting parsing");
        }
    }
    
    public void end() {
        if (monitor != null) {
            monitor.setState("Finished parsing");
        }
    }
    
    public Group startGroup() {
        Group group = new Group(folder);
        return pushGroup(group);
    }
    
    protected void registerToParent(Folder parent, FolderElement item) {
        modelExecutor.execute(() -> 
            parent.addElement(item)
        );
    }

    public void startGroupContent() {
        assert folder instanceof Group;
        Folder parent = getParent();
        Group g = (Group)folder;
        if (callback == null || parent instanceof Group) {
            registerToParent(parent, (FolderElement)folder);
        }
        if (callback != null && parent instanceof GraphDocument) {
            callback.started(g);
        }
    }
    
    public void endGroup() {
        popContext();
    }
    
    public void markGraphDuplicate() {
        getProperties().setProperty("_isDuplicate", "true"); // NOI18N
    }
    
    private Map<String, Object> nodeProperties;
    
    protected final void pushNode(InputNode node) {
        pushContext();
        entity = currentNode = node;
        nodeProperties = new HashMap<>();
    }
    
    public void startNode(int nodeId, boolean hasPredecessors) {
        assert currentGraph != null;
        InputNode node = new InputNode(nodeId);
        // TODO -- intern strings for the numbers
        node.getProperties().setProperty("idx", Integer.toString(nodeId));
        if (hasPredecessors) {
            node.getProperties().setProperty("hasPredecessor", "true");
        }
        pushNode(node);
        currentGraph.addNode(node);
    }
    
    public void endNode(int nodeId) {
        popContext();
    }
    
    public void setGroupName(String name, String shortName) {
        assert folder instanceof Group;
        setProperty("name", name);
        if (monitor != null) {
            monitor.setState(shortName);
        }
    }
    
    public void setNodeName(List<EdgeInfo> edges, NodeClass nodeClass) {
        assert currentNode != null;
        getProperties().setProperty("name", createName(edges, nodeClass.nameTemplate));
        getProperties().setProperty("name", nodeClass.className);
        switch (nodeClass.className) {
            case "BeginNode":
                getProperties().setProperty("shortName", "B");
                break;
            case "EndNode":
                getProperties().setProperty("shortName", "E");
                break;
        }
    }
    
    static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{(p|i)#([a-zA-Z0-9$_]+)(/(l|m|s))?\\}");

    private String createName(List<EdgeInfo> edges, String template) {
        Matcher m = TEMPLATE_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(2);
            String type = m.group(1);
            String result;
            switch (type) {
                case "i":
                    StringBuilder inputString = new StringBuilder();
                    for(EdgeInfo edge : edges) {
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
                        switch(length) {
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
    
    static final Set<String>    SYSTEM_PROPERTIES = new HashSet<>(Arrays.asList(
        "hasPredecessor",
        "name",
        "class",
        "id",
        "idx",
        "block"
    ));
    
    public void setNodeProperty(String key, Object value) {
        assert currentNode != null;
        if (SYSTEM_PROPERTIES.contains(key)) {
            key = "!data." + key;
        }
        nodeProperties.put(key, value);
        setProperty(key, value);
    }
    
    public EdgeInfo inputEdge(Port p, int from, int to, char num, int index) {
        assert currentNode != null;
        String label = (p.isList && index >= 0) ?  p.name + "[" + index + "]" : p.name;
        return new EdgeInfo(from, to, num, label, ((TypedPort)p).type.toString(Length.S), true);
    }
    
    public EdgeInfo successorEdge(Port p, int from, int to, char num, int index) {
        assert currentNode != null;
        String label = (p.isList && index >= 0) ?  p.name + "[" + index + "]" : p.name;
        return new EdgeInfo(to, from, num, label, "Successor", false);
    }
    
    public InputEdge immutableEdge(char fromIndex, char toIndex, int from, int to, String label, String type) {
        return InputEdge.createImmutable(fromIndex, toIndex, from, to, label, type);
    }
    
    public void createGraphEdges(List<EdgeInfo> inputEdges, List<EdgeInfo> succEdges) {
        assert currentGraph != null;
        InputGraph graph = currentGraph;
        
        Set<InputNode> nodesWithSuccessor = new HashSet<>();
        for (EdgeInfo e : succEdges) {
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
    
    private InputBlock currentBlock;
    
    private String blockName(int id) {
        return id >= 0 ? Integer.toString(id) : NO_BLOCK;
    }
    
    /**
     * "Grammar" for model builder:
     * 
     * Document := Group*
     * Group := {Group | Graph}*
     * Graph := Node*, Edge*, Block*, Edge*
     * Node := {Graph*}?
     * 
     * @param id 
     */
    
    public InputBlock startBlock(int id) {
        assert  currentGraph != null;
        assert  currentBlock == null;
        if (blockEdges == null) {
            // initialized lazily since if initialized in Graph, must be saved when
            // switching from Node to subgraph. Blocks do not nest any other structures.
            blockEdges = new ArrayList<>();
        }
        return currentBlock = currentGraph.addBlock(blockName(id));
    }
    
    public void endBlock(int id) {
        currentBlock = null;
    }
    
    public Properties getNodeProperties(int nodeId) {
        assert currentGraph != null;
        return currentGraph.getNode(nodeId).getProperties();
    }
    
    public void addNodeToBlock(int nodeId) {
        assert currentBlock != null;
        String name = currentBlock.getName();
        final Properties properties = getNodeProperties(nodeId);
        final String oldBlock = properties.get("block");
        if(oldBlock != null) {
            properties.setProperty("block", oldBlock + ", " + name);
        } else {
            currentBlock.addNode(nodeId);
            properties.setProperty("block", name);
        }
    }
    
    private List<EdgeInfo>  blockEdges;
    
    public void addBlockEdge(int from, int to) {
        blockEdges.add(new EdgeInfo(from, to));
    }
    
    public void createBlockEdges() {
        assert currentGraph != null;
        if (blockEdges == null) {
            return;
        }
        
        for (EdgeInfo e : blockEdges) {
            String fromName = blockName(e.from);
            String toName = blockName(e.to);
            currentGraph.addBlockEdge(currentGraph.getBlock(fromName), currentGraph.getBlock(toName));
        }
        currentGraph.ensureNodesInBlocks();
    }
    
    public void setMethod(String name, String shortName, int bci) {
        assert currentNode == null;
        assert currentGraph == null;
        assert folder instanceof Group;
        
        Group g = (Group)folder;

        InputMethod inMethod = new InputMethod(g, name, shortName, bci);
        inMethod.setBytecodes(""); // NO18N
        g.setMethod(inMethod);
    }
    
    /**
     * Called during reading when the reader encounters beginning of a new stream.
     * All pending data should be reset.
     */
    public void resetStreamData() {
        
    }
}
