package org.graalvm.visualizer.view;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.graalvm.visualizer.graph.Connection;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.graph.InputSlot;
import org.graalvm.visualizer.graph.OutputSlot;
import org.graalvm.visualizer.layout.Cluster;

import static org.graalvm.visualizer.view.ClusterId.getClusterName;


/**
 * Rewire connections from figures inside a given cluster to a replacement figure.
 *
 * @author Patrik Harag
 */
public class ConnectionMapper {

    private final Diagram diagram;
    private final Set<Figure> figures;
    private final Figure replacement;
    private final String clusterName;
    
    private Map<String, InputSlot> inputSlots;
    private Map<String, OutputSlot> outputSlots;

    public ConnectionMapper(String cluster, Set<Figure> figures, Figure replacement,
            Diagram diagram) {
        
        this.clusterName = cluster;
        this.figures = figures;    // figures inside the cluster
        this.replacement = replacement;
        this.diagram = diagram;
    }

    private void init() {
        this.inputSlots = new LinkedHashMap<>();
        this.outputSlots = new LinkedHashMap<>();
    }

    public void mapConnections() {
        init();
        
        for (Figure figure : figures) {
            for (InputSlot inputSlot : figure.getInputSlots()) {
                for (Connection connection : inputSlot.getConnections()) {
                    processConnection(connection);
                }
            }
            for (OutputSlot outputSlot : figure.getOutputSlots()) {
                for (Connection connection : outputSlot.getConnections()) {
                    processConnection(connection);
                }
            }
        }
    }
    
    private void processConnection(Connection connection) {
        InputSlot inputSlot = connection.getInputSlot();
        OutputSlot outputSlot = connection.getOutputSlot();
        
        Figure inputFigure = inputSlot.getFigure();
        Figure outputFigure = outputSlot.getFigure();

        Cluster inputCluster = inputFigure.getCluster();
        Cluster outputCluster = outputFigure.getCluster();

        if (inputCluster == outputCluster) {
            // connection inside cluster
            
        } else if (clusterName.equals(getClusterName(inputCluster))) {
            InputSlot replacementInputSlot = createInputSlot(outputCluster);
            createConnection(connection, replacementInputSlot, outputSlot);
            
        } else if (clusterName.equals(getClusterName(outputCluster))) {
            OutputSlot replacementOutputSlot = createOutputSlot(inputCluster);
            createConnection(connection, inputSlot, replacementOutputSlot);
            
        } else {
            assert false : "Connection between other clusters";
        }
    }
    
    /**
     * Returns input slot for given cluster.
     * 
     * @return InputSlot
     */
    private InputSlot createInputSlot(Cluster outputCluster) {
        final String key = getClusterName(outputCluster);

        if (inputSlots.containsKey(key)) {
            return inputSlots.get(key);
            
        } else {
            InputSlot slot = replacement.createInputSlot();
            inputSlots.put(key, slot);
            return slot;
        }
    }
    
    /**
     * Returns output slot for given cluster.
     * 
     * @return OutputSlot
     */
    private OutputSlot createOutputSlot(Cluster inputCluster) {
        final String key = getClusterName(inputCluster);

        if (outputSlots.containsKey(key)) {
            return outputSlots.get(key);
            
        } else {
            OutputSlot slot = replacement.createOutputSlot();
            outputSlots.put(key, slot);
            return slot;
        }
    }
    
    private void createConnection(Connection oldConnection, InputSlot in, OutputSlot out) {
        Connection c = diagram.createConnection(in, out, null, null);
        c.setStyle(oldConnection.getStyle());
        c.setColor(oldConnection.getColor());
    }
    
}
