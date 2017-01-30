/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.graalvm.visualizer.connection;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.visualizer.data.Folder;
import org.graalvm.visualizer.data.Pair;
import org.openide.modules.Places;

/**
 * Captures data from the network in a temporary file.  This class piggybacks on the main reading {@link BinaryParser}
 * loop - copies data read into the parser's input buffer. The data is received into a temp
 * buffer, then flushed to a file. Chunks from that file (sized as the origial receive buffer)
 * are memory mapped into {@link #cacheBuffers} in a hope that the OS does the memmap effectively
 * and discards pages which are not needed.
 * <p/>
 * {@link Subchannel} can be created for content received from the network and stored in the file.
 * <p/>
 * Note: the chunked mapping is not really needed; instead of that, the file can be re-mapped each time
 * a content is requested. 
 */
public class NetworkStreamContent implements ReadableByteChannel, CachedContent, AutoCloseable {
    private static final int RECEIVE_BUFFER_SIZE = 10 * 1024 * 1024;    // 10 MBytes
    
    private List<ByteBuffer>    cacheBuffers = new ArrayList<>();
    private ByteBuffer          receiveBuffer;
    private ReadableByteChannel ioDelegate;
    private boolean eof;
    private Map<Folder, Pair<Long, Long>> index = new HashMap();
    private int readBytes;
    private static final AtomicInteger contentId = new AtomicInteger();
    private File dumpFile;
    private FileChannel dumpChannel;
    private long receiveBufferOffset;
    
    public NetworkStreamContent(ReadableByteChannel ioDelegate) throws IOException {
        this.ioDelegate = ioDelegate;
        receiveBuffer = ByteBuffer.allocateDirect(RECEIVE_BUFFER_SIZE);
        File cacheDir = Places.getCacheSubdirectory(CACHE_DIRECTORY_NAME);
        dumpFile = File.createTempFile(String.format(CACHE_FILE_TEMPLATE, contentId.incrementAndGet()), CACHE_FILE_EXT, cacheDir);
        
        /*
                On Linux (MAC ?) when If StandardOpenOption.DELETE_ON_CLOSE is used,
                the file is removed right after opening, it consumes disk space, but is not visible.
                I prefer the old behaviour of File.deleteOnExit() when the machine attempts to delete the file,
                and the user knows what consumes his hard drive.
        
        */
        dumpFile.deleteOnExit();
        dumpChannel = FileChannel.open(dumpFile.toPath(), 
                StandardOpenOption.CREATE, 
                StandardOpenOption.WRITE, 
                StandardOpenOption.READ, 
                StandardOpenOption.DSYNC,
                StandardOpenOption.TRUNCATE_EXISTING);
    }
    
    private static final String CACHE_FILE_EXT = "dump"; // NOI18N
    private static final String CACHE_FILE_TEMPLATE = "igvdata_%d"; // NOI18N
    private static final String CACHE_DIRECTORY_NAME = "igv"; // NOI18N
    
    private synchronized void flushToDisk() throws IOException {
        ByteBuffer bb = receiveBuffer.duplicate();
        bb.flip();
        long startPos = dumpChannel.position();
        int len = bb.remaining();
        dumpChannel.write(bb);
        dumpChannel.force(false);
        ByteBuffer mappedBB = dumpChannel.map(FileChannel.MapMode.READ_ONLY, startPos, len);
        mappedBB.position(len);
        cacheBuffers.add(mappedBB);
        receiveBuffer.clear();
        receiveBufferOffset += len;
    }
    
    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (eof) {
            throw new EOFException();
        }
        int pos = dst.position();
        int count = ioDelegate.read(dst);
        if (count < 0) {
            eof = true;
            return count;
        }
        readBytes += count;
        
        synchronized (this) {
            // buffer in our cache:
            ByteBuffer del = dst.asReadOnlyBuffer();
            del.flip();
            del.position(pos);
            while (del.remaining() > 0) {
                if (del.remaining() < receiveBuffer.remaining()) {
                    receiveBuffer.put(del);
                } else {
                    del.limit(pos + receiveBuffer.remaining());
                    receiveBuffer.put(del);
                    flushToDisk();
                    pos = del.position();
                    del = dst.asReadOnlyBuffer();
                    del.flip();
                    del.position(pos);
                }
            }
        }
        int bufferedCount = (cacheBuffers.size()) * RECEIVE_BUFFER_SIZE + receiveBuffer.position();
        assert bufferedCount == readBytes;
        
