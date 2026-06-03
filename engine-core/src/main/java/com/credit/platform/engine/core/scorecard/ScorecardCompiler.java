package com.credit.platform.engine.core.scorecard;

import com.credit.platform.engine.common.exception.RuleCompileException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * 评分卡编译器 — 将 JSON 评分卡定义编译为 {@link CompiledScorecard}。
 *
 * <pre>
 * ScorecardCompiler compiler = new ScorecardCompiler();
 * CompiledScorecard sc = compiler.compile(json);
 * </pre>
 */
public class ScorecardCompiler {

    private final ObjectMapper objectMapper;

    public ScorecardCompiler() {
        this.objectMapper = new ObjectMapper();
    }

    public ScorecardCompiler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 从 JSON 字符串编译评分卡。
     *
     * @param json 评分卡 JSON 定义
     * @return 编译后的评分卡
     * @throws RuleCompileException 编译失败
     */
    public CompiledScorecard compile(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            return compile(root);
        } catch (RuleCompileException e) {
            throw e;
        } catch (Exception e) {
            throw new RuleCompileException("Failed to parse scorecard JSON: " + e.getMessage(), e);
        }
    }

    /**
     * 从 JsonNode 编译评分卡。
     */
    public CompiledScorecard compile(JsonNode root) {
        String scorecardId = requiredText(root, "scorecardId", "scorecardId is required");
        String name = optionalText(root, "name", scorecardId);
        int version = root.path("version").asInt(1);
        int initialScore = root.path("initialScore").asInt(0);

        // 编译特征列表
        JsonNode charsNode = requiredNode(root, "characteristics",
            "characteristics is required for scorecard: " + scorecardId);
        if (!charsNode.isArray() || charsNode.isEmpty()) {
            throw new RuleCompileException(
                "Scorecard '" + scorecardId + "' must have at least one characteristic");
        }

        List<CompiledScorecard.Characteristic> characteristics = new ArrayList<>();
        for (JsonNode charNode : charsNode) {
            characteristics.add(compileCharacteristic(charNode));
        }

        // 编译截断阈值
        JsonNode cutoffNode = requiredNode(root, "cutoff",
            "cutoff is required for scorecard: " + scorecardId);
        CompiledScorecard.Cutoff cutoff = compileCutoff(cutoffNode);

        return new CompiledScorecard(scorecardId, name, version,
            initialScore, characteristics, cutoff);
    }

    private CompiledScorecard.Characteristic compileCharacteristic(JsonNode node) {
        String charName = requiredText(node, "name", "characteristic name is required");
        String field = requiredText(node, "field", "characteristic field is required");

        JsonNode binsNode = requiredNode(node, "bins",
            "bins is required for characteristic: " + charName);
        if (!binsNode.isArray() || binsNode.isEmpty()) {
            throw new RuleCompileException(
                "Characteristic '" + charName + "' must have at least one bin");
        }

        List<CompiledScorecard.Bin> bins = new ArrayList<>();
        for (JsonNode binNode : binsNode) {
            bins.add(compileBin(binNode, charName));
        }

        return new CompiledScorecard.Characteristic(charName, field, bins);
    }

    private CompiledScorecard.Bin compileBin(JsonNode node, String charName) {
        JsonNode rangeNode = node.path("range");
        if (!rangeNode.isArray() || rangeNode.size() != 2) {
            throw new RuleCompileException(
                "Bin in characteristic '" + charName + "' must have range as [lower, upper]");
        }

        Object lower = rangeNode.get(0).isNull() ? null : extractNumber(rangeNode.get(0));
        Object upper = rangeNode.get(1).isNull() ? null : extractNumber(rangeNode.get(1));

        int score = node.path("score").asInt(0);
        String reason = optionalText(node, "reason", null);
        String label = optionalText(node, "label", formatRange(lower, upper));

        return new CompiledScorecard.Bin(lower, upper, score, reason, label);
    }

    private CompiledScorecard.Cutoff compileCutoff(JsonNode node) {
        int reject = node.path("reject").asInt(0);
        int review = node.path("review").asInt(0);
        int pass = node.path("pass").asInt(0);

        if (review < reject) {
            throw new RuleCompileException("cutoff.review must be >= cutoff.reject");
        }
        if (pass < review) {
            throw new RuleCompileException("cutoff.pass must be >= cutoff.review");
        }

        return new CompiledScorecard.Cutoff(reject, review, pass);
    }

    private Object extractNumber(JsonNode node) {
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble()) return node.asDouble();
        return node.asDouble();
    }

    private String formatRange(Object lower, Object upper) {
        String low = (lower == null) ? "-∞" : lower.toString();
        String high = (upper == null) ? "+∞" : upper.toString();
        return "[" + low + ", " + high + ")";
    }

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
