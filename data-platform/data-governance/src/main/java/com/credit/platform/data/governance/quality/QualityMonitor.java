package com.credit.platform.data.governance.quality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 数据质量监控器 — 五维质量检测。
 *
 * <p>五维质量:
 * <ol>
 *   <li>完整性 (COMPLETENESS) — 字段空值率检查</li>
 *   <li>准确性 (ACCURACY) — 枚举值、范围、正则校验</li>
 *   <li>一致性 (CONSISTENCY) — 跨表关联一致性</li>
 *   <li>及时性 (TIMELINESS) — 数据产出 SLA 监控</li>
 *   <li>唯一性 (UNIQUENESS) — 主键重复检测</li>
 * </ol>
 *
 * <p>违规时通过企微/邮件告警通知数据 Owner。
 */
public class QualityMonitor {

    private static final Logger log = LoggerFactory.getLogger(QualityMonitor.class);

    private final List<QualityRule> rules = new CopyOnWriteArrayList<>();
    private final List<QualityViolation> violations = new CopyOnWriteArrayList<>();

    /** 唯一性检查: 已见值的去重集合，key 格式为 table.column:value */
    private final Set<String> seenValues = ConcurrentHashMap.newKeySet();

    /** 一致性检查: 可插拔的跨表引用校验器 */
    private final Map<String, ConsistencyChecker> consistencyCheckers = new ConcurrentHashMap<>();

    /** 及时性检查: 各表最近的数据到达时间 */
    private final Map<String, LocalDateTime> lastArrivalTimes = new ConcurrentHashMap<>();

    /**
     * 注册质量规则。
     */
    public void addRule(QualityRule rule) {
        rules.add(rule);
        log.info("注册质量规则: {} [{}] {}.{} expression={}",
                rule.getRuleName(), rule.getDimension(),
                rule.getTargetTable(), rule.getTargetColumn(),
                rule.getExpression());
    }

    /**
     * 批量注册规则。
     */
    public void addRules(List<QualityRule> rules) {
        rules.forEach(this::addRule);
    }

    /**
     * 检查数据记录是否符合所有质量规则。
     *
     * @param table    表名
     * @param column   列名
     * @param value    字段值
     * @return 检测到的违规列表
     */
    public List<QualityViolation> check(String table, String column, Object value) {
        List<QualityViolation> found = new ArrayList<>();

        for (QualityRule rule : rules) {
            if (!rule.getTargetTable().equals(table) || !rule.getTargetColumn().equals(column)) {
                continue;
            }

            boolean violated = evaluateRule(rule, table, column, value);
            if (violated) {
                QualityViolation v = new QualityViolation(
                        rule.getRuleId(),
                        rule.getRuleName(),
                        rule.getDimension(),
                        table,
                        column,
                        buildViolationDetail(rule, value),
                        rule.getSeverity()
                );
                found.add(v);
                violations.add(v);
                log.warn("数据质量违规: {} — {}", rule.getRuleName(), v.getViolationDetail());
            }
        }

        return found;
    }

