/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.graalvm.visualizer.data.serialization;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.Semaphore;
import org.openide.util.Exceptions;

/**
 *
 * @author sdedic
 */
public class FreezeChannel implements ReadableByteChannel {
    public volatile long freezeAt;
    private ReadableByteChannel delegate;
    private long offset;
    public Semaphore condition = new Semaphore(0);
    public Semaphore frozen = new Semaphore(0);
    public Throwable throwException;
    public volatile boolean eof;

    public FreezeChannel(ReadableByteChannel delegate, long start, long freezeAt) throws IOException {
        this.delegate = delegate;
        this.freezeAt = freezeAt;
        this.offset = start;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (eof) {
            throw new EOFException();
        }
        int max = dst.remaining();
        if (max == 0) {
            // sorry
            return 0;
        }
        if (offset <= freezeAt && offset + max > freezeAt) {
            max = (int)(freezeAt - offset);
        }
        if (max == 0) {
            try {
                freezeAt = -1;
                frozen.release();
                condition.acquire();
            } catch (InterruptedException ex) {
                Exceptions.printStackTrace(ex);
            }
            if (throwException != null) {
                Throwable t = throwException;
                throwException = null;
                if (t instanceof IOException) {
                    throw (IOException)t;
                } else if (t instanceof RuntimeException) {
                    throw (RuntimeException)t;
                } else if (t instanceof Error) {
                    throw (Error)t;
                }
            } else if (eof) {
                throw new EOFException();
            }
            int res = read(dst);
            // offset already updated by recursive call
            return res;
        } else {
            ByteBuffer copy = dst.duplicate();
            copy.limit(copy.position() + max);
            int bytes = delegate.read(copy);
            if (bytes == -1) {
                return bytes;
            }
            dst.position(dst.position() + bytes);
            offset += bytes;
            return bytes;
        }
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
