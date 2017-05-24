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

import org.graalvm.visualizer.data.ChangedEventProvider;
import org.graalvm.visualizer.data.ChangedListener;
import org.graalvm.visualizer.data.FolderElement;
import org.graalvm.visualizer.data.Group.Feedback;
import org.graalvm.visualizer.data.serialization.BinaryReader;
import org.graalvm.visualizer.data.serialization.BinarySource;
import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.util.List;

/**
 *
 */
final class GroupCompleter extends BaseCompleter<List<? extends FolderElement>, LazyGroup> {
    private final StreamIndex streamIndex;

    /**
     * First expansion ofthe group. During first expansion, some statistics are gathered
     */
    private boolean firstExpand = true;

    public GroupCompleter(Env env, StreamIndex index, StreamEntry groupEntry) {
        super(env, groupEntry);
        this.streamIndex = index;
    }

    @Override
    protected List<? extends FolderElement> hookData(List<? extends FolderElement> data) {
        ChangedListener l;
        Object keepalive = future();
        if (keepalive instanceof ChangedListener) {
            l = (ChangedListener) keepalive;
        } else {
            l = new ChangedListener() {
                Object dataHook = keepalive;

                @Override
                public void changed(Object source) {
                }
            };
        }
        for (FolderElement f : data) {
            if (f instanceof ChangedEventProvider) {
                // just keep a backreference to the whole list
                ((ChangedEventProvider) f).getChangedEvent().addListener(l);
            }
            f.setParent(element());
        }
        return data;
    }

    protected List<? extends FolderElement> load(ReadableByteChannel channel, int majorVersion, int minorVersion, Feedback feedback) throws IOException {
        BinarySource bs = new BinarySource(channel, majorVersion, minorVersion);
        SingleGroupBuilder builder = new SingleGroupBuilder(
                        toComplete, env(), bs,
                        streamIndex, entry,
                        feedback,
                        firstExpand);
        firstExpand = false;
        new BinaryReader(bs, builder).parse();
        return builder.getItems();
    }

}
