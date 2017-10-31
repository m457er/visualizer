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
package org.graalvm.visualizer.view;

import org.graalvm.visualizer.view.actions.PrevDiagramAction;
import org.graalvm.visualizer.view.actions.HideDuplicatesAction;
import org.graalvm.visualizer.view.actions.ExtractAction;
import org.graalvm.visualizer.view.actions.NextDiagramAction;
import org.graalvm.visualizer.view.actions.EnableBlockLayoutAction;
import org.graalvm.visualizer.view.actions.ShowAllAction;
import org.graalvm.visualizer.view.actions.PanModeAction;
import org.graalvm.visualizer.view.actions.ZoomInAction;
import org.graalvm.visualizer.view.actions.PredSuccAction;
import org.graalvm.visualizer.view.actions.SelectionModeAction;
import org.graalvm.visualizer.view.actions.ExpandSuccessorsAction;
import org.graalvm.visualizer.view.actions.HideAction;
import org.graalvm.visualizer.view.actions.ZoomOutAction;
import org.graalvm.visualizer.view.actions.OverviewAction;
import org.graalvm.visualizer.view.actions.ExpandPredecessorsAction;
import org.graalvm.visualizer.view.actions.CollapseBlockAction;
import org.graalvm.visualizer.view.actions.CollapseAllBlocksAction;
import org.graalvm.visualizer.view.actions.ExpandBlockAction;
import org.graalvm.visualizer.view.actions.ExpandAllBlocksAction;
import org.graalvm.visualizer.data.ChangedEvent;
import org.graalvm.visualizer.data.ChangedListener;
import org.graalvm.visualizer.data.InputNode;
import org.graalvm.visualizer.data.Properties;
import org.graalvm.visualizer.data.Properties.PropertyMatcher;
import org.graalvm.visualizer.data.services.InputGraphProvider;
import org.graalvm.visualizer.filter.FilterChain;
import org.graalvm.visualizer.filter.FilterChainProvider;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.graph.services.DiagramProvider;
import org.graalvm.visualizer.svg.BatikSVG;
import org.graalvm.visualizer.util.LookupHistory;
import org.graalvm.visualizer.util.RangeSlider;
import java.awt.*;
import java.awt.event.HierarchyBoundsListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.util.List;
import java.util.*;
import javax.swing.*;
import javax.swing.border.Border;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.actions.RedoAction;
import org.openide.actions.UndoAction;
import org.openide.awt.Toolbar;
import org.openide.awt.ToolbarPool;
import org.openide.awt.UndoRedo;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.actions.Presenter;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ProxyLookup;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import java.util.stream.Collectors;
import org.graalvm.visualizer.view.actions.*;

public final class EditorTopComponent extends TopComponent implements PropertyChangeListener {

    private DiagramScene scene;
    private InstanceContent content;
    private InstanceContent graphContent;
    private EnableBlockLayoutAction blockLayoutAction;
    private OverviewAction overviewAction;
    private HideDuplicatesAction hideDuplicatesAction;
    private PredSuccAction predSuccAction;
    private SelectionModeAction selectionModeAction;
    private PanModeAction panModeAction;
    private boolean notFirstTime;
    private JComponent satelliteComponent;
    private JPanel centerPanel;
    private CardLayout cardLayout;
    private RangeSlider rangeSlider;
    private JToggleButton overviewButton;
    private JToggleButton hideDuplicatesButton;
    private static final String PREFERRED_ID = "EditorTopComponent";
    private static final String SATELLITE_STRING = "satellite";
    private static final String SCENE_STRING = "scene";
    private DiagramViewModel rangeSliderModel;
    private Component quicksearch;
    private JComponent toolbarContainer;
    private Toolbar toolbar;
    private ExportCookie exportCookie = new ExportCookie() {

        @Override
        public void export(File f) {

            Graphics2D svgGenerator = BatikSVG.createGraphicsObject();

            if (svgGenerator == null) {
                NotifyDescriptor message = new NotifyDescriptor.Message("For export to SVG files the Batik SVG Toolkit must be intalled.", NotifyDescriptor.ERROR_MESSAGE);
                DialogDisplayer.getDefault().notifyLater(message);
            } else {
                scene.paint(svgGenerator);
                FileOutputStream os = null;
                try {
                    os = new FileOutputStream(f);
                    Writer out = new OutputStreamWriter(os, "UTF-8");
                    BatikSVG.printToStream(svgGenerator, out, true);
                } catch (FileNotFoundException e) {
                    NotifyDescriptor message = new NotifyDescriptor.Message("For export to SVG files the Batik SVG Toolkit must be intalled.", NotifyDescriptor.ERROR_MESSAGE);
                    DialogDisplayer.getDefault().notifyLater(message);

                } catch (UnsupportedEncodingException e) {
                } finally {
                    if (os != null) {
                        try {
                            os.close();
                        } catch (IOException e) {
                        }
                    }
                }

            }
        }
    };

