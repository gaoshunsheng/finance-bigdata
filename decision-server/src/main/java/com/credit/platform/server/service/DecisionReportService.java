package com.credit.platform.server.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import com.credit.platform.server.model.DecisionLogDocument;
import com.credit.platform.server.repository.DecisionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 决策报告服务 — 基于 ES 聚合查询生成运营报表。
 */
@Service
public class DecisionReportService {

    private static final Logger log = LoggerFactory.getLogger(DecisionReportService.class);
    private static final DateTimeFormatter INDEX_SUFFIX = DateTimeFormatter.ofPattern("yyyy.MM");

    private final DecisionLogRepository logRepository;

    public DecisionReportService(DecisionLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * 获取每日决策统计。
     */
    public List<Map<String, Object>> getDailyStats(LocalDate start, LocalDate end) {
        try {
            List<String> indices = Arrays.asList(buildIndicesBetween(start, end));
            ElasticsearchClient client = logRepository.getEsClient();

            SearchRequest request = SearchRequest.of(s -> s
                    .index(indices)
                    .size(0)
                    .query(q -> q.range(r -> r
                            .field("timestamp")
                            .gte(JsonData.of(start.atStartOfDay().toString()))
                            .lte(JsonData.of(end.plusDays(1).atStartOfDay().toString()))))
                    .aggregations("daily", Aggregation.of(a -> a
                            .dateHistogram(dh -> dh
                                    .field("timestamp")
                                    .calendarInterval(CalendarInterval.Day)
                                    .format("yyyy-MM-dd")
                                    .minDocCount(0))
                            .aggregations("by_result", Aggregation.of(aa -> aa
                                    .terms(t -> t.field("decisionResult").size(10))))
                            .aggregations("avg_score", Aggregation.of(aa -> aa
                                    .avg(avg -> avg.field("score"))))
                    )));

            SearchResponse<DecisionLogDocument> response = client.search(request, DecisionLogDocument.class);

            List<Map<String, Object>> results = new ArrayList<>();
            if (response.aggregations() != null && response.aggregations().get("daily") != null) {
                for (DateHistogramBucket bucket : response.aggregations().get("daily").dateHistogram().buckets().array()) {
                    Map<String, Object> dayStat = new LinkedHashMap<>();
                    dayStat.put("date", bucket.keyAsString());
                    dayStat.put("total", bucket.docCount());

                    long approve = 0, reject = 0, manual = 0;
                    if (bucket.aggregations() != null && bucket.aggregations().get("by_result") != null) {
                        for (StringTermsBucket rb : bucket.aggregations().get("by_result").sterms().buckets().array()) {
                            switch (rb.key().stringValue()) {
                                case "APPROVE", "PASS" -> approve = rb.docCount();
                                case "REJECT" -> reject = rb.docCount();
                                case "MANUAL" -> manual = rb.docCount();
                            }
                        }
                    }
                    dayStat.put("approve", approve);
                    dayStat.put("reject", reject);
                    dayStat.put("manual", manual);

                    if (bucket.aggregations() != null && bucket.aggregations().get("avg_score") != null) {
                        Double avgVal = bucket.aggregations().get("avg_score").avg().value();
                        if (avgVal != null && !Double.isNaN(avgVal)) {
                            dayStat.put("avgScore", avgVal);
                        }
                    }
                    results.add(dayStat);
                }
            }
            return results;
        } catch (IOException e) {
            log.error("Failed to query daily stats from ES: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取风险等级分布。
     */
    public List<Map<String, Object>> getRiskDistribution() {
        try {
            String index = "decision-log-" + INDEX_SUFFIX.format(LocalDateTime.now());
            ElasticsearchClient client = logRepository.getEsClient();

            SearchRequest request = SearchRequest.of(s -> s
                    .index(index)
                    .size(0)
                    .aggregations("risk_levels", Aggregation.of(a -> a
                            .terms(t -> t.field("riskLevel").size(20))))
            );

            SearchResponse<DecisionLogDocument> response = client.search(request, DecisionLogDocument.class);

            List<Map<String, Object>> results = new ArrayList<>();
            if (response.aggregations() != null && response.aggregations().get("risk_levels") != null) {
                for (StringTermsBucket bucket : response.aggregations().get("risk_levels").sterms().buckets().array()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("riskLevel", bucket.key().stringValue());
                    entry.put("count", bucket.docCount());
                    results.add(entry);
                }
            }
            return results;
        } catch (IOException e) {
            log.error("Failed to query risk distribution from ES: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取 Top N 触发规则。
     */
    public List<Map<String, Object>> getTopRules(int limit) {
        try {
            String index = "decision-log-" + INDEX_SUFFIX.format(LocalDateTime.now());
            ElasticsearchClient client = logRepository.getEsClient();

            SearchRequest request = SearchRequest.of(s -> s
                    .index(index)
                    .size(0)
                    .aggregations("top_rules", Aggregation.of(a -> a
                            .terms(t -> t.field("rulesExecuted").size(limit))))
            );

            SearchResponse<DecisionLogDocument> response = client.search(request, DecisionLogDocument.class);

            List<Map<String, Object>> results = new ArrayList<>();
            if (response.aggregations() != null && response.aggregations().get("top_rules") != null) {
                for (StringTermsBucket bucket : response.aggregations().get("top_rules").sterms().buckets().array()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("rule", bucket.key().stringValue());
                    entry.put("count", bucket.docCount());
                    results.add(entry);
                }
            }
            return results;
        } catch (IOException e) {
            log.error("Failed to query top rules from ES: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private String[] buildIndicesBetween(LocalDate start, LocalDate end) {
        List<String> indices = new ArrayList<>();
        LocalDate cursor = start.withDayOfMonth(1);
        LocalDate endMonth = end.withDayOfMonth(1);
        while (!cursor.isAfter(endMonth)) {
            indices.add("decision-log-" + INDEX_SUFFIX.format(cursor.atStartOfDay()));
            cursor = cursor.plusMonths(1);
        }
        return indices.toArray(new String[0]);
    }
}
