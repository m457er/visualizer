/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.graalvm.visualizer.data.serialization;

import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.InputBlock;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.Properties;
import java.util.List;
import java.util.Objects;

/**
 * Interface for building IGV data from the stream
 */
public interface Builder {
    void addBlockEdge(int from, int to);

    void addNodeToBlock(int nodeId);

    void end();

    void endBlock(int id);

    InputGraph endGraph();

    void endGroup();

    void endNode(int nodeId);

    ConstantPool getConstantPool();

    Properties getNodeProperties(int nodeId);

    void inputEdge(Port p, int from, int to, char num, int index);

    void makeBlockEdges();

    void makeGraphEdges();

    void markGraphDuplicate();

    /**
     * Called during reading when the reader encounters beginning of a new stream. All pending data
     * should be reset.
     */
    void resetStreamData();

    GraphDocument rootDocument();

    void setGroupName(String name, String shortName);

    void setMethod(String name, String shortName, int bci);

    void setNodeName(NodeClass nodeClass);

    void setNodeProperty(String key, Object value);

    void setProperty(String key, Object value);

    void start();

    InputBlock startBlock(int id);

    InputBlock startBlock(String name);

    InputGraph startGraph(String title);

    void startGraphContents(InputGraph g);

    Group startGroup();

    void startGroupContent();

    void startNestedProperty(String propertyKey);

    void startNode(int nodeId, boolean hasPredecessors);

    void startRoot();

    void successorEdge(Port p, int from, int to, char num, int index);

    public enum Length {
        S,
        M,
        L
    }

    public interface LengthToString {
        String toString(ModelBuilder.Length l);
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

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 37 * hash + Objects.hashCode(this.className);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final NodeClass other = (NodeClass) obj;
            if (!Objects.equals(this.className, other.className)) {
                return false;
            }
            return true;
        }

    }
}
