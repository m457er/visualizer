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

import org.graalvm.visualizer.data.ChangedEventProvider;
import org.graalvm.visualizer.data.ChangedListener;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.Group.Feedback;
import org.graalvm.visualizer.data.serialization.ConstantPool;
import java.io.InterruptedIOException;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Loads lazy-load contents. Loading runs in {@link #fetchExecutor}, after load process completes,
 * change event is fired in {@link #notifyExecutor}. The Feature implemented as completion callback
 * hooks onto loaded data, so <b>all</b> the data remain as long as at least one loaded item is
 * reachable. If the Group to be completed is not yet scanned (end == -1), the Completer postpones
 * the loading for {@link #RESCHEDULE_DELAY} millis, gives up after {@link #ATTEMPT_COUNT} attempts
 * providing empty content for the group.
 */
class BaseCompleter<T, E extends Group.LazyContent & ChangedEventProvider> implements Completer<T>, Runnable {
    private static final Logger LOG = Logger.getLogger(BaseCompleter.class.getName());

    /**
     * Delay before the next attempt to read and complete the group. In milliseconds.
     */
    public static final int RESCHEDULE_DELAY = 5000;

    /**
     * Maximum attempts to complete the group.
     */
    public static final int ATTEMPT_COUNT = 10;

    protected final ConstantPool initialPool;
    protected final StreamEntry entry;

    protected E toComplete;

    private volatile KeepDataFuture future;
    private Feedback feedbackToFinish;

    // diagnostics only
    private int attemptCount;

    /**
     * Will keep the currently resolved elements until the events are delivered by the executor.
     */
    private T keepElements;
    private String name;
    private final Env env;

    BaseCompleter(Env env, StreamEntry entry) {
        this.env = env;
        this.initialPool = entry.getInitialPool();
        this.entry = entry;
    }

    protected synchronized void attachTo(E group, String name) {
        this.toComplete = group;
        this.name = name;
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, "Created completer for {0}, pool id {1}, start = {2}, end = {3}",
                            new Object[]{
                                            name, System.identityHashCode(initialPool), entry.getStart(), entry.getEnd()
                            });
        }
    }

    protected final Env env() {
        return env;
    }

    E getGroup() {
        return toComplete;
    }

    protected final E element() {
        return toComplete;
    }

    protected long size() {
        return entry.size();
    }

    public synchronized void end(long end) {
        LOG.log(Level.FINER, "End mark for group {0}", name);
    }

    @Override
    public synchronized Future<T> completeContents(Feedback feedback) {
        if (future != null) {
            return future;
        }
        feedbackToFinish = feedback;
        return future = new KeepDataFuture(scheduleFetch(feedback));
    }

    /**
     * Sends changed event from the completed group. This method runs first in the
     * {@link #EXPAND_RP} - it is posted so that the code executes <b>after</b> the computing task
     * finishes, and the {@link Future#isDone} turns true. The actual event delivery is replanned
     * into EDT, to maintain IGV threading model. This way
     */
    public void run() {
        env().getModelExecutor().execute(() -> {
            LOG.log(Level.FINER, "Expanding/notifying group " + name);
            toComplete.getChangedEvent().fire();
            Feedback f;

            synchronized (BaseCompleter.this) {
                keepElements = null;
                f = feedbackToFinish;
                feedbackToFinish = null;
            }
            if (f != null) {
                f.finish();
            }
        });
    }

    protected T load(ReadableByteChannel channel, Feedback feedback) throws IOException {
        return null;
    }

    protected Future<T> future() {
        return future;
    }

    Future<T> scheduleFetch(Feedback feedback) {
        LOG.log(Level.FINER, "Scheduling completion for {0}", name);
        return env.getFetchExecutor().schedule(new Worker(feedback), 0, TimeUnit.MILLISECONDS);
    }

    protected T createEmpty() {
        return null;
    }

    protected T hookData(T data) {
        return data;
    }

    @Override
    public boolean canComplete() {
        return !completingThread.get();
    }

    private final ThreadLocal<Boolean> completingThread = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    private class Worker implements Callable<T> {
        private final Feedback feedback;

        public Worker(Feedback feedback) {
            this.feedback = feedback;
        }

        private T invokeEvent(T newElements) {
            env.getFetchExecutor().schedule(BaseCompleter.this, 0, TimeUnit.MILLISECONDS);
            future.complete(newElements);
            future = null;
            completingThread.remove();
            return newElements;
        }

        @Override
        public T call() throws Exception {
            T newElements;
            synchronized (BaseCompleter.this) {
                if (entry.getEnd() < 0) {
                    if (attemptCount++ > ATTEMPT_COUNT) {
                        LOG.log(Level.WARNING, "Completion of Group {0} timed out", name);
                        return invokeEvent(createEmpty());
                    }
                    LOG.log(Level.FINE, "Group {0} not fully read, rescheduling; attempt #{1}", new Object[]{
                                    name, attemptCount
                    });
                    // reschedule, since
                    Future f = env.getFetchExecutor().schedule(this, RESCHEDULE_DELAY, TimeUnit.MILLISECONDS);
                    future.replaceDelegate(f);
                    return null;
                }
            }
            newElements = createEmpty();
            LOG.log(Level.FINER, "Reading group {0}, range {1}-{2}", new Object[]{name, entry.getStart(), entry.getEnd()});
            completingThread.set(true);
            try {
                newElements = load(env.getContent().subChannel(entry.getStart(), entry.getEnd()), feedback);
            } catch (InterruptedIOException ex) {
                future.cancel();
            } catch (ThreadDeath ex) {
                throw ex;
            } catch (Throwable ex) {
                LOG.log(Level.WARNING, "Error during completion of group " + name, ex);
            } finally {
                synchronized (BaseCompleter.this) {
                    keepElements = newElements;
                    LOG.log(Level.FINER, "Scheduling expansion of group  " + name);
                    invokeEvent(newElements);
                }
            }
            return newElements;
        }
    }

    /**
     * Wrapper for the Future, which keeps the whole FolderElement list in memory as long as at
     * least some item is alive. Also allows to rebind the delegate Future, so the completion task
     * can be rescheduled.
     */
    class KeepDataFuture implements Future<T>, ChangedListener {
        private volatile Future<T> delegate;
        private boolean resolved;
        private volatile boolean done;
        private volatile T items;
        private volatile boolean cancel;

        public KeepDataFuture(Future<T> delegate) {
            this.delegate = delegate;
        }

        void complete(T data) {
            this.done = true;
            hookData(data);
        }

        void cancel() {
            this.cancel = true;
        }

        void replaceDelegate(Future<T> del) {
            this.delegate = del;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return delegate.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return cancel || delegate.isCancelled();
        }

        @Override
        public boolean isDone() {
            return done && delegate.isDone();
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            T res;

            // if the Future completes, but the done flag is still false, loop back as new delegate
            // may be in effect
            Future<T> last;
            do {
                last = delegate;
                res = last.get();
            } while (!done && last != delegate);

            boolean r;

            synchronized (this) {
                items = res;
                // register just once.
                r = this.resolved;
                this.resolved = true;
            }
            /*
             * if (!r) { hookData(res); }
             */
            return res;
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.get(timeout, unit);
        }

        @Override
        public void changed(Object source) {
        }
    }
}