    /**
     * 检查完整数据记录（Map 形式）。
     */
    public List<QualityViolation> checkRecord(String table, Map<String, Object> record) {
        List<QualityViolation> allViolations = new ArrayList<>();
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            allViolations.addAll(check(table, entry.getKey(), entry.getValue()));
        }
        return allViolations;
    }

    /**
     * 获取所有违规记录。
     */
    public List<QualityViolation> getViolations() {
        return Collections.unmodifiableList(violations);
    }

    /**
     * 按维度获取违规记录。
     */
    public List<QualityViolation> getViolationsByDimension(QualityRule.QualityDimension dimension) {
        return violations.stream()
                .filter(v -> v.getDimension() == dimension)
                .toList();
    }

    /**
     * 获取已注册规则数。
     */
    public int getRuleCount() {
        return rules.size();
    }

    /**
     * 获取违规总数。
     */
    public int getViolationCount() {
        return violations.size();
    }

    /** 评估单条规则 */
    private boolean evaluateRule(QualityRule rule, String table, String column, Object value) {
        String expression = rule.getExpression().toUpperCase();

        return switch (rule.getDimension()) {
            case COMPLETENESS -> {
                // 完整性检查: NOT NULL
                yield "NOT NULL".equals(expression) && value == null;
            }
            case ACCURACY -> {
                // 准确性检查
                if (expression.startsWith(">=")) {
                    double threshold = Double.parseDouble(expression.substring(2).trim());
                    yield value instanceof Number n && n.doubleValue() < threshold;
                }
                if (expression.startsWith("<=")) {
                    double threshold = Double.parseDouble(expression.substring(2).trim());
                    yield value instanceof Number n && n.doubleValue() > threshold;
                }
                if (expression.startsWith("REGEX:")) {
                    String regex = rule.getExpression().substring(6);
                    yield value != null && !value.toString().matches(regex);
                }
                if (expression.startsWith("ENUM:")) {
                    String[] allowed = expression.substring(5).split(",");
                    if (value == null) yield false;
                    String strVal = value.toString();
                    yield !Arrays.asList(allowed).contains(strVal);
                }
                yield false;
            }
            case UNIQUENESS -> {
                // 唯一性检查: 使用 ConcurrentHashMap-backed Set 进行去重
                if (value == null) yield false;
                String compositeKey = table + "." + column + ":" + value;
                boolean alreadySeen = !seenValues.add(compositeKey);
                if (alreadySeen) {
                    log.debug("唯一性违规: {} 已存在于 {}.{}", value, table, column);
                }
                yield alreadySeen;
            }
            case CONSISTENCY -> {
                // 一致性检查: REF:referenceTable.referenceColumn 格式
                if (!expression.startsWith("REF:")) yield false;
                String refPart = rule.getExpression().substring(4); // 保留原始大小写
                ConsistencyChecker checker = consistencyCheckers.get(refPart);
                if (checker == null) {
                    log.debug("未注册一致性校验器: {}", refPart);
                    yield false;
                }
                yield !checker.check(refPart, null, value);
            }
            case TIMELINESS -> {
                // 及时性检查: SLA:HH:mm 或 SLA:Xh 格式
                if (!expression.startsWith("SLA:")) yield false;
                LocalDateTime arrivalTime = lastArrivalTimes.get(table);
                if (arrivalTime == null) {
                    log.debug("未注册表 {} 的数据到达时间，跳过及时性检查", table);
                    yield false;
                }
                String slaExpr = expression.substring(4).trim();
                yield checkTimeliness(slaExpr, arrivalTime);
            }
        };
    }

    /**
     * 检查数据到达时间是否满足 SLA 要求。
     *
     * @param slaExpr     SLA 表达式，如 "08:00" 或 "2H"
     * @param arrivalTime 数据实际到达时间
     * @return true 表示违反 SLA
     */
    private boolean checkTimeliness(String slaExpr, LocalDateTime arrivalTime) {
        try {
            if (slaExpr.endsWith("H")) {
                // SLA:Xh 格式 — 数据必须在某个基准时间后 X 小时内到达
                // 这里简化为: 检查到达时间的小时数是否超过 SLA 小时数
                int slaHours = Integer.parseInt(slaExpr.substring(0, slaExpr.length() - 1).trim());
                int arrivalHour = arrivalTime.getHour();
                if (arrivalHour >= slaHours) {
                    log.debug("及时性违规: 数据到达时间 {} 超过 SLA {} 小时", arrivalTime, slaHours);
                    return true;
                }
                return false;
            } else {
                // SLA:HH:mm 格式 — 数据必须在当天指定时间之前到达
                LocalTime slaTime = LocalTime.parse(slaExpr, DateTimeFormatter.ofPattern("H:mm"));
                LocalTime arrivalLocalTime = arrivalTime.toLocalTime();
                if (arrivalLocalTime.isAfter(slaTime)) {
                    log.debug("及时性违规: 数据到达时间 {} 超过 SLA 截止时间 {}", arrivalLocalTime, slaTime);
                    return true;
                }
                return false;
            }
        } catch (Exception e) {
            log.warn("解析 SLA 表达式失败: {}", slaExpr, e);
            return false;
        }
    }

    // ========== 一致性校验接口 ==========

    /**
     * 跨表一致性校验器函数接口。
     *
     * <p>实现类负责查询参考表，判断给定值是否在参考列中存在。
     */
    @FunctionalInterface
    public interface ConsistencyChecker {
        /**
         * @param refTable  参考表名（含列，如 dim_product.product_id）
         * @param refColumn 参考列名（可为 null，已包含在 refTable 中）
         * @param value     待校验的值
         * @return true 表示值在参考表中存在（一致），false 表示不存在（不一致）
         */
        boolean check(String refTable, String refColumn, Object value);
    }

    /**
     * 注册一致性校验器。
     *
     * @param refKey  参考标识，对应规则表达式中 REF: 后面的部分（如 dim_product.product_id）
     * @param checker 校验器实现
     */
    public void registerConsistencyChecker(String refKey, ConsistencyChecker checker) {
        consistencyCheckers.put(refKey, checker);
        log.info("注册一致性校验器: {}", refKey);
    }

    /**
     * 注册数据到达时间（用于及时性检查）。
     *
     * @param table       表名
     * @param arrivalTime 数据实际到达时间
     */
    public void registerArrival(String table, LocalDateTime arrivalTime) {
        lastArrivalTimes.put(table, arrivalTime);
        log.info("注册表 {} 数据到达时间: {}", table, arrivalTime);
    }

    /**
     * 清除唯一性检查的去重集合（用于测试清理或周期性重置）。
     */
    public void clearSeenValues() {
        seenValues.clear();
        log.debug("已清除唯一性去重集合");
    }

    private String buildViolationDetail(QualityRule rule, Object value) {
        return String.format("表 %s.%s 的值 [%s] 不满足规则: %s",
                rule.getTargetTable(), rule.getTargetColumn(),
                value, rule.getExpression());
    }
}
