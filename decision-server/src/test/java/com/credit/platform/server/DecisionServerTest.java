package com.credit.platform.server;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.json.JsonData;
import com.credit.platform.engine.common.model.DecisionResponse;
import com.credit.platform.engine.core.cache.VersionedArtifact;
import com.credit.platform.engine.core.cache.VersionedRuleCache;
import com.credit.platform.engine.core.scorecard.ScorecardExecutor;
import com.credit.platform.engine.core.trace.DecisionTrace;
import com.credit.platform.engine.core.trace.TracePublisher;
import com.credit.platform.server.interceptor.DecisionAuditInterceptor;
import com.credit.platform.server.model.DecisionLogDocument;
import com.credit.platform.server.repository.DecisionLogRepository;
import com.credit.platform.server.service.DecisionReportService;
import com.credit.platform.server.service.DecisionService;
import co.elastic.clients.elasticsearch._types.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Decision-server unit tests — services, repository, interceptor.
 * Pure unit tests, no Spring context.
 */
class DecisionServerTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ========================================================================
    // DecisionLogRepository Tests
    // ========================================================================
    @Nested
    class DecisionLogRepositoryTest {

        private ElasticsearchClient esClient;
        private DecisionLogRepository repository;

        @BeforeEach
        void setUp() {
            esClient = Mockito.mock(ElasticsearchClient.class);
            repository = new DecisionLogRepository(esClient);
        }

        @Test
        void save_generatesId_whenNull() throws IOException {
            when(esClient.index(any(co.elastic.clients.elasticsearch.core.IndexRequest.class)))
                    .thenReturn(IndexResponse.of(r -> r.id("test-id").index("decision-log-2026.06").version(1).result(Result.Created).shards(sh -> sh.total(1).successful(1).failed(0)).seqNo(1).primaryTerm(1)));

            DecisionLogDocument doc = new DecisionLogDocument();
            doc.setId(null);
            doc.setTraceId("trace-001");
            doc.setDecisionResult("PASS");

            repository.save(doc);

            assertNotNull(doc.getId(), "ID should be auto-generated");
            assertFalse(doc.getId().isBlank(), "Generated ID should not be blank");
            verify(esClient).index(any(co.elastic.clients.elasticsearch.core.IndexRequest.class));
        }

        @Test
        void save_preservesExistingId() throws IOException {
            when(esClient.index(any(co.elastic.clients.elasticsearch.core.IndexRequest.class)))
                    .thenReturn(IndexResponse.of(r -> r.id("my-id").index("decision-log-2026.06").version(1).result(Result.Created).shards(sh -> sh.total(1).successful(1).failed(0)).seqNo(1).primaryTerm(1)));

            DecisionLogDocument doc = new DecisionLogDocument();
            doc.setId("my-id");
            doc.setTraceId("trace-002");

            repository.save(doc);

            assertEquals("my-id", doc.getId(), "Existing ID should be preserved");
        }

        @Test
        void save_handlesIOException_gracefully() throws IOException {
            when(esClient.index(any(co.elastic.clients.elasticsearch.core.IndexRequest.class)))
                    .thenThrow(new IOException("Connection refused"));

            DecisionLogDocument doc = new DecisionLogDocument();
            doc.setTraceId("trace-003");

            assertDoesNotThrow(() -> repository.save(doc), "Save should not throw on IO error");
        }

        @Test
        @SuppressWarnings("unchecked")
        void findByTraceId_returnsResults() throws IOException {
            DecisionLogDocument doc1 = new DecisionLogDocument();
            doc1.setId("d1");
            doc1.setTraceId("T001");
            doc1.setDecisionResult("PASS");

            Hit<DecisionLogDocument> hit1 = Hit.of(h -> h.id("d1").index("decision-log-2026.06").source(doc1));

            SearchResponse<DecisionLogDocument> mockResponse = Mockito.mock(SearchResponse.class);
            HitsMetadata<DecisionLogDocument> hitsMeta = Mockito.mock(HitsMetadata.class);
            when(hitsMeta.hits()).thenReturn(List.of(hit1));
            when(mockResponse.hits()).thenReturn(hitsMeta);

            when(esClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(DecisionLogDocument.class)))
                    .thenReturn(mockResponse);

            List<DecisionLogDocument> results = repository.findByTraceId("T001");

            assertFalse(results.isEmpty(), "Should find results");
            assertEquals("T001", results.get(0).getTraceId());
        }

        @Test
        @SuppressWarnings("unchecked")
        void findByCustomerId_withPagination() throws IOException {
            SearchResponse<DecisionLogDocument> mockResponse = Mockito.mock(SearchResponse.class);
            HitsMetadata<DecisionLogDocument> hitsMeta = Mockito.mock(HitsMetadata.class);
            when(hitsMeta.hits()).thenReturn(Collections.emptyList());
            when(mockResponse.hits()).thenReturn(hitsMeta);

            when(esClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(DecisionLogDocument.class)))
                    .thenReturn(mockResponse);

            List<DecisionLogDocument> results = repository.findByCustomerId("C001", 10, 5);
            assertNotNull(results);
            verify(esClient).search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(DecisionLogDocument.class));
        }

        @Test
        @SuppressWarnings("unchecked")
        void search_withQueryString() throws IOException {
            SearchResponse<DecisionLogDocument> mockResponse = Mockito.mock(SearchResponse.class);
            HitsMetadata<DecisionLogDocument> hitsMeta = Mockito.mock(HitsMetadata.class);
            when(hitsMeta.hits()).thenReturn(Collections.emptyList());
            when(mockResponse.hits()).thenReturn(hitsMeta);

            when(esClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(DecisionLogDocument.class)))
                    .thenReturn(mockResponse);

            List<DecisionLogDocument> results = repository.search("test query", 0, 10);
            assertNotNull(results);
        }

        @Test
        @SuppressWarnings("unchecked")
        void search_handlesIOException_returnsEmpty() throws IOException {
            when(esClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(DecisionLogDocument.class)))
                    .thenThrow(new IOException("ES down"));

            List<DecisionLogDocument> results = repository.findByTraceId("T001");
            assertTrue(results.isEmpty(), "Should return empty on IO error");
        }

        @Test
        void getEsClient_returnsClient() {
            assertEquals(esClient, repository.getEsClient());
        }
    }

    // ========================================================================
    // DecisionReportService Tests
    // ========================================================================
    @Nested
    class DecisionReportServiceTest {

        private DecisionLogRepository logRepository;
        private ElasticsearchClient esClient;
        private DecisionReportService reportService;

        @BeforeEach
        void setUp() {
            logRepository = Mockito.mock(DecisionLogRepository.class);
            esClient = Mockito.mock(ElasticsearchClient.class);
            when(logRepository.getEsClient()).thenReturn(esClient);
            reportService = new DecisionReportService(logRepository);
        }

        @Test
        void getRiskDistribution_returnsEmpty_onIOException() throws IOException {
            when(esClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(DecisionLogDocument.class)))
                    .thenThrow(new IOException("Connection refused"));

            List<Map<String, Object>> result = reportService.getRiskDistribution();
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        void getTopRules_returnsEmpty_onIOException() throws IOException {
            when(esClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(DecisionLogDocument.class)))
                    .thenThrow(new IOException("Connection refused"));

            List<Map<String, Object>> result = reportService.getTopRules(10);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        void getDailyStats_returnsEmpty_onIOException() throws IOException {
            when(esClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(DecisionLogDocument.class)))
                    .thenThrow(new IOException("Connection refused"));

            List<Map<String, Object>> result = reportService.getDailyStats(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @SuppressWarnings("unchecked")
        void getRiskDistribution_parsesBuckets() throws IOException {
            // Build a mock search response with aggregation buckets
            SearchResponse<DecisionLogDocument> mockResponse = Mockito.mock(SearchResponse.class);
            var aggregations = Mockito.mock(co.elastic.clients.elasticsearch._types.aggregations.Aggregate.class);
            var stringTermsAggregate = Mockito.mock(co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate.class);
            var bucket = Mockito.mock(co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket.class);

            when(bucket.key()).thenReturn(co.elastic.clients.elasticsearch._types.FieldValue.of(f -> f.stringValue("HIGH")));
            when(bucket.docCount()).thenReturn(42L);
            when(stringTermsAggregate.buckets()).thenReturn(
                    co.elastic.clients.elasticsearch._types.aggregations.Buckets.of(b -> b.array(List.of(bucket))));
            when(aggregations.sterms()).thenReturn(stringTermsAggregate);

            var aggMap = new HashMap<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregate>();
            aggMap.put("risk_levels", aggregations);

            when(mockResponse.aggregations()).thenReturn(aggMap);
            when(mockResponse.hits()).thenReturn(Mockito.mock(HitsMetadata.class));

            when(esClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(DecisionLogDocument.class)))
                    .thenReturn(mockResponse);

            List<Map<String, Object>> result = reportService.getRiskDistribution();

            assertFalse(result.isEmpty());
            assertEquals("HIGH", result.get(0).get("riskLevel"));
            assertEquals(42L, result.get(0).get("count"));
        }
    }

    // ========================================================================
    // DecisionAuditInterceptor Tests
    // ========================================================================
    @Nested
    class DecisionAuditInterceptorTest {

        private DecisionLogRepository logRepository;
        private DecisionAuditInterceptor interceptor;
        private HttpServletRequest request;
        private HttpServletResponse response;

        @BeforeEach
        void setUp() throws IOException {
            logRepository = Mockito.mock(DecisionLogRepository.class);
            // Stub save to do nothing
            doNothing().when(logRepository).save(any(DecisionLogDocument.class));
            interceptor = new DecisionAuditInterceptor(logRepository);
            request = Mockito.mock(HttpServletRequest.class);
            response = Mockito.mock(HttpServletResponse.class);
        }

        @Test
        void preHandle_returnsTrue() {
            assertTrue(interceptor.preHandle(request, response, new Object()));
        }

        @Test
        void preHandle_setsStartTimeAttribute() {
            interceptor.preHandle(request, response, new Object());
            verify(request).setAttribute(eq("auditStartTime"), any(Long.class));
        }

        @Test
        void afterCompletion_savesAuditLog() {
            when(request.getAttribute("auditStartTime")).thenReturn(System.nanoTime());
            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/api/v1/decision/execute");
            when(request.getRequestId()).thenReturn("req-001");
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            when(response.getStatus()).thenReturn(200);
            when(request.getHeader("Authorization")).thenReturn(null);
            when(request.getHeader("User-Agent")).thenReturn("test-agent");

            interceptor.afterCompletion(request, response, new Object(), null);

            ArgumentCaptor<DecisionLogDocument> captor = ArgumentCaptor.forClass(DecisionLogDocument.class);
            verify(logRepository).save(captor.capture());

            DecisionLogDocument saved = captor.getValue();
            assertEquals("AUDIT", saved.getDecisionResult());
            assertNotNull(saved.getId());
            assertEquals("req-001", saved.getTraceId());
            assertTrue(saved.getExecutionTimeMs() >= 0);
        }

        @Test
        void afterCompletion_extractsOperatorFromBearerToken() {
            when(request.getAttribute("auditStartTime")).thenReturn(System.nanoTime());
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/v1/decision/report");
            when(response.getStatus()).thenReturn(200);
            when(request.getHeader("Authorization")).thenReturn("Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature");
            when(request.getHeader("User-Agent")).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("10.0.0.1");

            interceptor.afterCompletion(request, response, new Object(), null);

            verify(logRepository).save(any(DecisionLogDocument.class));
        }

        @Test
        void afterCompletion_handlesException() {
            when(request.getAttribute("auditStartTime")).thenReturn(System.nanoTime());
            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/api/v1/decision/execute");
            when(response.getStatus()).thenReturn(500);
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            when(request.getHeader("Authorization")).thenReturn(null);
            when(request.getHeader("User-Agent")).thenReturn(null);

            interceptor.afterCompletion(request, response, new Object(),
                    new RuntimeException("Internal error"));

            verify(logRepository).save(any(DecisionLogDocument.class));
        }

        @Test
        void afterCompletion_handlesNullStartTime() {
            when(request.getAttribute("auditStartTime")).thenReturn(null);
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/v1/decision/report");
            when(response.getStatus()).thenReturn(200);
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");

            interceptor.afterCompletion(request, response, new Object(), null);

            verify(logRepository).save(any(DecisionLogDocument.class));
        }

        @Test
        void afterCompletion_handlesSaveException_gracefully() {
            when(request.getAttribute("auditStartTime")).thenReturn(System.nanoTime());
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/v1/decision/report");
            when(response.getStatus()).thenReturn(200);
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");

            doThrow(new RuntimeException("ES down")).when(logRepository).save(any(DecisionLogDocument.class));

            // Should not throw
            assertDoesNotThrow(() ->
                    interceptor.afterCompletion(request, response, new Object(), null));
        }

        @Test
        void getClientIp_prefersXForwardedFor() {
            when(request.getAttribute("auditStartTime")).thenReturn(System.nanoTime());
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/test");
            when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 70.41.3.18");
            when(response.getStatus()).thenReturn(200);
            when(request.getHeader("Authorization")).thenReturn(null);
            when(request.getHeader("User-Agent")).thenReturn(null);

            interceptor.afterCompletion(request, response, new Object(), null);

            verify(logRepository).save(any(DecisionLogDocument.class));
        }
    }

    // ========================================================================
    // DecisionService Tests
    // ========================================================================
    @Nested
    class DecisionServiceTest {

        private VersionedRuleCache ruleCache;
        private ScorecardExecutor scorecardExecutor;
        private TracePublisher tracePublisher;
        private DecisionLogRepository logRepository;
        private DecisionService service;

        @BeforeEach
        void setUp() {
            ruleCache = Mockito.mock(VersionedRuleCache.class);
            scorecardExecutor = Mockito.mock(ScorecardExecutor.class);
            tracePublisher = Mockito.mock(TracePublisher.class);
            logRepository = Mockito.mock(DecisionLogRepository.class);

            service = new DecisionService(ruleCache, scorecardExecutor,
                    tracePublisher, logRepository, objectMapper);
        }

        @Test
        void execute_returnsManual_whenNoStrategy() {
            when(ruleCache.get(any())).thenReturn(null);

            DecisionResponse response = service.execute(
                    "nonexistent-strategy", "APP",
                    Map.of("customerId", "C001"), Map.of());

            assertNotNull(response);
            // Manual review expected when strategy not found
            verify(tracePublisher).publish(any(DecisionTrace.class));
            verify(logRepository).save(any(DecisionLogDocument.class));
        }

        @Test
        void execute_handlesNullApplicant() {
            when(ruleCache.get(any())).thenReturn(null);

            DecisionResponse response = service.execute(
                    "strategy-1", "WEB", null, null);

            assertNotNull(response);
        }

        @Test
        void execute_handlesException_gracefully() {
            when(ruleCache.get(any())).thenThrow(new RuntimeException("Cache error"));

            DecisionResponse response = service.execute(
                    "strategy-1", "APP", Map.of(), Map.of());

            assertNotNull(response);
            verify(tracePublisher).publish(any(DecisionTrace.class));
        }

        @Test
        void getReport_returnsNull_whenNoLogs() {
            when(logRepository.findByTraceId("not-found")).thenReturn(Collections.emptyList());

            Map<String, Object> report = service.getReport("not-found");
            assertNull(report);
        }

        @Test
        void getReport_returnsReport_whenLogFound() {
            DecisionLogDocument doc = new DecisionLogDocument();
            doc.setId("dec-001");
            doc.setTraceId("T001");
            doc.setDecisionResult("PASS");
            doc.setScore(720);
            doc.setRiskLevel("LOW");
            doc.setRulesExecuted(List.of("R001", "R002"));
            doc.setExecutionTimeMs(15);

            when(logRepository.findByTraceId("T001")).thenReturn(List.of(doc));

            Map<String, Object> report = service.getReport("T001");

            assertNotNull(report);
            assertEquals("dec-001", report.get("decisionId"));
            assertEquals("T001", report.get("traceId"));
            assertEquals("PASS", report.get("result"));
            assertEquals(720.0, report.get("score"));
            assertEquals("LOW", report.get("riskLevel"));
        }
    }
}
