package com.credit.platform.data.governance.lineage;

import java.util.*;

/**
 * 数据血缘图 — 描述数据从源到目标的完整流转路径。
 *
 * <p>节点表示数据资产（表/字段），边表示数据转换关系（ETL/API/流处理）。
 */
public class LineageGraph {

    private final Map<String, LineageNode> nodes = new LinkedHashMap<>();
    private final List<LineageEdge> edges = new ArrayList<>();

    /**
     * 添加节点。
     */
    public void addNode(LineageNode node) {
        nodes.put(node.getNodeId(), node);
    }

    /**
     * 添加边（数据流转关系）。
     */
    public void addEdge(LineageEdge edge) {
        edges.add(edge);
    }

    /**
     * 获取指定节点的所有上游节点（数据来源）。
     */
    public List<LineageNode> getUpstream(String nodeId) {
        return edges.stream()
                .filter(e -> e.getTargetId().equals(nodeId))
                .map(e -> nodes.get(e.getSourceId()))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 获取指定节点的所有下游节点（数据消费方）。
     */
    public List<LineageNode> getDownstream(String nodeId) {
        return edges.stream()
                .filter(e -> e.getSourceId().equals(nodeId))
                .map(e -> nodes.get(e.getTargetId()))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 获取从指定节点出发的完整上游链路（递归）。
     */
    public List<String> getFullUpstreamPath(String nodeId) {
        List<String> path = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        collectUpstream(nodeId, path, visited);
        return path;
    }

    /**
     * 获取从指定节点出发的完整下游链路（递归）。
     */
    public List<String> getFullDownstreamPath(String nodeId) {
        List<String> path = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        collectDownstream(nodeId, path, visited);
        return path;
    }

    /** 影响分析 — 表/字段变更时，找出所有受影响的下游 */
    public List<String> impactAnalysis(String nodeId) {
        return getFullDownstreamPath(nodeId);
    }

    private void collectUpstream(String nodeId, List<String> path, Set<String> visited) {
        if (visited.contains(nodeId)) return;
        visited.add(nodeId);
        for (LineageNode up : getUpstream(nodeId)) {
            path.add(up.getNodeId());
            collectUpstream(up.getNodeId(), path, visited);
        }
    }

    private void collectDownstream(String nodeId, List<String> path, Set<String> visited) {
        if (visited.contains(nodeId)) return;
        visited.add(nodeId);
        for (LineageNode down : getDownstream(nodeId)) {
            path.add(down.getNodeId());
            collectDownstream(down.getNodeId(), path, visited);
        }
    }

    // Getters
    public Map<String, LineageNode> getNodes() { return nodes; }
    public List<LineageEdge> getEdges() { return edges; }
}
