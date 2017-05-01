package org.graalvm.visualizer.view;

import java.awt.Point;
import java.awt.event.ActionEvent;
import java.util.function.Consumer;
import javax.swing.Timer;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Patrik Harag
 */
@ServiceProvider(service = TransitionAnimationProvider.class)
public class LinearTransitionAnimationProvider implements TransitionAnimationProvider {
    
    private static final int UPDATE_MS = 1000/60;
    
    private Timer timer;
    
    public void terminateLast() {
        if (timer != null)
            timer.stop();
    }
    
    @Override
    public void translate(Point startPosition, Point finalPosition, long duration,
            Consumer<Point> moveProvider) {
        
        terminateLast();
        
        final double diffY = finalPosition.y - startPosition.y;
        final double diffX = finalPosition.x - startPosition.x;
        
        final long start = System.currentTimeMillis();
        
        timer = new Timer(UPDATE_MS, (ActionEvent e) -> {
            long diff = System.currentTimeMillis() - start;
            double progress = (double) diff / duration;
            
            if (progress > 1d) {
                progress = 1d;
                ((Timer) e.getSource()).stop();
            }
            
            Point p = new Point();
            p.x = (int) (startPosition.x + diffX * progress);
            p.y = (int) (startPosition.y + diffY * progress);
            moveProvider.accept(p);
        });
        
        timer.setRepeats(true);
        timer.setCoalesce(true);
        timer.setInitialDelay(0);
        timer.start();
    }
    
}