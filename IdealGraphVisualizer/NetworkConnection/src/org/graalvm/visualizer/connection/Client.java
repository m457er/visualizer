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
package org.graalvm.visualizer.connection;

import java.io.EOFException;
import org.graalvm.visualizer.data.serialization.lazy.ScanningModelBuilder;
import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.serialization.Parser;
import org.graalvm.visualizer.data.services.GroupCallback;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.graalvm.visualizer.data.serialization.BinaryReader;
import org.graalvm.visualizer.data.serialization.BinarySource;
import org.graalvm.visualizer.data.serialization.ModelBuilder;
import org.graalvm.visualizer.data.serialization.lazy.StreamPool;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

public class Client implements Runnable {
    private static final Logger LOG = Logger.getLogger(Client.class.getName());

    private final boolean binary;
    private final SocketChannel socket;
    private final GraphDocument rootDocument;
    private final GroupCallback callback;
    private final RequestProcessor loader;

    public Client(SocketChannel socket,
                    GraphDocument rootDocument, GroupCallback callback, boolean binary, RequestProcessor loadProcessor) {
        this.callback = callback;
        this.socket = socket;
        this.binary = binary;
        this.rootDocument = rootDocument;
        this.loader = loadProcessor;
    }

    /**
     * Model operations should happen in a dedicated thread, AWT right now.
     * 
     * @param r
     */
    private void runInAWT(Runnable r) {
        SwingUtilities.invokeLater(r);
    }

    private static final AtomicInteger clientId = new AtomicInteger();

    @Override
    public void run() {
        int id = clientId.incrementAndGet();
        try {
            LOG.log(Level.FINE, "Client {0} starting for remote {1}", new Object[]{id, socket.getRemoteAddress()});
            final SocketChannel channel = socket;
            channel.configureBlocking(true);
            try (NetworkStreamContent captureChannel = new NetworkStreamContent(channel)) {
                if (binary) {
                    BinarySource bs = new BinarySource(captureChannel);
                    ModelBuilder mb = new ScanningModelBuilder(
                                    bs, captureChannel, rootDocument, callback,
                                    this::runInAWT,
                                    loader,
                                    new StreamPool());
                    BinaryReader reader = new BinaryReader(bs, mb);
                    reader.parse();
                } else {
                    new Parser(channel, null, callback).parse();
                }
            }
        } catch (EOFException ex) {
            LOG.log(Level.INFO, "Client {0} encountered end-of-file", id);
        } catch (IOException | RuntimeException ex) {
            LOG.log(Level.WARNING, "Error during processing the stream",
                            Exceptions.attachSeverity(ex, Level.INFO));
        } finally {
            try {
                socket.close();
                LOG.log(Level.FINE, "Client {0} terminated", id);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }
}
