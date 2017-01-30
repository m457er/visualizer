/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.graalvm.visualizer.connection;

import java.io.IOException;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.swing.SwingUtilities;
import org.graalvm.visualizer.data.ChangedEventProvider;
import org.graalvm.visualizer.data.ChangedListener;
import org.graalvm.visualizer.data.Folder;
import org.graalvm.visualizer.data.FolderElement;
import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.InputBlock;
import org.graalvm.visualizer.data.InputEdge;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.Properties;
import org.graalvm.visualizer.data.serialization.BinaryParser;
import org.graalvm.visualizer.data.serialization.BinarySource;
import org.graalvm.visualizer.data.serialization.ConstantPool;
import org.graalvm.visualizer.data.services.GroupCallback;
import org.openide.util.RequestProcessor;

/**
 *
 */
public class ScanningBinaryParser extends BinaryParser {
    private CachedContent streamContent;
    private BinarySource dataSource;
    private GroupCallback   callback;
    private Map<Group, Compl>   completors = new LinkedHashMap<>();
    
    public ScanningBinaryParser(BinarySource dataSource, CachedContent content, StreamPool pool,
            GraphDocument rootDocument, GroupCallback callback) {
        super(dataSource, pool, rootDocument, callback);
        this.dataSource = dataSource;
        this.streamContent = content;
    }
    
    public ScanningBinaryParser(CachedContent content, GraphDocument rootDocument, GroupCallback callback) {
        this(new BinarySource(content), content, new StreamPool(), rootDocument, callback);
    }
    
    Properties nodeProps = new Properties() {
        @Override
        protected void setPropertyInternal(String name, String value) {
        }
    };
    
    InputBlock block;
    
    @Override
    protected String createName(List<Edge> edges, Map<String, Object> properties, String template) {
        return ""; // NOI18N
    }

    @Override
    protected InputEdge immutableEdge(char fromIndex, char toIndex, int from, int to, String label, String type) {
        return null;
    }

    @Override
    protected Edge successorEdge(Port p, int from, int to, char num, String label) {
        return null;
    }

    @Override
    protected Edge inputEdge(Port p, int from, int to, char num, String label) {
        return null;
    }

    @Override
    protected void addEdge(int id, int to, List<Edge> edges) {
        // no op
    }

    @Override
    protected Properties getNodeProperties(InputGraph graph, int nodeId) {
        return nodeProps;
    }

    @Override
    protected InputBlock createBlock(InputGraph graph, String name) {
        if (block == null) {
            block = graph.addBlock(name);
        }
        return block;
    }

    @Override
    protected void addNodeToBlock(InputGraph graph, InputBlock block, int nodeId, String name) {
    }
    
    private Compl groupCompleter;

    @Override
    protected Group createGroup(Folder parent) {
        if (parent instanceof GraphDocument) {
            return new LazyGroup(parent, groupCompleter);
        } else {
            return super.createGroup(parent);
        }
    }

    @Override
    protected void registerGraph(Folder parent, FolderElement graph) {
        if (parent instanceof GraphDocument) {
            super.registerGraph(parent, graph);
        }
    }
    
    private Group currentTopGroup;

    @Override
    protected void closeGroup(Group g) throws IOException {
        if (g != currentTopGroup) {
            super.closeGroup(g);
            return;
        }
        groupCompleter.end(dataSource.getMark());
        currentTopGroup = null;
        groupCompleter = null;
        super.closeGroup(g);
    }

    @Override
    protected Group parseGroup(Folder parent) throws IOException {
        if (!(parent instanceof GraphDocument)) {
            return super.parseGroup(parent);
        }
        groupCompleter = new Compl(dataSource.getMark() - 1);
        constantPool = ((StreamPool)constantPool).forkIfNeeded();
        Group g = super.parseGroup(parent);
        completors.put(g, groupCompleter);
        currentTopGroup = g;
        groupCompleter.attachGroup((LazyGroup)g, constantPool);
        return g;
    }

    @Override
    public GraphDocument parse() throws IOException {
        GraphDocument doc = super.parse(); 
        return doc;
    }
    
    private static final RequestProcessor  EXPAND_RP = new RequestProcessor(ScanningBinaryParser.class);
    
    class PartialParser extends BinaryParser {
        private GraphDocument rootDoc;
        private Group   toComplete;
        private List<FolderElement> items = new ArrayList<>();
        
        PartialParser(long start, long end, ConstantPool pool, GraphDocument rootDoc, Group toComplete) throws IOException {
            super(new BinarySource(streamContent.subChannel(start, end)), pool, rootDoc, null);
            this.toComplete = toComplete;
        }
        
        public void parseRoot() throws IOException {
            super.parseRoot();
        }

        @Override
        protected void registerGraph(Folder parent, FolderElement graph) {
            if (parent == toComplete) {
                items.add(graph);
            } else {
                super.registerGraph(parent, graph);
            }
        }

