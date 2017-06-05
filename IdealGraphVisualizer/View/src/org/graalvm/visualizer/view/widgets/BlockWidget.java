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
package org.graalvm.visualizer.view.widgets;

import org.graalvm.visualizer.data.InputBlock;
import org.graalvm.visualizer.graph.Diagram;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.graalvm.visualizer.data.InputNode;
import org.graalvm.visualizer.util.DoubleClickAction;
import org.graalvm.visualizer.util.DoubleClickHandler;
import org.graalvm.visualizer.view.DiagramScene;
import org.netbeans.api.visual.action.WidgetAction;
import org.netbeans.api.visual.model.ObjectState;
import org.netbeans.api.visual.widget.Widget;

public final class BlockWidget extends Widget {

    public static final Color BACKGROUND_COLOR = new Color(235, 235, 255);
    public static final Color BACKGROUND_COLOR_SELECTED = new Color(225, 225, 255);
    private static final Font TITLE_FONT = new Font("Serif", Font.PLAIN, 14).deriveFont(Font.BOLD);
    private final InputBlock blockNode;
    private final Diagram diagram;
    private final DiagramScene scene;

    public BlockWidget(DiagramScene scene, Diagram diagram, InputBlock blockNode) {
        super(scene);
        this.scene = scene;
        this.diagram = diagram;
        this.blockNode = blockNode;

        this.setBackground(BACKGROUND_COLOR);
        this.setOpaque(true);
        this.setCheckClipping(true);

        this.getActions().addAction(new DoubleClickAction(new BlockDoubleClick()));
    }

    private class BlockDoubleClick implements DoubleClickHandler {

        @Override
        public void handleDoubleClick(Widget w, WidgetAction.WidgetMouseEvent e) {
            if (scene.getModel().getHiddenNodes().isEmpty()) {
                // no selected nodes - extract
                scene.getModel().showOnly(new HashSet<>(blockNode.getNodes()));

            } else {
                toggleVisibility(collectSourceNodes(blockNode));
            }
        }

        private Set<Integer> collectSourceNodes(InputBlock block) {
            return block.getNodes().stream()
                    .map(InputNode::getId)
                    .collect(Collectors.toSet());
        }

        private void toggleVisibility(Set<Integer> selectedNodes) {
            Set<Integer> hiddenNodes = scene.getModel().getHiddenNodes();
 
            Set<Integer> hiddenSelected = new LinkedHashSet<>(hiddenNodes);
            hiddenSelected.retainAll(selectedNodes);
 
            if (hiddenSelected.isEmpty()) {
                // all selected figures are visible
                int size = hiddenNodes.size() + selectedNodes.size();
                Set<Integer> toHide = new LinkedHashSet<>(size);
                toHide.addAll(hiddenNodes);
                toHide.addAll(selectedNodes);
                scene.getModel().showNot(toHide);
            } else {
                // some of selected figures are hidden
                Set<Integer> toHide = new LinkedHashSet<>();
                toHide.addAll(hiddenNodes);
                toHide.removeAll(selectedNodes);
                scene.getModel().showNot(toHide);
            }
        }
    }

    @Override
    protected void paintWidget() {
        super.paintWidget();
        Graphics2D g = this.getGraphics();
        Stroke old = g.getStroke();
        g.setColor(Color.BLUE);
        Rectangle r = new Rectangle(this.getPreferredBounds());
        r.width--;
        r.height--;
        if (this.getBounds().width > 0 && this.getBounds().height > 0) {
            g.setStroke(new BasicStroke(2));
            g.drawRect(r.x, r.y, r.width, r.height);
        }

        Color titleColor = Color.BLACK;
        g.setColor(titleColor);
        g.setFont(TITLE_FONT);

        String s = "B" + blockNode.getName();
        Rectangle2D r1 = g.getFontMetrics().getStringBounds(s, g);
        g.drawString(s, r.x + 5, r.y + (int) r1.getHeight());
        g.setStroke(old);
    }

    @Override
    protected void notifyStateChanged(ObjectState previousState, ObjectState state) {
        super.notifyStateChanged(previousState, state);

        if (state.isSelected() || state.isHighlighted()) {
            this.setBackground(BACKGROUND_COLOR_SELECTED);
        } else {
            this.setBackground(BACKGROUND_COLOR);
        }
    }

}
