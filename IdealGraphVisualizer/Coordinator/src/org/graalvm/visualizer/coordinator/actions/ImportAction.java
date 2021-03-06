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

import org.graalvm.visualizer.coordinator.OutlineTopComponent;
import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.serialization.GraphParser;
import org.graalvm.visualizer.data.serialization.ParseMonitor;
import org.graalvm.visualizer.data.serialization.Parser;
import org.graalvm.visualizer.settings.Settings;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import org.graalvm.visualizer.connection.Server;
import org.graalvm.visualizer.data.serialization.BinaryReader;
import org.graalvm.visualizer.data.serialization.FileContent;
import org.graalvm.visualizer.data.serialization.ModelBuilder;
import org.graalvm.visualizer.data.serialization.lazy.CancelableSource;
import org.graalvm.visualizer.data.serialization.lazy.ScanningModelBuilder;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.Cancellable;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.SystemAction;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;

@ActionID(category = "File", id = "org.graalvm.visualizer.coordinator.actions.ImportAction")
@ActionRegistration(iconBase = "org/graalvm/visualizer/coordinator/images/import.png", displayName = "#CTL_ImportAction")
@ActionReferences({
                @ActionReference(path = "Menu/File", position = 0),
                @ActionReference(path = "Shortcuts", name = "C-O")
})
public final class ImportAction extends SystemAction {
    private static final Logger LOG = Logger.getLogger(ImportAction.class.getName());

    private static final int WORKUNITS = 10000;

    public static FileFilter getFileFilter() {
        return new FileFilter() {

            @Override
            public boolean accept(File f) {
                return f.getName().toLowerCase().endsWith(".xml") || f.getName().toLowerCase().endsWith(".bgv") || f.isDirectory();
            }

            @Override
            public String getDescription() {
                return "Graph files (*.xml, *.bgv)";
            }
        };
    }

    /**
     * Request processor used to lazy-load; shared with the network receiver, but could be also
     * separated.
     */
    private static final RequestProcessor LOADER_RP = Server.LOADER_RP;

    @NbBundle.Messages({
                    "# {0} - file name",
                    "# {1} - error message",
                    "ERR_ReadingFile=Error importing from file {0}: {1}",
                    "# {0} - file name",
                    "MSG_LoadCancelled=Load of {0} was cancelled"
    })
    @Override
    public void actionPerformed(ActionEvent e) {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(ImportAction.getFileFilter());
        fc.setCurrentDirectory(new File(Settings.get().get(Settings.DIRECTORY, Settings.DIRECTORY_DEFAULT)));
        fc.setMultiSelectionEnabled(true);

        if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            for (final File file : fc.getSelectedFiles()) {
                File dir = file;
                if (!dir.isDirectory()) {
                    dir = dir.getParentFile();
                }

                Settings.get().put(Settings.DIRECTORY, dir.getAbsolutePath());
                try {
                    final FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
                    final long startTime = System.currentTimeMillis();
                    final long start = channel.size();

                    class Mon implements ParseMonitor, Cancellable {
                        final AtomicBoolean cancelFlag = new AtomicBoolean();
                        ProgressHandle handle;

                        synchronized void setHandle(ProgressHandle h) {
                            this.handle = h;
                        }

                        @Override
                        public void updateProgress() {
                            try {
                                int prog = (int) (WORKUNITS * (double) channel.position() / (double) channel.size());
                                if (prog > WORKUNITS) {
                                    prog = WORKUNITS;
                                }
                                synchronized (this) {
                                    handle.progress(prog);
                                }
                            } catch (IOException ex) {
                                // ignore
                            }
                        }

                        @Override
                        public void setState(String state) {
                            updateProgress();
                            handle.progress(state);
                        }

                        public boolean isCancelled() {
                            return cancelFlag.get();
                        }

                        @Override
                        public boolean cancel() {
                            cancelFlag.set(true);
                            return true;
                        }
                    }
                    Mon monitor = new Mon();
                    final ProgressHandle handle = ProgressHandleFactory.createHandle("Opening file " + file.getName(), monitor);
                    monitor.setHandle(handle);
                    handle.start(WORKUNITS);
                    final GraphParser parser;
                    final OutlineTopComponent component = OutlineTopComponent.findInstance();
                    if (file.getName().endsWith(".xml")) {
                        parser = new Parser(channel, monitor, null);
                    } else if (file.getName().endsWith(".bgv")) {
                        FileContent content = new FileContent(file.toPath(), channel);
                        CancelableSource src = new CancelableSource(monitor, content);
                        ModelBuilder bld = new ScanningModelBuilder(
                                        src,
                                        content,
                                        new GraphDocument(), monitor,
                                        (r) -> r.run(), LOADER_RP);
                        parser = new BinaryReader(src, bld);
                    } else {
                        parser = null;
                    }
                    RequestProcessor.getDefault().post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                final GraphDocument document = parser.parse();
                                if (document != null) {
                                    SwingUtilities.invokeLater(new Runnable() {
                                        @Override
                                        public void run() {
                                            component.requestActive();
                                            component.getDocument().addGraphDocument(document);
                                        }
                                    });
                                }
                            } catch (InterruptedIOException ex) {
                                DialogDisplayer.getDefault().notifyLater(
                                                new NotifyDescriptor.Message(
                                                                Bundle.MSG_LoadCancelled(file.toPath()),
                                                                NotifyDescriptor.INFORMATION_MESSAGE));
                            } catch (IOException ex) {
                                LOG.log(Level.INFO, "Error reading file: ",
                                                Exceptions.attachSeverity(ex, Level.FINE));
                                DialogDisplayer.getDefault().notifyLater(
                                                new NotifyDescriptor.Message(
                                                                Bundle.ERR_ReadingFile(file.toPath(), ex.getLocalizedMessage()),
                                                                NotifyDescriptor.ERROR_MESSAGE));
                            } finally {
                                try {
                                    channel.close();
                                } catch (IOException ex) {
                                    Exceptions.printStackTrace(ex);
                                }
                            }
                            handle.finish();
                            long stop = System.currentTimeMillis();
                            Logger.getLogger(getClass().getName()).log(Level.INFO, "Loaded in " + file + " in " + ((stop - startTime) / 1000.0) + " seconds");
                        }
                    });
                } catch (FileNotFoundException ex) {
                    Exceptions.printStackTrace(ex);
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(ImportAction.class, "CTL_ImportAction");
    }

    @Override
    protected String iconResource() {
        return "org/graalvm/visualizer/coordinator/images/import.png";
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
}
