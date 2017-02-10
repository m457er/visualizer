/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.graalvm.visualizer.graph;

import org.graalvm.visualizer.data.InputNode;
import org.graalvm.visualizer.data.Source;

/**
 * Specialized version of Source, which invalidates data in Figure and/or Diagram upon modification.
 */
class FigureSource extends Source {
    /**
     * Backreference to the Figure.
     */
    private final Figure fig;

    public FigureSource(Figure fig) {
        this.fig = fig;
    }

    private void superAddSourceNode(InputNode n) {
        super.addSourceNode(n);
    }

    @Override
    public void addSourceNodes(Source s) {
        for (InputNode n : s.getSourceNodes()) {
            superAddSourceNode(n);
        }
        fig.sourcesChanged(this);
    }

    @Override
    public void addSourceNode(InputNode n) {
        super.addSourceNode(n);
        fig.sourcesChanged(this);
    }
}
