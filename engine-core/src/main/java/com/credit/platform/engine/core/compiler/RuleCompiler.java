package com.credit.platform.engine.core.compiler;

import com.credit.platform.engine.common.exception.RuleCompileException;
import com.credit.platform.engine.core.compiler.model.ComparisonConditionNode;
import com.credit.platform.engine.core.compiler.model.ComparisonOperator;
import com.credit.platform.engine.core.compiler.model.ConditionNode;
import com.credit.platform.engine.core.compiler.model.HitPolicy;
import com.credit.platform.engine.core.compiler.model.LogicalConditionNode;
import com.credit.platform.engine.core.compiler.model.LogicalOperator;
import com.credit.platform.engine.core.compiler.model.RuleAction;
import com.credit.platform.engine.common.model.ActionType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则编译器 — 将 JSON 规则定义编译为不可变的 AST 产物。
 * <p>
 * 编译流程: JSON String → Jackson JsonNode → AST (ConditionNode) → CompiledRule
 * </p>
 *
 * <pre>
 * RuleCompiler compiler = new RuleCompiler();
 *
 * // 编译条件规则
 * CompiledConditionRule rule = compiler.compileConditionRule(json);
 *
 * // 编译规则集
 * CompiledRuleSet ruleSet = compiler.compileRuleSet(json);
 * </pre>
 */
public class RuleCompiler {

    private final ObjectMapper objectMapper;

    public RuleCompiler() {
        this.objectMapper = new ObjectMapper();
    }

