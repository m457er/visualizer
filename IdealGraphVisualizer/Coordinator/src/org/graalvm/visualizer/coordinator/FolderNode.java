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
package org.graalvm.visualizer.coordinator;

import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.Folder;
import org.graalvm.visualizer.data.Properties;
import org.graalvm.visualizer.data.FolderElement;
import org.graalvm.visualizer.data.ChangedListener;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.coordinator.actions.RemoveCookie;
import org.graalvm.visualizer.util.PropertiesSheet;
import java.awt.Image;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.graalvm.visualizer.util.ListenerSupport;
import java.util.concurrent.Future;
import org.graalvm.visualizer.data.Group.LazyContent;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.awt.StatusDisplayer;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.Cancellable;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import java.util.concurrent.atomic.AtomicBoolean;
import org.openide.util.RequestProcessor;

public class FolderNode extends AbstractNode {
    private static final RequestProcessor REFRESH_RP = new RequestProcessor(FolderNode.class);
    private InstanceContent content;
    private final Folder folder;

    /**
     * Marker value for "please wait" node
     */
    @SuppressWarnings("RedundantStringConstructorCall") private static final FolderElement WAIT_KEY = new FolderElement() {
        @Override
        public Folder getParent() {
            return null;
        }

        @Override
        public String getName() {
            return "(wait)";
        }

        @Override
        public void setParent(Folder parent) {
        }
    };

    private static class FolderChildren extends Children.Keys<FolderElement> implements ChangedListener<Folder> {

        private final Folder folder;
        private ChangedListener l;
        // delay refreshing UI for ~200ms to batch changes.
        private RequestProcessor.Task   refreshTask;
        
        public FolderChildren(Folder folder) {
            this.folder = folder;
        }

        @Override
        protected Node[] createNodes(FolderElement e) {
            Node[] ret = new Node[1];
            Node n;

            if (e == WAIT_KEY) {
                n = new WaitNode();
            } else if (e instanceof InputGraph) {
                n = new GraphNode((InputGraph) e);
            } else if (e instanceof Folder) {
                n = new FolderNode((Folder) e);
            } else {
                return null;
            }
            ret[0] = n;
            return ret;
        }

        @Override
        public void addNotify() {
            this.l = ListenerSupport.addWeakListener(this, folder.getChangedEvent());
            refreshKeys();
        }

        @Override
        protected void removeNotify() {
            if (l != null) {
                folder.getChangedEvent().removeListener(l);
            }
            setKeys(Collections.<FolderElement> emptyList());
            super.removeNotify();
        }

        @Override
        public void changed(Folder source) {
            synchronized (this) {
                if (refreshTask == null) {
                    // delay first refresh for 200ms
                    refreshTask = REFRESH_RP.post(this::refreshKeys, 200);
                }
            }
        }

        @NbBundle.Messages({
                        "# {0} - name of the loaded folder",
                        "MSG_Loading=Loading contents of {0}",
                        "# {0} - name of the loaded folder",
                        "MSG_ExpansionFailed=Expansion of {0} failed, please see log for possible error.",
                        "# {0} - name of the loaded folder",
                        "MSG_ExpansionCancelled=Expansion of {0} cancelled"
        })
        class Feedback implements Group.Feedback, Cancellable, Runnable {
            final AtomicBoolean cancelled = new AtomicBoolean();
            ProgressHandle handle;
            Future f;
            boolean indeterminate;
            RequestProcessor.Task   cancelIndeterminate = REFRESH_RP.create(this, true);
            boolean removed;
            int lastTotal;
            
            String name() {
                return ((Group) folder).getName();
            }

            void setFuture(Future f) {
                this.f = f;
            }

            @Override
            public void run() {
                synchronized (this) {
                    if (indeterminate) {
                        init(1);
                        handle.finish();
                        removed = true;
                    }
                }
            }

