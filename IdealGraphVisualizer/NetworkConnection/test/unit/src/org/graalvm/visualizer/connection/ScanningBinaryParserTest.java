/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.graalvm.visualizer.connection;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import static junit.framework.TestCase.assertEquals;
import org.graalvm.visualizer.data.Folder;
import org.graalvm.visualizer.data.FolderElement;
import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.serialization.BinaryParser;
import org.graalvm.visualizer.data.serialization.BinarySource;
import org.graalvm.visualizer.data.serialization.ConstantPool;
import org.graalvm.visualizer.data.services.GroupCallback;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 *
 */
public class ScanningBinaryParserTest extends NbTestCase {
    private static final String DIR_NAME = "/space/src/igv/visualizer/data/2";
    
    public ScanningBinaryParserTest(String name) {
        super(name);
    }
    
    private static class TrackConstantPool extends StreamPool {
        protected List<CPData>    cpReads = new ArrayList<>();

        public TrackConstantPool() {
        }

        public TrackConstantPool(List<Object> data, int generation, List<CPData> cpReads) {
            super(generation, data);
        }
        
        @Override
        public Object get(int index, long where) {
            Object o = super.get(index, where);
            cpReads.add(new CPData(true, index, o));
            return o;
        }

        @Override
        public synchronized Object addPoolEntry(int index, Object obj, long where) {
            Object o = super.addPoolEntry(index, obj, where);
            cpReads.add(new CPData(false, index, o));
            return o;
        }
        
        void reset() {
            cpReads.clear();
        }
        
        @Override
        protected StreamPool create(List<Object> data) {
            return new TrackConstantPool(data, generation + 1, cpReads);
        }
    }
    
    private static class TrackConstantPool2 extends TrackConstantPool {
        private ConstantPool    delegate;

        public TrackConstantPool2(ConstantPool delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object get(int index, long where) {
            Object o = delegate.get(index, where);
            cpReads.add(new CPData(true, index, o));
            return o;
        }

        @Override
        public synchronized Object addPoolEntry(int index, Object obj, long where) {
            Object o = delegate.addPoolEntry(index, obj, where);
            cpReads.add(new CPData(false, index, o));
            return o;
        }
        
        void reset() {
            cpReads.clear();
        }

        @Override
        public int size() {
            return delegate.size(); 
        }
        
        
    }
    
    AtomicInteger groupIndex = new AtomicInteger();
    
    void verifyScanningAndBinary() {
        assertEquals(mockScanning.start, mockBinary.start);
        assertEquals(mockScanning.end, mockBinary.end);
        
        TrackConstantPool tPoolB = (TrackConstantPool)mockBinary.getConstantPool();
        TrackConstantPool tPoolS = (TrackConstantPool)mockScanning.getConstantPool();
        assertEquals(tPoolB.cpReads.size(), tPoolS.cpReads.size());
        
        int max = tPoolS.cpReads.size();
        for (int i = 0; i < max; i++) {
            CPData d1 = tPoolS.cpReads.get(i);
            CPData d2 = tPoolB.cpReads.get(i);
            
            assertEquals("inconsistent index, operation " + i + ", index = " + d2.index + " / scanIndex = " + d1.index, d2.index, d1.index);
            assertEquals("inconsistent read/write, operation " + i + ", index = " + d1.index, d2.read, d1.read);
            

            assertEquals("Inconsistent data on operation " + i + ", index " + d1.index, d2.data.toString(), d1.data.toString());
        }
    }
    
    private MockScanningParser   mockScanning;
    private MockBinaryParser     mockBinary;
    private NetworkStreamContent netContent;
    
    private Semaphore      waitScanning = new Semaphore(0);
    private Semaphore      waitBinary = new Semaphore(0);
    
    private static class CPData {
        private boolean read;
        private int index;
        private Object data;

        public CPData(boolean read, int index, Object data) {
            this.read = read;
            this.index = index;
            this.data = data;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final CPData other = (CPData) obj;
            if (this.read != other.read) {
                return false;
            }
            if (this.index != other.index) {
                return false;
            }
            if (!Objects.equals(this.data, other.data)) {
                return false;
            }
            return true;
        }
    }
    
    private volatile Group binaryGroup;
    
    private class MockScanningParser extends ScanningBinaryParser implements Runnable {
        BinarySource dSource;
        long start;
        long end;
        
        public MockScanningParser(BinarySource source, NetworkStreamContent content, TrackConstantPool pool, GraphDocument rootDocument) {
            super(source, content, pool, rootDocument, null);
            dSource = source;
        }
        
        @Override
        protected void closeGroup(Group g) throws IOException {
            super.closeGroup(g);
            if (!(g.getParent() instanceof GraphDocument)) {
                return;
            }
            end = dSource.getMark();
            try {
                try {
                    verifyScanningAndBinary();
                } catch (AssertionError ex) {
                    System.err.println("Occurred at group index " + groupIndex);
                    throw ex;
                }
            } finally {
                ((TrackConstantPool)getConstantPool()).reset();
                waitScanning.release();
            }
        }

        @Override
        protected void beginGroup(Folder parent) throws IOException {
            if (!(parent instanceof GraphDocument)) {
                super.beginGroup(parent);
                return;
            }
            try {
                waitBinary.acquire();
            } catch (InterruptedException ex) {
                Exceptions.printStackTrace(ex);
            }
            start = dSource.getMark();
            super.beginGroup(parent);
        }

        public void run() {
            try {
                parse();
            } catch (IOException | AssertionError ex) {
                compareError = ex;
                ex.printStackTrace();
            }
        }
    }
    
