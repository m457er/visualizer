package org.graalvm.visualizer.view.actions;

import java.awt.event.KeyEvent;
import javax.swing.Action;
import javax.swing.KeyStroke;
import org.graalvm.visualizer.view.CollapseManager;
import org.graalvm.visualizer.view.DiagramViewModel;
import org.graalvm.visualizer.view.EditorTopComponent;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.actions.CallableSystemAction;

/**
 *
 * @author Patrik Harag
 */
public final class CollapseAllBlocksAction extends CallableSystemAction {

    public CollapseAllBlocksAction() {
        putValue(Action.SHORT_DESCRIPTION, "Collapse all");
        putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0, false));
    }

    @Override
    public void performAction() {
        EditorTopComponent editor = EditorTopComponent.getActive();
        if (editor != null) {
            DiagramViewModel model = editor.getModel();
            Lookup.getDefault().lookup(CollapseManager.class).collapseAll(model);
        }
    }

    @Override
    public String getName() {
        return "Collapse all blocks";
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

    @Override
    protected String iconResource() {
        return "org/graalvm/visualizer/view/images/collapse_blocks.png";
    }

}
