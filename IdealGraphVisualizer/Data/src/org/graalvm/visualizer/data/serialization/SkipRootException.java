/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.graalvm.visualizer.data.serialization;

/**
 * If thhrown from {@link ModelBuilder} methods, causes the {@link BinaryReader} to skip up to the
 * passed `end' position. The next root element (group or graph) is then processed. It is the caller
 * responsibility to provide the correct positioning information.
 */
public class SkipRootException extends RuntimeException {
    private final long start;
    private final long end;
    private ConstantPool    readFromPool;

    public SkipRootException(long start, long end, ConstantPool pool) {
        this.start = start;
        this.end = end;
        this.readFromPool = pool;
    }
    
    public ConstantPool getConstantPool() {
        return readFromPool;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }
    
    public String toString() {
        return "Skip[from " + start + " to " + end + ", pool=" + Integer.toHexString(readFromPool == null ? 0 : System.identityHashCode(readFromPool));
    }
}
