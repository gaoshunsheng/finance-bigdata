package com.credit.platform.engine.core.table;

import com.credit.platform.engine.common.exception.RuleCompileException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 决策表编译器 — 将 JSON 决策表定义编译为 {@link CompiledDecisionTable}。
 */
public class DecisionTableCompiler {

    private final ObjectMapper objectMapper;

    public DecisionTableCompiler() { this.objectMapper = new ObjectMapper(); }
    public DecisionTableCompiler(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public CompiledDecisionTable compile(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            return compile(root);
        } catch (RuleCompileException e) {
            throw e;
        } catch (Exception e) {
            throw new RuleCompileException("Failed to parse decision table JSON: " + e.getMessage(), e);
        }
    }

    public CompiledDecisionTable compile(JsonNode root) {
        String tableId = requiredText(root, "tableId", "tableId is required");
        String name = optionalText(root, "name", tableId);
        int version = root.path("version").asInt(1);

        // 编译列定义
        JsonNode columnsNode = requiredNode(root, "columns", "columns is required");
        if (!columnsNode.isArray() || columnsNode.isEmpty()) {
            throw new RuleCompileException("Decision table must have at least one column");
        }
        List<CompiledDecisionTable.Column> columns = new ArrayList<>();
        for (JsonNode colNode : columnsNode) {
            columns.add(new CompiledDecisionTable.Column(
                requiredText(colNode, "name", "column name is required"),
                requiredText(colNode, "field", "column field is required")
            ));
        }

        // 编译行定义
        JsonNode rowsNode = requiredNode(root, "rows", "rows is required");
        if (!rowsNode.isArray() || rowsNode.isEmpty()) {
            throw new RuleCompileException("Decision table must have at least one row");
        }
        List<CompiledDecisionTable.TableRow> rows = new ArrayList<>();
        for (JsonNode rowNode : rowsNode) {
            rows.add(compileRow(rowNode, tableId));
        }

        String hitPolicy = optionalText(root, "hitPolicy", "FIRST_MATCH");

        return new CompiledDecisionTable(tableId, name, version, columns, rows, hitPolicy);
    }

    private CompiledDecisionTable.TableRow compileRow(JsonNode rowNode, String tableId) {
        JsonNode conditionsNode = requiredNode(rowNode, "conditions",
            "Row conditions is required in table: " + tableId);
        if (!conditionsNode.isArray()) {
            throw new RuleCompileException("Row conditions must be an array in table: " + tableId);
        }

        List<Object> conditions = new ArrayList<>();
        for (JsonNode cond : conditionsNode) {
            conditions.add(extractValue(cond));
        }

        JsonNode resultNode = rowNode.path("result");
        Map<String, Object> result = new LinkedHashMap<>();
        if (resultNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = resultNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                result.put(entry.getKey(), extractValue(entry.getValue()));
            }
        }

        return new CompiledDecisionTable.TableRow(conditions, result);
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

    private JsonNode requiredNode(JsonNode parent, String field, String msg) {
        JsonNode n = parent.path(field);
        if (n.isMissingNode() || n.isNull()) throw new RuleCompileException(msg);
        return n;
    }
}
