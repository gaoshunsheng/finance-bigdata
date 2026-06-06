package com.credit.platform.admin.service;

import java.util.*;

import org.springframework.stereotype.Service;

import com.credit.platform.admin.model.RuleEntity;
import com.credit.platform.admin.model.VersionDiff;
import com.credit.platform.admin.model.VersionDiff.DiffType;
import com.credit.platform.admin.model.VersionDiff.FieldDiff;

/**
 * 版本差异服务 — 比较两个版本规则内容，生成结构化差异报告。
 * <p>
 * 对 JSON 规则内容做深度比较，检测新增/删除/修改/未变字段。
 * 使用递归 Map 比较算法，支持嵌套 JSON 结构。
 * </p>
 */
@Service
public class VersionDiffService {

    private final RuleRepository ruleRepository;

    public VersionDiffService(RuleRepository ruleRepository) {
        this.ruleRepository = Objects.requireNonNull(ruleRepository);
    }

    /**
     * 比较两个版本的规则内容。
     *
     * @param type          规则类型
     * @param id            规则 ID
     * @param sourceVersion 源版本（旧）
     * @param targetVersion 目标版本（新）
     * @return VersionDiff 差异报告
     */
    public VersionDiff diff(String type, String id, int sourceVersion, int targetVersion) {
        RuleEntity source = ruleRepository.find(type, id, sourceVersion)
            .orElseThrow(() -> new IllegalArgumentException(
                type + ":" + id + " v" + sourceVersion + " not found"));
        RuleEntity target = ruleRepository.find(type, id, targetVersion)
            .orElseThrow(() -> new IllegalArgumentException(
                type + ":" + id + " v" + targetVersion + " not found"));

        return diffEntities(source, target);
    }

    /**
     * 比较最新两个版本。
     */
    public VersionDiff diffLatest(String type, String id) {
        List<RuleEntity> versions = ruleRepository.findVersions(type, id);
        if (versions.size() < 2) {
            throw new IllegalArgumentException(
                "Need at least 2 versions to diff, found " + versions.size());
        }

        // findVersions 返回按版本号倒序
        RuleEntity newer = versions.get(0);
        RuleEntity older = versions.get(1);
        return diffEntities(older, newer);
    }

    /**
     * 比较两个 RuleEntity。
     */
    VersionDiff diffEntities(RuleEntity source, RuleEntity target) {
        Map<String, Object> sourceMap = parseJson(source.getContent());
        Map<String, Object> targetMap = parseJson(target.getContent());

        VersionDiff.Builder builder = VersionDiff.builder()
            .targetType(source.getType())
            .targetId(source.getId())
            .sourceVersion(source.getVersion())
            .targetVersion(target.getVersion());

        // 比较元数据字段
        compareMetadata(source, target, builder);

        // 比较内容 JSON
        compareMaps(sourceMap, targetMap, "", builder);

        return builder.build();
    }

    /**
     * 比较元数据差异。
     */
    private void compareMetadata(RuleEntity source, RuleEntity target, VersionDiff.Builder builder) {
        if (!Objects.equals(source.getName(), target.getName())) {
            builder.addDiff(new FieldDiff("name", DiffType.MODIFIED, source.getName(), target.getName()));
        }
        if (!Objects.equals(source.getDescription(), target.getDescription())) {
            builder.addDiff(new FieldDiff("description", DiffType.MODIFIED,
                source.getDescription(), target.getDescription()));
        }
    }

    /**
     * 递归比较两个 Map。
     */
    @SuppressWarnings("unchecked")
    private void compareMaps(Map<String, Object> source, Map<String, Object> target,
                              String pathPrefix, VersionDiff.Builder builder) {
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(source.keySet());
        allKeys.addAll(target.keySet());

        for (String key : allKeys) {
            String path = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;
            Object sourceVal = source.get(key);
            Object targetVal = target.get(key);

            if (!source.containsKey(key)) {
                // 新增字段
                builder.addDiff(new FieldDiff(path, DiffType.ADDED, null, String.valueOf(targetVal)));
            } else if (!target.containsKey(key)) {
                // 删除字段
                builder.addDiff(new FieldDiff(path, DiffType.REMOVED, String.valueOf(sourceVal), null));
            } else if (sourceVal instanceof Map && targetVal instanceof Map) {
                // 嵌套 Map — 递归比较
                compareMaps((Map<String, Object>) sourceVal, (Map<String, Object>) targetVal, path, builder);
            } else if (sourceVal instanceof List && targetVal instanceof List) {
                // List — 简单字符串比较
                String oldStr = String.valueOf(sourceVal);
                String newStr = String.valueOf(targetVal);
                if (!oldStr.equals(newStr)) {
                    builder.addDiff(new FieldDiff(path, DiffType.MODIFIED, oldStr, newStr));
                }
            } else {
                // 简单值 — 比较字符串表示
                String oldStr = String.valueOf(sourceVal);
                String newStr = String.valueOf(targetVal);
                if (!oldStr.equals(newStr)) {
                    builder.addDiff(new FieldDiff(path, DiffType.MODIFIED, oldStr, newStr));
                }
            }
        }
    }

