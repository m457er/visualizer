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

import org.graalvm.visualizer.data.serialization.ConstantPool;

/**
 * Describes an entry in the data stream. For graphs, holds GraphMetadata
 */
class StreamEntry {
    static final int LARGE_ENTRY_THRESHOLD = 1024 * 1024 * 2; // 5Mbyte of serialized data

    private byte type;
    /**
     * Offset in file/stream where the object starts.
     */
    private long start;

    /**
     * End of the object.
     */
    private long end;

    /**
     * Constant pool to be used when the object should be read. Must be cloned.
     */
    private ConstantPool initialPool;

    /**
     * Constant pool to be used when this object is <b>skipped</b>. Must be cloned.
     */
    private ConstantPool skipPool;
    private GraphMetadata graphMeta;

    public StreamEntry(long start, ConstantPool initialPool) {
        this.start = start;
        this.initialPool = initialPool;
    }

    StreamEntry end(long end, ConstantPool skipPool) {
        this.end = end;
        this.skipPool = skipPool;
        return this;
    }

    StreamEntry setMetadata(GraphMetadata meta) {
        this.graphMeta = meta;
        return this;
    }

    public byte getType() {
        return type;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    public long size() {
        return end - start;
    }

    public ConstantPool getInitialPool() {
        return initialPool;
    }

    public ConstantPool getSkipPool() {
        return skipPool;
    }

    public GraphMetadata getGraphMeta() {
        return graphMeta;
    }
}
