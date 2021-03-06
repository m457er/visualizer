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

import org.graalvm.visualizer.data.ChangedEvent;
import org.graalvm.visualizer.data.ChangedEventProvider;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.Group.Feedback;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.InputNode;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Implementation which loads the data lazily
 */
public class LazyGraph extends InputGraph implements Group.LazyContent<Collection<InputNode>>, ChangedEventProvider<InputGraph> {
    private final ChangedEvent<InputGraph> changedEvent = new ChangedEvent(this);
    private final LoadSupport<GraphData> cSupport;
    private final GraphMetadata meta;

    public LazyGraph(String name, GraphMetadata meta, Completer<GraphData> completer) {
        super(name);
        this.cSupport = new LoadSupport(completer);
        this.meta = meta;
        cSupport.setName(name);
    }

    @Override
    protected GraphData data() {
        return cSupport.getContents();
    }

    @Override
    public ChangedEvent<InputGraph> getChangedEvent() {
        return changedEvent;
    }

    @Override
    public boolean isComplete() {
        return cSupport.isComplete();
    }

    @Override
    public int getEdgeCount() {
        return meta.getEdgeCount();
    }

    @Override
    public int getNodeCount() {
        return meta.getNodeCount();
    }

    @Override
    public Future<Collection<InputNode>> completeContents(Feedback feedback) {
        return new F(cSupport.completeContents(feedback));
    }

    /**
     * Converts from internal GraphData to collection of nodes.
     */
    private final class F implements Future<Collection<InputNode>> {
        private final Future<GraphData> delegate;

        public F(Future<GraphData> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return delegate.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        @Override
        public boolean isDone() {
            return delegate.isDone();
        }

        @Override
        public Collection<InputNode> get() throws InterruptedException, ExecutionException {
            return delegate.get().getNodes();
        }

        @Override
        public Collection<InputNode> get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.get(timeout, unit).getNodes();
        }
    }

    @Override
    public Set<Integer> getNodeIds() {
        return new IdSet();
    }

    public String toString() {
        if (isComplete()) {
            return super.toString();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Graph ").append(getName()).append(" ").append(getProperties().toString()).append("\n");
        sb.append("<incomplete>");
        return sb.toString();
    }

    @Override
    public boolean containsNode(int id) {
        return meta.nodeIds.get(id);
    }

    @Override
    public boolean isNodeChanged(int nodeId) {
        return meta.changedNodeIds.get(nodeId);
    }

    /**
     * Set implementation over BitSet.
     */
    private class IdSet implements Set<Integer> {
        public IdSet() {
        }

        @Override
        public int size() {
            return meta.getNodeCount();
        }

        @Override
        public boolean isEmpty() {
            return meta.getNodeIds().isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            assert o instanceof Integer;
            return meta.getNodeIds().get((Integer) o);
        }

        @Override
        public Iterator<Integer> iterator() {
            return new It(meta.getNodeIds());
        }

        @Override
        public Object[] toArray() {
            return toArray(null);
        }

        @Override
        public <T> T[] toArray(T[] a) {
            Integer[] arr = a != null && a.length >= size() ? (Integer[]) a : new Integer[size()];
            class P {
                int index = 0;

                void process(int i) {
                    arr[index++] = i;
                }
            }
            meta.getNodeIds().stream().forEachOrdered(new P()::process);
            return (T[]) a;
        }

        @Override
        public boolean add(Integer e) {
            throw new UnsupportedOperationException("Readonly set");
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException("Readonly set");
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            for (Integer u : (Collection<Integer>) c) {
                if (!meta.getNodeIds().get(u)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean addAll(Collection<? extends Integer> c) {
            throw new UnsupportedOperationException("Readonly set");
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException("Readonly set");
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException("Readonly set");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("Readonly set");
        }
    }

    static final class It implements Iterator<Integer> {
        private final BitSet set;
        /**
         * Current bit index. -1 means start
         */
        private int index;

        public It(BitSet set) {
            this.set = set;
            index = set.nextSetBit(0);
        }

        @Override
        public boolean hasNext() {
            return index != -1;
        }

        @Override
        public Integer next() {
            if (index == -1) {
                throw new NoSuchElementException();
            }
            int n = index;
            index = set.nextSetBit(index + 1);
            return n;
        }
    }
}
