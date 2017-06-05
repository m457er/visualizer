/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.graalvm.visualizer.data.serialization.lazy;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Function;
import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.serialization.BinaryReader;
import org.graalvm.visualizer.data.serialization.BinarySource;
import org.graalvm.visualizer.data.serialization.Builder;
import org.graalvm.visualizer.data.serialization.FileContent;
import org.graalvm.visualizer.data.serialization.ParseMonitor;
import org.graalvm.visualizer.data.serialization.SkipRootException;
import org.graalvm.visualizer.data.services.GroupCallback;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 *
 * @author sdedic
 */
public class BinaryDataTestBase extends NbTestCase {
    protected static final RequestProcessor RP = new RequestProcessor(DelayedLoadTest.class);
    protected  static final RequestProcessor PARALLEL_LOAD = new RequestProcessor(DelayedLoadTest.class.getName(), 100);
    protected  BinaryReader reader;
    
    public BinaryDataTestBase(String name) {
        super(name);
    }
    
    protected class TestBuilder extends ScanningModelBuilder {
        public int groupCount = -1;
        public Consumer<Group> groupConsumer;
        
        public StreamIndex index() {
            return super.index;
        }
        
        public TestBuilder(BinarySource dataSource, CachedContent content, GraphDocument rootDocument, GroupCallback callback, ParseMonitor monitor, Executor modelExecutor, ScheduledExecutorService fetchExecutor, StreamPool initialPool) {
            super(dataSource, content, rootDocument, callback, monitor, modelExecutor, fetchExecutor, initialPool);
        }

        @Override
        public void startGroupContent() {
            super.startGroupContent();
            if (groupLevel == 1 && groupCount >= 0 && --groupCount == 0) {
                groupConsumer.accept((Group)folder());
            }
        }
    }
    
    protected class TestFileContent extends FileContent {
        public volatile long freezeAt = -1;
        private long offset;
        public Semaphore condition = new Semaphore(0);
        public Semaphore frozen = new Semaphore(0);

        public Semaphore subchannelPermits = new Semaphore(0);
        public Semaphore subchannelOpens = new Semaphore(0);
        public boolean playSemaphores;
        public Function<long[], ReadableByteChannel> channelFactory;
        public volatile boolean eof;
        public volatile Throwable throwException;
        
        public TestFileContent(Path filePath, FileChannel channel) {
            super(filePath, channel);
        }
        
        
        @Override
        public int read(ByteBuffer dst) throws IOException {
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
                int bytes = super.read(copy);
                if (bytes == -1) {
                    return bytes;
                }
                dst.position(dst.position() + bytes);
                offset += bytes;
                return bytes;
            }
        }

        void permitSubchannelAndWait() throws Exception {
            subchannelPermits.release();
            subchannelOpens.acquire();
        }

        @Override
        public ReadableByteChannel subChannel(long start, long end) throws IOException {
            if (playSemaphores) {
                try {
                    subchannelPermits.acquire();
                } catch (InterruptedException ex) {
                    throw new InterruptedIOException();
                }
                subchannelOpens.release();
            }
            if (channelFactory != null) {
                return channelFactory.apply(new long[] { start, end });
            } else {
                return super.subChannel(start, end);
            }
        }
        
        ReadableByteChannel superSubChannel(long start, long end) throws IOException {
            return super.subChannel(start, end);
        }
    }
    
    protected TestFileContent file;
    protected TestBuilder mb;
    protected GraphDocument checkDocument;
    protected BinarySource scanSource;
    protected StreamPool streamPool = new StreamPool();
    
    protected Builder createScanningTestBuilder() {
        mb = new TestBuilder(scanSource, file, checkDocument, 
                null, null,
                this::run, RP, streamPool);
        return mb;
    }
    
    protected void loadData(String dFile) throws Exception {
        URL bigv = DelayedLoadTest.class.getResource(dFile);
        File f = new File(bigv.toURI());
        loadData(f);
    }
    
    protected void loadData(File f) throws Exception {
        file = new TestFileContent(f.toPath(), null);
        checkDocument = new GraphDocument();
        scanSource = new BinarySource(file);
        Builder b = createScanningTestBuilder();
        reader = new BinaryReader(scanSource, b);
    }
    
    static {
        LoadSupport._testUseWeakRefs = true;
    }
    
    protected void run(Runnable r) {
        r.run();
    }
    
    class FreezeChannel extends org.graalvm.visualizer.data.serialization.FreezeChannel {
        public FreezeChannel(long start, long end, long freezeAt) throws IOException {
            super(file.superSubChannel(start, end), start, freezeAt);
        }
    }
    
    protected FreezeChannel freezeChannel;
    
}
