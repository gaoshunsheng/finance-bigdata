package com.credit.platform.data.governance.lineage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据血缘分析器 — 解析 SQL/ETL 依赖关系，生成数据血缘图。
 *
 * <p>支持的解析类型:
 * <ul>
 *   <li>INSERT OVERWRITE ... SELECT ... — Spark SQL ETL 脚本</li>
 *   <li>FROM ... INSERT INTO ... — Hive SQL</li>
 *   <li>简单表引用 — FROM table_name</li>
 *   <li>JOIN 关系 — JOIN table_name</li>
 * </ul>
 */
public class LineageAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(LineageAnalyzer.class);

    /** 匹配 INSERT OVERWRITE TABLE xxx 或 INSERT INTO xxx */
    private static final Pattern TARGET_TABLE_PATTERN =
            Pattern.compile("(?i)INSERT\\s+(?:OVERWRITE\\s+)?(?:INTO\\s+)?(?:TABLE\\s+)?(\\S+)");

    /** 匹配 FROM/JOIN 后的表名（忽略别名） */
    private static final Pattern SOURCE_TABLE_PATTERN =
            Pattern.compile("(?i)(?:FROM|JOIN)\\s+(\\S+)");

    /**
     * 解析 SQL 脚本，提取血缘关系。
     *
     * @param sql          SQL 脚本内容
     * @param transformName 转换名称（如 ETL 脚本名）
     * @return 解析出的血缘边列表
     */
    public List<LineageEdge> analyzeSql(String sql, String transformName) {
        List<LineageEdge> edges = new ArrayList<>();

        // 按分号分割多条 SQL
        String[] statements = sql.split(";");

        for (String stmt : statements) {
            stmt = stmt.trim();
            if (stmt.isEmpty()) continue;

            // 提取目标表
            String targetTable = extractTargetTable(stmt);
            if (targetTable == null) {
                continue; // 非 INSERT 语句，跳过
            }

            // 提取源表
            List<String> sourceTables = extractSourceTables(stmt);

            // 创建血缘边
            for (String sourceTable : sourceTables) {
                edges.add(new LineageEdge(
                        normalizeTableName(sourceTable),
                        normalizeTableName(targetTable),
                        "ETL",
                        transformName
                ));
            }

            log.debug("血缘解析: {} → {} (sources={})", sourceTables, targetTable, sourceTables);
        }

        return edges;
    }

    /**
     * 解析多条 SQL 脚本并构建完整的血缘图。
     */
    public LineageGraph buildLineageGraph(Map<String, String> scripts) {
        LineageGraph graph = new LineageGraph();

        for (Map.Entry<String, String> entry : scripts.entrySet()) {
            String scriptName = entry.getKey();
            String sql = entry.getValue();

            List<LineageEdge> edges = analyzeSql(sql, scriptName);
            for (LineageEdge edge : edges) {
                // 添加节点（如果不存在）
                if (graph.getNodes().get(edge.getSourceId()) == null) {
                    String layer = inferLayer(edge.getSourceId());
                    graph.addNode(new LineageNode(
                            edge.getSourceId(),
                            edge.getSourceId(),
                            LineageNode.NodeType.TABLE,
                            layer, "HIVE"));
                }
                if (graph.getNodes().get(edge.getTargetId()) == null) {
                    String layer = inferLayer(edge.getTargetId());
                    graph.addNode(new LineageNode(
                            edge.getTargetId(),
                            edge.getTargetId(),
                            LineageNode.NodeType.TABLE,
                            layer, "HIVE"));
                }
                graph.addEdge(edge);
            }
        }

        log.info("血缘图构建完成: 节点数={}, 边数={}", graph.getNodes().size(), graph.getEdges().size());
        return graph;
    }

    /** 提取 INSERT 语句的目标表名 */
    private String extractTargetTable(String sql) {
        Matcher matcher = TARGET_TABLE_PATTERN.matcher(sql);
        if (matcher.find()) {
            return normalizeTableName(matcher.group(1));
        }
        return null;
    }

    /** 提取 FROM/JOIN 引用的源表名 */
    private List<String> extractSourceTables(String sql) {
        List<String> tables = new ArrayList<>();
        // 去掉 INSERT 部分后再匹配 FROM/JOIN
        String fromClause = sql.replaceAll("(?i)INSERT.*?FROM", "FROM");
        Matcher matcher = SOURCE_TABLE_PATTERN.matcher(fromClause);
        while (matcher.find()) {
            String table = matcher.group(1);
            // 过滤掉子查询关键字
            if (!isSqlKeyword(table)) {
                tables.add(table);
            }
        }
        return tables;
    }

    private boolean isSqlKeyword(String word) {
        Set<String> keywords = Set.of("SELECT", "WHERE", "GROUP", "ORDER", "HAVING",
                "LIMIT", "UNION", "EXCEPT", "INTERSECT", "ON", "AND", "OR", "NOT",
                "AS", "IN", "EXISTS", "BETWEEN", "LIKE", "CASE", "WHEN", "THEN",
                "ELSE", "END", "WITH", "OVER", "PARTITION", "LEFT", "RIGHT",
                "INNER", "OUTER", "CROSS", "FULL", "NATURAL");
        return keywords.contains(word.toUpperCase());
    }

    /** 标准化表名 — 去掉反引号、尾部分号、括号等 */
    private String normalizeTableName(String tableName) {
        return tableName
                .replace("`", "")
                .replace(";", "")
                .replaceAll("\\)$", "")
                .trim();
    }

    /** 从表名推断数据层 */
    private String inferLayer(String tableName) {
        String lower = tableName.toLowerCase();
        if (lower.startsWith("ods.")) return "ODS";
        if (lower.startsWith("dwd.")) return "DWD";
        if (lower.startsWith("dws.")) return "DWS";
        if (lower.startsWith("ads.")) return "ADS";
        return "UNKNOWN";
    }
}
