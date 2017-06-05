package org.graalvm.visualizer.view;

import org.graalvm.visualizer.graph.Diagram;

/**
 * Manages block collapsing.
 *
 * @author Patrik Harag
 */
public interface CollapseManager {

    void apply(Diagram diagram);

    /**
     * Collapses all clusters.
     *
     * @param model
     */
    void collapseAll(DiagramViewModel model);

    /**
     * Collapses selected clusters.
     *
     * @param model
     */
    void collapseSelected(DiagramViewModel model);

    /**
     * Expands all collapsed blocks.
     *
     * @param model
     */
    void expandAll(DiagramViewModel model);

    /**
     * Expands selected clusters.
     *
     * @param model
     */
    void expandSelected(DiagramViewModel model);

    /**
     * Returns {@code true} if collapse manager is initialized and there are
     * some collapsed blocks.
     *
     * @return boolean
     */
    boolean isActive();

}
