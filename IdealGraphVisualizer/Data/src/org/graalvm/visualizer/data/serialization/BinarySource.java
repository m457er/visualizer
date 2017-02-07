/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */
package org.graalvm.visualizer.data.serialization;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import static org.graalvm.visualizer.data.serialization.StreamUtils.maybeIntern;

/**
 * Performs basic decoding, manages the input buffer. Reports the current position.
 */
public class BinarySource {
    static final byte[] MAGIC_BYTES = {'B', 'I', 'G', 'V'};
    static final int CURRENT_MAJOR_VERSION = 1;
    static final int CURRENT_MINOR_VERSION = 0;
    static final String CURRENT_VERSION = versionPair(CURRENT_MAJOR_VERSION, CURRENT_MINOR_VERSION);

    public static final Charset UTF8 = Charset.forName("UTF-8");
    public static final Charset UTF16 = Charset.forName("UTF-16");

    private final ByteBuffer buffer;
    private int lastPosition = 0;
    final ReadableByteChannel channel;
    Charset stringCharset;
    long bufferOffset;

    private int majorVersion;
    private int minorVersion;
    private MessageDigest digest;
    private boolean performDigest;

    public BinarySource(ReadableByteChannel channel) {
        buffer = ByteBuffer.allocateDirect(256 * 1024);
        buffer.flip();
        this.channel = channel;
        this.bufferOffset = 0;
        try {
            this.digest = MessageDigest.getInstance("SHA-1"); // NOI18N
        } catch (NoSuchAlgorithmException e) {
        }
    }

    public void useDigest(MessageDigest digest) {
        this.digest = digest;
    }

    private static String versionPair(int major, int minor) {
        return major + "." + minor;
    }

    private void setVersion(int newMajorVersion, int newMinorVersion) throws IOException {
        if (newMajorVersion > CURRENT_MAJOR_VERSION || (newMajorVersion == CURRENT_MAJOR_VERSION && newMinorVersion > CURRENT_MINOR_VERSION)) {
            throw new VersionMismatchException("File format version " + versionPair(newMajorVersion, newMinorVersion) + " unsupported.  Current version is " + CURRENT_VERSION);
        }
        majorVersion = newMajorVersion;
        minorVersion = newMinorVersion;
        stringCharset = UTF8;
    }

    public long getMark() {
        return bufferOffset + buffer.position();
    }

    public void setMark(long mark) {

    }

    int readInt() throws IOException {
        ensureAvailable(4);
        return buffer.getInt();
    }

    String readDoublesToString() throws IOException {
        int len = readInt();
        if (len < 0) {
            return "null";
        }
        ensureAvailable(len * 8);
        StringBuilder sb = new StringBuilder().append('[');
        for (int i = 0; i < len; i++) {
            sb.append(buffer.getDouble());
            if (i < len - 1) {
                sb.append(", ");
            }
        }
        sb.append(']');
        return maybeIntern(sb.toString());
    }

    long readLong() throws IOException {
        ensureAvailable(8);
        return buffer.getLong();
    }

    byte[] peekBytes(int len) throws IOException {
        ensureAvailable(len);
        byte[] b = new byte[len];
        buffer.mark();
        buffer.get(b);
        buffer.reset();
        return b;
    }

    float readFloat() throws IOException {
        ensureAvailable(4);
        return buffer.getFloat();
    }

    int readByte() throws IOException {
        ensureAvailable(1);
        return ((int) buffer.get()) & 255;
    }

    double readDouble() throws IOException {
        ensureAvailable(8);
        return buffer.getDouble();
    }

    public byte[] finishDigest() {
        assert performDigest;
        digestUpToPosition();
        performDigest = false;
        return digest.digest();
    }

    public void startDigest() {
        digest.reset();
        performDigest = true;
        lastPosition = buffer.position();
    }

    private void digestUpToPosition() {
        if (!performDigest) {
            return;
        }
        // All the data between lastPosition and position has been
        // used so add it to the digest.
        int position = buffer.position();
        buffer.position(lastPosition);
        byte[] remaining = new byte[position - buffer.position()];
        buffer.get(remaining);
        digest.update(remaining);
        assert position == buffer.position();
    }

    private void fill() throws IOException {
        int position = buffer.position();
        digestUpToPosition();
        buffer.compact();
        receiveBytes(buffer);
        buffer.flip();
        bufferOffset = bufferOffset + position;
        lastPosition = buffer.position();
    }

    protected void receiveBytes(ByteBuffer b) throws IOException {
        if (channel.read(b) < 0) {
            throw new EOFException();
        }
    }

    byte[] readBytes() throws IOException {
        int len = readInt();
        if (len < 0) {
            return null;
        }
        return readBytes(len);
    }

    byte[] readBytes(int len) throws IOException {
        byte[] b = new byte[len];
        int bytesRead = 0;
        while (bytesRead < b.length) {
            int toRead = Math.min(b.length - bytesRead, buffer.capacity());
            ensureAvailable(toRead);
            buffer.get(b, bytesRead, toRead);
            bytesRead += toRead;
        }
        return b;
    }

    char readShort() throws IOException {
        ensureAvailable(2);
        return buffer.getChar();
    }

    String readIntsToString() throws IOException {
        int len = readInt();
        if (len < 0) {
            return "null";
        }
        ensureAvailable(len * 4);
        StringBuilder sb = new StringBuilder().append('[');
        for (int i = 0; i < len; i++) {
            sb.append(buffer.getInt());
            if (i < len - 1) {
                sb.append(", ");
            }
        }
        sb.append(']');
        return maybeIntern(sb.toString());
    }

    // readString is only called from CP reads, CP items are cached, no need to intern
    String readString() throws IOException {
        if (stringCharset == UTF8) {
            return maybeIntern(new String(readBytes(), UTF8));
        } else if (stringCharset == UTF16) {
            int len = readInt();
            byte[] b = readBytes(len * 2);
            return maybeIntern(new String(b, UTF16));
        }
        int len = readInt();
        // Backwards compatibility
        if (len == 0) {
            return "";
        }
        byte[] b = peekBytes(1);
        if (b[0] == '\u0000') {
            // Assume UTF16 encoding
            stringCharset = UTF16;
            return maybeIntern(new String(readBytes(len * 2), UTF16));
        } else {
            setVersion(1, 0);
            return maybeIntern(new String(readBytes(len), UTF8));
        }
    }

    private void ensureAvailable(int i) throws IOException {
        if (i > buffer.capacity()) {
            throw new IllegalArgumentException(String.format("Can not request %d bytes: buffer capacity is %d", i, buffer.capacity()));
        }
        while (buffer.remaining() < i) {
            fill();
        }
    }

    public boolean readHeader() throws IOException {
        // Check for a version specification
        byte[] magic = peekBytes(MAGIC_BYTES.length);
        if (Arrays.equals(MAGIC_BYTES, magic)) {
            // Consume the bytes for real
            readBytes(MAGIC_BYTES.length);
            setVersion(readByte(), readByte());
            return true;
        } else {
            return false;
        }
    }
}
