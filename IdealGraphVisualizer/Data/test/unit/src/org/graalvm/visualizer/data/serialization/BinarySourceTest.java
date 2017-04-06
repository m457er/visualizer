/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.graalvm.visualizer.data.serialization;

import org.netbeans.junit.NbTestCase;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 */
public class BinarySourceTest extends NbTestCase {
    public BinarySourceTest(String name) {
        super(name);
    }
    
    static class Channel implements ReadableByteChannel {
        boolean open;
        List<byte[]>    content = new ArrayList<>();
        int pos;
        boolean eof;
        ByteBuffer      current = ByteBuffer.allocate(1000);
        
        Channel appendContent(byte[] arr) {
            content.add(arr);
            return this;
        }
        
        Channel newChunk() {
            byte[] chunk = new byte[current.position()];
            System.arraycopy(current.array(), 0, chunk, 0, current.position());
            content.add(chunk);
            current.clear();
            return this;
        }
        
        @Override
        public int read(ByteBuffer dst) throws IOException {
            if (content.isEmpty()) {
                if (eof) {
                    throw new EOFException();
                } else {
                    eof = true;
                    return -1;
                }
            }
            byte[] arr = content.get(0);
            if (pos < arr.length) {
                int l = Math.min(dst.remaining(), arr.length - pos);
                dst.put(arr, pos, l);
                pos += l;
                if (pos >= arr.length) {
                    content.remove(0);
                    pos = 0;
                }
                return l;
            } else {
                content.remove(0);
                pos = 0;
                return 0;
            }
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() throws IOException {
            open = false;
        }
        
        void updateDigest(MessageDigest digest) {
            for (byte[] chunk : content) {
                digest.update(chunk);
            }
        }
        
    }
    
    byte[] slice(byte[] arr, int off, int l) {
        byte []r = new byte[l];
        System.arraycopy(arr, off, r, 0, l);
        return r;
    }
    
    public void testDigestAcrossFill() throws Exception {
        Channel ch = new Channel();
        String s = "Whatever";
        byte[] bytes = s.getBytes("UTF-8");
        ch.current.putInt(bytes.length).put(slice(bytes, 0, 5));
        ch.newChunk();
        ch.current.put(slice(bytes, 5, bytes.length - 5));
        ch.newChunk();
        
        MessageDigest checkDigest = MessageDigest.getInstance("SHA-1"); // NOI18N
        ch.updateDigest(checkDigest);

        BinarySource src = new BinarySource(ch);
        src.startDigest();
        String read = src.readString();
        assertEquals(s, read);
        byte[] digest = src.finishDigest();
        
        byte[] toCheck = checkDigest.digest();
        assertTrue(Arrays.equals(toCheck, digest));
    }
}
