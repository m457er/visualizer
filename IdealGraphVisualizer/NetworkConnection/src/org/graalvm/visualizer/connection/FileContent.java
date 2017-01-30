/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.graalvm.visualizer.connection;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 *
 */
public class FileContent  implements ReadableByteChannel, CachedContent, AutoCloseable {
    private final Path      filePath;
    private FileChannel     ioDelegate;
    private boolean         eof;

    public FileContent(Path filePath) {
        this.filePath = filePath;
    }
    
    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (eof) {
            throw new EOFException();
        } else if (ioDelegate == null) {
            ioDelegate = FileChannel.open(filePath, StandardOpenOption.READ);
        }
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
    public void close() throws IOException {
        ioDelegate.close();
    }

    @Override
    public ReadableByteChannel subChannel(long start, long end) throws IOException {
        MappedByteBuffer mbb = ioDelegate.map(FileChannel.MapMode.READ_ONLY, start, end - start);
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
                closed = true;
            }
        };
    }
}