    private DiagramProvider diagramProvider = new DiagramProvider() {

        @Override
        public Diagram getDiagram() {
            return getModel().getDiagramToView();
        }

        @Override
        public ChangedEvent<DiagramProvider> getChangedEvent() {
            return diagramChangedEvent;
        }
    };

    private ChangedEvent<DiagramProvider> diagramChangedEvent = new ChangedEvent<>(diagramProvider);

    private void updateDisplayName() {
        setDisplayName(getDiagram().getName());
        setToolTipText(getDiagram().getGraph().getGroup().getName());
    }

    public EditorTopComponent(Diagram diagram) {

        LookupHistory.init(InputGraphProvider.class);
        LookupHistory.init(DiagramProvider.class);
        this.setFocusable(true);
        FilterChain filterChain = null;
        FilterChain sequence = null;
        FilterChainProvider provider = Lookup.getDefault().lookup(FilterChainProvider.class);
        if (provider == null) {
            filterChain = new FilterChain();
            sequence = new FilterChain();
        } else {
            filterChain = provider.getFilterChain();
            sequence = provider.getSequence();
        }

        setName(NbBundle.getMessage(EditorTopComponent.class, "CTL_EditorTopComponent"));
        setToolTipText(NbBundle.getMessage(EditorTopComponent.class, "HINT_EditorTopComponent"));

        Action[] actions = new Action[]{
                        PrevDiagramAction.get(PrevDiagramAction.class),
                        NextDiagramAction.get(NextDiagramAction.class),
                        null,
                        ExtractAction.get(ExtractAction.class),
                        ShowAllAction.get(HideAction.class),
                        ShowAllAction.get(ShowAllAction.class),
                        CollapseBlockAction.get(CollapseBlockAction.class),
                        ExpandBlockAction.get(ExpandBlockAction.class),
                        null,
                        ZoomInAction.get(ZoomInAction.class),
                        ZoomOutAction.get(ZoomOutAction.class),
        };

        Action[] actionsWithSelection = new Action[]{
                        ExtractAction.get(ExtractAction.class),
                        ShowAllAction.get(HideAction.class),
                        null,
                        ExpandPredecessorsAction.get(ExpandPredecessorsAction.class),
                        ExpandSuccessorsAction.get(ExpandSuccessorsAction.class)
        };

        initComponents();

        ToolbarPool.getDefault().setPreferredIconSize(16);
        Toolbar toolBar = new Toolbar();
        this.toolbar = toolBar;
        Border b = (Border) UIManager.get("Nb.Editor.Toolbar.border"); // NOI18N
        toolBar.setBorder(b);
        JPanel container = new JPanel();
        this.add(container, BorderLayout.NORTH);
        this.toolbarContainer = container;
        container.setLayout(new BorderLayout());
        container.add(BorderLayout.NORTH, toolBar);

        rangeSliderModel = new DiagramViewModel(diagram.getGraph().getGroup(), filterChain, sequence);
        rangeSlider = new RangeSlider();
        rangeSlider.setModel(rangeSliderModel);
        JScrollPane pane = new JScrollPane(rangeSlider, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        container.add(BorderLayout.CENTER, pane);

        scene = new DiagramScene(actions, actionsWithSelection, rangeSliderModel);
        content = new InstanceContent();
        graphContent = new InstanceContent();
        this.associateLookup(new ProxyLookup(new Lookup[]{scene.getLookup(), new AbstractLookup(graphContent), new AbstractLookup(content)}));
        content.add(exportCookie);
        content.add(rangeSliderModel);
        content.add(diagramProvider);

        rangeSliderModel.getDiagramChangedEvent().addListener(diagramChangedListener);
        rangeSliderModel.selectGraph(diagram.getGraph());
        rangeSliderModel.getViewPropertiesChangedEvent().addListener(new ChangedListener<DiagramViewModel>() {
            @Override
            public void changed(DiagramViewModel source) {
                hideDuplicatesButton.setSelected(getModel().getHideDuplicates());
                hideDuplicatesAction.setState(getModel().getHideDuplicates());
            }
        });

        toolBar.add(NextDiagramAction.get(NextDiagramAction.class));
        toolBar.add(PrevDiagramAction.get(PrevDiagramAction.class));
        toolBar.addSeparator();
        toolBar.add(ExtractAction.get(ExtractAction.class));
        toolBar.add(ShowAllAction.get(HideAction.class));
        toolBar.add(ShowAllAction.get(ShowAllAction.class));
        toolBar.addSeparator();

        SwitchLayoutingAlgorithmAction switchLayoutAction = SwitchLayoutingAlgorithmAction.get(
                SwitchLayoutingAlgorithmAction.class);
        JToggleButton buttonSwitchLayout = new JToggleButton(switchLayoutAction);
        buttonSwitchLayout.setSelected(true);
        toolBar.add(buttonSwitchLayout);
        
        SwitchAlwaysOptimizeSlotsAction switchAlwaysOptimizeSlotsAction = SwitchAlwaysOptimizeSlotsAction.get(
                SwitchAlwaysOptimizeSlotsAction.class);
        JToggleButton buttonAlwaysOptimizeSlots = new JToggleButton(switchAlwaysOptimizeSlotsAction);
        buttonAlwaysOptimizeSlots.setSelected(false);
        toolBar.add(buttonAlwaysOptimizeSlots);
        toolBar.addSeparator();

        toolBar.add(CollapseAllBlocksAction.get(CollapseAllBlocksAction.class));
        toolBar.add(ExpandAllBlocksAction.get(ExpandAllBlocksAction.class));
        toolBar.addSeparator();
        toolBar.add(ShowAllAction.get(ZoomInAction.class));
        toolBar.add(ShowAllAction.get(ZoomOutAction.class));

        blockLayoutAction = new EnableBlockLayoutAction();
        JToggleButton button = new JToggleButton(blockLayoutAction);
        button.setSelected(false);
        toolBar.add(button);
        blockLayoutAction.addPropertyChangeListener(this);

        overviewAction = new OverviewAction();
        overviewButton = new JToggleButton(overviewAction);
        overviewButton.setSelected(false);
        toolBar.add(overviewButton);
        overviewAction.addPropertyChangeListener(this);

        predSuccAction = new PredSuccAction();
        button = new JToggleButton(predSuccAction);
        button.setSelected(true);
        toolBar.add(button);
        predSuccAction.addPropertyChangeListener(this);

        hideDuplicatesAction = new HideDuplicatesAction();
        hideDuplicatesButton = new JToggleButton(hideDuplicatesAction);
        hideDuplicatesButton.setSelected(false);
        toolBar.add(hideDuplicatesButton);
        hideDuplicatesAction.addPropertyChangeListener(this);

        toolBar.addSeparator();
        toolBar.add(UndoAction.get(UndoAction.class));
        toolBar.add(RedoAction.get(RedoAction.class));

        toolBar.addSeparator();
        ButtonGroup interactionButtons = new ButtonGroup();

        panModeAction = new PanModeAction();
        panModeAction.setSelected(true);
        button = new JToggleButton(panModeAction);
        button.setSelected(true);
        interactionButtons.add(button);
        toolBar.add(button);
        panModeAction.addPropertyChangeListener(this);

        selectionModeAction = new SelectionModeAction();
        button = new JToggleButton(selectionModeAction);
        interactionButtons.add(button);
        toolBar.add(button);
        selectionModeAction.addPropertyChangeListener(this);

        toolBar.add(Box.createHorizontalGlue());
        Action action = Utilities.actionsForPath("QuickSearchShadow").get(0);
        quicksearch = ((Presenter.Toolbar) action).getToolbarPresenter();
        try {
            // (aw) workaround for disappearing search bar due to reparenting one shared component
            // instance.
            quicksearch = (Component) quicksearch.getClass().getConstructor(KeyStroke.class).newInstance(new Object[]{null});
        } catch (ReflectiveOperationException | IllegalArgumentException | SecurityException e) {
        }
        Dimension preferredSize = quicksearch.getPreferredSize();
        preferredSize = new Dimension((int) preferredSize.getWidth() * 2, (int) preferredSize.getHeight());
        quicksearch.setMinimumSize(preferredSize); // necessary for GTK LAF
        quicksearch.setPreferredSize(preferredSize);
        toolBar.add(quicksearch);

        centerPanel = new JPanel();
        this.add(centerPanel, BorderLayout.CENTER);
        cardLayout = new CardLayout();
        centerPanel.setLayout(cardLayout);
        centerPanel.add(SCENE_STRING, scene.getComponent());
        centerPanel.setBackground(Color.WHITE);
        satelliteComponent = scene.createSatelliteView();
        satelliteComponent.setSize(200, 200);
        centerPanel.add(SATELLITE_STRING, satelliteComponent);

        // TODO: Fix the hot key for entering the satellite view
        this.addKeyListener(keyListener);

        scene.getComponent().addHierarchyBoundsListener(new HierarchyBoundsListener() {

            @Override
            public void ancestorMoved(HierarchyEvent e) {
            }

            @Override
            public void ancestorResized(HierarchyEvent e) {
                if (!notFirstTime && scene.getComponent().getBounds().width > 0) {
                    notFirstTime = true;
                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                            EditorTopComponent.this.scene.initialize();
                        }
                    });
                }
            }
        });

        if (diagram.getGraph().getGroup().getGraphsCount() == 1) {
            rangeSlider.setVisible(false);
        }

        updateDisplayName();
    }

    private KeyListener keyListener = new KeyListener() {

        @Override
        public void keyTyped(KeyEvent e) {
        }

        @Override
        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_S) {
                EditorTopComponent.this.overviewButton.setSelected(true);
                EditorTopComponent.this.overviewAction.setState(true);
            }
        }

        @Override
        public void keyReleased(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_S) {
                EditorTopComponent.this.overviewButton.setSelected(false);
                EditorTopComponent.this.overviewAction.setState(false);
            }
        }
    };

    public DiagramScene getScene() {
        return scene;
    }

    public DiagramViewModel getDiagramModel() {
        return rangeSliderModel;
    }

    private void showSatellite() {
        cardLayout.show(centerPanel, SATELLITE_STRING);
        satelliteComponent.requestFocus();

    }

    private void showScene() {
        cardLayout.show(centerPanel, SCENE_STRING);
        scene.getComponent().requestFocus();
    }

    public void zoomOut() {
        scene.zoomOut();
    }

    public void zoomIn() {
        scene.zoomIn();
    }

    public void showPrevDiagram() {
        int fp = getModel().getFirstPosition();
        int sp = getModel().getSecondPosition();
        if (fp != 0) {
            fp--;
            sp--;
            getModel().setPositions(fp, sp);
        }
    }

    public DiagramViewModel getModel() {
        return rangeSliderModel;
    }

    public FilterChain getFilterChain() {
        return getModel().getFilterChain();
    }

    public static EditorTopComponent getActive() {
        Set<? extends Mode> modes = WindowManager.getDefault().getModes();
        for (Mode m : modes) {
            TopComponent tc = m.getSelectedTopComponent();
            if (tc instanceof EditorTopComponent) {
                return (EditorTopComponent) tc;
            }
        }
        return null;
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT
     * modify this code. The content of this method is always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jCheckBox1 = new javax.swing.JCheckBox();

        org.openide.awt.Mnemonics.setLocalizedText(jCheckBox1, "jCheckBox1");
        jCheckBox1.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        jCheckBox1.setMargin(new java.awt.Insets(0, 0, 0, 0));

        setLayout(new java.awt.BorderLayout());
    }// </editor-fold>//GEN-END:initComponents
     // Variables declaration - do not modify//GEN-BEGIN:variables

    private javax.swing.JCheckBox jCheckBox1;
    // End of variables declaration//GEN-END:variables

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_NEVER;
    }

    @Override
    public void componentClosed() {
        rangeSliderModel.close();
        // the quicksearch does not unregister from global result list.
        // remove it from the AWT hiearchy, so it does not keep this Component
        // through parent chain.
        this.remove(toolbarContainer);
        this.toolbar.remove(this.quicksearch);
        super.componentClosed();
    }

    @Override
    protected String preferredID() {
        return PREFERRED_ID;
    }

    private ChangedListener<DiagramViewModel> diagramChangedListener = new ChangedListener<DiagramViewModel>() {

        @Override
        public void changed(DiagramViewModel source) {
            updateDisplayName();
            Collection<Object> list = new ArrayList<>();
            list.add(new EditorInputGraphProvider(EditorTopComponent.this));
            graphContent.set(list, null);
            diagramProvider.getChangedEvent().fire();
        }

    };

    public boolean showPredSucc() {
        return (Boolean) predSuccAction.getValue(PredSuccAction.STATE);
    }

    public void setSelection(PropertyMatcher matcher) {

        Properties.PropertySelector<Figure> selector = new Properties.PropertySelector<>(getModel().getDiagramToView().getFigures());
        List<Figure> list = selector.selectMultiple(matcher);
        setSelectedFigures(list);
    }

    public void setSelectedFigures(List<Figure> list) {
        scene.setSelection(list);
        scene.centerFigures(list);
    }

    public void setSelectedNodes(Set<InputNode> nodes) {

        List<Figure> list = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        for (InputNode n : nodes) {
            ids.add(n.getId());
        }

        for (Figure f : getModel().getDiagramToView().getFigures()) {
            for (InputNode n : f.getSource().getSourceNodes()) {
                if (ids.contains(n.getId())) {
                    list.add(f);
                    break;
                }
            }
        }

        setSelectedFigures(list);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getSource() == this.predSuccAction) {
            boolean b = (Boolean) predSuccAction.getValue(PredSuccAction.STATE);
            this.getModel().setShowNodeHull(b);
        } else if (evt.getSource() == this.overviewAction) {
            boolean b = (Boolean) overviewAction.getValue(OverviewAction.STATE);
            if (b) {
                showSatellite();
            } else {
                showScene();
            }
        } else if (evt.getSource() == this.blockLayoutAction) {
            boolean b = (Boolean) blockLayoutAction.getValue(EnableBlockLayoutAction.STATE);
            this.getModel().setShowBlocks(b);
        } else if (evt.getSource() == this.hideDuplicatesAction) {
            boolean b = (Boolean) hideDuplicatesAction.getValue(HideDuplicatesAction.STATE);
            this.getModel().setHideDuplicates(b);
        } else if (evt.getSource() == this.selectionModeAction || evt.getSource() == this.panModeAction) {
            if (panModeAction.isSelected()) {
                scene.setInteractionMode(DiagramViewer.InteractionMode.PANNING);
            } else if (selectionModeAction.isSelected()) {
                scene.setInteractionMode(DiagramViewer.InteractionMode.SELECTION);
            }
        } else {
            assert false : "Unknown event source";
        }
    }

    public void extract() {
        getModel().showOnly(collectSelectedNodes());
    }

    public void hideNodes() {
        Set<InputNode> nodes = collectSelectedNodes();
        nodes.addAll(getModel().getHiddenGraphNodes());

        getModel().showNot(nodes.stream().map(InputNode::getId).collect(Collectors.toSet()));
    }

    private Set<InputNode> collectSelectedNodes() {
        HashSet<InputNode> nodes = new HashSet<>();

        // selected nodes
        nodes.addAll(getModel().getSelectedNodes());
 
        // nodes from selected blocks
        getModel().getSelectedBlocks().stream()
                .flatMap(b -> b.getNodes().stream())
                .forEach(nodes::add);

        return nodes;
    }

    public void expandPredecessors() {
        Set<Figure> oldSelection = getModel().getSelectedFigures();
        Set<Figure> figures = new HashSet<>();

        for (Figure f : this.getDiagramModel().getDiagramToView().getFigures()) {
            boolean ok = false;
            if (oldSelection.contains(f)) {
                ok = true;
            } else {
                for (Figure pred : f.getSuccessors()) {
                    if (oldSelection.contains(pred)) {
                        ok = true;
                        break;
                    }
                }
            }

            if (ok) {
                figures.add(f);
            }
        }

        getModel().showAll(figures);
    }

    public void expandSuccessors() {
        Set<Figure> oldSelection = getModel().getSelectedFigures();
        Set<Figure> figures = new HashSet<>();

        for (Figure f : this.getDiagramModel().getDiagramToView().getFigures()) {
            boolean ok = false;
            if (oldSelection.contains(f)) {
                ok = true;
            } else {
                for (Figure succ : f.getPredecessors()) {
                    if (oldSelection.contains(succ)) {
                        ok = true;
                        break;
                    }
                }
            }

            if (ok) {
                figures.add(f);
            }
        }

        getModel().showAll(figures);
    }

    public void showAll() {
        getModel().showNot(Collections.<Integer> emptySet());
    }

    public Diagram getDiagram() {
        return getDiagramModel().getDiagramToView();
    }

    @Override
    protected void componentHidden() {
        super.componentHidden();
        scene.componentHidden();

    }

    @Override
    protected void componentShowing() {
        super.componentShowing();
        scene.componentShowing();
    }

    @Override
    public void requestActive() {
        super.requestActive();
        scene.getComponent().requestFocus();
    }

    @Override
    public UndoRedo getUndoRedo() {
        return scene.getUndoRedo();
    }

    @Override
    protected Object writeReplace() throws ObjectStreamException {
        throw new NotSerializableException();
    }
}
