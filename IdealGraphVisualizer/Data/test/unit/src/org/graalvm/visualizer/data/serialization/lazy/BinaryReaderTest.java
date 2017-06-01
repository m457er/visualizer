/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.graalvm.visualizer.data.serialization.lazy;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.serialization.Builder;
import org.graalvm.visualizer.data.serialization.ModelBuilder;
import org.graalvm.visualizer.data.serialization.ParseMonitor;
import org.graalvm.visualizer.data.services.GroupCallback;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor.Task;

/**
 *
 * @author sdedic
 */
public class BinaryReaderTest extends BinaryDataTestBase {

    public BinaryReaderTest(String name) {
        super(name);
    }
    
    class GroupCountingBuilder extends ModelBuilder {
        volatile int groupStart;
        volatile int groupEnd;
        volatile int groupContents;
        
        volatile int graphStart;
        volatile int graphEnd;
        volatile int graphContents;
        volatile boolean endCalled;
        
        public GroupCountingBuilder(GraphDocument rootDocument, Executor modelExecutor, GroupCallback callback, ParseMonitor monitor) {
            super(rootDocument, modelExecutor, callback, monitor);
        }

        @Override
        public void end() {
            super.end(); 
            endCalled = true;
        }

        @Override
        public void endGroup() {
            groupEnd++;
            super.endGroup();
        }

        @Override
        public void startGroupContent() {
            groupContents++;
            super.startGroupContent();
        }

        @Override
        public Group startGroup() {
            groupStart++;
            return super.startGroup();
        }

        @Override
        public InputGraph endGraph() {
            graphEnd++;
            return super.endGraph();
        }

        @Override
        public InputGraph startGraph(String title) {
            graphStart++;
            return super.startGraph(title);
        }

        @Override
        public void startGraphContents(InputGraph g) {
            graphContents++;
            super.startGraphContents(g);
        }
    }
    
    GroupCountingBuilder countingBuilder;
    
    private boolean countGroups;
    
    protected Builder createScanningTestBuilder() {
        if (!countGroups) {
            return super.createScanningTestBuilder();
        }
        return countingBuilder = new GroupCountingBuilder(checkDocument, this::run, null, null);
    }
    
    /**
     * Checks that groups are closed on EOF
     */
    public void testDanglingGroups() throws Exception {
        countGroups = true;
        loadData("bigv-1.0.bgv");
        
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        // freeze in an inner group
        file.freezeAt = 85000;
        Task t = PARALLEL_LOAD.post(() -> {
            try {
                // will freeze at 85000
                reader.parse();
            } catch (IOException ex) {
                thrown.set(ex);
            }
        });
        // wait for reader
        file.frozen.acquire();
        assertTrue(countingBuilder.graphStart > countingBuilder.groupEnd);
        file.eof = true;
        // release reader and let it get the EOF
        file.condition.release();
        
        t.waitFinished();
        // no exception propagated from the reader
        assertNull(thrown.get());
        assertSame(countingBuilder.graphStart, countingBuilder.graphEnd);
        assertSame(countingBuilder.groupStart, countingBuilder.groupEnd);
    }
    
    public void testGroupsAreClosed() throws Exception {
        countGroups = true;
        loadData("bigv-1.0.bgv");
        
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        // freeze in an inner group
        file.freezeAt = 85000;
        Task t = PARALLEL_LOAD.post(() -> {
            try {
                // will freeze at 85000
                reader.parse();
            } catch (IOException ex) {
                thrown.set(ex);
            }
        });
        // wait for reader
        file.frozen.acquire();
        assertTrue(countingBuilder.graphStart > countingBuilder.groupEnd);
        file.throwException = new IOException("Interrupted");
        // release reader and let it get the EOF
        file.condition.release();
        
        t.waitFinished();
        // no exception propagated from the reader
        assertNotNull(thrown.get());
        assertSame(countingBuilder.groupStart, countingBuilder.groupEnd);
    }
    
    public void testGraphsAreClosed() throws Exception {
        countGroups = true;
        loadData("bigv-1.0.bgv");
        
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        // freeze in an inner group
        file.freezeAt = 85000;
        Task t = PARALLEL_LOAD.post(() -> {
            try {
                // will freeze at 85000
                reader.parse();
            } catch (IOException ex) {
                thrown.set(ex);
            }
        });
        // wait for reader
        file.frozen.acquire();
        assertTrue(countingBuilder.graphStart > countingBuilder.groupEnd);
        file.throwException = new IOException("Interrupted");
        // release reader and let it get the EOF
        file.condition.release();
        
        t.waitFinished();
        // no exception propagated from the reader
        assertNotNull(thrown.get());
        assertSame(countingBuilder.graphStart, countingBuilder.graphEnd);
    }
}
