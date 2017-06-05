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
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.InputGraph.GraphData;
import org.graalvm.visualizer.data.InputNode;
import org.graalvm.visualizer.data.Properties;
import org.graalvm.visualizer.data.serialization.ConstantPool;
import org.graalvm.visualizer.data.serialization.ModelBuilder;
import org.graalvm.visualizer.data.serialization.ParseMonitor;
import java.util.concurrent.Executor;

/**
 * Loads an individual graph + possible nested subgraphs.
 */
final class GraphBuilder extends ModelBuilder {
    private final InputGraph toComplete;
    private final DG dummyGraph = new DG("dummy"); // NOI18N
    private Object keepData;
    private final ConstantPool pool;

    public GraphBuilder(GraphDocument rootDocument, Executor modelExecutor, InputGraph toComplete, Object keepData,
                    StreamEntry entry, ParseMonitor monitor) {
        super(rootDocument, modelExecutor, null, monitor);
        this.keepData = keepData;
        this.toComplete = toComplete;
        this.pool = entry.getInitialPool().copy();
        // establish context
        pushGroup(toComplete.getGroup());
    }

    @Override
    public ConstantPool getConstantPool() {
        return pool;
    }

    @Override
    protected InputGraph createGraph(String title, String name, Properties.Entity parent) {
        if (parent == toComplete.getGroup() && toComplete.getName().equals(title)) {
            return dummyGraph;
        }
        return super.createGraph(title, name, parent);
    }

    /**
     * Yiels the actual graph data
     * 
     * @return
     */
    public GraphData data() {
        return dummyGraph.data();
    }

    @Override
    protected InputNode createNode(int id) {
        reportProgress();
        return new GN(id, keepData);
    }

    @Override
    protected void registerToParent(Folder parent, FolderElement item) {
        if (item == dummyGraph || parent == toComplete.getGroup()) {
            // avoid duplicate registrations
            return;
        }
        super.registerToParent(parent, item);
    }

    @Override
    public void end() {
        super.end();
        // copy dummy graph's data to the real one
    }

    /**
     * Will keep the referenced "keep" object in memory.
     */
    static class GN extends InputNode {
        final Object keep;

        public GN(int id, Object keep) {
            super(id);
            this.keep = keep;
        }
    }

    static class DG extends InputGraph {
        public DG(String name) {
            super(name);
        }

        // accessor
        @Override
        protected GraphData data() {
            return super.data();
        }

        void copyData(LazyGraph target) {

        }
    }
}
