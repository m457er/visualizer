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

import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.InputBlock;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.Properties;
import org.graalvm.visualizer.data.serialization.Builder;
import org.graalvm.visualizer.data.serialization.ConstantPool;

/**
 * Helper class which delegates to another builder. Used to switch processing for different data
 * containers. All interface methods are intentionally overridable.
 */
class DelegatingBuilder implements Builder {
    private ModelControl poolExchange;
    /**
     * The current delegate
     */
    private Builder delegate;
    
    /**
     * Switches the delegate. Subsequent calls to {@link DelegatingBuilder} implemetations will
     * delegate to the new instance. Pay attention whether {@code super.* methods} are called before
     * or after this or, or are called at all.
     * 
     * @param newDelegate the new instance of builder to delegate to
     * @return the builder
     */
    protected final Builder delegateTo(Builder newDelegate) {
        this.delegate = newDelegate;
        if (delegate != null && poolExchange != null) {
            delegate.setModelControl(poolExchange);
        }
        return newDelegate;
    }

    protected final Builder delegate() {
        return delegate;
    }

    public final GraphDocument rootDocument() {
        return delegate.rootDocument();
    }

    public void setProperty(String key, Object value) {
        delegate.setProperty(key, value);
    }

    public void startNestedProperty(String propertyKey) {
        delegate.startNestedProperty(propertyKey);
    }

    public void startGraphContents(InputGraph g) {
        delegate.startGraphContents(g);
    }

    public InputGraph startGraph(String title) {
        checkConstantPool();
        return delegate.startGraph(title);
    }

    public InputGraph endGraph() {
        return delegate.endGraph();
    }

    public void start() {
        delegate.start();
    }

    public void end() {
        delegate.end();
    }

    public Group startGroup() {
        checkConstantPool();
        return delegate.startGroup();
    }

    public void startGroupContent() {
        delegate.startGroupContent();
    }

    public void endGroup() {
        delegate.endGroup();
    }

    public void markGraphDuplicate() {
        delegate.markGraphDuplicate();
    }

    public void startNode(int nodeId, boolean hasPredecessors) {
        delegate.startNode(nodeId, hasPredecessors);
    }

    public void endNode(int nodeId) {
        delegate.endNode(nodeId);
    }

    public void setGroupName(String name, String shortName) {
        delegate.setGroupName(name, shortName);
    }

    public void setNodeName(NodeClass nodeClass) {
        delegate.setNodeName(nodeClass);
    }

    public void setNodeProperty(String key, Object value) {
        delegate.setNodeProperty(key, value);
    }

    public void inputEdge(Port p, int from, int to, char num, int index) {
        delegate.inputEdge(p, from, to, num, index);
    }

    public void successorEdge(Port p, int from, int to, char num, int index) {
        delegate.successorEdge(p, from, to, num, index);
    }

    public void makeGraphEdges() {
        delegate.makeGraphEdges();
    }

    public InputBlock startBlock(int id) {
        return delegate.startBlock(id);
    }

    public InputBlock startBlock(String name) {
        return delegate.startBlock(name);
    }

    public void endBlock(int id) {
        delegate.endBlock(id);
    }

    public Properties getNodeProperties(int nodeId) {
        return delegate.getNodeProperties(nodeId);
    }

    public void addNodeToBlock(int nodeId) {
        delegate.addNodeToBlock(nodeId);
    }

    public void addBlockEdge(int from, int to) {
        delegate.addBlockEdge(from, to);
    }

    public void makeBlockEdges() {
        delegate.makeBlockEdges();
    }

    public void setMethod(String name, String shortName, int bci) {
        delegate.setMethod(name, shortName, bci);
    }

    public void resetStreamData() {
        delegate.resetStreamData();
    }

    public ConstantPool getConstantPool() {
        return delegate.getConstantPool();
    }

    public void startRoot() {
        delegate.startRoot();
    }

    @Override
    public void setModelControl(ModelControl poolExchange) {
        this.poolExchange = poolExchange;
        if (delegate != null && poolExchange != null) {
            delegate.setModelControl(poolExchange);
        }
    }
    
    private void checkConstantPool() {
        assert getConstantPool() == poolExchange.getConstantPool();
    }
}