    private volatile Throwable compareError;
       
    private class MockBinaryParser extends BinaryParser {
        long    start;
        long    end;
        Group   closingGroup;
        BinarySource  dSource;
        
        public MockBinaryParser(BinarySource dataSource, TrackConstantPool pool, GraphDocument rootDocument, GroupCallback callback) {
            super(dataSource, pool, rootDocument, callback);
            this.dSource = dataSource;
        }

        @Override
        protected void registerGraph(Folder parent, FolderElement graph) {
        }

        @Override
        protected void beginGroup(Folder parent) throws IOException {
            if (parent instanceof GraphDocument) {
                start = dSource.getMark();
            }
            super.beginGroup(parent); 
        }
        
        private void rethrowCompareError() throws IOException {
            if (compareError != null) {
                if (compareError instanceof IOException) {
                    throw (IOException)compareError;
                } else if (compareError instanceof Error) {
                    throw (Error)compareError;
                }
            }
        }

        @Override
        protected void closeGroup(Group g) throws IOException {
            super.closeGroup(g);
            if (g.getParent() instanceof GraphDocument) {
                binaryGroup = g;
                System.err.println("Parsed: " + g.getName());
                end = dSource.getMark();
                waitBinary.release();
                rethrowCompareError();
                try {
                    waitScanning.acquire();
                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex);
                }
                rethrowCompareError();
                groupIndex.incrementAndGet();
                ((TrackConstantPool)getConstantPool()).reset();
            }
        }
    }
    
    private class PartialParserRunner implements Runnable {
        ScanningBinaryParser scanner;

        public PartialParserRunner(ScanningBinaryParser scanner) {
            this.scanner = scanner;
        }
        
        
        
        public void run() {
            try {
                doRun();
            } catch (IOException | AssertionError ex) {
                compareError = ex;
                ex.printStackTrace();
            }
        }
        
        private void doRun() throws IOException {
            Collection<Group>   toCheck = scanner.groups();
            for (Group g : toCheck) {
                try {
                    waitBinary.acquire();
                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex);
                }
                GraphDocument root = new GraphDocument();
                long[] range = scanner.getRange(g);
                assertNotNull(range);
                ConstantPool cp = scanner.initialPool(g).clone();
                TrackConstantPool2 track = new TrackConstantPool2(cp);

                ScanningBinaryParser.PartialParser partial;
                synchronized (this) {
                    partial = scanner.new PartialParser(
                            range[0], range[1],
                            track,
                            root, g);
                }
                partial.parse();
                verify(track);
                track.reset();
                if (g instanceof LazyGroup) {
                    g.removeAll();
                }
                waitScanning.release();
            }
        }

        void verify(TrackConstantPool2 track) {
            TrackConstantPool tPoolB = (TrackConstantPool)mockBinary.getConstantPool();
            TrackConstantPool tPoolS = track;
//            assertEquals(tPoolB.cpReads.size(), tPoolS.cpReads.size());

            int max = tPoolS.cpReads.size();
            for (int i = 0; i < max; i++) {
                CPData d1 = tPoolS.cpReads.get(i);
                CPData d2 = tPoolB.cpReads.get(i);

                assertEquals("inconsistent index, operation " + i + ", index = " + d2.index + " / scanIndex = " + d1.index, d2.index, d1.index);
                assertEquals("inconsistent read/write, operation " + i + ", index = " + d1.index, d2.read, d1.read);
                assertEquals("Inconsistent data on operation " + i + ", index " + d1.index, d2.data.toString(), d1.data.toString());
            }
        }
    }
    
    private void checkReadFile(File f) throws IOException {
        Path p = f.toPath();
        FileContent fc = new FileContent(p);
        // first scan the whole file using scanning binary parser
        GraphDocument rootDocument = new GraphDocument();
        ScanningBinaryParser sbp = new ScanningBinaryParser(fc, rootDocument, null);
        sbp.parse();
        
        PartialParserRunner runner = new PartialParserRunner(sbp);
        RequestProcessor.getDefault().post(runner);
        
        GraphDocument checkDocument = new GraphDocument();
        FileChannel fch = FileChannel.open(f.toPath(), StandardOpenOption.READ);
        this.mockBinary = new MockBinaryParser(new BinarySource(fch), new TrackConstantPool(), checkDocument, null);
        GraphDocument r = mockBinary.parse();
        assertNotNull(r);
    }
    
    public void xtestScanningParserReadsTheSame() throws Exception {
        Path p = Paths.get(DIR_NAME);
        for (File f : p.toFile().listFiles()) {
            System.err.println("Checking file: " + f);
            
            
            FileChannel fch = FileChannel.open(f.toPath(), StandardOpenOption.READ);
            FileChannel fch2 = FileChannel.open(f.toPath(), StandardOpenOption.READ);
            GraphDocument checkDocument = new GraphDocument();
            BinarySource scanSource = new BinarySource(fch2);
            MockScanningParser msp = new MockScanningParser(scanSource, null, new TrackConstantPool(), checkDocument);
            this.mockScanning = msp;
            RequestProcessor.getDefault().post(msp);
            GraphDocument rootDocument = new GraphDocument();
            this.mockBinary = new MockBinaryParser(new BinarySource(fch), new TrackConstantPool(), rootDocument, null);
            GraphDocument r = mockBinary.parse();
            assertNotNull(r);
        }
    }
    
    public void testPartialParserSame() throws Exception {
        Path p = Paths.get(DIR_NAME);
        for (File f : p.toFile().listFiles()) {
            System.err.println("Chekcing file: " + f);
            checkReadFile(f);
        }
    }
}
