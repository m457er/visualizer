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

import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.Group.Feedback;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Support class handling synchronization and caching of the obtained result.
 */
class LoadSupport<T> implements Group.LazyContent<T> {
    private static final Logger LOG = Logger.getLogger(LoadSupport.class.getName());
    private static final Reference EMPTY = new WeakReference(null);

    private final Completer<T> completer;

    private volatile Reference<Future<T>> processing = EMPTY;
    private String name;

    public LoadSupport(Completer<T> completer) {
        this.completer = completer;
    }

    /**
     * Sets name of the completed object. Used for diagnostic purposes.
     * 
     * @param name display name
     */
    void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean isComplete() {
        if (completer == null) {
            return true;
        }
        // pretend we're done
        if (!completer.canComplete()) {
            return true;
        }
        Future<T> f = processing.get();
        return f != null && f.isDone();
    }
    
    public T partialData() {
        return completer == null ? emptyData() : completer.partialData();
    }

    public T getContents() {
        try {
            if (completer != null && !completer.canComplete()) {
                return emptyData();
            }
            Future<T> wait;
            synchronized (this) {
                Future<T> f = completeContents(null);
                if (f.isDone()) {
                    return f.get();
                } else {
                    Future<T> cur;
                    // HACK: first attempt to blindly getContents will block on the future.
                    // After computation launches (cur == wait), other attempts will try to return at least
                    // partial data, if the completer is willing to produce it.
                    cur = processing.get();
                    wait = completeContents(null);
                    if (cur == wait) {
                        if (completer != null) {
                            T x = completer.partialData();
                            if (x != null) {
                                return x;
                            }
                        }
                    }
                }
            }
            return wait.get();
        } catch (InterruptedException | ExecutionException ex) {
            LOG.log(Level.WARNING, "Exception during expansion of group " + name, ex);
        }
        LOG.log(Level.FINE, "Group " + name + " contents incomplete, return empty");
        return emptyData();
    }

    public synchronized Future<T> completeContents(Feedback feedback) {
        Future<T> f = processing.get();
        if (f == null) {
            if (completer == null) {
                CompletableFuture<T> c = new CompletableFuture<>();
                c.complete(emptyData());
                f = c;
                processing = new SoftReference(processing) {
                    // keep forever
                    Future x = c;
                };
                LOG.log(Level.FINE, "No completer, provide empty contents");
            } else {
                if (completer.canComplete()) {
                    f = completer.completeContents(feedback);
                } else {
                    CompletableFuture<T> c = new CompletableFuture<>();
                    c.complete(emptyData());
                    // do not cache
                    return c;
                }
            }
            LOG.log(Level.FINE, "Contents of group " + name + " not available, scheduling fetch");
            this.processing = new WeakReference<>(f);
        }
        return f;
    }

    /**
     * Provides empty data for the case the completer cannot complete, or an error occurs. The
     * method may provide an immutable shared instance.
     * 
     * @return empty data object instance
     */
    protected T emptyData() {
        return null;
    }
}
