package org.graalvm.visualizer.view.actions;

import java.awt.event.ActionEvent;
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
public class LayoutMaxLayerGap extends CallableSystemAction {

    @Override
    public void performAction() {
        // nothing
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
        EditorTopComponent editor = EditorTopComponent.getActive();
        if (editor != null) {
            DiagramScene scene = editor.getScene();

            String actionCommand = ev.getActionCommand();
            int max = Integer.parseInt(actionCommand);
            // TODO: handle NumberFormatException

            StableLayoutProperties properties = scene.getStableLayoutProperties();
            properties.setMaxLayerGap(max);

            LayoutSwitchAlgorithmAction.update(editor);
        }
    }

    public LayoutMaxLayerGap() {
        putValue(Action.SHORT_DESCRIPTION, "Max layer gap");
        putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_MASK));
    }

    @Override
    public String getName() {
        return "Max layer gap";
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