        @Override
        protected Group createGroup(Folder parent) {
            if (parent instanceof GraphDocument) {
                return toComplete;
            } else {
                return super.createGroup(parent);
            }
        }
        
        
        
        public List<? extends FolderElement> getItems() {
            return items;
        }
    }
    
    Collection<Group> groups() {
        return completors.keySet();
    }
    
    long[] getRange(Group g) {
        Compl c = completors.get(g);
        if (c != null) {
            return new long[] {
                c.start, c.end
            };
        } else {
            return null;
        }
    }
    
    ConstantPool initialPool(Group g) {
        Compl c = completors.get(g);
        if (c != null) {
            return c.initialPool;
        } else {
            return null;
        }
    }
    
    /**
     * Wrapper for the Future, which keeps the whole FolderElement list in memory
     * as long as at least one FolderElement is alive. Also allows to rebind the
     * delegate Future, so the completion task can be rescheduled.
     */
    private static final class WrapF implements Future< List<? extends FolderElement>>, ChangedListener {
        private volatile Future<List<? extends FolderElement>> delegate;
        private boolean resolved;
        private volatile boolean done;
        public WrapF(Future<List<? extends FolderElement>> delegate) {
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
            return done && delegate.isDone();
        }

        @Override
        public void changed(Object source) {
        }

        @Override
        public List<? extends FolderElement> get() throws InterruptedException, ExecutionException {
            List<? extends FolderElement> res;
            
            // if the Future completes, but the done flag is still false, loop back as new delegate
            // may be in effect
            Future<List<? extends FolderElement>>  last;
            do {
                last = delegate;
                res = last.get();
            } while (!done && last != delegate);
            
            synchronized (this) {
                // register just once.
                if (!resolved) {
                    resolved = true;
                    for (FolderElement f : res) {
                        if (f instanceof ChangedEventProvider) {
                            // just keep a backreference to the whole list
                            ((ChangedEventProvider)f).getChangedEvent().addListener(this);
                        }
                    }
                }
            }
            return res;
        }
        
        @Override
        public List<? extends FolderElement> get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.get(timeout, unit);
        }
        
        void attach(Future del) {
            this.delegate = del;
        }
    }
    
    private class Compl implements LazyGroup.Completer, Callable<List<? extends FolderElement>>, Runnable {
        private long    start;
        private long    end = -1;
        private ConstantPool    initialPool;
        private LazyGroup   toComplete;
        private List<? extends FolderElement> keepElements;
        private WrapF   future;
        
        public Compl(long start) {
            this.start = start;
        }
        
        synchronized void end(long end) {
            this.end = end;
        }
        
        synchronized void attachGroup(LazyGroup g, ConstantPool pool) {
            this.initialPool = pool;
            this.toComplete = g;
        }
        
        @Override
        public synchronized Future<List<? extends FolderElement>> completeContents() {
            System.err.println("Wrapping future");
            return future = new WrapF(EXPAND_RP.submit((Callable)this));
        }

        /**
         * Sends changed event from the completed group.
         * This method runs first in the {@link #EXPAND_RP} - it is posted so that the code
         * executes <b>after</b> the computing task finishes, and the {@link Future#isDone} 
         * turns true. The actual event delivery is replanned into EDT, to maintain
         * IGV threading model. This way
         */
        public void run() {
            if (!SwingUtilities.isEventDispatchThread()) {
                SwingUtilities.invokeLater(this);
            } else {
                try {
                    toComplete.getChangedEvent().fire();
                } finally {
                    synchronized (this) {
                        keepElements = null;
                    }
                }
            }
        }

        @Override
        public List<? extends FolderElement> call() throws Exception {
            System.err.println("Expanding");
            GraphDocument root = new GraphDocument();
            try {
                PartialParser partial;
                synchronized (this) {
                    if (end < 0) {
                        System.err.println("Group " + toComplete + " not read yet, rescheduling");
                        // reschedule, since 
                        Future f = EXPAND_RP.schedule((Callable)this, 5000, TimeUnit.MILLISECONDS);
                        if (this.future != null) {
                            this.future.attach(f);
                        }
                        return null;
                    }
                    partial = new PartialParser(
                            start, end,
                            initialPool.clone(),
                            root, toComplete);
                }
                partial.parse();
                synchronized (this) {
                    List<? extends FolderElement> newElements = partial.getItems();
                    for (FolderElement e : newElements) {
                        e.setParent(toComplete);
                    }
                    keepElements = newElements;
                    System.err.println("Schedule expansion");
                    EXPAND_RP.post(this);
                    future.done = true;
                    return newElements;
                }
            } catch (Throwable ex) {
                ex.printStackTrace();
                return Collections.emptyList();
            }
        }
    }
}
