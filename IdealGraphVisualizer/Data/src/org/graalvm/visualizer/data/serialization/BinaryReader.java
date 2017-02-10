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
 */package org.graalvm.visualizer.data.serialization;

import java.io.EOFException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;
import org.graalvm.visualizer.data.Folder;
import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.serialization.ModelBuilder.TypedPort;
import org.graalvm.visualizer.data.serialization.ModelBuilder.Length;
import org.graalvm.visualizer.data.serialization.ModelBuilder.LengthToString;
import org.graalvm.visualizer.data.serialization.ModelBuilder.NodeClass;
import org.graalvm.visualizer.data.serialization.ModelBuilder.Port;
import static org.graalvm.visualizer.data.serialization.BinaryStreamDefs.*;
import static org.graalvm.visualizer.data.serialization.StreamUtils.maybeIntern;

/**
 * The class reads the Graal binary dump format. All model object creation or property value
 * computation / logic is delegated to the {@link ModelBuilder} class. While the BinaryReader should
 * change seldom, together with Graal runtime, the ModelBuilder can be adapted or subclassed to
 * provide different ways how to process the binary data.
 * <p/>
 * Uses {@link BinarySource} to actually read the underlying stream. The Source can report positions
 * to the Builder.
 * <p/>
 * The Reader obtains initial {@link ConstantPool} from the builder; it also allows the Builder to
 * replace ConstantPool in the reader (useful for partial reading).
 */
public final class BinaryReader implements GraphParser {
    private static final Logger LOG = Logger.getLogger(BinaryReader.class.getName());

    private BinarySource dataSource;

    private final Deque<byte[]> hashStack;
    private int folderLevel;

    private MessageDigest digest;

    private int constantPoolSize;

    private ConstantPool constantPool;

    private ModelBuilder builder;

    private static abstract class Member implements LengthToString {
        public final Klass holder;
        public final int accessFlags;
        public final String name;

        public Member(Klass holder, String name, int accessFlags) {
            this.holder = holder;
            this.accessFlags = accessFlags;
            this.name = name;
        }
    }

    private static class Method extends Member {
        public final Signature signature;
        public final byte[] code;

        public Method(String name, Signature signature, byte[] code, Klass holder, int accessFlags) {
            super(holder, name, accessFlags);
            this.signature = signature;
            this.code = code;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(holder).append('.').append(name).append('(');
            for (int i = 0; i < signature.argTypes.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(signature.argTypes[i]);
            }
            sb.append(')');
            return sb.toString();
        }

        @Override
        public String toString(Length l) {
            switch (l) {
                case M:
                    return holder.toString(Length.L) + "." + name;
                case S:
                    return holder.toString(Length.S) + "." + name;
                default:
                case L:
                    return toString();
            }
        }
    }

    private static class Signature {
        public final String returnType;
        public final String[] argTypes;

        public Signature(String returnType, String[] argTypes) {
            this.returnType = returnType;
            this.argTypes = argTypes;
        }

        public String toString() {
            return "Signature(" + returnType + ":" + String.join(":", argTypes) + ")";
        }
    }

    private static class Field extends Member {
        public final String type;

        public Field(String type, Klass holder, String name, int accessFlags) {
            super(holder, name, accessFlags);
            this.type = type;
        }

        @Override
        public String toString() {
            return holder + "." + name;
        }

        @Override
        public String toString(Length l) {
            switch (l) {
                case M:
                    return holder.toString(Length.L) + "." + name;
                case S:
                    return holder.toString(Length.S) + "." + name;
                default:
                case L:
                    return toString();
            }
        }
    }

    private static class Klass implements LengthToString {
        public final String name;
        public final String simpleName;

        public Klass(String name) {
            this.name = name;
            String simple;
            try {
                simple = name.substring(name.lastIndexOf('.') + 1);
            } catch (IndexOutOfBoundsException ioobe) {
                simple = name;
            }
            this.simpleName = simple;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public String toString(Length l) {
            switch (l) {
                case S:
                    return simpleName;
                default:
                case L:
                case M:
                    return toString();
            }
        }
    }

    private static class EnumKlass extends Klass {
        public final String[] values;

        public EnumKlass(String name, String[] values) {
            super(name);
            this.values = values;
        }
    }

