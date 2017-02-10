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

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.graalvm.visualizer.data.ChangedEvent;
import org.graalvm.visualizer.data.ChangedEventProvider;
import org.graalvm.visualizer.data.Folder;
import org.graalvm.visualizer.data.FolderElement;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.InputGraph;

/**
 * Lazy implementation of Group, which fetches its contents lazily, using {@link GroupCompleter}.
 * The implementation overrides {@link #getElements} in a way that the contents is loaded, if they
 * are not yet present through the {@link #completer}. The exact same data is then provided until
 * all the loaded items become unreachable; then they are GCed, and can be loaded again.
 */
final class LazyGroup extends Group implements Group.LazyContent {
    private static final Logger LOG = Logger.getLogger(LazyGroup.class.getName());

    /**
     * If true, then incomplete groups wait for completion in {@link #getElementsInternal}.
     */
    private static final boolean incompleteWaits = true;

    private static final Reference EMPTY = new WeakReference(null);

    /**
     * Completer responsible to load group's contents on demand.
     */
    private final Completer completer;

    private volatile Reference<Future<List<? extends FolderElement>>> processing = EMPTY;

    /**
     * Filtered list of completed elements
     */
    private volatile Reference<List<InputGraph>> graphs = EMPTY;

    public LazyGroup(Folder parent, Completer completer) {
        super(parent);
        this.completer = completer;
    }

    @Override
    public boolean isComplete() {
        if (completer == null) {
            return true;
        }
        Future<List<? extends FolderElement>> f = processing.get();
        return f != null && f.isDone();
    }

    @Override
    protected List<? extends FolderElement> getElementsInternal() {
        try {
            Future<List<? extends FolderElement>> f = completeContents();
            if (f.isDone() || incompleteWaits) {
                return f.get();
            }
        } catch (InterruptedException | ExecutionException ex) {
            LOG.log(Level.WARNING, "Exception during expansion of group " + getName());
        }
        LOG.log(Level.FINE, "Group " + getName() + " contents incomplete, return empty");
        return Collections.emptyList();
    }

    @Override
    public List<InputGraph> getGraphs() {
        Reference<List<InputGraph>> rg = graphs;
        List<InputGraph> l = rg.get();
        if (l != null) {
            return l;
        }
        boolean c = isComplete();
        List<FolderElement> fl = (List<FolderElement>) getElementsInternal();
        l = Collections.unmodifiableList((List) fl.stream().filter((e) -> e instanceof InputGraph).collect(
                        Collectors.toList()));
        if (incompleteWaits || c) {
            graphs = new WeakReference(l);
        }
        return l;
    }

    @Override
    public synchronized Future<List<? extends FolderElement>> completeContents() {
        Future<List<? extends FolderElement>> f = processing.get();
        if (f == null) {
            graphs = EMPTY;
            if (completer == null) {
                CompletableFuture<List<? extends FolderElement>> c = new CompletableFuture<>();
                c.complete(super.getElements());
                f = c;
                processing = new SoftReference(processing) {
                    // keep forever
                    Future x = c;
                };
                LOG.log(Level.FINE, "No completer, provide empty contents");
            } else {
                f = completer.completeContents();
            }
            LOG.log(Level.FINE, "Contents of group " + getName() + " not available, scheduling fetch");
            this.processing = new WeakReference<>(f);
        }
        return f;
    }

    @Override
    public void addElement(FolderElement element) {
    }

    public void addElements(List<? extends FolderElement> newElements) {
    }

    /**
     * Speicalized version, which just allows hooking of the list of graphs and folders. The hook
     * ensures that a single graph keeps all its siblings in memory - LazyGroup content is discarded
     * as a whole.
     */
    static class LoadedGraph extends InputGraph implements ChangedEventProvider<Object> {
        private ChangedEvent ev = new ChangedEvent(this);

        public LoadedGraph(String name) {
            super(name);
        }

        @Override
        public ChangedEvent<Object> getChangedEvent() {
            return ev;
        }
    }

    public interface Completer {
        public Future<List<? extends FolderElement>> completeContents();
    }
}