    public RuleCompiler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 从 JSON 字符串编译条件规则。
     *
     * @param json 规则 JSON 定义
     * @return 编译后的条件规则
     * @throws RuleCompileException 编译失败
     */
    public CompiledConditionRule compileConditionRule(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            return compileConditionRule(root);
        } catch (RuleCompileException e) {
            throw e;
        } catch (Exception e) {
            throw new RuleCompileException("Failed to parse rule JSON: " + e.getMessage(), e);
        }
    }

    /**
     * 从 JsonNode 编译条件规则。
     */
    public CompiledConditionRule compileConditionRule(JsonNode root) {
        String ruleId = requiredText(root, "ruleId", "ruleId is required");
        String name = optionalText(root, "name", ruleId);
        int version = root.path("version").asInt(1);
        int priority = root.path("priority").asInt(0);

        // 编译条件树
        JsonNode conditionsNode = requiredNode(root, "conditions",
            "conditions is required for rule: " + ruleId);
        ConditionNode condition = compileConditionNode(conditionsNode);

        // 编译动作列表
        JsonNode actionsNode = requiredNode(root, "actions",
            "actions is required for rule: " + ruleId);
        List<RuleAction> actions = compileActions(actionsNode);

        if (actions.isEmpty()) {
            throw new RuleCompileException("Rule '" + ruleId + "' must have at least one action");
        }

        return new CompiledConditionRule(ruleId, name, version, priority, condition, actions);
    }

    /**
     * 从 JSON 字符串编译规则集。
     *
     * @param json 规则集 JSON 定义
     * @return 编译后的规则集
     * @throws RuleCompileException 编译失败
     */
    public CompiledRuleSet compileRuleSet(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            return compileRuleSet(root);
        } catch (RuleCompileException e) {
            throw e;
        } catch (Exception e) {
            throw new RuleCompileException("Failed to parse rule set JSON: " + e.getMessage(), e);
        }
    }

    /**
     * 从 JsonNode 编译规则集。
     */
    public CompiledRuleSet compileRuleSet(JsonNode root) {
        String ruleSetId = requiredText(root, "ruleSetId", "ruleSetId is required");
        String name = optionalText(root, "name", ruleSetId);
        int version = root.path("version").asInt(1);

        HitPolicy hitPolicy = HitPolicy.fromString(
            requiredText(root, "hitPolicy", "hitPolicy is required for rule set: " + ruleSetId));

        JsonNode rulesNode = requiredNode(root, "rules",
            "rules array is required for rule set: " + ruleSetId);
        if (!rulesNode.isArray() || rulesNode.isEmpty()) {
            throw new RuleCompileException(
                "Rule set '" + ruleSetId + "' must contain at least one rule");
        }

        List<CompiledConditionRule> rules = new ArrayList<>();
        Iterator<JsonNode> it = rulesNode.elements();
        while (it.hasNext()) {
            rules.add(compileConditionRule(it.next()));
        }

        return new CompiledRuleSet(ruleSetId, name, version, hitPolicy, rules);
    }

    // ==================== 条件树编译 ====================

    /**
     * 递归编译条件节点。
     * <p>
     * 两种形态:
     * <ul>
     *   <li>逻辑节点: { "operator": "AND/OR/NOT", "operands": [...] }</li>
     *   <li>比较节点: { "field": "age", "op": "GTE", "value": 22 }</li>
     * </ul>
     * </p>
     */
    private ConditionNode compileConditionNode(JsonNode node) {
        if (node.has("operator") && node.has("operands")) {
            // 逻辑组合节点
            return compileLogicalNode(node);
        } else if (node.has("field") && node.has("op")) {
            // 字段比较节点
            return compileComparisonNode(node);
        } else {
            throw new RuleCompileException(
                "Invalid condition node: must have either (operator+operands) or (field+op). Node: "
                    + node);
        }
    }

    private ConditionNode compileLogicalNode(JsonNode node) {
        LogicalOperator op = LogicalOperator.fromString(
            requiredText(node, "operator", "Logical operator is required"));

        JsonNode operandsNode = requiredNode(node, "operands",
            "operands is required for logical node");

        if (!operandsNode.isArray() || operandsNode.isEmpty()) {
            throw new RuleCompileException("Logical node operands must be a non-empty array");
        }

        if (op == LogicalOperator.NOT && operandsNode.size() != 1) {
            throw new RuleCompileException("NOT operator requires exactly one operand");
        }

        List<ConditionNode> children = new ArrayList<>();
        Iterator<JsonNode> it = operandsNode.elements();
        while (it.hasNext()) {
            children.add(compileConditionNode(it.next()));
        }

        return LogicalConditionNode.create(op, children);
    }

    private ConditionNode compileComparisonNode(JsonNode node) {
        String field = requiredText(node, "field", "field is required for comparison");
        ComparisonOperator op = ComparisonOperator.fromString(
            requiredText(node, "op", "op is required for comparison"));

        Object value = extractValue(node.get("value"), op);
        return new ComparisonConditionNode(field, op, value);
    }

    /**
     * 提取比较值，处理特殊类型。
     */
    private Object extractValue(JsonNode valueNode, ComparisonOperator op) {
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }

        // BETWEEN: value 为 [min, max] 数组
        if (op == ComparisonOperator.BETWEEN) {
            if (!valueNode.isArray() || valueNode.size() != 2) {
                throw new RuleCompileException(
                    "BETWEEN operator requires value as [min, max] array");
            }
            Object min = valueNode.get(0).isNull() ? null : extractScalar(valueNode.get(0));
            Object max = valueNode.get(1).isNull() ? null : extractScalar(valueNode.get(1));
            return new Object[]{min, max};
        }

        // IN: value 为数组
        if (op == ComparisonOperator.IN) {
            if (valueNode.isArray()) {
                List<Object> list = new ArrayList<>();
                for (JsonNode item : valueNode) {
                    list.add(extractScalar(item));
                }
                return list;
            }
            // 也可以是单个值（等价于只含一个元素的集合）
            return List.of(extractScalar(valueNode));
        }

        return extractScalar(valueNode);
    }

    private Object extractScalar(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
        return node.asText();
    }

    // ==================== 动作编译 ====================

    private List<RuleAction> compileActions(JsonNode actionsNode) {
        List<RuleAction> actions = new ArrayList<>();
        if (!actionsNode.isArray()) {
            throw new RuleCompileException("actions must be a JSON array");
        }
        for (JsonNode actionNode : actionsNode) {
            ActionType type = ActionType.valueOf(
                requiredText(actionNode, "type", "action type is required"));
            String reason = optionalText(actionNode, "reason", null);
            String code = optionalText(actionNode, "code", null);

            RuleAction.Builder builder = RuleAction.builder()
                .type(type).reason(reason).code(code);

            // 提取额外参数（累积到同一个 Map，避免覆盖）
            Map<String, Object> params = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = actionNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                if (!key.equals("type") && !key.equals("reason") && !key.equals("code")) {
                    params.put(key, extractScalar(entry.getValue()));
                }
            }
            if (!params.isEmpty()) {
                builder.params(params);
            }

            actions.add(builder.build());
        }
        return actions;
    }

    // ==================== 工具方法 ====================

    private String requiredText(JsonNode node, String field, String errorMsg) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isEmpty()) {
            throw new RuleCompileException(errorMsg);
        }
        return value.asText();
    }

    private String optionalText(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.path(field);
        return (value.isMissingNode() || value.isNull()) ? defaultValue : value.asText();
    }

    private JsonNode requiredNode(JsonNode parent, String field, String errorMsg) {
        JsonNode node = parent.path(field);
        if (node.isMissingNode() || node.isNull()) {
            throw new RuleCompileException(errorMsg);
        }
        return node;
    }
}
