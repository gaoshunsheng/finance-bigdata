package com.credit.platform.engine.core.flow;

import com.credit.platform.engine.common.model.ActionType;
import com.credit.platform.engine.core.compiler.CompiledRule;
import com.credit.platform.engine.core.executor.ExecutionContext;
import com.credit.platform.engine.core.expression.ExpressionEngine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 编译后的 DAG 决策流 — 拓扑排序 + 逐步执行。
 * <p>
 * 不可变对象，线程安全。执行流程:
 * <ol>
 *   <li>确定入口节点（无入边的节点）</li>
 *   <li>按拓扑序逐步执行节点</li>
 *   <li>每执行完一个节点，根据出边的条件决定下一个节点</li>
 *   <li>遇到 ACTION 节点或无后续节点时结束</li>
 * </ol>
 * </p>
 */
public final class CompiledDAG implements CompiledRule {

    private static final String RULE_TYPE = "DECISION_FLOW";

    private final String flowId;
    private final String name;
    private final int version;
    private final Map<String, FlowNode> nodes;
    private final List<FlowEdge> edges;
    private final Map<String, List<FlowEdge>> outgoingEdges;  // nodeId → 出边列表

    public CompiledDAG(String flowId, String name, int version,
                       List<FlowNode> nodes, List<FlowEdge> edges) {
        this.flowId = Objects.requireNonNull(flowId);
        this.name = name;
        this.version = version;

        Map<String, FlowNode> nodeMap = new LinkedHashMap<>();
        for (FlowNode node : nodes) {
            nodeMap.put(node.getId(), node);
        }
        this.nodes = Collections.unmodifiableMap(nodeMap);

        this.edges = Collections.unmodifiableList(new ArrayList<>(
            Objects.requireNonNull(edges)));

        // 构建出边索引
        Map<String, List<FlowEdge>> outMap = new HashMap<>();
        for (FlowEdge edge : this.edges) {
            outMap.computeIfAbsent(edge.getFrom(), k -> new ArrayList<>()).add(edge);
        }
        this.outgoingEdges = new HashMap<>();
        for (Map.Entry<String, List<FlowEdge>> e : outMap.entrySet()) {
            this.outgoingEdges.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }

        // 安全修复: 编译时检测 DAG 中的环，防止无限循环
        detectCycles();
    }

    /**
     * 使用 DFS 检测环 — Kahn 算法验证 DAG 有效性。
     * 如果存在环，抛出 IllegalStateException。
     */
    private void detectCycles() {
        // 计算入度
        Map<String, Integer> inDegree = new HashMap<>();
        for (String nodeId : nodes.keySet()) {
            inDegree.put(nodeId, 0);
        }
        for (FlowEdge edge : edges) {
            inDegree.merge(edge.getTo(), 1, Integer::sum);
        }

        // 拓扑排序 (Kahn's algorithm)
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        int visited = 0;
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            visited++;
            List<FlowEdge> outEdges = outgoingEdges.get(nodeId);
            if (outEdges != null) {
                for (FlowEdge edge : outEdges) {
                    int newDegree = inDegree.get(edge.getTo()) - 1;
                    inDegree.put(edge.getTo(), newDegree);
                    if (newDegree == 0) {
                        queue.add(edge.getTo());
                    }
                }
            }
        }