    private static class EnumValue implements LengthToString {
        public EnumKlass enumKlass;
        public int ordinal;

        public EnumValue(EnumKlass enumKlass, int ordinal) {
            this.enumKlass = enumKlass;
            this.ordinal = ordinal;
        }

        @Override
        public String toString() {
            return enumKlass.simpleName + "." + enumKlass.values[ordinal];
        }

        @Override
        public String toString(Length l) {
            switch (l) {
                case S:
                    return enumKlass.values[ordinal];
                default:
                case M:
                case L:
                    return toString();
            }
        }
    }

    public BinaryReader(BinarySource dataSource, ModelBuilder builder) {
        this.dataSource = dataSource;
        this.builder = builder;
        this.constantPool = builder.getConstantPool();
        // allow the builder to reconfigure the reader.
        this.builder.setPoolTarget(this::replaceConstantPool);
        hashStack = new LinkedList<>();
    }

    private String readPoolObjectsToString() throws IOException {
        int len = dataSource.readInt();
        if (len < 0) {
            return "null";
        }
        StringBuilder sb = new StringBuilder().append('[');
        for (int i = 0; i < len; i++) {
            sb.append(readPoolObject(Object.class));
            if (i < len - 1) {
                sb.append(", ");
            }
        }
        sb.append(']');
        return maybeIntern(sb.toString());
    }

    @SuppressWarnings("unchecked")
    private <T> T readPoolObject(Class<T> klass) throws IOException {
        int type = dataSource.readByte();
        if (type == POOL_NULL) {
            return null;
        }
        if (type == POOL_NEW) {
            return (T) addPoolEntry(klass);
        }
        assert assertObjectType(klass, type);
        char index = dataSource.readShort();
        if (index < 0 || index >= constantPool.size()) {
            throw new IOException("Invalid constant pool index : " + index);
        }
        Object obj = getPoolData(index);
        return (T) obj;
    }

    private Object getPoolData(int index) {
        return constantPool.get(index, dataSource.getMark() - 1);
    }

    private boolean assertObjectType(Class<?> klass, int type) {
        switch (type) {
            case POOL_CLASS:
                return klass.isAssignableFrom(EnumKlass.class);
            case POOL_ENUM:
                return klass.isAssignableFrom(EnumValue.class);
            case POOL_METHOD:
                return klass.isAssignableFrom(Method.class);
            case POOL_STRING:
                return klass.isAssignableFrom(String.class);
            case POOL_NODE_CLASS:
                return klass.isAssignableFrom(NodeClass.class);
            case POOL_FIELD:
                return klass.isAssignableFrom(Field.class);
            case POOL_SIGNATURE:
                return klass.isAssignableFrom(Signature.class);
            case POOL_NULL:
                return true;
            default:
                return false;
        }
    }

