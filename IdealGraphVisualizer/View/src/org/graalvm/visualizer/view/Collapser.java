package org.graalvm.visualizer.view;

import java.util.Set;
import org.graalvm.visualizer.graph.Diagram;

/**
 *
 * @author Patrik Harag
 */
interface Collapser {

    /**
     * Collapses selected clusters.
     *
     * @param diagram diagram
     * @param clusters clusters to be collapsed (set of names)
     */
    void apply(Diagram diagram, Set<ClusterId> clusters);

}
