package org.graalvm.visualizer.view.actions;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.Action;
import javax.swing.KeyStroke;
import org.graalvm.visualizer.view.DiagramScene;
import org.graalvm.visualizer.view.EditorTopComponent;
import org.graalvm.visualizer.view.StableLayoutProperties;
import org.openide.util.HelpCtx;
import org.openide.util.actions.CallableSystemAction;

/**
 *
 * @author Patrik Harag
 */
public class LayoutAlwaysOptimizeSlotsAction extends CallableSystemAction {

    @Override
    public void performAction() {
        EditorTopComponent editor = EditorTopComponent.getActive();
        if (editor != null) {
            DiagramScene scene = editor.getScene();
            
            StableLayoutProperties properties = scene.getStableLayoutProperties();
            properties.setAlwaysOptimizeSlots(!properties.isAlwaysOptimizeSlots());
            putProperty(SELECTED_KEY, properties.isAlwaysOptimizeSlots());
            
            LayoutSwitchAlgorithmAction.update(editor);
        }
    }

    public LayoutAlwaysOptimizeSlotsAction() {
        putValue(Action.SHORT_DESCRIPTION, "Always optimize slots");
        putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_MASK));
    }

    @Override
    public String getName() {
        return "Always optimize slots";
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