    private Object addPoolEntry(Class<?> klass) throws IOException {
        long where = dataSource.getMark();
        char index = dataSource.readShort();
        int type = dataSource.readByte();
        int size = 0;
        assert assertObjectType(klass, type) : "Wrong object type : " + klass + " != " + type;
        Object obj;
        switch (type) {
            case POOL_CLASS: {
                String name = dataSource.readString();
                int klasstype = dataSource.readByte();
                if (klasstype == ENUM_KLASS) {
                    int len = dataSource.readInt();
                    String[] values = new String[len];
                    for (int i = 0; i < len; i++) {
                        values[i] = readPoolObject(String.class);
                    }
                    size = 2 + name.length();
                    obj = new EnumKlass(name, values);
                } else if (klasstype == KLASS) {
                    size = name.length();
                    obj = new Klass(name);
                } else {
                    throw new IOException("unknown klass type : " + klasstype);
                }
                break;
            }
            case POOL_ENUM: {
                EnumKlass enumClass = readPoolObject(EnumKlass.class);
                int ordinal = dataSource.readInt();
                obj = new EnumValue(enumClass, ordinal);
                size = 2;
                break;
            }
            case POOL_NODE_CLASS: {
                String className = dataSource.readString();
                String nameTemplate = dataSource.readString();
                int inputCount = dataSource.readShort();
                List<TypedPort> inputs = new ArrayList<>(inputCount);
                for (int i = 0; i < inputCount; i++) {
                    boolean isList = dataSource.readByte() != 0;
                    String name = readPoolObject(String.class);
                    EnumValue inputType = readPoolObject(EnumValue.class);
                    inputs.add(new TypedPort(isList, name, inputType));
                }
                int suxCount = dataSource.readShort();
                List<Port> sux = new ArrayList<>(suxCount);
                for (int i = 0; i < suxCount; i++) {
                    boolean isList = dataSource.readByte() != 0;
                    String name = readPoolObject(String.class);
                    sux.add(new Port(isList, name));
                }
                obj = new NodeClass(className, nameTemplate, inputs, sux);
                break;
            }
            case POOL_METHOD: {
                Klass holder = readPoolObject(Klass.class);
                String name = readPoolObject(String.class);
                Signature sign = readPoolObject(Signature.class);
                int flags = dataSource.readInt();
                byte[] code = dataSource.readBytes();
                obj = new Method(name, sign, code, holder, flags);
                break;
            }
            case POOL_FIELD: {
                Klass holder = readPoolObject(Klass.class);
                String name = readPoolObject(String.class);
                String fType = readPoolObject(String.class);
                int flags = dataSource.readInt();
                obj = new Field(fType, holder, name, flags);
                break;
            }
            case POOL_SIGNATURE: {
                int argc = dataSource.readShort();
                String[] args = new String[argc];
                for (int i = 0; i < argc; i++) {
                    args[i] = readPoolObject(String.class);
                }
                String returnType = readPoolObject(String.class);
                obj = new Signature(returnType, args);
                break;
            }
            case POOL_STRING: {
                obj = dataSource.readString();
                size = obj.toString().length();
                break;
            }
            default:
                throw new IOException("unknown pool type");
        }
        this.constantPoolSize += size;
        return constantPool.addPoolEntry(index, obj, where);
    }

    private Object readPropertyObject(String key) throws IOException {
        int type = dataSource.readByte();
        switch (type) {
            case PROPERTY_INT:
                return dataSource.readInt();
            case PROPERTY_LONG:
                return dataSource.readLong();
            case PROPERTY_FLOAT:
                return dataSource.readFloat();
            case PROPERTY_DOUBLE:
                return dataSource.readDouble();
            case PROPERTY_TRUE:
                return Boolean.TRUE;
            case PROPERTY_FALSE:
                return Boolean.FALSE;
            case PROPERTY_POOL:
                return readPoolObject(Object.class);
            case PROPERTY_ARRAY:
                int subType = dataSource.readByte();
                switch (subType) {
                    case PROPERTY_INT:
                        return dataSource.readIntsToString();
                    case PROPERTY_DOUBLE:
                        return dataSource.readDoublesToString();
                    case PROPERTY_POOL:
                        return readPoolObjectsToString();
                    default:
                        throw new IOException("Unknown type");
                }
            case PROPERTY_SUBGRAPH:
                builder.startNestedProperty(key);
                return parseGraph("", false);
            default:
                throw new IOException("Unknown type");
        }
    }

    public GraphDocument parse() throws IOException {
        hashStack.push(null);

        boolean restart = false;
        try {
            while (true) {
                // allows to concatenate BGV files; at the top-level, either BIGV signature,
                // or 0x00-0x02 should be present.
                if (folderLevel == 0) {
                    // Check for a version specification
                    if (dataSource.readHeader() && restart) {
                        // if not at the start of the stream, reinitialize the constant pool.
                        constantPool = constantPool.restart();
                    }
                    restart = true;
                }
                parseRoot();
            }
        } catch (EOFException e) {
            // ignore
        }
        while (folderLevel > 0) {
            doCloseGroup();
        }
        return builder.rootDocument();
    }

    protected void beginGroup() throws IOException {
        parseGroup();
        builder.startGroupContent();
        folderLevel++;
        hashStack.push(null);
    }

    private void doCloseGroup() throws IOException {
        if (folderLevel-- == 0) {
            throw new IOException("Unbalanced groups");
        }
        builder.endGroup();
        hashStack.pop();
    }

    protected void parseRoot() throws IOException {
        int type = dataSource.readByte();
        switch (type) {
            case BEGIN_GRAPH: {
                parseGraph();
                break;
            }
            case BEGIN_GROUP: {
                beginGroup();
                break;
            }
            case CLOSE_GROUP: {
                doCloseGroup();
                break;
            }
            default:
                throw new IOException("unknown root : " + type);
        }
    }

