/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.graalvm.visualizer.connection;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.graalvm.visualizer.data.ChangedEventProvider;
import org.graalvm.visualizer.data.ChangedListener;
import org.graalvm.visualizer.data.FolderElement;
import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.serialization.BinaryReader;
import org.graalvm.visualizer.data.serialization.BinarySource;
import org.graalvm.visualizer.data.serialization.ConstantPool;
import org.openide.util.RequestProcessor;

/**
 *
 */
class GroupCompleter implements LazyGroup.Completer, Callable<List<? extends FolderElement>>, Runnable {
    /**
     * Delay before the next attempt to read and complete the group. In milliseconds.
     */
    public static final int RESCHEDULE_DELAY = 5000;
    
    /**
     * Maximum attempts to complete the group.
     */
    public static final int ATTEMPT_COUNT = 10;

    private static final Logger LOG = Logger.getLogger(GroupCompleter.class.getName());
    private static final RequestProcessor  EXPAND_RP = new RequestProcessor(ScanningBinaryParser.class);
    
    private final long  start;
    private final Executor notifyExecutor;
    private final  CachedContent   content;

    // @GuardedBy(this)
    private long    end = -1;
    // @GuardedBy(this)
    private ConstantPool    initialPool;
    // @GuardedBy(this)
    private LazyGroup   toComplete;
    // @GuardedBy(this)
    
    // @GuardedBy(this)
    private WrapF   future;
    
    private int     attemptCount;
    
    /**
     * Will keep the currently resolved elements until the events are delivered by the executor.
     */
    private List<? extends FolderElement> keepElements;

    GroupCompleter(CachedContent content, Executor executor, long start) {
        this.start = start;
        this.content = content;
        this.notifyExecutor = executor;
    }
    
    Group getGroup() {
        return toComplete;
    }

    synchronized void end(long end) {
        this.end = end;
    }

    synchronized void attachGroup(LazyGroup g, ConstantPool pool) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "Created completer for group {0}, pool id {1}, start = {2}, end = {3}",
                new Object[] {
                        g.getName(), System.identityHashCode(pool), start, end
            });
        }
        this.initialPool = pool;
        this.toComplete = g;
    }

    @Override
    public synchronized Future<List<? extends FolderElement>> completeContents() {
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
        notifyExecutor.execute(() -> {
            LOG.log(Level.FINER, "Expanding/notifying group " + toComplete.getName());
            toComplete.getChangedEvent().fire();
            keepElements = null;
        });
    }

    @Override
    public List<? extends FolderElement> call() throws Exception {
        System.err.println("Expanding");
        GraphDocument root = new GraphDocument();
        List<? extends FolderElement> newElements;
        synchronized (this) {
            if (end < 0) {
                if (attemptCount++ > ATTEMPT_COUNT) {
                    LOG.log(Level.WARNING, "Completion of Group {0} timed out", toComplete.getName());
                    EXPAND_RP.post(this);
                    future.done = true;
                    future = null;
                    return Collections.emptyList();
                }
                LOG.log(Level.FINE, "Group {0} not fully read, rescheduling; attempt #{1}", new Object[] {
                    toComplete.getName(), attemptCount
                });
                // reschedule, since 
                Future f = EXPAND_RP.schedule((Callable)this, RESCHEDULE_DELAY, TimeUnit.MILLISECONDS);
                this.future.attach(f);
                return null;
            }
        }
        newElements = Collections.emptyList();
        LOG.log(Level.FINER, "Reading group {0}, range {1}-{2}", new Object[] { toComplete.getName(), start, end });
        try {
            ReadableByteChannel channel = content.subChannel(start, end);
            BinarySource bs = new BinarySource(channel);
            SingleGroupBuilder builder = new SingleGroupBuilder(root, notifyExecutor, toComplete);
            new BinaryReader(bs, initialPool.clone()).parse(builder);
            newElements = new ArrayList<>(builder.getItems());
            for (FolderElement e : newElements) {
                e.setParent(toComplete);
            }
            builder.getItems().clear();
            
        } catch (IOException | RuntimeException ex) {
            LOG.log(Level.WARNING, "Error during completion of group " + toComplete.getName(), ex);
        } finally {
            synchronized (this) {
                keepElements = newElements;
                LOG.log(Level.FINER, "Scheduling expansion of group  " + toComplete.getName());
                EXPAND_RP.post(this);
                future.done = true;
                future = null;
            }
        }
        return newElements;
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
    
}
