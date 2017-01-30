/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.graalvm.visualizer.data.serialization;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dictionary of values found in the stream. Serves also as a factory to
 * create additional instances of ConstantPool - the feature is used 
 * by subclass(es) to supply custom implementations.
 */
public class ConstantPool {
    private List<Object>  data = new ArrayList<>();
    private int entriesAdded;
    private BinaryReader    replaceInReader;
    public static AtomicInteger totalEntries = new AtomicInteger();
    
    public ConstantPool() {
    }
    
    void withReader(BinaryReader r) {
        this.replaceInReader = r;
    }
    
    /**
     * Adds an entry to the pool, potentially replacing and existing value.
     * If index is larger than constant pool size, the pool grows to accommodate
     * the index.
     * 
     * @param index entry index
     * @param obj the value
     * @param where stream position which introduced the value. For diagnostics.
     * @return value introduced to the pool.
     */
    public synchronized Object addPoolEntry(int index, Object obj, long where) {
        while (data.size() <= index) {
            data.add(null);
        }
        entriesAdded++;
        data.set(index, obj);
        return obj;
    }

    /**
     * Retrieves an entry from the pool.
     * The index must already exist.
     * 
     * @param index index to fetch
     * @param where stream position that accesses the pool. Diagnostics.
     * @return value in the pool.
     */
    public Object get(int index, long where) {
        return internalGet(index);
    }
    
    /**
     * The current pool size. The greatest index used plus 1.
     * @return pool size.
     */
    public int size() {
        return data.size();
    }
    
    /**
     * Clones the current pool's data. 
     * @return copy of storage
     */
    protected final List<Object>  snapshot() {
        return new ArrayList<>(data);
    }
    
    /**
     * Forks the constant pool, swaps data. Replaces this pool's data with the 
     * `replacementData'. Original pool contents is retuned in a new instance
     * of ConstantPool.
     * <p/>
     * The method effectively forks the constant pool, retains some previous snapshot 
     * in <b>this instance</b> (assuming there are already some refereces to it),
     * and the current state is returned as a new instance of ConstantPool.
     * 
     * @param replacementData new data for this constant pool, preferrably made
     * by {@link #snapshot}.
     * 
     * @return new ConstantPool instance with the current data
     */
    protected synchronized final ConstantPool swap(List<Object> replacementData) {
        ConstantPool copy = create(this.data);
        this.data = replacementData;
        if (replaceInReader != null) {
            replaceInReader.replacePool(copy);
        }
        return copy;
    }
    
    /**
     * Acecssor method for superclass, which just accesses the data.
     * @param index
     * @return 
     */
    protected final Object internalGet(int index) {
        return data.get(index);
    }
    
    /**
     * Initializes the instance with the passed data
     * @param data initial data
     */
    protected ConstantPool(List<Object> data) {
        this.data = data;
    }

    /**
     * Makes a copy of the pool contents.
     * @return new instance of the pool, with identical data as this instance.
     */
    public final ConstantPool clone() {
        return create(new ArrayList<>(data));
    }
    
    /**
     * Creates a new instance of ConstantPool with the passed data.
     * This method should be used in favour of new operator, to allow custom subclasses
     * to provide their own implementation for the new ConstantPool.
     * 
     * @param data the initial data
     * @return new instance of ConstantPool.
     */
    protected ConstantPool create(List<Object> data) {
        return new ConstantPool(data);
    }
    
    /**
     * Reinitializes the constant pool. May return a different fresh instance.
     * @return the reinitialized instance.
     */
    public ConstantPool restart() {
        this.data.clear();
        return this;
    }
}
