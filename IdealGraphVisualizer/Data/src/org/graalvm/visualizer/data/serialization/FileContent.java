/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.data.serialization;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.visualizer.data.serialization.lazy.CachedContent;

/**
 * Implementation of {@link CachedContent} which works with files. Channels are
 * allocated over memory-mapped buffer from the file.
 */

// PENDING: limit number of memory mappings; make one shared mapping up to the current
// file size, since mappings are freed only when the buffer is GCed, which is unreliable.
public class FileContent  implements ReadableByteChannel, CachedContent, AutoCloseable {
    private final Path      filePath;
    private FileChannel     ioDelegate;
    private boolean         eof;
    /**
     * Self-opened channels will be closed by close().
     */
    private boolean         selfOpened;

    public FileContent(Path filePath, FileChannel channel) {
        this.filePath = filePath;
        this.ioDelegate = channel;
    }
    
    private synchronized void openDelegate() throws IOException {
        if (ioDelegate == null || !ioDelegate.isOpen()) {
            ioDelegate = FileChannel.open(filePath, StandardOpenOption.READ);
            selfOpened = true;
        }
    }
    
    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (eof) {
            throw new EOFException();
        }
        openDelegate();
        int count = ioDelegate.read(dst);
        if (count < 0) {
            eof = true;
            return count;
        }
        return count;
    }

    @Override
    public boolean isOpen() {
        return ioDelegate.isOpen();
    }

    @Override
    public synchronized void close() throws IOException {
        if (selfOpened) {
            ioDelegate.close();
        }
        ioDelegate = null;
    }
    
    private AtomicInteger subchannelCount = new AtomicInteger();
    
    private void subchannelClosed() throws IOException {
        if (subchannelCount.decrementAndGet() == 0) {
            close();
        }
    }

    @Override
    public ReadableByteChannel subChannel(long start, long end) throws IOException {
        openDelegate();
        MappedByteBuffer mbb = ioDelegate.map(FileChannel.MapMode.READ_ONLY, start, end - start);
        subchannelCount.incrementAndGet();
        return new ReadableByteChannel() {
            private boolean closed;
            private boolean eof;
            
            @Override
            public int read(ByteBuffer dst) throws IOException {
                if (mbb.remaining() == 0) {
                    eof = true;
                    return -1;
                } else if (eof) {
                    throw new EOFException();
                } else if (closed) {
                    throw new ClosedChannelException();
                }
                if (dst.remaining() < mbb.remaining()) {
                    ByteBuffer b = mbb.duplicate();
                    int count = dst.remaining();
                    int pos = mbb.position() + count;
                    b.limit(pos);
                    dst.put(b);
                    mbb.position(pos);
                    return count;
                } else {
                    int count = mbb.remaining();
                    dst.put(mbb);
                    return count;
                }
            }

            @Override
            public boolean isOpen() {
                return !closed;
            }

            @Override
            public void close() throws IOException {
                boolean c = closed;
                closed = true;
                if (!closed) {
                    FileContent.this.subchannelClosed();
                }
            }
        };
    }
}
