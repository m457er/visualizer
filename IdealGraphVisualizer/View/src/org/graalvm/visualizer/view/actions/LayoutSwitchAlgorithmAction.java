package org.graalvm.visualizer.view.actions;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.Action;
import javax.swing.KeyStroke;
import org.graalvm.visualizer.filter.FilterChain;
import org.graalvm.visualizer.view.DiagramScene;
import org.graalvm.visualizer.view.DiagramViewModel;
import org.graalvm.visualizer.view.EditorTopComponent;
import org.openide.util.HelpCtx;
import org.openide.util.actions.CallableSystemAction;

/**
 *
 * @author Patrik Harag
 */
public class LayoutSwitchAlgorithmAction extends CallableSystemAction {

    @Override
    public void performAction() {
        EditorTopComponent editor = EditorTopComponent.getActive();
        if (editor != null) {
            DiagramScene scene = editor.getScene();
             
            scene.setStableLayouting(!scene.isStableLayouting());
            putProperty(SELECTED_KEY, scene.isStableLayouting());
            
            update(editor);
        }
    }

    static void update(EditorTopComponent editor) {
        // enforce update
        // TODO: is a better way?
        
        DiagramViewModel model = editor.getModel();
        FilterChain filterChain = model.getFilterChain();
        filterChain.getChangedEvent().fire();
    }
    
    public LayoutSwitchAlgorithmAction() {
        putValue(Action.SHORT_DESCRIPTION, "New layout");
        putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_MASK));
    }

    @Override
    public String getName() {
        return "New layout";
    }

    @Override
    protected void initialize() {
        super.initialize();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected boolean asynchronous() {
        return false;
    }

}