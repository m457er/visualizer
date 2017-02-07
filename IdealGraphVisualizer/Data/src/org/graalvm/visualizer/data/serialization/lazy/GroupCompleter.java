/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
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

/**
 * Loads LazyGroup contents. Loading runs in {@link #fetchExecutor}, after load process completes,
 * change event is fired in {@link #notifyExecutor}. The Feature implemented as completion callback
 * hooks onto loaded data, so <b>all</b> the data remain as long as at least one loaded item is
 * reachable. If the Group to be completed is not yet scanned (end == -1), the Completer postpones
 * the loading for {@link #RESCHEDULE_DELAY} millis, gives up after {@link #ATTEMPT_COUNT} attempts
 * providing empty content for the group.
 */
final class GroupCompleter implements LazyGroup.Completer, Callable<List<? extends FolderElement>>, Runnable {
    /**
     * Delay before the next attempt to read and complete the group. In milliseconds.
     */
    public static final int RESCHEDULE_DELAY = 5000;

    /**
     * Maximum attempts to complete the group.
     */
    public static final int ATTEMPT_COUNT = 10;

    private static final Logger LOG = Logger.getLogger(GroupCompleter.class.getName());

    private final long start;
    private final Executor notifyExecutor;
    private final ScheduledExecutorService fetchExecutor;
    private final CachedContent content;

    private long end = -1;
    private ConstantPool initialPool;
    private LazyGroup toComplete;
    private WrapF future;

    // diagnostics only
    private int attemptCount;

    /**
     * Will keep the currently resolved elements until the events are delivered by the executor.
     */
    private List<? extends FolderElement> keepElements;

    GroupCompleter(CachedContent content, ConstantPool initialPool,
                    Executor notifyExecutor, ScheduledExecutorService fetchExecutor, long start) {
        this.start = start;
        this.content = content;
        this.notifyExecutor = notifyExecutor;
        this.fetchExecutor = fetchExecutor;
        this.initialPool = initialPool;
    }

    synchronized void attachGroup(LazyGroup group) {
        this.toComplete = group;
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST, "Created completer for group {0}, pool id {1}, start = {2}, end = {3}",
                            new Object[]{
                                            group.getName(), System.identityHashCode(initialPool), start, end
                            });
        }
    }

    Group getGroup() {
        return toComplete;
    }

    synchronized void end(long end) {
        this.end = end;
        LOG.log(Level.FINEST, "End mark for group {0}", toComplete.getName());
    }

    synchronized void attachGroup(LazyGroup g, ConstantPool pool) {
        this.initialPool = pool;
        this.toComplete = g;
    }

    @Override
    public synchronized Future<List<? extends FolderElement>> completeContents() {
        return future = new WrapF(scheduleFetch());
    }

    /**
     * Sends changed event from the completed group. This method runs first in the
     * {@link #EXPAND_RP} - it is posted so that the code executes <b>after</b> the computing task
     * finishes, and the {@link Future#isDone} turns true. The actual event delivery is replanned
     * into EDT, to maintain IGV threading model. This way
     */
    public void run() {
        notifyExecutor.execute(() -> {
            LOG.log(Level.FINER, "Expanding/notifying group " + toComplete.getName());
            toComplete.getChangedEvent().fire();
            keepElements = null;
        });
    }

    List<? extends FolderElement> load(GraphDocument root) throws IOException {
        ReadableByteChannel channel = content.subChannel(start, end);
        BinarySource bs = new BinarySource(channel);
        SingleGroupBuilder builder = new SingleGroupBuilder(root, notifyExecutor, toComplete, initialPool.clone());
        new BinaryReader(bs, builder).parse();
        return builder.getItems();
    }

    Future<List<? extends FolderElement>> scheduleFetch() {
        LOG.log(Level.FINER, "Scheduling completion for {0}", toComplete.getName());
        return fetchExecutor.schedule((Callable<List<? extends FolderElement>>) this, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    public List<? extends FolderElement> call() throws Exception {
        GraphDocument root = new GraphDocument();
        List<? extends FolderElement> newElements;
        synchronized (this) {
            if (end < 0) {
                if (attemptCount++ > ATTEMPT_COUNT) {
                    LOG.log(Level.WARNING, "Completion of Group {0} timed out", toComplete.getName());
                    scheduleFetch();
                    future.done = true;
                    future = null;
                    return Collections.emptyList();
                }
                LOG.log(Level.FINE, "Group {0} not fully read, rescheduling; attempt #{1}", new Object[]{
                                toComplete.getName(), attemptCount
                });
                // reschedule, since
                Future f = fetchExecutor.schedule((Callable) this, RESCHEDULE_DELAY, TimeUnit.MILLISECONDS);
                this.future.attach(f);
                return null;
            }
        }
        newElements = Collections.emptyList();
        LOG.log(Level.FINER, "Reading group {0}, range {1}-{2}", new Object[]{toComplete.getName(), start, end});
        try {
            newElements = load(root);
            for (FolderElement e : newElements) {
                e.setParent(toComplete);
            }
        } catch (ThreadDeath ex) {
            throw ex;
        } catch (Throwable ex) {
            LOG.log(Level.WARNING, "Error during completion of group " + toComplete.getName(), ex);
        } finally {
            synchronized (this) {
                keepElements = newElements;
                LOG.log(Level.FINER, "Scheduling expansion of group  " + toComplete.getName());
                fetchExecutor.schedule((Runnable) this, 0, TimeUnit.MILLISECONDS);
                future.done = true;
                future = null;
            }
        }
        return newElements;
    }

    /**
     * Wrapper for the Future, which keeps the whole FolderElement list in memory as long as at
     * least one FolderElement is alive. Also allows to rebind the delegate Future, so the
     * completion task can be rescheduled.
     */
    private static final class WrapF implements Future<List<? extends FolderElement>>, ChangedListener {
        private volatile Future<List<? extends FolderElement>> delegate;
        private boolean resolved;
        private volatile boolean done;
        private volatile List<? extends FolderElement> items;

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
            Future<List<? extends FolderElement>> last;
            do {
                last = delegate;
                res = last.get();
            } while (!done && last != delegate);

            synchronized (this) {
                items = res;
                // register just once.
                if (!resolved) {
                    resolved = true;
                    for (FolderElement f : res) {
                        if (f instanceof ChangedEventProvider) {
                            // just keep a backreference to the whole list
                            ((ChangedEventProvider) f).getChangedEvent().addListener(this);
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
