package com.credit.platform.engine.core.flow;

import com.credit.platform.engine.common.exception.RuleCompileException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * DAG 决策流编译器 — 将 JSON 决策流定义编译为 {@link CompiledDAG}。
 */
public class DAGCompiler {

    private final ObjectMapper objectMapper;

    public DAGCompiler() { this.objectMapper = new ObjectMapper(); }
    public DAGCompiler(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public CompiledDAG compile(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            return compile(root);
        } catch (RuleCompileException e) {
            throw e;
        } catch (Exception e) {
            throw new RuleCompileException("Failed to parse DAG JSON: " + e.getMessage(), e);
        }
    }

    public CompiledDAG compile(JsonNode root) {
        String flowId = requiredText(root, "flowId", "flowId is required");
        String name = optionalText(root, "name", flowId);
        int version = root.path("version").asInt(1);

        // 编译节点
        JsonNode nodesNode = requiredNode(root, "nodes", "nodes is required");
        if (!nodesNode.isArray() || nodesNode.isEmpty()) {
            throw new RuleCompileException("DAG must have at least one node");
        }
        List<FlowNode> nodes = new ArrayList<>();
        for (JsonNode n : nodesNode) {
            nodes.add(compileNode(n));
        }

        // 编译边
        List<FlowEdge> edges = new ArrayList<>();
        JsonNode edgesNode = root.path("edges");
        if (edgesNode.isArray()) {
            for (JsonNode e : edgesNode) {
                edges.add(compileEdge(e));
            }
        }

        return new CompiledDAG(flowId, name, version, nodes, edges);
    }

    private FlowNode compileNode(JsonNode node) {
        String id = requiredText(node, "id", "node id is required");
        FlowNode.Type type = FlowNode.Type.valueOf(
            requiredText(node, "type", "node type is required for: " + id));

        Map<String, Object> config = new HashMap<>();
        JsonNode configNode = node.path("config");
        if (configNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = configNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                config.put(entry.getKey(), extractValue(entry.getValue()));
            }
        }

        return new FlowNode(id, type, config);
    }

    private FlowEdge compileEdge(JsonNode edge) {
        String from = requiredText(edge, "from", "edge from is required");
        String to = requiredText(edge, "to", "edge to is required");
        String condition = edge.has("condition") ? edge.get("condition").asText(null) : null;
        return new FlowEdge(from, to, condition);
    }

    private Object extractValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isTextual()) return node.asText();
        return node.asText();
    }

    private String requiredText(JsonNode node, String field, String msg) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull() || v.asText().isEmpty()) throw new RuleCompileException(msg);
        return v.asText();
    }

    private String optionalText(JsonNode node, String field, String def) {
        JsonNode v = node.path(field);
        return (v.isMissingNode() || v.isNull()) ? def : v.asText();
    }

    private JsonNode requiredNode(JsonNode parent, String field, String msg) {
        JsonNode n = parent.path(field);
        if (n.isMissingNode() || n.isNull()) throw new RuleCompileException(msg);
        return n;
    }
}
