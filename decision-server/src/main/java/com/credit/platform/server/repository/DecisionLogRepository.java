package com.credit.platform.server.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.credit.platform.server.model.DecisionLogDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 决策日志 ES 仓库 — 按月索引存储决策执行记录。
 * <p>索引格式: {@code decision-log-{yyyy.MM}}</p>
 */
@Repository
public class DecisionLogRepository {

    private static final Logger log = LoggerFactory.getLogger(DecisionLogRepository.class);
    private static final DateTimeFormatter INDEX_SUFFIX = DateTimeFormatter.ofPattern("yyyy.MM");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ElasticsearchClient esClient;

    public DecisionLogRepository(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    /**
     * 保存决策日志到 ES。
     */
    public void save(DecisionLogDocument doc) {
        try {
            String indexName = resolveIndex(doc.getTimestamp() != null ? doc.getTimestamp() : LocalDateTime.now());
            if (doc.getId() == null || doc.getId().isBlank()) {
                doc.setId(generateId());
            }
            IndexRequest<DecisionLogDocument> request = IndexRequest.of(b -> b
                    .index(indexName)
                    .id(doc.getId())
                    .document(doc)
            );
            esClient.index(request);
            log.debug("Saved decision log {} to index {}", doc.getId(), indexName);
        } catch (IOException e) {
            log.error("Failed to save decision log to ES: {}", e.getMessage(), e);
        }
    }

    /**
     * 按 traceId 查询决策日志。
     */
    public List<DecisionLogDocument> findByTraceId(String traceId) {
        return search(Query.of(q -> q.term(t -> t.field("traceId").value(v -> v.stringValue(traceId)))),
                0, 10);
    }

    /**
     * 按 customerId 分页查询决策日志。
     */
    public List<DecisionLogDocument> findByCustomerId(String customerId, int from, int size) {
        return search(Query.of(q -> q.term(t -> t.field("customerId").value(v -> v.stringValue(customerId)))),
                from, size);
    }

    /**
     * 全文搜索决策日志。
     */
    public List<DecisionLogDocument> search(String query, int from, int size) {
        return search(Query.of(q -> q.multiMatch(m -> m
                        .fields("traceId", "customerId", "decisionResult", "riskLevel")
                        .query(query))),
                from, size);
    }

    /**
     * 获取 ES 客户端（供 ReportService 做聚合查询）。
     */
    public ElasticsearchClient getEsClient() {
        return esClient;
    }

    // ========== 内部方法 ==========

    private List<DecisionLogDocument> search(Query query, int from, int size) {
        try {
            // 搜索最近 12 个月的索引
            List<String> indices = Arrays.asList(buildRecentIndices(12));
            SearchRequest request = SearchRequest.of(s -> s
                    .index(indices)
                    .query(query)
                    .from(from)
                    .size(size)
                    .sort(so -> so.field(f -> f.field("timestamp").order(SortOrder.Desc)))
            );
            SearchResponse<DecisionLogDocument> response = esClient.search(request, DecisionLogDocument.class);
            List<DecisionLogDocument> results = new ArrayList<>();
            for (Hit<DecisionLogDocument> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    results.add(hit.source());
                }
            }
            return results;
        } catch (IOException e) {
            log.error("Failed to search decision logs from ES: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private String resolveIndex(LocalDateTime timestamp) {
        return "decision-log-" + INDEX_SUFFIX.format(timestamp);
    }

    private String[] buildRecentIndices(int months) {
        String[] indices = new String[months];
        LocalDate date = LocalDate.now();
        for (int i = 0; i < months; i++) {
            indices[i] = "decision-log-" + INDEX_SUFFIX.format(date.atStartOfDay());
            date = date.minusMonths(1);
        }
        return indices;
    }

    private String generateId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
