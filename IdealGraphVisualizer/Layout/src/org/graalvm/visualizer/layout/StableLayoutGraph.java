package org.graalvm.visualizer.layout;

import java.util.*;

public class StableLayoutGraph 
{
    
    private List<? extends Link> links;
    private List<? extends Vertex> vertices;

    public StableLayoutGraph(List<? extends Link> links) {
        this(links, new ArrayList<Vertex>(1));
    }

    public StableLayoutGraph(List<? extends Link> links, List<? extends Vertex> vertices) {
        this.links = links;
        this.vertices = vertices;
    }

    public List<? extends Link> getLinks() {
        return links;
    }

    public List<? extends Vertex> getVertices() {
        return vertices;
    }

    public List<Cluster> getClusters() {
        Set<Cluster> containsCluster = new HashSet<>();
        List<Cluster> clusters = new ArrayList<>();
        
        for (Vertex v : getVertices()) {
            if (v.getCluster() != null && !containsCluster.contains(v.getCluster())) {
                containsCluster.add(v.getCluster());
                clusters.add(v.getCluster());
            }
        }

        return clusters;
    }
}
