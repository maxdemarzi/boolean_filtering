package com.maxdemarzi.results;

import org.neo4j.graphdb.Node;

import java.util.List;

public class SizeAndNodeResult {
    public final List<Node> nodes;
    public final Long size;

    public SizeAndNodeResult(List<Node> nodes, Long size) {
        this.nodes = nodes;
        this.size = size;
    }
}
