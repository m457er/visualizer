/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.graalvm.visualizer.connection;

import java.util.BitSet;
import java.util.List;
import org.graalvm.visualizer.data.serialization.ConstantPool;

/**
 *
 */
public class StreamPool extends ConstantPool {
    private List<Object>  originalData;
    private final BitSet itemRead = new BitSet();
    // for testing
    protected final int generation;
    private int entriesAdded;

    public StreamPool() {
        this.generation = 0;
    }

    public StreamPool(int generation, List<Object> data) {
        super(data);
        this.generation = generation;
    }
    
    protected StreamPool create(List<Object> data) {
        return new StreamPool(generation + 1, data);
    }
    
    @Override
    public Object get(int index, long where) {
        itemRead.set(index);
        return super.get(index, where);
    }

    @Override
    public synchronized Object addPoolEntry(int index, Object obj, long where) {
        if (size() > index) {
            if (itemRead.get(index)) {
                if (originalData == null) {
                    originalData = snapshot();
                }
                itemRead.clear();
            }
        }
        return super.addPoolEntry(index, obj, where);
    }

    public synchronized ConstantPool forkIfNeeded() {
        totalEntries.addAndGet(entriesAdded);
        entriesAdded = 0;
        if (originalData != null) {
            ConstantPool r = swap(originalData);
            originalData = null;
            itemRead.clear();
            return r;
        } else {
            return this;
        }
    }
    
}
