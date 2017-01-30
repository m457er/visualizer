/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.data;

import java.util.*;
import java.util.concurrent.Future;

public class Group extends Properties.Entity implements ChangedEventProvider<Group>, Folder, FolderElement {

    private final List<FolderElement> elements;
    private final List<InputGraph> graphs;

    private InputMethod method;
    private transient ChangedEvent<Group> changedEvent;
    private Folder parent;

    public Group(Folder parent) {
        elements = new ArrayList<>();
        graphs = new ArrayList<>();
        changedEvent = new ChangedEvent<>(this);
        this.parent = parent;

        // Ensure that name and type are never null
        getProperties().setProperty("name", "");
        getProperties().setProperty("type", "");
    }
    
    public void fireChangedEvent() {
        changedEvent.fire();
    }

    public void setMethod(InputMethod method) {
        this.method = method;
    }

    public InputMethod getMethod() {
        return method;
    }

    @Override
    public ChangedEvent<Group> getChangedEvent() {
        return changedEvent;
    }

    @Override
    public List<FolderElement> getElements() {
        return Collections.unmodifiableList(getElementsInternal());
    }

    public int getGraphsCount() {
        return getElementsInternal().size();
    }

    @Override
    public void addElement(FolderElement element) {
        elements.add(element);
        if (element instanceof InputGraph) {
            graphs.add((InputGraph) element);
        }
        element.setParent(this);
        changedEvent.fire();
    }

    public void addElements(List<? extends FolderElement> newElements) {
        for (FolderElement element : newElements) {
            elements.add(element);
            if (element instanceof InputGraph) {
                graphs.add((InputGraph) element);
            }
            element.setParent(this);
        }
        changedEvent.fire();
    }
    
    protected List<? extends FolderElement> getElementsInternal() {
        return elements;
    }

    public Set<Integer> getAllNodes() {
        Set<Integer> result = new HashSet<>();
        for (FolderElement e : getElementsInternal()) {
            if (e instanceof InputGraph) {
                InputGraph g = (InputGraph) e;
                result.addAll(g.getNodesAsSet());
            }
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Group ").append(getProperties()).append("\n");
        for (FolderElement g : getElementsInternal()) {
            sb.append(g.toString());
            sb.append('\n');
        }
        return sb.toString();
    }

    @Override
    public String getName() {
        return getProperties().get("name");
    }

    public String getType() {
        return getProperties().get("type");

    }

    InputGraph getPrev(InputGraph graph) {
        InputGraph lastGraph = null;
        for (FolderElement e : getElementsInternal()) {
            if (e == graph) {
                return lastGraph;
            }
            if (e instanceof InputGraph) {
                lastGraph = (InputGraph) e;
            }
        }
        return null;
    }

    InputGraph getNext(InputGraph graph) {
        boolean found = false;
        for (FolderElement e : getElementsInternal()) {
            if (e == graph) {
                found = true;
            } else if (found && e instanceof InputGraph) {
                return (InputGraph) e;
            }
        }
        return null;
    }

    public InputGraph getLastGraph() {
        InputGraph lastGraph = null;
        for (FolderElement e : getElementsInternal()) {
            if (e instanceof InputGraph) {
                lastGraph = (InputGraph) e;
            }
        }
        return lastGraph;
    }

    @Override
    public Folder getParent() {
         return parent;
    }

    @Override
    public void removeElement(FolderElement element) {
        if (elements.remove(element)) {
            if (element instanceof InputGraph) {
                graphs.remove((InputGraph) element);
            }
            changedEvent.fire();
        }
    }

    public void removeAll() {
        if (elements.isEmpty()) {
            return;
        }
        elements.clear();
        graphs.clear();
        changedEvent.fire();
    }

    public List<InputGraph> getGraphs() {
        return graphs;
    }

    @Override
    public void setParent(Folder parent) {
        this.parent = parent;
    }
    
    /**
     * Special mixin interface, which indicates the Group contents may not be
     * fetched. The LazyContent object has two states:
     * <ul>
     * <li>incomplete, when it serves only partial or no nested data. Properties for the
     * object should be all available.
     * <li>complete, when it contains complete set of directly nested data
     * </ul>
     * Contents of the LazyContent may be eventually released, reverting the state
     * into incomplete; an {@link ChangedEvent} must be fired in such case.
     */
    public interface LazyContent {
        /**
         * Indicates that whether the contents was loaded.
         * @return if true, the contents was loaded fully
         */
        public boolean                      isComplete();
        
        /**
         * Fills the content, and returns the resulting data.
         * Note that potentially the content may contain another LazyContent
         * implementations.
         * <p/>
         * In addition to returning the contents, the implementation must fire
         * a {@link ChangedEvent} upon completing the data, <b>after</b> {@link #isComplete} changes
         * to true. If the implementation supports release of the nested data, the data must not be 
         * released until after event is delivered to all listeners.
         * @return handle to contents of the group.
         */
        public Future<List<? extends FolderElement>>  completeContents();
    }
}
