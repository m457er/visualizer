/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.graalvm.visualizer.connection;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.graalvm.visualizer.data.Folder;
import org.graalvm.visualizer.data.FolderElement;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.InputGraph;
import org.openide.util.Exceptions;

/**
 * Lazy implementation of Group, which fetches its contents lazily.
 */
public class LazyGroup extends Group implements Group.LazyContent {
    private static final Logger LOG = Logger.getLogger(LazyGroup.class.getName());
    
    private static final Reference EMPTY = new WeakReference(null);
    
    private volatile Completer   completer;
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
            if (f.isDone()) {
                return f.get();
            }
        } catch (InterruptedException | ExecutionException ex) {
            Exceptions.printStackTrace(ex);
        }
        return Collections.emptyList();
    }

    @Override
    public List<InputGraph> getGraphs() {
         Reference<List<InputGraph>> rg = graphs;
         List<InputGraph> l = rg.get();
         if (l != null) {
             return l;
         }
        try {
            List<FolderElement> fl = (List<FolderElement>)completeContents().get();
            l = Collections.unmodifiableList((List)
                    fl.stream().filter((e) -> e instanceof InputGraph).collect(
                            Collectors.toList()));
            graphs = new WeakReference(l);
        } catch (InterruptedException | ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            l = Collections.emptyList();
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
            }
            this.processing = new WeakReference<>(f = completer.completeContents());
        }
        return f;
    }

    @Override
    public int getGraphsCount() {
        return super.getGraphsCount();
    }

    @Override
    public void addElement(FolderElement element) {
    }
    
    public void addElements(List<? extends FolderElement> newElements) {
    }
    
    public interface Completer {
        public Future<List<? extends FolderElement>>  completeContents();
    }
}
