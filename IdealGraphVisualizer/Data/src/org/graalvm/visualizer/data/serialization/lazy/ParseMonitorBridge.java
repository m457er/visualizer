/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

package org.graalvm.visualizer.data.serialization.lazy;

import org.graalvm.visualizer.data.Group.Feedback;
import org.graalvm.visualizer.data.serialization.BinarySource;
import org.graalvm.visualizer.data.serialization.ParseMonitor;

/**
 * Bridge between ParseMonitor and Feedback. Derives the amount of work done from the datasource's
 * position.
 */
final class ParseMonitorBridge implements ParseMonitor {
    private final Feedback feedback;
    private final BinarySource dataSource;
    private final int entrySize;
    private final int scaleFactor;

    public ParseMonitorBridge(StreamEntry entry, Feedback feedback, BinarySource dataSource) {
        this.feedback = feedback;
        this.dataSource = dataSource;
        // Progress API does not support long workunits
        long size = entry.size();
        if (size > Integer.MAX_VALUE) {
            scaleFactor = (int) Math.ceil((double) entry.size() / Integer.MAX_VALUE);
            entrySize = (int) (entry.size() / scaleFactor);
        } else {
            scaleFactor = 1;
            entrySize = (int) size;
        }
    }

    private int work() {
        return (int) (dataSource.getMark() / scaleFactor);
    }

    @Override
    public void updateProgress() {
        if (feedback != null) {
            feedback.reportProgress(work(), entrySize, null);
        }
    }

    @Override
    public void setState(String state) {
        if (feedback != null) {
            feedback.reportProgress(work(), entrySize, state);
        }
    }

    @Override
    public boolean isCancelled() {
        if (feedback != null) {
            return feedback.isCancelled();
        } else {
            return false;
        }
    }
}
