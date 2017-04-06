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
import org.graalvm.visualizer.data.serialization.Builder.TypedPort;
import org.graalvm.visualizer.data.serialization.Builder.Length;
import org.graalvm.visualizer.data.serialization.Builder.LengthToString;
import org.graalvm.visualizer.data.serialization.Builder.NodeClass;
import org.graalvm.visualizer.data.serialization.Builder.Port;
import static org.graalvm.visualizer.data.serialization.BinaryStreamDefs.*;
import static org.graalvm.visualizer.data.serialization.StreamUtils.maybeIntern;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

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
    private static final boolean POOL_STATS = Boolean.getBoolean(BinaryReader.class.getName() + ".poolStats");
    private static final Logger LOG = Logger.getLogger(BinaryReader.class.getName());

    private BinarySource dataSource;

    private final Deque<byte[]> hashStack;
    private int folderLevel;

    private MessageDigest digest;

    private ConstantPool constantPool;

    private Builder builder;
    // diagnostics only
    private int constantPoolSize;
    private int graphReadCount;

    private static abstract class Member implements LengthToString {
        public final Klass holder;
        public final int accessFlags;
        public final String name;

        public Member(Klass holder, String name, int accessFlags) {
            this.holder = holder;
            this.accessFlags = accessFlags;
            this.name = name;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 29 * hash + Objects.hashCode(this.holder);
            hash = 29 * hash + Objects.hashCode(this.name);
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
            if (!(obj instanceof Member)) {
                return false;
            }
            final Member other = (Member) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            if (!Objects.equals(this.holder, other.holder)) {
                return false;
            }
            return true;
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

        @Override
        public int hashCode() {
            int hash = super.hashCode();
            hash = 79 * hash + Objects.hashCode(this.signature);
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
            if (!super.equals(obj)) {
                return false;
            }
            final Method other = (Method) obj;
            if (!Objects.equals(this.signature, other.signature)) {
                return false;
            }
            return true;
        }

    }

    private static class Signature {
        public final String returnType;
        public final String[] argTypes;
        private int hash;

        public Signature(String returnType, String[] argTypes) {
            this.returnType = returnType;
            this.argTypes = argTypes;
            this.hash = toString().hashCode();
        }

        public String toString() {
            return "Signature(" + returnType + ":" + String.join(":", argTypes) + ")";
        }

        @Override
        public int hashCode() {
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
            final Signature other = (Signature) obj;
            if (!Objects.equals(this.returnType, other.returnType)) {
                return false;
            }
            if (!Arrays.deepEquals(this.argTypes, other.argTypes)) {
                return false;
            }
            return true;
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
        private final int hash;

        public Klass(String name) {
            this.name = name;
            String simple;
            try {
                simple = name.substring(name.lastIndexOf('.') + 1);
            } catch (IndexOutOfBoundsException ioobe) {
                simple = name;
            }
            this.simpleName = simple;
            this.hash = (simple + "#" + name).hashCode();
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

        @Override
        public int hashCode() {
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
            final Klass other = (Klass) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            if (!Objects.equals(this.simpleName, other.simpleName)) {
                return false;
            }
            return true;
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

    public BinaryReader(BinarySource dataSource, Builder builder) {
        if (builder instanceof ModelBuilder) {
            ((ModelBuilder) builder).setPoolTarget(this::replaceConstantPool);
        }
        this.dataSource = dataSource;
        this.builder = builder;
        this.constantPool = builder.getConstantPool();
        // allow the builder to reconfigure the reader.
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
                size = className.length() + nameTemplate.length();
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
        if (POOL_STATS) {
            recordNewEntry(obj, size);
        }
        this.constantPoolSize += size;
        return constantPool.addPoolEntry(index, obj, where);
    }

    /**
     * Each value holds 2 ints - 0 is the approx size of the data, 1 is the number of addPooLEntry
     * calls for this value - the number of redundant appearances in the constant pool.
     */
    private Map<Object, int[]> poolEntries = new LinkedHashMap<>(100, 0.8f, true);

    private void recordNewEntry(Object data, int size) {
        // TODO: the stats can be compacted from time to time - e.g. if the number of objects goes
        // large,
        // entries with < N usages can be removed in a hope they are rare.
        poolEntries.compute(data, (o, v) -> {
            if (v == null) {
                return new int[]{size, 1};
            } else {
                v[1]++;
                return v;
            }
        });
    }

    public void dumpPoolStats() {
        if (poolEntries.isEmpty()) {
            return;
        }
        List<Map.Entry<Object, int[]>> entries = new ArrayList(poolEntries.entrySet());
        Collections.sort(entries, (o1, o2) -> {
            return o1.getValue()[0] * o1.getValue()[1] - o2.getValue()[0] * o2.getValue()[1];
        });
        int oneSize = poolEntries.entrySet().stream().mapToInt((e) -> (e.getValue()[0])).sum();
        int totalSize = poolEntries.entrySet().stream().mapToInt((e) -> (e.getValue()[1] * e.getValue()[0])).sum();
        // ignore smal overhead
        if (totalSize < oneSize * 2) {
            return;
        }
        // ignore small # of duplications
        if (entries.get(entries.size() - 1).getValue()[1] < 10) {
            return;
        }
        LOG.log(Level.FINE, "Dumping cpool statistics");
        LOG.log(Level.FINE, "Total {0} values, {1} size of useful data, {2} size with redefinitions", new Object[]{poolEntries.size(), oneSize, totalSize});
        LOG.log(Level.FINE, "Dumping the most consuming entries:");
        int count = 0;
        for (int i = entries.size() - 1; count < 50 && i >= 0; i--, count++) {
            Map.Entry<Object, int[]> e = entries.get(i);
            LOG.log(Level.FINE, "#{0}\t: {1}, size {2}, redefinitions {3}", new Object[]{count, e.getKey(), e.getValue()[0] * e.getValue()[1], e.getValue()[1]});
        }

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

    private void closeDanglingGroups() throws IOException {
        while (folderLevel > 0) {
            doCloseGroup();
        }
        builder.end();
    }

    public GraphDocument parse() throws IOException {
        hashStack.push(null);

        boolean restart = false;
        try {
            while (true) {
                // allows to concatenate BGV files; at the top-level, either BIGV signature,
                // or 0x00-0x02 should be present.
                // Check for a version specification
                if (dataSource.readHeader() && restart) {
                    // if not at the start of the stream, reinitialize the constant pool.
                    closeDanglingGroups();
                    builder.resetStreamData();
                    constantPool = builder.getConstantPool();
                }
                restart = true;
                parseRoot();
            }
        } catch (EOFException e) {
            // ignore
        }
        closeDanglingGroups();
        dumpPoolStats();
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
        builder.startRoot();
        int type = dataSource.readByte();
        try {
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
        } catch (SkipRootException ex) {
            long s = ex.getStart();
            long e = ex.getEnd();
            long pos = dataSource.getMark();
            LOG.log(Level.FINE, "Skipping to offset " + e + ", " + (e - pos) + " bytes skipped");

            assert s < pos && e >= pos;
            if (pos < e) {
                long count = e - pos;
                byte[] scratch = new byte[(int) Math.min(count, 1024 * 1024 * 50)];
                while (count > 0) {
                    int l = (int) Math.min(scratch.length, count);
                    dataSource.readBytes(scratch, l);
                    count -= l;
                }
            }
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

    private InputGraph parseGraph(String title, boolean toplevel) throws IOException {
        graphReadCount++;
        InputGraph g = builder.startGraph(title);
        try {
            parseProperties();
            builder.startGraphContents(g);
            dataSource.startDigest();
            parseNodes();
            parseBlocks();
            if (toplevel) {
                computeGraphDigest();
            }
        } finally {
            g = builder.endGraph();
        }
        return g;
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
