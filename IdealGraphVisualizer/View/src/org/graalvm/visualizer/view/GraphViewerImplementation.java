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
package org.graalvm.visualizer.view;

import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.Group.Feedback;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.services.GraphViewer;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.settings.Settings;
import org.netbeans.api.progress.*;
import org.openide.util.Cancellable;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;

@ServiceProvider(service = GraphViewer.class)
public class GraphViewerImplementation implements GraphViewer {

    @NbBundle.Messages({
                    "# {0} - graph name",
                    "MSG_PrepareGraph=Preparing graph {0}, please wait"
    })
    @Override
    public void view(InputGraph graph, boolean clone) {

        if (!clone) {
            WindowManager manager = WindowManager.getDefault();
            for (Mode m : manager.getModes()) {
                for (TopComponent t : manager.getOpenedTopComponents(m)) {
                    if (t instanceof EditorTopComponent) {
                        EditorTopComponent etc = (EditorTopComponent) t;
                        if (etc.getModel().getGroup().getGraphs().contains(graph)) {
                            initAndRunWithProgress(graph, () -> {
                                etc.getModel().selectGraph(graph);
                                t.requestActive();
                            });
                            return;
                        }
                    }
                }
            }
        }
        initAndRunWithProgress(graph, () -> openSimple(graph));
    }

    private void openSimple(InputGraph graph) {
        Diagram diagram = Diagram.createDiagram(graph, Settings.get().get(Settings.NODE_TEXT, Settings.NODE_TEXT_DEFAULT));
        EditorTopComponent tc = new EditorTopComponent(diagram);
        tc.open();
        tc.requestActive();
    }

    static void initAndRunWithProgress(InputGraph graph, Runnable swingRunnable) {
        assert SwingUtilities.isEventDispatchThread();
        if (graph instanceof Group.LazyContent) {
            processLargeGraph(graph, swingRunnable);
        } else {
            swingRunnable.run();
        }
    }

    private static void processLargeGraph(InputGraph graph, Runnable runNext) {
        final Group.LazyContent lazy = (Group.LazyContent) graph;
        String title = Bundle.MSG_PrepareGraph(graph.getName());

        class F implements Feedback, Cancellable, Runnable {
            private boolean start;
            private final ProgressHandle ph = ProgressHandle.createHandle(title, this);
            private final AtomicBoolean cancelled = new AtomicBoolean();
            private volatile Future waitFor;
            private int lastTotal;

            @Override
            public void reportProgress(int workDone, int totalWork, String description) {
                synchronized (this) {
                    if (!start) {
                        if (totalWork == -1) {
                            ph.start();
                        } else {
                            ph.start(totalWork);
                        }
                        start = true;
                    }
                }
                if (totalWork != lastTotal) {
                    ph.switchToDeterminate(totalWork);
                }
                if (description != null) {
                    if (totalWork > 0) {
                        ph.progress(description, workDone);
                    } else {
                        ph.progress(description);
                    }
                } else if (totalWork > 0) {
                    ph.progress(workDone);
                }
                lastTotal = totalWork;
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }

            @Override
            public synchronized void finish() {
                ph.finish();
            }

            @Override
            public boolean cancel() {
                cancelled.set(true);
                return true;
            }

            public void run() {
                Future f;
                synchronized (this) {
                    f = waitFor;
                }
                try {
                    f.get();
                } catch (InterruptedException ex) {
                    return;
                } catch (ExecutionException ex) {
                    Exceptions.printStackTrace(ex);
                }
                SwingUtilities.invokeLater(runNext);
            }
        }
        F feedback = new F();
        // synchronize so that runOff finishes & initializes progress handle before
        // first progress is reported.
        synchronized (feedback) {
            Future f = lazy.completeContents(feedback);
            if (f.isDone() && !f.isCancelled()) {
                runNext.run();
                return;
            }
            BaseProgressUtils.runOffEventThreadWithProgressDialog(feedback, title, feedback.ph, false, 200, 3000);
            feedback.waitFor = f;
        }
    }
}
