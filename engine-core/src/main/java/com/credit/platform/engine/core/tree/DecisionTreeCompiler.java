package com.credit.platform.engine.core.tree;

import com.credit.platform.engine.common.exception.RuleCompileException;
import com.credit.platform.engine.common.model.ActionType;
import com.credit.platform.engine.core.compiler.model.ComparisonConditionNode;
import com.credit.platform.engine.core.compiler.model.ComparisonOperator;
import com.credit.platform.engine.core.compiler.model.ConditionNode;
import com.credit.platform.engine.core.compiler.model.RuleAction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * 决策树编译器 — 将 JSON 决策树定义编译为 {@link CompiledDecisionTree}。
 *
 * <pre>
 * JSON 格式:
 * {
 *   "treeId": "TREE_001",
 *   "condition": {"field": "age", "op": "GTE", "value": 22},
 *   "trueChild": { ... },   // 条件为 true 时的子树
 *   "falseChild": {         // 条件为 false 时的子树
 *     "action": {"type": "REJECT", "reason": "年龄不足"}
 *   }
 * }
 * </pre>
 */
public class DecisionTreeCompiler {

    private final ObjectMapper objectMapper;

    public DecisionTreeCompiler() { this.objectMapper = new ObjectMapper(); }
    public DecisionTreeCompiler(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public CompiledDecisionTree compile(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            return compile(root);
        } catch (RuleCompileException e) {
            throw e;
        } catch (Exception e) {
            throw new RuleCompileException("Failed to parse decision tree JSON: " + e.getMessage(), e);
        }
    }

    public CompiledDecisionTree compile(JsonNode root) {
        String treeId = requiredText(root, "treeId", "treeId is required");
        String name = optionalText(root, "name", treeId);
        int version = root.path("version").asInt(1);

        DecisionTreeNode node = compileNode(root);

        return new CompiledDecisionTree(treeId, name, version, node);
    }

    private DecisionTreeNode compileNode(JsonNode node) {
        // 叶子节点: 包含 "action" 字段
        if (node.has("action")) {
            return compileLeaf(node.get("action"));
        }

        // 分支节点: 包含 "condition" + "trueChild" + "falseChild"
        if (!node.has("condition")) {
            throw new RuleCompileException("Tree node must have either 'action' or 'condition'");
        }

        ConditionNode condition = compileCondition(node.get("condition"));

        JsonNode trueChildNode = node.path("trueChild");
        JsonNode falseChildNode = node.path("falseChild");

        if (trueChildNode.isMissingNode() && falseChildNode.isMissingNode()) {
            throw new RuleCompileException("Branch node must have at least one child");
        }

        DecisionTreeNode trueChild = trueChildNode.isMissingNode()
            ? null : compileNode(trueChildNode);
        DecisionTreeNode falseChild = falseChildNode.isMissingNode()
            ? null : compileNode(falseChildNode);

        // 如果只有 trueChild，falseChild 默认为 null 叶子
        if (trueChild == null) trueChild = new LeafNode(null);
        if (falseChild == null) falseChild = new LeafNode(null);

        return new BranchNode(condition, trueChild, falseChild);
    }

    private LeafNode compileLeaf(JsonNode actionNode) {
        ActionType type = ActionType.valueOf(
            requiredText(actionNode, "type", "action type is required"));
        String reason = optionalText(actionNode, "reason", null);
        String code = optionalText(actionNode, "code", null);

        return new LeafNode(RuleAction.builder()
            .type(type).reason(reason).code(code).build());
    }

    private ConditionNode compileCondition(JsonNode node) {
        // 支持逻辑组合
        if (node.has("operator") && node.has("operands")) {
            return compileLogicalCondition(node);
        }
        // 比较条件
        if (node.has("field") && node.has("op")) {
            return compileComparisonCondition(node);
        }
        throw new RuleCompileException("Invalid condition node in tree");
    }

    private ConditionNode compileLogicalCondition(JsonNode node) {
        // 复用 RuleCompiler 的逻辑，但简化处理
        String opStr = requiredText(node, "operator", "operator required");
        var operator = com.credit.platform.engine.core.compiler.model.LogicalOperator.fromString(opStr);
        var operands = node.path("operands");
        if (!operands.isArray() || operands.isEmpty()) {
            throw new RuleCompileException("Logical node operands must be non-empty");
        }
        java.util.List<ConditionNode> children = new java.util.ArrayList<>();
        for (JsonNode child : operands) {
            children.add(compileCondition(child));
        }
        return com.credit.platform.engine.core.compiler.model.LogicalConditionNode.create(operator, children);
    }

    private ConditionNode compileComparisonCondition(JsonNode node) {
        String field = requiredText(node, "field", "field required");
        ComparisonOperator op = ComparisonOperator.fromString(
            requiredText(node, "op", "op required"));
        Object value = extractValue(node.get("value"));
        return new ComparisonConditionNode(field, op, value);
    }

    private Object extractValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
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
}