    protected Group createGroup(Folder parent) {
        return new Group(parent);
    }

    protected Group parseGroup() throws IOException {
        Group group = builder.startGroup();
        String name = readPoolObject(String.class);
        String shortName = readPoolObject(String.class);
        Method method = readPoolObject(Method.class);
        int bci = dataSource.readInt();
        builder.setGroupName(name, shortName);
        parseProperties();
        if (method != null) {
            builder.setMethod(name, shortName, bci);
        }
        return group;
    }

    private InputGraph parseGraph() throws IOException {
        String title = readPoolObject(String.class);
        return parseGraph(title, true);
    }

    private void parseProperties() throws IOException {
        int propCount = dataSource.readShort();
        for (int j = 0; j < propCount; j++) {
            String key = readPoolObject(String.class);
            Object value = readPropertyObject(key);
            builder.setProperty(key, value);
        }
    }

    private void computeGraphDigest() {
        byte[] d = dataSource.finishDigest();
        byte[] hash = hashStack.peek();
        if (hash != null && Arrays.equals(hash, d)) {
            builder.markGraphDuplicate();
        } else {
            hashStack.pop();
            hashStack.push(d);
        }
    }

    private int graphReadCount;

    private InputGraph parseGraph(String title, boolean toplevel) throws IOException {
        graphReadCount++;
        builder.startGraph(title);
        parseProperties();
        dataSource.startDigest();
        parseNodes();
        parseBlocks();
        if (toplevel) {
            computeGraphDigest();
        }
        return builder.endGraph();
    }

    private void parseBlocks() throws IOException {
        int blockCount = dataSource.readInt();
        for (int i = 0; i < blockCount; i++) {
            int id = dataSource.readInt();
            builder.startBlock(id);
            int nodeCount = dataSource.readInt();
            for (int j = 0; j < nodeCount; j++) {
                int nodeId = dataSource.readInt();
                if (nodeId < 0) {
                    continue;
                }
                builder.addNodeToBlock(nodeId);
            }
            builder.endBlock(id);
            int edgeCount = dataSource.readInt();
            for (int j = 0; j < edgeCount; j++) {
                int to = dataSource.readInt();
                builder.addBlockEdge(id, to);
            }
        }
        builder.makeBlockEdges();
    }

    protected final void createEdges(int id, int preds, List<? extends Port> portList,
                    boolean dir,
                    EdgeBuilder factory) throws IOException {
        int portNum = 0;
        for (Port p : portList) {
            if (p.isList) {
                int size = dataSource.readShort();
                for (int j = 0; j < size; j++) {
                    int in = dataSource.readInt();
                    if (in >= 0) {
                        factory.edge(p, in, id, (char) (preds + portNum), j);
                        portNum++;
                    }
                }
            } else {
                int in = dataSource.readInt();
                if (in >= 0) {
                    factory.edge(p, in, id, (char) (preds + portNum), -1);
                    portNum++;
                }
            }
        }
    }

    interface EdgeBuilder {
        void edge(Port p, int from, int to, char num, int index);
    }

    private void parseNodes() throws IOException {
        int count = dataSource.readInt();
        for (int i = 0; i < count; i++) {
            int id = dataSource.readInt();
            NodeClass nodeClass = readPoolObject(NodeClass.class);
            int preds = dataSource.readByte();
            builder.startNode(id, preds > 0);
            if (preds > 0) {

            }
            int propCount = dataSource.readShort();
            for (int j = 0; j < propCount; j++) {
                String key = readPoolObject(String.class);
                Object value = readPropertyObject(key);
                builder.setNodeProperty(key, value);
            }
            createEdges(id, preds, nodeClass.inputs, true, builder::inputEdge);
            createEdges(id, 0, nodeClass.sux, true, builder::successorEdge);
            builder.setNodeName(nodeClass);
            builder.endNode(id);
        }
        builder.makeGraphEdges();
    }

    public final ConstantPool getConstantPool() {
        return constantPool;
    }

    /**
     * Used during reading, to compact, reset or change constant pool. Use with great care, wrong
     * constant pool may damage the rest of reading process.
     * 
     * @param cp
     */
    public final void replaceConstantPool(ConstantPool cp) {
        this.constantPool = cp;
    }
}
