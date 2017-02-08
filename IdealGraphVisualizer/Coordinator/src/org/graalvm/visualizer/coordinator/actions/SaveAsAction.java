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

package org.graalvm.visualizer.coordinator.actions;

import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.serialization.Printer;
import org.graalvm.visualizer.settings.Settings;
import java.io.*;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.Action;
import javax.swing.JFileChooser;
import org.graalvm.visualizer.data.FolderElement;
import org.netbeans.api.progress.BaseProgressUtils;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.nodes.Node;
import org.openide.util.Cancellable;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.openide.util.actions.CookieAction;
import org.openide.util.actions.NodeAction;

public final class SaveAsAction extends NodeAction {
    
    public SaveAsAction() {
        this(null);
    }
    
    private SaveAsAction(Lookup actionContext) {
        putValue(Action.SHORT_DESCRIPTION, "Save selected groups to XML file...");
    }

    @Override
    protected void performAction(Node[] activatedNodes) {
        GraphDocument doc = new GraphDocument();
        for (Node n : activatedNodes) {
            Group group = n.getLookup().lookup(Group.class);
            doc.addElement(group);
        }
        save(doc, true);
    }

    @NbBundle.Messages({
        "# 0 - compilation name",
        "MSG_SaveSingle=Saving compilation {0}",
        "# 0 - number of compilations",
        "MSG_SaveSelected=Saving {0} compilations",
        "# 0 - number of compilations",
        "MSG_SaveAll=Saving all compilations ({0} items)",
        "# 0 - error description",
        "ERR_Save=Error during save: {0}"
    })
    public static void save(GraphDocument doc, boolean selected) {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(ImportAction.getFileFilter());
        fc.setCurrentDirectory(new File(Settings.get().get(Settings.DIRECTORY, Settings.DIRECTORY_DEFAULT)));

        if (fc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            if (!file.getName().contains(".")) {
                file = new File(file.getAbsolutePath() + ".xml");
            }

            File dir = file;
            if (!dir.isDirectory()) {
                dir = dir.getParentFile();
            }
            Settings.get().put(Settings.DIRECTORY, dir.getAbsolutePath());
            
            final File f = file;
            
            int num = doc.getElements().size();
            AtomicBoolean cHandle = new AtomicBoolean();
            String msg = selected ? Bundle.MSG_SaveSelected(num) : Bundle.MSG_SaveAll(num);
            ProgressBridge bridge = new ProgressBridge(doc.getElements(), cHandle);
            ProgressHandle handle = ProgressHandle.createHandle(
                    msg,
                    bridge);
            Runnable r = new Runnable() {
                public void run() {
                    try {
                        try (Writer writer = new OutputStreamWriter(new FileOutputStream(f))) {
                            Printer p = new Printer();
                            p.export(writer, doc, bridge, cHandle);
                        }
                    } catch (IOException e) {
                        DialogDisplayer.getDefault().notifyLater(new NotifyDescriptor.Message(
                                Bundle.ERR_Save(e.getLocalizedMessage()), 
                                NotifyDescriptor.ERROR_MESSAGE)
                        );
                    }
                }
            };
            if (num == 1) {
                BaseProgressUtils.runOffEventDispatchThread(r, 
                        Bundle.MSG_SaveSingle(doc.getElements().iterator().next().getName()), 
                        cHandle, true);
            } else {
                BaseProgressUtils.runOffEventThreadWithProgressDialog(
                        () -> { bridge.setHandle(handle); r.run(); },
                        msg, handle, true, num, num);
            }
        }
    }
    
    @NbBundle.Messages({
        "# 0 - item number",
        "# 1 - total items",
        "# 2 - item name",
        "PROGRESS_SaveElement=Saving item {0} of {1}: {2}"
    })
    private static class ProgressBridge implements Consumer<FolderElement>, Cancellable {
        private ProgressHandle handle;
        private final Collection<? extends FolderElement> allItems;
        private final AtomicBoolean cancelHandle;
        private int counter;
        
        public ProgressBridge(Collection<? extends FolderElement> allItems, AtomicBoolean cancelHandle) {
            this.allItems = allItems;
            this.cancelHandle = cancelHandle;
        }
        
        void setHandle(ProgressHandle handle) {
            this.handle = handle;
            handle.start(allItems.size());
        }

        @Override
        public void accept(FolderElement t) {
            if (handle == null) {
                return;
            }
            String n = t.getName();
            handle.progress(Bundle.PROGRESS_SaveElement(++counter, allItems.size(), n), counter);
        }

        @Override
        public boolean cancel() {
            cancelHandle.set(true);
            return true;
        }
        
        public AtomicBoolean getCancelHandle() {
            return cancelHandle;
        }
    }
    
    
    protected int mode() {
        return CookieAction.MODE_SOME;
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(SaveAsAction.class, "CTL_SaveAsAction");
    }

    @Override
    protected String iconResource() {
        return "org/graalvm/visualizer/coordinator/images/save.png";
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected boolean asynchronous() {
        return false;
    }

    @Override
    protected boolean enable(Node[] nodes) {

        int cnt = 0;
        for (Node n : nodes) {
            cnt += n.getLookup().lookupAll(Group.class).size();
        }

        return cnt > 0;
    }
}