    /**
     * 解析 JSON 字符串为 Map。
     * 使用简易 JSON 解析器（纯 Java，无额外依赖）。
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return SimpleJsonParser.parse(json);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /**
     * 简易 JSON 解析器 — 只支持 Map<String, Object> 的第一层。
     * 不引入 Jackson/Gson 等外部依赖。
     */
    static class SimpleJsonParser {

        static Map<String, Object> parse(String json) {
            json = json.trim();
            if (!json.startsWith("{") || !json.endsWith("}")) {
                return Collections.emptyMap();
            }
            return parseObject(json);
        }

        private static Map<String, Object> parseObject(String json) {
            Map<String, Object> result = new LinkedHashMap<>();
            json = json.trim();
            if (json.startsWith("{")) json = json.substring(1);
            if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

            int i = 0;
            while (i < json.length()) {
                // 跳过空白和逗号
                while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == ','
                    || json.charAt(i) == '\n' || json.charAt(i) == '\r' || json.charAt(i) == '\t')) {
                    i++;
                }
                if (i >= json.length()) break;

                // 读取 key（处理转义引号）
                if (json.charAt(i) != '"') break;
                int keyEnd = findStringEnd(json, i + 1);
                if (keyEnd <= i + 1) break;
                String key = json.substring(i + 1, keyEnd);
                i = keyEnd + 1;

                // 跳过冒号和空白
                while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == ':'
                    || json.charAt(i) == '\t')) {
                    i++;
                }
                if (i >= json.length()) break;

                // 读取 value
                Object value;
                char c = json.charAt(i);
                if (c == '"') {
                    // 字符串
                    int valEnd = findStringEnd(json, i + 1);
                    value = json.substring(i + 1, valEnd);
                    i = valEnd + 1;
                } else if (c == '{') {
                    // 嵌套对象
                    int objEnd = findMatchingBracket(json, i, '{', '}');
                    String subJson = json.substring(i, objEnd + 1);
                    value = parseObject(subJson);
                    i = objEnd + 1;
                } else if (c == '[') {
                    // 数组
                    int arrEnd = findMatchingBracket(json, i, '[', ']');
                    value = json.substring(i, arrEnd + 1);
                    i = arrEnd + 1;
                } else if (c == 't' || c == 'f') {
                    // boolean
                    if (json.substring(i).startsWith("true")) {
                        value = true;
                        i += 4;
                    } else {
                        value = false;
                        i += 5;
                    }
                } else if (c == 'n') {
                    value = null;
                    i += 4;
                } else {
                    // 数字
                    int numEnd = i;
                    while (numEnd < json.length() && json.charAt(numEnd) != ','
                        && json.charAt(numEnd) != '}' && json.charAt(numEnd) != ']'
                        && json.charAt(numEnd) != ' ' && json.charAt(numEnd) != '\n') {
                        numEnd++;
                    }
                    String numStr = json.substring(i, numEnd).trim();
                    try {
                        if (numStr.contains(".")) {
                            value = Double.parseDouble(numStr);
                        } else {
                            value = Long.parseLong(numStr);
                        }
                    } catch (NumberFormatException e) {
                        value = numStr;
                    }
                    i = numEnd;
                }

                result.put(key, value);
            }
            return result;
        }

        private static int findStringEnd(String json, int start) {
            int i = start;
            while (i < json.length()) {
                if (json.charAt(i) == '\\' && i + 1 < json.length()) {
                    i += 2;
                } else if (json.charAt(i) == '"') {
                    return i;
                } else {
                    i++;
                }
            }
            return json.length() - 1;
        }

        private static int findMatchingBracket(String json, int start, char open, char close) {
            int depth = 0;
            boolean inString = false;
            for (int i = start; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '\\' && inString) {
                    i++;
                    continue;
                }
                if (c == '"') {
                    inString = !inString;
                    continue;
                }
                if (inString) continue;
                if (c == open) depth++;
                if (c == close) {
                    depth--;
                    if (depth == 0) return i;
                }
            }
            return json.length() - 1;
        }
    }
}