            private void init(int total) {
                if (removed) {
                    return;
                }
                if (handle == null) {
                    handle = ProgressHandle.createHandle(Bundle.MSG_Loading(name()), this);
                    if (total > 0) {
                        handle.start(total);
                    } else {
                        handle.start();
                        handle.switchToIndeterminate();
                        indeterminate = true;
                    }
                    lastTotal = total;
                } else if (indeterminate && total > 0) {
                    handle.switchToDeterminate(total);
                    lastTotal = total;
                } else if (lastTotal < total) {
                    handle.switchToDeterminate(total);
                    lastTotal = total;
                }
            }

            @Override
            public void reportProgress(int workDone, int totalWork, String description) {
                synchronized (this) {
                    init(totalWork);
                    if (removed) {
                        return;
                    }
                }
                if (description != null) {
                    if (totalWork > 0) {
                        handle.progress(description, Math.min(lastTotal, workDone));
                    } else {
                        handle.progress(description);
                    }
                } else if (totalWork > 0) {
                    handle.progress(Math.min(lastTotal, workDone));
                }
                if (indeterminate) {
                    // cancel indeterminate (for unfinished entries) progress after some time, so it does not obscur
                    cancelIndeterminate.schedule(5000);
                }
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }

            @Override
            public boolean cancel() {
                f.cancel(true);
                return !cancelled.getAndSet(true);
            }

            @Override
            public void finish() {
                // same sync as in refreshKeys
                synchronized (FolderChildren.this) {
                    if (removed) {
                        return;
                    }
                    if (!f.isDone()) {
                        StatusDisplayer.getDefault().setStatusText(Bundle.MSG_ExpansionFailed(name()), StatusDisplayer.IMPORTANCE_ANNOTATION);
                    } else if (f.isCancelled()) {
                        StatusDisplayer.getDefault().setStatusText(Bundle.MSG_ExpansionCancelled(name()), StatusDisplayer.IMPORTANCE_ANNOTATION);
                    }
                }
                init(1);
                handle.finish();
            }
        }

        private synchronized void refreshKeys() {
            refreshTask = null;
            List<FolderElement> elements;
            if (folder instanceof Group.LazyContent) {
                LazyContent<List<? extends FolderElement>> lazyFolder = (LazyContent) folder;
                Feedback feedback = new Feedback();
                Future<List<? extends FolderElement>> fContents = lazyFolder.completeContents(feedback);
                if (!fContents.isDone()) {
                    feedback.setFuture(fContents);
                    elements = new ArrayList<>(lazyFolder.partialData());
                    elements.add(WAIT_KEY);
                } else {
                    try {
                        elements = (List)fContents.get();
                    } catch (InterruptedException | ExecutionException ex) {
                        // ignore, reported elsewhere
                        return;
                    }
                }
            } else {
                elements = (List)folder.getElements();
            }
            this.setKeys(elements);
        }
    }

    @Override
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        if (folder instanceof Properties.Entity) {
            Properties.Entity p = (Properties.Entity) folder;
            PropertiesSheet.initializeSheet(p.getProperties(), s);
        }
        return s;
    }

    @Override
    public Image getIcon(int i) {
        return ImageUtilities.loadImage("org/graalvm/visualizer/coordinator/images/folder.png");
    }

    protected FolderNode(Folder folder) {
        this(folder, new FolderChildren(folder), new InstanceContent());
    }

    private FolderNode(final Folder folder, FolderChildren children, InstanceContent content) {
        super(children, new AbstractLookup(content));
        this.folder = folder;
        this.content = content;
        if (folder instanceof FolderElement) {
            final FolderElement folderElement = (FolderElement) folder;
            this.setDisplayName(folderElement.getName());
            content.add(new RemoveCookie() {
                @Override
                public void remove() {
                    folderElement.getParent().removeElement(folderElement);
                }
            });

            content.add(folder);
        }
    }

    @Override
    public Image getOpenedIcon(int i) {
        return getIcon(i);
    }

    @NbBundle.Messages({
                    "TITLE_PleaseWait=Please wait, loading data..."
    })
    static class WaitNode extends AbstractNode {
        public WaitNode() {
            super(Children.LEAF);
            setDisplayName(Bundle.TITLE_PleaseWait());
            setIconBaseWithExtension("org/graalvm/visualizer/coordinator/images/wait.gif");
        }
    }

    /*
     * private static class R implements Feedback, Runnable {
     * 
     * }
     */
}
