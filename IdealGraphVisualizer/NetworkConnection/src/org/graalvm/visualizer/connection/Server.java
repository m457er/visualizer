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

import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.services.GroupCallback;
import org.graalvm.visualizer.settings.Settings;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

public class Server implements PreferenceChangeListener {
    /**
     * Maximum parallel network reading threads.
     */
    private static final int MAX_PARALLEL_READS = 50;

    private final boolean binary;
    private ServerSocketChannel serverSocket;
    private final GraphDocument rootDocument;
    private final GroupCallback callback;
    private int port;
    private Runnable serverRunnable;

    /**
     * Request processor which reads network data and stores to disk files.
     */
    private static final RequestProcessor NETWORK_RP = new RequestProcessor(Server.class.getName(), MAX_PARALLEL_READS);

    /**
     * Lazy-loading RP.
     */
    public static final RequestProcessor LOADER_RP = new RequestProcessor(Client.class);

    public Server(GraphDocument rootDocument, GroupCallback callback, boolean binary) {
        this.binary = binary;
        this.rootDocument = rootDocument;
        this.callback = callback;
        initializeNetwork();
        Settings.get().addPreferenceChangeListener(this);
    }

    @Override
    public void preferenceChange(PreferenceChangeEvent e) {

        int curPort = Integer.parseInt(Settings.get().get(binary ? Settings.PORT_BINARY : Settings.PORT, binary ? Settings.PORT_BINARY_DEFAULT : Settings.PORT_DEFAULT));
        if (curPort != port) {
            initializeNetwork();
        }
    }

    @NbBundle.Messages({
                    "ERR_CannotListen=Could not create server. Listening for incoming binary data is disabled.",
                    "# 0 - error description",
                    "ERR_ProcessingAccept=Error listening for connections: {0}"
    })
    private void initializeNetwork() {

        int curPort = Integer.parseInt(Settings.get().get(binary ? Settings.PORT_BINARY : Settings.PORT, binary ? Settings.PORT_BINARY_DEFAULT : Settings.PORT_DEFAULT));
        this.port = curPort;
        try {
            serverSocket = ServerSocketChannel.open();
            serverSocket.bind(new InetSocketAddress(curPort));
        } catch (IOException ex) {
            NotifyDescriptor message = new NotifyDescriptor.Message(
                            Bundle.ERR_CannotListen(), NotifyDescriptor.ERROR_MESSAGE);
            DialogDisplayer.getDefault().notifyLater(message);
            return;
        }

        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                while (true) {
                    try {
                        SocketChannel clientSocket = serverSocket.accept();
                        if (serverRunnable != this) {
                            clientSocket.close();
                            return;
                        }
                        NETWORK_RP.post(new Client(clientSocket, rootDocument, callback, binary, LOADER_RP), 0, Thread.MAX_PRIORITY);
                    } catch (IOException ex) {
                        serverSocket = null;
                        NotifyDescriptor message = new NotifyDescriptor.Message(
                                        Bundle.ERR_ProcessingAccept(ex.getLocalizedMessage()), NotifyDescriptor.ERROR_MESSAGE);
                        DialogDisplayer.getDefault().notifyLater(message);
                        return;
                    }
                    // break;
                }
            }
        };

        serverRunnable = runnable;

        RequestProcessor.getDefault().post(runnable, 0, Thread.MAX_PRIORITY);
    }
}
