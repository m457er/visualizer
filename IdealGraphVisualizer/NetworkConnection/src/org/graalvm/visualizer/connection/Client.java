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
import org.graalvm.visualizer.data.serialization.Parser;
import org.graalvm.visualizer.data.services.GroupCallback;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import javax.swing.SwingUtilities;
import org.graalvm.visualizer.data.serialization.BinaryReader;
import org.graalvm.visualizer.data.serialization.BinarySource;
import org.graalvm.visualizer.data.serialization.ConstantPool;
import org.graalvm.visualizer.data.serialization.ModelBuilder;
import org.openide.util.Exceptions;

public class Client implements Runnable {
    private final boolean binary;
    private final SocketChannel socket;
    private final GraphDocument rootDocument;
    private final GroupCallback callback;
    
    public Client(SocketChannel socket, GraphDocument rootDocument, GroupCallback callback, boolean  binary) {
        this.callback = callback;
        this.socket = socket;
        this.binary = binary;
        this.rootDocument = rootDocument;
    }
    
    private void runInAWT(Runnable r) {
        SwingUtilities.invokeLater(r);
    }
    
    BinaryReader reader;
    
    ConstantPool readerPool() {
        return reader.getConstantPool();
    }
    
    @Override
    public void run() {
        try {
            final SocketChannel channel = socket;
            channel.configureBlocking(true);
            try (NetworkStreamContent captureChannel = new NetworkStreamContent(channel)) {
                if (binary) {
//                    new BinaryParser(new BinarySource(captureChannel), (ParseMonitor)null, rootDocument, callback).parse();
                    //new ScanningBinaryParser(captureChannel, rootDocument, callback).parse();
                    BinarySource bs = new BinarySource(captureChannel);
                    
                    ModelBuilder mb = new ScanningModelBuilder(
                            bs, captureChannel, rootDocument, callback,
                            this::runInAWT, this::readerPool
                    );
                    /*
                    ModelBuilder mb = new ModelBuilder(
                            rootDocument, this::runInAWT, callback, null);
                    */
                    reader = new BinaryReader(bs, new StreamPool());
                    reader.parse(mb);
                } else {
                    new Parser(channel, null, callback).parse();
                }
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            System.err.println("Client terminating, constant pool entries: "+ ConstantPool.totalEntries.get());
            try {
                socket.close();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }
}
