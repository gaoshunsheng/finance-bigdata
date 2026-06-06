package com.credit.platform.data.service.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 企业画像查询服务 — 聚合多个数据源生成统一企业画像。
 *
 * <p>数据源:
 * <ul>
 *   <li>内部: 数仓客户维度数据（DWS 层）— Redis / HBase</li>
 *   <li>外部: 工商/司法/税务/运营商等外部 API 数据 — Redis / HBase</li>
 *   <li>决策: 历史决策记录（ES）</li>
 * </ul>
 *
 * <p>查询策略: Redis -> HBase -> ES，任何数据源不可用时优雅回退到模拟数据。
 * <p>目标: P99 < 500ms
 */
@Service
public class EnterpriseProfileService {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseProfileService.class);
    private static final DateTimeFormatter ES_INDEX_SUFFIX = DateTimeFormatter.ofPattern("yyyy.MM");

    private final StringRedisTemplate stringRedisTemplate;
    private final Connection hbaseConnection;
    private final ElasticsearchClient esClient;
    private final ObjectMapper objectMapper;

    public EnterpriseProfileService(StringRedisTemplate stringRedisTemplate,
                                    Connection hbaseConnection,
                                    ElasticsearchClient esClient,
                                    ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.hbaseConnection = hbaseConnection;
        this.esClient = esClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询企业画像。
     *
     * @param enterpriseId 企业 ID
     * @return 企业画像数据（多源聚合）
     */
    public Map<String, Object> queryProfile(String enterpriseId) {
        long start = System.currentTimeMillis();

        Map<String, Object> profile = new LinkedHashMap<>();

        // 基本信息（DWS 层: Redis -> HBase）
        profile.put("basicInfo", queryBasicInfo(enterpriseId));

        // 工商信息（外部 API: Redis -> HBase）
        profile.put("businessRegistration", queryBusinessRegistration(enterpriseId));

        // 信用摘要（ES 决策日志聚合）
        profile.put("creditSummary", queryCreditSummary(enterpriseId));

        // 关联人员
        // TODO: [PLACEHOLDER] 硬编码假数据，需替换为真实数据源查询
        profile.put("_mockData", true);
        profile.put("relatedPersons", List.of(
                Map.of("name", "张**", "role", "法人", "idCard", "110***********1234")
        ));

        // 风险信号
        // TODO: [PLACEHOLDER] 硬编码假数据，需替换为真实数据源查询
        profile.put("riskSignals", List.of(
                Map.of("type", "OVERDUE", "level", "WARNING", "description", "近6月逾期2次")
        ));

        // 数据完整性
        // TODO: [PLACEHOLDER] 硬编码假数据，需替换为真实数据源查询
        List<String> availableSources = List.of("internal", "business_registration", "credit_bureau");
        List<String> missingSources = List.of("judicial", "telecom");
        profile.put("dataSourceStatus", Map.of(
                "available", availableSources,
                "missing", missingSources
        ));

        long elapsed = System.currentTimeMillis() - start;
        log.info("企业画像查询完成: enterpriseId={}, 耗时={}ms", enterpriseId, elapsed);
        return profile;
    }

    // ========== 基本信息: Redis -> HBase ==========

    private Map<String, Object> queryBasicInfo(String enterpriseId) {
        // 1. 尝试 Redis
        try {
            String key = "profile:basic:" + enterpriseId;
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value != null) {
                log.debug("基本信息命中 Redis: enterpriseId={}", enterpriseId);
                return objectMapper.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
            }
        } catch (Exception e) {
            log.warn("Redis 查询基本信息失败，回退到 HBase: enterpriseId={}, error={}",
                    enterpriseId, e.getMessage());
        }

        // 2. 尝试 HBase
        try {
            Table table = hbaseConnection.getTable(TableName.valueOf("enterprise_profile"));
            Get get = new Get(Bytes.toBytes(enterpriseId));
            get.addFamily(Bytes.toBytes("cf"));
            Result result = table.get(get);
            table.close();

            if (!result.isEmpty()) {
                // 从 HBase cf 列族逐列读取
                Map<String, Object> info = new LinkedHashMap<>();
                putIfPresent(info, result, "cf", "enterpriseId");
                putIfPresent(info, result, "cf", "enterpriseName");
                putIfPresent(info, result, "cf", "unifiedSocialCreditCode");
                putIfPresent(info, result, "cf", "registeredCapital");
                putIfPresent(info, result, "cf", "establishedDate");
                putIfPresent(info, result, "cf", "legalPerson");
                putIfPresent(info, result, "cf", "industry");
                putIfPresent(info, result, "cf", "status");

                if (!info.isEmpty()) {
                    log.debug("基本信息命中 HBase: enterpriseId={}", enterpriseId);
                    // 回写 Redis 缓存
                    cacheToRedis("profile:basic:" + enterpriseId, info);
                    return info;
                }
            }
        } catch (Exception e) {
            log.warn("HBase 查询基本信息失败: enterpriseId={}, error={}",
                    enterpriseId, e.getMessage());
        }

        // 3. 回退到模拟数据
        log.info("基本信息数据源不可用，使用模拟数据: enterpriseId={}", enterpriseId);
        return fallbackBasicInfo(enterpriseId);
    }

    // ========== 工商信息: Redis -> HBase ==========

    private Map<String, Object> queryBusinessRegistration(String enterpriseId) {
        // 1. 尝试 Redis
        try {
            String key = "profile:business:" + enterpriseId;
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value != null) {
                log.debug("工商信息命中 Redis: enterpriseId={}", enterpriseId);
                return objectMapper.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
            }
        } catch (Exception e) {
            log.warn("Redis 查询工商信息失败，回退到 HBase: enterpriseId={}, error={}",
                    enterpriseId, e.getMessage());
        }

        // 2. 尝试 HBase
        try {
            Table table = hbaseConnection.getTable(TableName.valueOf("enterprise_profile"));
            Get get = new Get(Bytes.toBytes(enterpriseId));
            get.addFamily(Bytes.toBytes("biz"));
            Result result = table.get(get);
            table.close();

            if (!result.isEmpty()) {
                Map<String, Object> info = new LinkedHashMap<>();
                putIfPresent(info, result, "biz", "registrationStatus");
                putIfPresent(info, result, "biz", "businessScope");
                putIfPresent(info, result, "biz", "registeredAddress");
                putIfPresent(info, result, "biz", "lastUpdateTime");

                if (!info.isEmpty()) {
                    log.debug("工商信息命中 HBase: enterpriseId={}", enterpriseId);
                    cacheToRedis("profile:business:" + enterpriseId, info);
                    return info;
                }
            }
        } catch (Exception e) {
            log.warn("HBase 查询工商信息失败: enterpriseId={}, error={}",
                    enterpriseId, e.getMessage());
        }

        // 3. 回退到模拟数据
        log.info("工商信息数据源不可用，使用模拟数据: enterpriseId={}", enterpriseId);
        return fallbackBusinessRegistration(enterpriseId);
    }

    // ========== 信用摘要: ES 决策日志聚合 ==========

    private Map<String, Object> queryCreditSummary(String enterpriseId) {
        try {
            // 搜索最近 12 个月的决策日志索引
            String[] indices = buildRecentIndices(12);

            Query query = Query.of(q -> q.term(t -> t
                    .field("customerId")
                    .value(v -> v.stringValue(enterpriseId))));

            SearchRequest request = SearchRequest.of(s -> s
                    .index(Arrays.asList(indices))
                    .query(query)
                    .size(1000)
                    .sort(so -> so.field(f -> f.field("timestamp").order(
                            co.elastic.clients.elasticsearch._types.SortOrder.Desc))));

            SearchResponse<Map> response = esClient.search(request, Map.class);

            int totalLoanCount = (int) response.hits().total().value();
            int activeLoanCount = 0;
            int overdueCount = 0;
            double totalLoanAmount = 0.0;
            double activeLoanAmount = 0.0;
            int maxOverdueDays = 0;

            for (Hit<Map> hit : response.hits().hits()) {
                Map source = hit.source();
                if (source == null) continue;

                Object decisionResult = source.get("decisionResult");
                String resultStr = decisionResult != null ? decisionResult.toString() : "";

                if ("APPROVED".equalsIgnoreCase(resultStr) || "PASS".equalsIgnoreCase(resultStr)) {
                    activeLoanCount++;
                    // 尝试从 outputSnapshot 中解析金额
                    activeLoanAmount += extractAmount(source.get("outputSnapshot"));
                }
                if ("REJECTED".equalsIgnoreCase(resultStr)) {
                    // 不计入活跃贷款
                }
                if ("OVERDUE".equalsIgnoreCase(resultStr)) {
                    overdueCount++;
                    int days = extractOverdueDays(source.get("outputSnapshot"));
                    maxOverdueDays = Math.max(maxOverdueDays, days);
                }
                totalLoanAmount += extractAmount(source.get("outputSnapshot"));
            }

            // 计算信用评分和风险等级
            int creditScore = calculateCreditScore(totalLoanCount, overdueCount);
            String riskLevel = calculateRiskLevel(overdueCount, creditScore);

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("totalLoanCount", totalLoanCount);
            info.put("totalLoanAmount", totalLoanAmount);
            info.put("activeLoanCount", activeLoanCount);
            info.put("activeLoanAmount", activeLoanAmount);
            info.put("overdueCount", overdueCount);
            info.put("maxOverdueDays", maxOverdueDays);
            info.put("creditScore", creditScore);
            info.put("riskLevel", riskLevel);

            log.debug("信用摘要命中 ES: enterpriseId={}, totalLoanCount={}", enterpriseId, totalLoanCount);
            return info;

        } catch (Exception e) {
            log.warn("ES 查询信用摘要失败: enterpriseId={}, error={}",
                    enterpriseId, e.getMessage());
        }

        // 回退到模拟数据
        log.info("信用摘要数据源不可用，使用模拟数据: enterpriseId={}", enterpriseId);
        return fallbackCreditSummary(enterpriseId);
    }

    // ========== ES 索引构建 ==========

    private String[] buildRecentIndices(int months) {
        String[] indices = new String[months];
        LocalDate date = LocalDate.now();
        for (int i = 0; i < months; i++) {
            indices[i] = "decision-log-" + ES_INDEX_SUFFIX.format(date.atStartOfDay());
            date = date.minusMonths(1);
        }
        return indices;
    }

    // ========== 辅助方法 ==========

    /**
     * 从 HBase Result 中读取指定列族和列限定符的值，非空则放入 Map。
     */
    private void putIfPresent(Map<String, Object> map, Result result, String cf, String qualifier) {
        byte[] value = result.getValue(Bytes.toBytes(cf), Bytes.toBytes(qualifier));
        if (value != null) {
            map.put(qualifier, Bytes.toString(value));
        }
    }

    /**
     * 将数据缓存到 Redis，TTL 1 小时。
     */
    private void cacheToRedis(String key, Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            stringRedisTemplate.opsForValue().set(key, json, java.time.Duration.ofHours(1));
        } catch (Exception e) {
            log.warn("Redis 回写失败: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 从 outputSnapshot 中尝试解析贷款金额。
     */
    private double extractAmount(Object outputSnapshot) {
        if (outputSnapshot == null) return 0.0;
        try {
            if (outputSnapshot instanceof String) {
                Map<String, Object> map = objectMapper.readValue((String) outputSnapshot,
                        new TypeReference<Map<String, Object>>() {});
                Object amount = map.get("loanAmount");
                if (amount instanceof Number) return ((Number) amount).doubleValue();
            } else if (outputSnapshot instanceof Map) {
                Object amount = ((Map<String, Object>) outputSnapshot).get("loanAmount");
                if (amount instanceof Number) return ((Number) amount).doubleValue();
            }
        } catch (Exception e) {
            // 忽略解析失败
        }
        return 0.0;
    }

    /**
     * 从 outputSnapshot 中尝试解析逾期天数。
     */
    private int extractOverdueDays(Object outputSnapshot) {
        if (outputSnapshot == null) return 0;
        try {
            if (outputSnapshot instanceof String) {
                Map<String, Object> map = objectMapper.readValue((String) outputSnapshot,
                        new TypeReference<Map<String, Object>>() {});
                Object days = map.get("overdueDays");
                if (days instanceof Number) return ((Number) days).intValue();
            } else if (outputSnapshot instanceof Map) {
                Object days = ((Map<String, Object>) outputSnapshot).get("overdueDays");
                if (days instanceof Number) return ((Number) days).intValue();
            }
        } catch (Exception e) {
            // 忽略解析失败
        }
        return 0;
    }

    /**
     * 简易信用评分计算。
     */
    private int calculateCreditScore(int totalLoanCount, int overdueCount) {
        int score = 750; // 基准分
        score -= overdueCount * 30;
        score += Math.min(totalLoanCount, 10) * 5; // 良好借贷记录加分
        return Math.max(300, Math.min(950, score));
    }

    /**
     * 简易风险等级计算。
     */
    private String calculateRiskLevel(int overdueCount, int creditScore) {
        if (overdueCount >= 3 || creditScore < 550) return "HIGH";
        if (overdueCount >= 1 || creditScore < 700) return "MEDIUM";
        return "LOW";
    }

    // ========== 回退模拟数据 ==========
    // TODO: [PLACEHOLDER] 以下方法返回硬编码假数据，仅用于开发/测试，生产环境需替换为真实数据源

    private Map<String, Object> fallbackBasicInfo(String enterpriseId) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("enterpriseId", enterpriseId);
        info.put("enterpriseName", "示例科技有限公司");
        info.put("unifiedSocialCreditCode", "91110108MA01XXXXX");
        info.put("registeredCapital", "1000万元");
        info.put("establishedDate", "2015-03-15");
        info.put("legalPerson", "张**");
        info.put("industry", "信息技术");
        info.put("status", "存续");
        return info;
    }

    private Map<String, Object> fallbackBusinessRegistration(String enterpriseId) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("registrationStatus", "存续（在营）");
        info.put("businessScope", "技术开发、技术咨询、技术服务");
        info.put("registeredAddress", "北京市海淀区XXX路XXX号");
        info.put("lastUpdateTime", "2026-01-15");
        return info;
    }

    private Map<String, Object> fallbackCreditSummary(String enterpriseId) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("totalLoanCount", 5);
        info.put("totalLoanAmount", 5000000.00);
        info.put("activeLoanCount", 2);
        info.put("activeLoanAmount", 2000000.00);
        info.put("overdueCount", 1);
        info.put("maxOverdueDays", 15);
        info.put("creditScore", 680);
        info.put("riskLevel", "MEDIUM");
        return info;
    }
}
