/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.graalvm.visualizer.connection;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;

/**
 *
 */
public interface CachedContent extends ReadableByteChannel {
    public ReadableByteChannel subChannel(long start, long end) throws IOException;
}