        return count;
    }
        
    @Override
    public boolean isOpen() {
        return ioDelegate.isOpen();
    }

    @Override
    public void close() throws IOException {
        flushToDisk();
        ioDelegate.close();
    }
    
    public synchronized ReadableByteChannel subChannel(long start, long end) {
        int fromBuffer = -1;
        long pos = 0;
        long prevPos = 0;
        int startAt = 0;
        int endAt = 0;
        ByteBuffer b;
        ByteBuffer copyBuffer = null;
        ByteBuffer startBuf;
        ByteBuffer endBuf;
        List<ByteBuffer> buffers = new ArrayList<>();
        int toBuffer;
        try {

            if (start >= receiveBufferOffset) {
                copyBuffer = ByteBuffer.allocate(receiveBuffer.position());
                ByteBuffer src = (ByteBuffer)receiveBuffer.duplicate().flip();
                copyBuffer.put(src);
                startAt = (int)(start - receiveBufferOffset);
                startBuf = copyBuffer;
            } else {
                do {
                    fromBuffer++;
                    b = cacheBuffers.get(fromBuffer);
                    prevPos = pos;
                    pos += b.position();
                } while (pos < start);
                startAt = (int)(start - prevPos);
                startBuf = cacheBuffers.get(fromBuffer).asReadOnlyBuffer();
            }
            toBuffer = fromBuffer;
            pos = prevPos;
            if (end > receiveBufferOffset) {
                if (copyBuffer == null) {
                    copyBuffer = ByteBuffer.allocate(receiveBuffer.position());
                    ByteBuffer src = (ByteBuffer)receiveBuffer.duplicate().flip();
                    copyBuffer.put(src);
                }
                endAt = (int)(end - receiveBufferOffset);
                endBuf = copyBuffer;
            } else {
                do {
                    b = cacheBuffers.get(toBuffer);
                    toBuffer++;
                    prevPos = pos;
                    pos += b.position();
                } while (pos < end);
                toBuffer--;
                endAt = (int)(end - prevPos);
                if (fromBuffer == toBuffer) {
                    endBuf = startBuf;
                } else {
                    endBuf = (fromBuffer == toBuffer) ? startBuf : cacheBuffers.get(toBuffer).asReadOnlyBuffer();
                    endBuf.flip();
                }
            }

            startBuf.flip();
            startBuf.position(startAt);
            endBuf.limit(endAt);
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            throw ex;
        }
        
        byte[] contents = new byte[200];
        startBuf.mark();
        startBuf.get(contents);
        startBuf.reset();
        
        buffers.add(startBuf);
        for (int i = fromBuffer + 1; i < toBuffer; i++) {
            b = cacheBuffers.get(i).asReadOnlyBuffer();
            buffers.add((ByteBuffer)b.flip());
        }
        if (startBuf != endBuf) {
            buffers.add(endBuf);
        }
        // sanity check:
        for (ByteBuffer x : buffers) {
            if (x.position() == 0) {
                System.err.println("0 position");
            }
        }
        return new Subchannel(buffers.iterator());
    }
    
    private static class Subchannel implements ReadableByteChannel {
        private Iterator<ByteBuffer>    buffers;
        private ByteBuffer current;
        private boolean eof;

        public Subchannel(Iterator<ByteBuffer> buffers) {
            this.buffers = buffers;
            this.current = buffers.next();
        }
        
        @Override
        public int read(ByteBuffer dst) throws IOException {
            if (eof) {
                throw new EOFException();
            }
            do {
                if (current == null || current.position() == 0) {
                    if (!buffers.hasNext()) {
                        eof = true;
                        // clear to allow GC
                        buffers = null;
                        current = null;
                        return -1;
                    }
                    current = buffers.next();
                }
            } while (current.position()== 0);
            int cnt = 0;
            if (current.remaining() <= dst.remaining()) {
                cnt = current.remaining();
                dst.put(current);
                current = null;
                return cnt;
            }
            cnt = dst.remaining();
            ByteBuffer from = current.duplicate();
            from.limit(from.position() + dst.remaining());
            dst.put(from);
            current.position(from.limit());
            return cnt;
        }

        @Override
        public boolean isOpen() {
            return !eof;
        }

        @Override
        public void close() throws IOException {
            eof = true;
        }
    }
}
