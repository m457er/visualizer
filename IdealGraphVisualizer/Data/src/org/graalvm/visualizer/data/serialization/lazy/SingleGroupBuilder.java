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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.graalvm.visualizer.data.Folder;
import org.graalvm.visualizer.data.FolderElement;
import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.Properties;
import org.graalvm.visualizer.data.serialization.ConstantPool;
import org.graalvm.visualizer.data.serialization.ModelBuilder;

/**
 *
 */
final class SingleGroupBuilder extends ModelBuilder {
    private final List<FolderElement> items = new ArrayList<>();
    private final Group toComplete;
    private int nestLevel;
    private final ConstantPool pool;

    public SingleGroupBuilder(GraphDocument rootDocument, Executor modelExecutor, Group toComplete, ConstantPool initialPool) {
        super(rootDocument, modelExecutor, null, null);
        this.toComplete = toComplete;
        this.pool = initialPool;
    }

    @Override
    public void endGroup() {
        Folder f = folder();
        super.endGroup();
        --nestLevel;
    }

    @Override
    public InputGraph startGraph(String title) {
        nestLevel++;
        return super.startGraph(title);
    }

    @Override
    protected void registerToParent(Folder parent, FolderElement item) {
        if (parent == toComplete) {
            items.add(item);
        } else {
            // ignore threading, the item should have no listeners yet.
            parent.addElement(item);
        }
    }

    @Override
    public InputGraph endGraph() {
        InputGraph gr = super.endGraph();
        --nestLevel;
        return gr;
    }

    @Override
    public Group startGroup() {
        if (nestLevel++ == 0) {
            return pushGroup(toComplete);
        } else {
            return super.startGroup();
        }
    }

    @Override
    protected InputGraph createGraph(String title, String name, Properties.Entity parent) {
        if (parent instanceof LazyGroup) {
            return new LazyGroup.LoadedGraph(title);
        } else {
            return super.createGraph(title, name, parent);
        }
    }

    @Override
    public ConstantPool getConstantPool() {
        return pool;
    }

    public List<FolderElement> getItems() {
        return items;
    }
}
