package org.graalvm.visualizer.view;

import java.awt.Point;
import java.util.function.Consumer;

/**
 *
 * @author Patrik Harag
 */
public interface TransitionAnimationProvider {
    
    public void translate(Point startPosition, Point finalPosition, long duration,
            Consumer<Point> moveProvider);
    
}