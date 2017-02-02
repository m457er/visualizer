/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.view;

import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.services.GraphViewer;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.settings.Settings;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

@ServiceProvider(service = GraphViewer.class)
public class GraphViewerImplementation implements GraphViewer {

    @Override
    public void view(InputGraph graph, boolean clone) {

        if (!clone) {
            WindowManager manager = WindowManager.getDefault();
            for (Mode m : manager.getModes()) {
                for (TopComponent t : manager.getOpenedTopComponents(m)) {
                    if (t instanceof EditorTopComponent) {
                        EditorTopComponent etc = (EditorTopComponent) t;
                        if (etc.getModel().getGroup().getGraphs().contains(graph)) {
                            etc.getModel().selectGraph(graph);
                            t.requestActive();
                            return;
                        }
                    }
                }
            }
        }

        Diagram diagram = Diagram.createDiagram(graph, Settings.get().get(Settings.NODE_TEXT, Settings.NODE_TEXT_DEFAULT));
        EditorTopComponent tc = new EditorTopComponent(diagram);
        tc.open();
        tc.requestActive();
    }
}