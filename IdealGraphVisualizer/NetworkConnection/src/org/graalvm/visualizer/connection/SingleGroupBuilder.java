/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.graalvm.visualizer.connection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.graalvm.visualizer.data.Folder;
import org.graalvm.visualizer.data.FolderElement;
import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.serialization.ModelBuilder;

/**
 *
 */
final class SingleGroupBuilder extends ModelBuilder {
    private final List<FolderElement> items = new ArrayList<>();
    private final Group   toComplete;
    private int nestLevel;
    
    public SingleGroupBuilder(GraphDocument rootDocument, Executor modelExecutor, Group toComplete) {
        super(rootDocument, modelExecutor, null, null);
        this.toComplete = toComplete;
    }

    @Override
    public void endGroup() {
        Folder f = folder();
        super.endGroup();
        --nestLevel;
    }

    @Override
    public InputGraph startGraph(String title) {
        nestLevel++;
        return super.startGraph(title);
    }

    @Override
    protected void registerToParent(Folder parent, FolderElement item) {
        if (parent == toComplete) {
            items.add(item);
        } else {
            super.registerToParent(parent, item);
        }
    }
    
    @Override
    public InputGraph endGraph() {
        InputGraph gr = super.endGraph();
        --nestLevel;
        return gr;
    }

    @Override
    public Group startGroup() {
        if (nestLevel++ == 0) {
            return pushGroup(toComplete);
        } else {
            return super.startGroup();
        }
    }
    
    public List<FolderElement> getItems() {
        return items;
    }
}