        // 如果访问的节点数小于总节点数，说明存在环
        if (visited < nodes.size()) {
            throw new IllegalStateException(
                "DAG '" + flowId + "' contains a cycle — only " + visited
                + " of " + nodes.size() + " nodes reachable in topological order. "
                + "Cyclic decision flows are not allowed.");
        }
    }

    /**
     * 执行 DAG 决策流。
     * <p>
     * 从入口节点开始，按条件边逐步执行，直到 ACTION 节点或无后续节点。
     * </p>
     *
     * @param context 执行上下文
     * @param expressionEngine 表达式引擎（用于条件边求值）
     * @return 执行结果
     */
    public FlowExecutionResult execute(ExecutionContext context, ExpressionEngine expressionEngine) {
        long start = System.nanoTime();
        List<String> path = new ArrayList<>();
        Map<String, Object> nodeOutputs = new LinkedHashMap<>();

        // 找入口节点
        FlowNode entry = findEntryNode();
        if (entry == null) {
            return buildResult(path, nodeOutputs, false, start);
        }

        FlowNode current = entry;
        int maxSteps = nodes.size() + 1; // 安全上限：DAG 最多 N 个节点

        while (current != null) {
            if (path.size() >= maxSteps) {
                // 安全保护: 防止无限循环（环检测应已阻止，这是双重保险）
                LOGGER.warning("DAG execution exceeded max steps (" + maxSteps + ") for flow: " + flowId);
                break;
            }
            path.add(current.getId());

            // 执行当前节点
            Object output = executeNode(current, context, expressionEngine);
            nodeOutputs.put(current.getId(), output);

            // ACTION 节点 → 结束
            if (current.getType() == FlowNode.Type.ACTION) {
                ActionType action = extractAction(current, output);
                String reason = extractReason(output);
                context.setVariable("flow_result", action.name());
                return buildResult(path, nodeOutputs, action, reason, start);
            }

            // 根据出边找下一个节点
            current = resolveNextNode(current, output, context, expressionEngine);
        }

        return buildResult(path, nodeOutputs, false, start);
    }

    /**
     * 执行单个节点。简化实现 — 真实实现中会委托给具体的 NodeExecutor。
     */
    private Object executeNode(FlowNode node, ExecutionContext context,
                                ExpressionEngine expressionEngine) {
        switch (node.getType()) {
            case DATA_PREP:
                // 数据准备节点 — 将请求变量复制到上下文
                return context.getAllVariables().size();

            case RULE_SET:
                // 规则集节点 — 在真实实现中会委托给 RuleExecutor
                return node.getConfig().get("ruleSetId");

            case SCORECARD:
                return node.getConfig().get("scorecardId");

            case MODEL:
                return node.getConfig().get("modelId");

            case DECISION:
                // 条件分支节点 — 求值条件表达式
                String expr = node.getConfig("expression", "true");
                return expressionEngine.executeAsBoolean(expr, context.getAllVariables());

            case SCRIPT:
                String script = node.getConfig("script", "null");
                return expressionEngine.execute(script, context.getAllVariables());

            case ACTION:
                return node.getConfig();

            case SUB_FLOW:
            case AB_SPLIT:
            default:
                return node.getConfig();
        }
    }

    /**
     * 根据出边条件和节点输出，决定下一个执行的节点。
     */
    private FlowNode resolveNextNode(FlowNode current, Object output,
                                      ExecutionContext context,
                                      ExpressionEngine expressionEngine) {
        List<FlowEdge> outEdges = outgoingEdges.get(current.getId());
        if (outEdges == null || outEdges.isEmpty()) {
            return null;
        }

        // 将当前节点的输出放入上下文
        String nodeVar = current.getId().replace(".", "_");
        context.setVariable(nodeVar + ".output", output);

        for (FlowEdge edge : outEdges) {
            if (edge.isUnconditional()) {
                return nodes.get(edge.getTo());
            }

            // 条件边求值
            Map<String, Object> vars = new HashMap<>(context.getAllVariables());
            vars.put("output", output);
            if (expressionEngine.executeAsBoolean(edge.getCondition(), vars)) {
                return nodes.get(edge.getTo());
            }
        }

        return null;
    }

    private FlowNode findEntryNode() {
        Set<String> hasIncoming = new HashSet<>();
        for (FlowEdge edge : edges) {
            hasIncoming.add(edge.getTo());
        }
        for (FlowNode node : nodes.values()) {
            if (!hasIncoming.contains(node.getId())) {
                return node;
            }
        }
        return null;
    }

    private ActionType extractAction(FlowNode node, Object output) {
        if (output instanceof Map) {
            Object action = ((Map<?, ?>) output).get("action");
            if (action instanceof String) {
                try { return ActionType.valueOf((String) action); }
                catch (IllegalArgumentException e) { /* ignore */ }
            }
        }
        String actionStr = node.getConfig("action", "PASS");
        try { return ActionType.valueOf(actionStr); }
        catch (IllegalArgumentException e) { return ActionType.PASS; }
    }

    private String extractReason(Object output) {
        if (output instanceof Map) {
            Object reason = ((Map<?, ?>) output).get("reason");
            if (reason instanceof String) return (String) reason;
        }
        return null;
    }

    private FlowExecutionResult buildResult(List<String> path, Map<String, Object> outputs,
                                             boolean success, long startNanos) {
        return FlowExecutionResult.builder()
            .flowId(flowId).success(success)
            .decisionPath(path).nodeOutputs(outputs)
            .durationMs((System.nanoTime() - startNanos) / 1_000_000)
            .build();
    }

    private FlowExecutionResult buildResult(List<String> path, Map<String, Object> outputs,
                                             ActionType action, String reason, long startNanos) {
        return FlowExecutionResult.builder()
            .flowId(flowId).success(true)
            .finalAction(action).finalReason(reason)
            .decisionPath(path).nodeOutputs(outputs)
            .durationMs((System.nanoTime() - startNanos) / 1_000_000)
            .build();
    }

    @Override
    public String getRuleId() { return flowId; }
    @Override
    public String getName() { return name; }
    @Override
    public int getVersion() { return version; }
    @Override
    public String getRuleType() { return RULE_TYPE; }

    public Map<String, FlowNode> getNodes() { return nodes; }
    public List<FlowEdge> getEdges() { return edges; }

    @Override
    public String toString() {
        return "CompiledDAG{id='" + flowId + "', nodes=" + nodes.size()
            + ", edges=" + edges.size() + '}';
    }
}
