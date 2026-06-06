package com.credit.platform.data.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.credit.platform.data.service.controller.EnterpriseProfileController;
import com.credit.platform.data.service.controller.FeatureController;
import com.credit.platform.data.service.controller.ReportController;
import com.credit.platform.data.service.model.ApiResponse;
import com.credit.platform.data.service.model.CustomerFeatures;
import com.credit.platform.data.service.service.EnterpriseProfileService;
import com.credit.platform.data.service.service.FeatureQueryService;
import com.credit.platform.data.service.service.ReportService;
import com.credit.platform.data.service.service.TrinoQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for the data-service module.
 *
 * <p>Covers all controllers and services with mocked dependencies.
 * No Spring context or real database connections required.
 */
class DataServiceTest {

    // -----------------------------------------------------------------------
    // FeatureController
    // -----------------------------------------------------------------------

    @Nested
    class FeatureControllerTest {

        private FeatureQueryService featureQueryService;
        private FeatureController controller;

        @BeforeEach
        void setUp() {
            featureQueryService = mock(FeatureQueryService.class);
            controller = new FeatureController(featureQueryService);
        }

        @Test
        @DisplayName("GET /api/v1/features/{customerId} - returns all features")
        void queryAllFeatures_returnsCustomerFeatures() {
            // Arrange
            String customerId = "CUST001";
            Map<String, Object> features = Map.of(
                    "credit_query_3m", 5,
                    "overdue_6m", 0,
                    "credit_score", 720
            );
            CustomerFeatures expected = new CustomerFeatures(customerId, features, "REDIS", 3L);
            when(featureQueryService.queryFeatures(customerId)).thenReturn(expected);

            // Act
            ApiResponse response = controller.queryAllFeatures(customerId);

            // Assert
            assertTrue(response.isSuccess());
            assertNotNull(response.getData());
            assertEquals("特征查询成功", response.getMessage());
            assertNotNull(response.getTimestamp());

            CustomerFeatures data = (CustomerFeatures) response.getData();
            assertEquals(customerId, data.getCustomerId());
            assertEquals("REDIS", data.getSource());
            assertEquals(3L, data.getQueryTimeMs());
            assertEquals(3, data.getFeatures().size());

            verify(featureQueryService, times(1)).queryFeatures(customerId);
        }

        @Test
        @DisplayName("GET /api/v1/features/{customerId} - returns MISS when no features found")
        void queryAllFeatures_returnsMissWhenEmpty() {
            String customerId = "CUST999";
            CustomerFeatures expected = new CustomerFeatures(customerId, Map.of(), "MISS", 1L);
            when(featureQueryService.queryFeatures(customerId)).thenReturn(expected);

            ApiResponse response = controller.queryAllFeatures(customerId);

            assertTrue(response.isSuccess());
            CustomerFeatures data = (CustomerFeatures) response.getData();
            assertEquals("MISS", data.getSource());
            assertTrue(data.getFeatures().isEmpty());
        }

        @Test
        @DisplayName("GET /api/v1/features/{customerId}/select?keys=... - returns selected features")
        void querySelectedFeatures_returnsFilteredFeatures() {
            String customerId = "CUST002";
            String featureKeys = "credit_query_3m,overdue_6m";

            Map<String, Object> features = Map.of(
                    "credit_query_3m", 12,
                    "overdue_6m", 2
            );
            CustomerFeatures expected = new CustomerFeatures(customerId, features, "HBASE", 8L);
            when(featureQueryService.queryFeatures(eq(customerId), anyList())).thenReturn(expected);

            ApiResponse response = controller.querySelectedFeatures(customerId, featureKeys);

            assertTrue(response.isSuccess());
            assertNotNull(response.getData());
            assertEquals("特征查询成功", response.getMessage());

            CustomerFeatures data = (CustomerFeatures) response.getData();
            assertEquals(customerId, data.getCustomerId());
            assertEquals(2, data.getFeatures().size());
            assertTrue(data.getFeatures().containsKey("credit_query_3m"));
            assertTrue(data.getFeatures().containsKey("overdue_6m"));

            // Verify the keys list is correctly parsed from the comma-separated string
            verify(featureQueryService).queryFeatures(eq(customerId), argThat(list ->
                    list.size() == 2
                            && list.contains("credit_query_3m")
                            && list.contains("overdue_6m")
            ));
        }

        @Test
        @DisplayName("GET /api/v1/features/{customerId}/select?keys= - single key returns single feature")
        void querySelectedFeatures_singleKey() {
            String customerId = "CUST003";
            String featureKeys = "credit_score";

            Map<String, Object> features = Map.of("credit_score", 680);
            CustomerFeatures expected = new CustomerFeatures(customerId, features, "REDIS", 1L);
            when(featureQueryService.queryFeatures(eq(customerId), anyList())).thenReturn(expected);

            ApiResponse response = controller.querySelectedFeatures(customerId, featureKeys);

            assertTrue(response.isSuccess());
            CustomerFeatures data = (CustomerFeatures) response.getData();
            assertEquals(1, data.getFeatures().size());

            verify(featureQueryService).queryFeatures(eq(customerId), argThat(list ->
                    list.size() == 1 && list.contains("credit_score")
            ));
        }
    }

    // -----------------------------------------------------------------------
    // EnterpriseProfileController
    // -----------------------------------------------------------------------

    @Nested
    class EnterpriseProfileControllerTest {

        private EnterpriseProfileService profileService;
        private EnterpriseProfileController controller;

        @BeforeEach
        void setUp() {
            profileService = mock(EnterpriseProfileService.class);
            controller = new EnterpriseProfileController(profileService);
        }

        @Test
        @DisplayName("GET /api/v1/profile/{enterpriseId} - returns enterprise profile")
        void queryProfile_returnsProfile() {
            String enterpriseId = "ENT001";
            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("basicInfo", Map.of("enterpriseId", enterpriseId, "enterpriseName", "测试公司"));
            profile.put("creditSummary", Map.of("creditScore", 700, "riskLevel", "LOW"));

            when(profileService.queryProfile(enterpriseId)).thenReturn(profile);

            ApiResponse response = controller.queryProfile(enterpriseId);

            assertTrue(response.isSuccess());
            assertEquals("企业画像查询成功", response.getMessage());
            assertNotNull(response.getTimestamp());
            assertNotNull(response.getData());

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getData();
            assertEquals(2, data.size());
            assertTrue(data.containsKey("basicInfo"));
            assertTrue(data.containsKey("creditSummary"));

            verify(profileService, times(1)).queryProfile(enterpriseId);
        }

        @Test
        @DisplayName("GET /api/v1/profile/{enterpriseId} - handles unknown enterprise")
        void queryProfile_handlesUnknownEnterprise() {
            String enterpriseId = "ENT_UNKNOWN";
            Map<String, Object> profile = Map.of(); // empty profile
            when(profileService.queryProfile(enterpriseId)).thenReturn(profile);

            ApiResponse response = controller.queryProfile(enterpriseId);

            assertTrue(response.isSuccess());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getData();
            assertTrue(data.isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // ReportController
    // -----------------------------------------------------------------------

    @Nested
    class ReportControllerTest {

        private ReportService reportService;
        private ReportController controller;

        @BeforeEach
        void setUp() {
            reportService = mock(ReportService.class);
            controller = new ReportController(reportService);
        }

        @Test
        @DisplayName("GET /api/v1/reports/business?date=2026-06-06 - returns business report")
        void queryReport_businessReport() {
            String date = "2026-06-06";
            Map<String, Object> report = Map.of(
                    "reportType", "经营分析看板",
                    "metrics", Map.of("totalApplications", 1250)
            );
            when(reportService.queryReport("business", date)).thenReturn(report);

            ApiResponse response = controller.queryReport("business", date);

            assertTrue(response.isSuccess());
            assertEquals("报表查询成功", response.getMessage());
            assertNotNull(response.getData());

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getData();
            assertEquals("经营分析看板", data.get("reportType"));
            verify(reportService).queryReport("business", date);
        }

        @Test
        @DisplayName("GET /api/v1/reports/risk - returns risk report")
        void queryReport_riskReport() {
            Map<String, Object> report = Map.of(
                    "reportType", "风控监控看板",
                    "topHitRules", List.of(Map.of("ruleId", "R001"))
            );
            when(reportService.queryReport("risk", null)).thenReturn(report);

            ApiResponse response = controller.queryReport("risk", null);

            assertTrue(response.isSuccess());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getData();
            assertEquals("风控监控看板", data.get("reportType"));
            verify(reportService).queryReport("risk", null);
        }

        @Test
        @DisplayName("GET /api/v1/reports/invalid_type - returns error response from service")
        void queryReport_invalidType_returnsError() {
            String invalidType = "invalid_type";
            Map<String, Object> errorResult = Map.of(
                    "error", "未知报表类型: " + invalidType,
                    "supported", List.of("business", "risk", "channel", "quality")
            );
            when(reportService.queryReport(invalidType, null)).thenReturn(errorResult);

            ApiResponse response = controller.queryReport(invalidType, null);

            // Controller wraps it in ok() -- the service-level error is in the data payload
            assertTrue(response.isSuccess());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getData();
            assertTrue(data.containsKey("error"));
            assertEquals("未知报表类型: " + invalidType, data.get("error"));

            @SuppressWarnings("unchecked")
            List<String> supported = (List<String>) data.get("supported");
            assertEquals(4, supported.size());
            assertTrue(supported.contains("business"));
            assertTrue(supported.contains("risk"));
            assertTrue(supported.contains("channel"));
            assertTrue(supported.contains("quality"));
        }

        @Test
        @DisplayName("GET /api/v1/reports/channel - returns channel report")
        void queryReport_channelReport() {
            Map<String, Object> report = Map.of(
                    "reportType", "渠道分析看板",
                    "channels", List.of(Map.of("channel", "ONLINE"))
            );
            when(reportService.queryReport("channel", "2026-06-01")).thenReturn(report);

            ApiResponse response = controller.queryReport("channel", "2026-06-01");

            assertTrue(response.isSuccess());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getData();
            assertEquals("渠道分析看板", data.get("reportType"));
            assertNotNull(data.get("channels"));
        }

        @Test
        @DisplayName("GET /api/v1/reports/quality - returns quality report")
        void queryReport_qualityReport() {
            Map<String, Object> report = Map.of(
                    "reportType", "数据质量看板",
                    "overallScore", 99.5,
                    "dimensions", Map.of("completeness", Map.of("score", 99.2))
            );
            when(reportService.queryReport("quality", null)).thenReturn(report);

            ApiResponse response = controller.queryReport("quality", null);

            assertTrue(response.isSuccess());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getData();
            assertEquals("数据质量看板", data.get("reportType"));
            assertEquals(99.5, data.get("overallScore"));
        }
    }

    // -----------------------------------------------------------------------
    // FeatureQueryService (Redis -> HBase fallback)
    // -----------------------------------------------------------------------

    @Nested
    class FeatureQueryServiceTest {

        private StringRedisTemplate redisTemplate;
        private Connection hbaseConnection;
        private ObjectMapper objectMapper;
        private FeatureQueryService service;

        @BeforeEach
        void setUp() {
            redisTemplate = mock(StringRedisTemplate.class);
            hbaseConnection = mock(Connection.class);
            objectMapper = new ObjectMapper();
            service = new FeatureQueryService(redisTemplate, hbaseConnection, objectMapper);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("Redis hit - returns features from Redis cache")
        void queryFeatures_redisHit() throws Exception {
            String customerId = "CUST_REDIS_HIT";
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);

            // Return JSON for the credit_score feature type, null for the others
            String creditScoreJson = objectMapper.writeValueAsString(Map.of("credit_score", 720));
            when(valueOps.get(startsWith("feature:")))
                    .thenReturn(creditScoreJson)   // credit_query_3m -> hit
                    .thenReturn(null)              // overdue_6m -> miss
                    .thenReturn(null)              // apply_freq_1m
                    .thenReturn(null)              // transaction_summary_1h
                    .thenReturn(creditScoreJson)   // credit_score -> hit
                    .thenReturn(null);             // risk_level

            CustomerFeatures result = service.queryFeatures(customerId);

            assertNotNull(result);
            assertEquals(customerId, result.getCustomerId());
            assertFalse(result.getFeatures().isEmpty());
            assertEquals("HIT", result.getSource());
            assertTrue(result.getQueryTimeMs() >= 0);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("Redis miss -> HBase hit - returns features from HBase")
        void queryFeatures_redisMissHbaseHit() throws Exception {
            String customerId = "CUST_HBASE_HIT";
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(anyString())).thenReturn(null); // Redis miss for all

            // Mock HBase
            Table table = mock(Table.class);
            when(hbaseConnection.getTable(any(TableName.class))).thenReturn(table);

            ResultScanner scanner = mock(ResultScanner.class);
            when(table.getScanner(any(Scan.class))).thenReturn(scanner);

            String hbaseJson = objectMapper.writeValueAsString(Map.of("credit_query_3m", 8));
            Result hbaseResult = mock(Result.class);
            when(hbaseResult.isEmpty()).thenReturn(false);
            when(hbaseResult.getValue(Bytes.toBytes("cf"), Bytes.toBytes("value")))
                    .thenReturn(Bytes.toBytes(hbaseJson));
            when(scanner.next()).thenReturn(hbaseResult).thenReturn(null);

            CustomerFeatures result = service.queryFeatures(customerId);

            assertNotNull(result);
            assertEquals(customerId, result.getCustomerId());
            assertFalse(result.getFeatures().isEmpty());
            assertEquals("HIT", result.getSource());

            // Verify Redis write-back was attempted
            verify(valueOps, atLeastOnce()).set(anyString(), anyString(), any());
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("Both Redis and HBase miss - returns empty features with MISS source")
        void queryFeatures_bothMiss() throws Exception {
            String customerId = "CUST_TOTAL_MISS";
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(anyString())).thenReturn(null); // Redis miss

            Table table = mock(Table.class);
            when(hbaseConnection.getTable(any(TableName.class))).thenReturn(table);

            ResultScanner scanner = mock(ResultScanner.class);
            when(table.getScanner(any(Scan.class))).thenReturn(scanner);
            when(scanner.next()).thenReturn(null); // HBase miss

            CustomerFeatures result = service.queryFeatures(customerId);

            assertNotNull(result);
            assertEquals(customerId, result.getCustomerId());
            assertTrue(result.getFeatures().isEmpty());
            assertEquals("MISS", result.getSource());
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("Redis read exception - fallback to HBase")
        void queryFeatures_redisException_fallsBackToHBase() throws Exception {
            String customerId = "CUST_REDIS_ERR";
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            // Redis throws exception
            when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis connection refused"));

            // HBase returns data
            Table table = mock(Table.class);
            when(hbaseConnection.getTable(any(TableName.class))).thenReturn(table);

            ResultScanner scanner = mock(ResultScanner.class);
            when(table.getScanner(any(Scan.class))).thenReturn(scanner);

            String hbaseJson = objectMapper.writeValueAsString(Map.of("overdue_6m", 1));
            Result hbaseResult = mock(Result.class);
            when(hbaseResult.isEmpty()).thenReturn(false);
            when(hbaseResult.getValue(Bytes.toBytes("cf"), Bytes.toBytes("value")))
                    .thenReturn(Bytes.toBytes(hbaseJson));
            when(scanner.next()).thenReturn(hbaseResult).thenReturn(null);

            CustomerFeatures result = service.queryFeatures(customerId);

            assertNotNull(result);
            assertEquals("HIT", result.getSource());
            assertFalse(result.getFeatures().isEmpty());
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("queryFeatures(customerId, keys) - selective feature query")
        void queryFeatures_selectiveQuery() throws Exception {
            String customerId = "CUST_SELECT";
            List<String> keys = List.of("credit_score", "risk_level");

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);

            String scoreJson = objectMapper.writeValueAsString(Map.of("credit_score", 750));
            String riskJson = objectMapper.writeValueAsString(Map.of("risk_level", "LOW"));
            when(valueOps.get("feature:credit_score:" + customerId)).thenReturn(scoreJson);
            when(valueOps.get("feature:risk_level:" + customerId)).thenReturn(riskJson);

            CustomerFeatures result = service.queryFeatures(customerId, keys);

            assertNotNull(result);
            assertEquals(customerId, result.getCustomerId());
            assertEquals("HIT", result.getSource());
            assertEquals(2, result.getFeatures().size());
            assertEquals(750, result.getFeatures().get("credit_score"));
            assertEquals("LOW", result.getFeatures().get("risk_level"));
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("HBase exception - returns empty features gracefully")
        void queryFeatures_hbaseException_gracefulDegradation() throws Exception {
            String customerId = "CUST_HBASE_ERR";
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(anyString())).thenReturn(null); // Redis miss

            // HBase throws exception
            Table table = mock(Table.class);
            when(hbaseConnection.getTable(any(TableName.class))).thenReturn(table);
            when(table.getScanner(any(Scan.class))).thenThrow(new RuntimeException("HBase unavailable"));

            CustomerFeatures result = service.queryFeatures(customerId);

            assertNotNull(result);
            assertTrue(result.getFeatures().isEmpty());
            assertEquals("MISS", result.getSource());
        }
    }

    // -----------------------------------------------------------------------
    // EnterpriseProfileService (Redis -> HBase -> ES with mock fallback)
    // -----------------------------------------------------------------------

    @Nested
    class EnterpriseProfileServiceTest {

        private StringRedisTemplate redisTemplate;
        private Connection hbaseConnection;
        private ElasticsearchClient esClient;
        private ObjectMapper objectMapper;
        private EnterpriseProfileService service;

        @SuppressWarnings("unchecked")
        @BeforeEach
        void setUp() throws Exception {
            redisTemplate = mock(StringRedisTemplate.class);
            hbaseConnection = mock(Connection.class);
            esClient = mock(ElasticsearchClient.class);
            objectMapper = new ObjectMapper();

            // Make Redis miss for all queries (returns null)
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(anyString())).thenReturn(null);

            // Make HBase return empty results (miss)
            Table table = mock(Table.class);
            when(hbaseConnection.getTable(any(TableName.class))).thenReturn(table);
            Result emptyResult = mock(Result.class);
            when(emptyResult.isEmpty()).thenReturn(true);
            when(table.get(any(Get.class))).thenReturn(emptyResult);

            // Make ES throw exception (miss -> fallback)
            when(esClient.search(any(SearchRequest.class), any(Class.class)))
                    .thenThrow(new RuntimeException("ES unavailable for test"));

            service = new EnterpriseProfileService(redisTemplate, hbaseConnection, esClient, objectMapper);
        }

        @Test
        @DisplayName("queryProfile returns all expected sections via fallback data")
        void queryProfile_returnsAllSections() {
            Map<String, Object> profile = service.queryProfile("ENT001");

            assertNotNull(profile);
            assertTrue(profile.containsKey("basicInfo"), "Missing basicInfo section");
            assertTrue(profile.containsKey("businessRegistration"), "Missing businessRegistration section");
            assertTrue(profile.containsKey("creditSummary"), "Missing creditSummary section");
            assertTrue(profile.containsKey("relatedPersons"), "Missing relatedPersons section");
            assertTrue(profile.containsKey("riskSignals"), "Missing riskSignals section");
            assertTrue(profile.containsKey("dataSourceStatus"), "Missing dataSourceStatus section");
            assertEquals(6, profile.size());
        }

        @Test
        @DisplayName("basicInfo has all expected keys from fallback data")
        void queryProfile_basicInfoStructure() {
            Map<String, Object> profile = service.queryProfile("ENT001");

            @SuppressWarnings("unchecked")
            Map<String, Object> basicInfo = (Map<String, Object>) profile.get("basicInfo");

            assertEquals("ENT001", basicInfo.get("enterpriseId"));
            assertNotNull(basicInfo.get("enterpriseName"));
            assertNotNull(basicInfo.get("unifiedSocialCreditCode"));
            assertNotNull(basicInfo.get("registeredCapital"));
            assertNotNull(basicInfo.get("establishedDate"));
            assertNotNull(basicInfo.get("legalPerson"));
            assertNotNull(basicInfo.get("industry"));
            assertNotNull(basicInfo.get("status"));
        }

        @Test
        @DisplayName("businessRegistration has expected keys from fallback data")
        void queryProfile_businessRegistrationStructure() {
            Map<String, Object> profile = service.queryProfile("ENT001");

            @SuppressWarnings("unchecked")
            Map<String, Object> reg = (Map<String, Object>) profile.get("businessRegistration");

            assertNotNull(reg.get("registrationStatus"));
            assertNotNull(reg.get("businessScope"));
            assertNotNull(reg.get("registeredAddress"));
            assertNotNull(reg.get("lastUpdateTime"));
        }

        @Test
        @DisplayName("creditSummary has expected keys and numeric types from fallback data")
        void queryProfile_creditSummaryStructure() {
            Map<String, Object> profile = service.queryProfile("ENT001");

            @SuppressWarnings("unchecked")
            Map<String, Object> credit = (Map<String, Object>) profile.get("creditSummary");

            assertNotNull(credit.get("totalLoanCount"));
            assertNotNull(credit.get("totalLoanAmount"));
            assertNotNull(credit.get("activeLoanCount"));
            assertNotNull(credit.get("activeLoanAmount"));
            assertNotNull(credit.get("overdueCount"));
            assertNotNull(credit.get("maxOverdueDays"));
            assertNotNull(credit.get("creditScore"));
            assertNotNull(credit.get("riskLevel"));

            assertInstanceOf(Integer.class, credit.get("totalLoanCount"));
            assertInstanceOf(Double.class, credit.get("totalLoanAmount"));
            assertInstanceOf(Integer.class, credit.get("creditScore"));
        }

        @Test
        @DisplayName("relatedPersons is a non-empty list with expected fields")
        void queryProfile_relatedPersons() {
            Map<String, Object> profile = service.queryProfile("ENT001");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> persons = (List<Map<String, Object>>) profile.get("relatedPersons");

            assertFalse(persons.isEmpty());
            Map<String, Object> person = persons.get(0);
            assertTrue(person.containsKey("name"));
            assertTrue(person.containsKey("role"));
            assertTrue(person.containsKey("idCard"));
        }

        @Test
        @DisplayName("riskSignals is a non-empty list with type and level")
        void queryProfile_riskSignals() {
            Map<String, Object> profile = service.queryProfile("ENT001");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> signals = (List<Map<String, Object>>) profile.get("riskSignals");

            assertFalse(signals.isEmpty());
            Map<String, Object> signal = signals.get(0);
            assertTrue(signal.containsKey("type"));
            assertTrue(signal.containsKey("level"));
            assertTrue(signal.containsKey("description"));
        }

        @Test
        @DisplayName("dataSourceStatus has available and missing source lists")
        void queryProfile_dataSourceStatus() {
            Map<String, Object> profile = service.queryProfile("ENT001");

            @SuppressWarnings("unchecked")
            Map<String, List<String>> status = (Map<String, List<String>>) profile.get("dataSourceStatus");

            assertTrue(status.containsKey("available"));
            assertTrue(status.containsKey("missing"));
            assertFalse(status.get("available").isEmpty());
            assertFalse(status.get("missing").isEmpty());
        }

        @Test
        @DisplayName("queryProfile preserves enterpriseId in basicInfo")
        void queryProfile_preservesEnterpriseId() {
            String customId = "CUSTOM_ENT_999";
            Map<String, Object> profile = service.queryProfile(customId);

            @SuppressWarnings("unchecked")
            Map<String, Object> basicInfo = (Map<String, Object>) profile.get("basicInfo");
            assertEquals(customId, basicInfo.get("enterpriseId"));
        }
    }

    // -----------------------------------------------------------------------
    // ReportService (Trino + Elasticsearch with mock fallback)
    // -----------------------------------------------------------------------

    @Nested
    class ReportServiceTest {

        private TrinoQueryService trinoQueryService;
        private ElasticsearchClient elasticsearchClient;
        private ReportService service;

        @BeforeEach
        void setUp() {
            trinoQueryService = mock(TrinoQueryService.class);
            elasticsearchClient = mock(ElasticsearchClient.class);
            service = new ReportService(trinoQueryService, elasticsearchClient);
        }

        // --- Business Report Tests ---

        @Test
        @DisplayName("Business report falls back to mock when Trino fails")
        void businessReport_trinoFails_fallsBackToMock() throws Exception {
            // Make Trino throw an exception for both the aggregate and trend queries
            when(trinoQueryService.queryOne(anyString()))
                    .thenThrow(new RuntimeException("Trino unavailable"));

            Map<String, Object> report = service.queryReport("business", "2026-06-06");

            assertEquals("经营分析看板", report.get("reportType"));
            assertEquals("T+1", report.get("refreshFrequency"));
            assertEquals("2026-06-06", report.get("statDate"));
            assertEquals("mock", report.get("dataSource"));

            @SuppressWarnings("unchecked")
            Map<String, Object> metrics = (Map<String, Object>) report.get("metrics");
            assertNotNull(metrics);
            assertEquals(1250, metrics.get("totalApplications"));
            assertEquals(875, metrics.get("approvedCount"));
            assertEquals(375, metrics.get("rejectedCount"));
            assertEquals("70.0%", metrics.get("approveRate"));
            assertEquals(87500000.00, metrics.get("totalLoanAmount"));
            assertEquals(100000.00, metrics.get("avgLoanAmount"));
            assertEquals("1.8%", metrics.get("nplRate"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> trend = (List<Map<String, Object>>) report.get("trend");
            assertNotNull(trend);
            assertEquals(7, trend.size()); // 7-day trend
            for (Map<String, Object> day : trend) {
                assertTrue(day.containsKey("date"));
                assertTrue(day.containsKey("applications"));
                assertTrue(day.containsKey("approveRate"));
            }
        }

        @Test
        @DisplayName("Business report returns Trino data when available")
        void businessReport_trinoSuccess() throws Exception {
            // Main aggregate query returns data
            Map<String, Object> aggregateRow = Map.of(
                    "total_applications", 500L,
                    "approved_count", 350L,
                    "rejected_count", 150L,
                    "approve_rate", 0.70,
                    "total_loan_amount", 35000000.0,
                    "avg_loan_amount", 100000.0,
                    "npl_rate", 0.018
            );
            when(trinoQueryService.queryOne(anyString()))
                    .thenReturn(Optional.of(aggregateRow));

            // Trend query returns data
            when(trinoQueryService.query(anyString()))
                    .thenReturn(List.of(
                            Map.of("dt", "2026-05-30", "applications", 150L, "approve_rate", 0.70)
                    ));

            Map<String, Object> report = service.queryReport("business", "2026-06-06");

            assertEquals("经营分析看板", report.get("reportType"));
            assertEquals("trino-ads", report.get("dataSource"));

            @SuppressWarnings("unchecked")
            Map<String, Object> metrics = (Map<String, Object>) report.get("metrics");
            assertEquals(500L, metrics.get("totalApplications"));
            assertEquals(350L, metrics.get("approvedCount"));
        }

        // --- Risk Report Tests ---

        @Test
        @DisplayName("Risk report falls back to mock when ES fails")
        void riskReport_esFails_fallsBackToMock() throws Exception {
            when(elasticsearchClient.search(any(SearchRequest.class), any(Class.class)))
                    .thenThrow(new RuntimeException("ES unavailable"));

            Map<String, Object> report = service.queryReport("risk", "2026-06-06");

            assertEquals("风控监控看板", report.get("reportType"));
            assertEquals("实时", report.get("refreshFrequency"));
            assertEquals("2026-06-06", report.get("statDate"));
            assertEquals("mock", report.get("dataSource"));

            @SuppressWarnings("unchecked")
            Map<String, Object> metrics = (Map<String, Object>) report.get("metrics");
            assertNotNull(metrics);
            assertEquals(125, metrics.get("decisionQPS"));
            assertEquals("8ms", metrics.get("p50Latency"));
            assertEquals("15ms", metrics.get("p99Latency"));
            assertEquals("72.5%", metrics.get("passRate"));
            assertEquals(0.42, metrics.get("modelKS"));
            assertEquals(0.78, metrics.get("modelAUC"));
            assertEquals(3, metrics.get("activeAlerts"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rules = (List<Map<String, Object>>) report.get("topHitRules");
            assertNotNull(rules);
            assertEquals(3, rules.size());
            for (Map<String, Object> rule : rules) {
                assertTrue(rule.containsKey("ruleId"));
                assertTrue(rule.containsKey("ruleName"));
                assertTrue(rule.containsKey("hitCount"));
                assertTrue(rule.containsKey("hitRate"));
            }
        }

        // --- Channel Report Tests ---

        @Test
        @DisplayName("Channel report falls back to mock when Trino fails")
        void channelReport_trinoFails_fallsBackToMock() throws Exception {
            when(trinoQueryService.query(anyString()))
                    .thenThrow(new RuntimeException("Trino unavailable"));

            Map<String, Object> report = service.queryReport("channel", "2026-06-06");

            assertEquals("渠道分析看板", report.get("reportType"));
            assertEquals("T+1", report.get("refreshFrequency"));
            assertEquals("mock", report.get("dataSource"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> channels = (List<Map<String, Object>>) report.get("channels");
            assertNotNull(channels);
            assertEquals(3, channels.size());

            Set<String> channelNames = new HashSet<>();
            for (Map<String, Object> ch : channels) {
                assertTrue(ch.containsKey("channel"));
                assertTrue(ch.containsKey("applications"));
                assertTrue(ch.containsKey("approveRate"));
                assertTrue(ch.containsKey("conversionRate"));
                channelNames.add((String) ch.get("channel"));
            }
            assertTrue(channelNames.contains("ONLINE"));
            assertTrue(channelNames.contains("OFFLINE"));
            assertTrue(channelNames.contains("PARTNER"));
        }

        @Test
        @DisplayName("Channel report returns Trino data when available")
        void channelReport_trinoSuccess() throws Exception {
            when(trinoQueryService.query(anyString()))
                    .thenReturn(List.of(
                            Map.of("channel", "ONLINE", "applications", 800L,
                                    "approve_rate", 0.75, "conversion_rate", 0.125),
                            Map.of("channel", "OFFLINE", "applications", 300L,
                                    "approve_rate", 0.65, "conversion_rate", 0.20)
                    ));

            Map<String, Object> report = service.queryReport("channel", "2026-06-06");

            assertEquals("trino-ads", report.get("dataSource"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> channels = (List<Map<String, Object>>) report.get("channels");
            assertEquals(2, channels.size());
        }

        // --- Quality Report Tests ---

        @Test
        @DisplayName("Quality report falls back to mock when Trino fails")
        void qualityReport_trinoFails_fallsBackToMock() throws Exception {
            when(trinoQueryService.query(anyString()))
                    .thenThrow(new RuntimeException("Trino unavailable"));

            Map<String, Object> report = service.queryReport("quality", "2026-06-06");

            assertEquals("数据质量看板", report.get("reportType"));
            assertEquals("实时", report.get("refreshFrequency"));
            assertEquals("mock", report.get("dataSource"));
            assertEquals(99.5, report.get("overallScore"));
            assertEquals("PASS", report.get("overallStatus"));
            assertEquals(13, report.get("totalViolations"));

            @SuppressWarnings("unchecked")
            Map<String, Object> dimensions = (Map<String, Object>) report.get("dimensions");
            assertNotNull(dimensions);
            assertEquals(5, dimensions.size());

            // Verify all five quality dimensions
            assertTrue(dimensions.containsKey("completeness"));
            assertTrue(dimensions.containsKey("accuracy"));
            assertTrue(dimensions.containsKey("consistency"));
            assertTrue(dimensions.containsKey("timeliness"));
            assertTrue(dimensions.containsKey("uniqueness"));

            // Each dimension has score, status, violations
            for (String dimName : dimensions.keySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> dim = (Map<String, Object>) dimensions.get(dimName);
                assertTrue(dim.containsKey("score"), dimName + " missing score");
                assertTrue(dim.containsKey("status"), dimName + " missing status");
                assertTrue(dim.containsKey("violations"), dimName + " missing violations");
            }
        }

        @Test
        @DisplayName("Quality report returns Trino data when available")
        void qualityReport_trinoSuccess() throws Exception {
            when(trinoQueryService.query(anyString()))
                    .thenReturn(List.of(
                            Map.of("dimension", "COMPLETENESS", "avg_score", 99.5, "total_violations", 2L, "status", "PASS"),
                            Map.of("dimension", "ACCURACY", "avg_score", 98.0, "total_violations", 5L, "status", "WARNING")
                    ));

            Map<String, Object> report = service.queryReport("quality", "2026-06-06");

            assertEquals("trino-ads", report.get("dataSource"));

            @SuppressWarnings("unchecked")
            Map<String, Object> dimensions = (Map<String, Object>) report.get("dimensions");
            assertEquals(2, dimensions.size());
            assertTrue(dimensions.containsKey("completeness"));
            assertTrue(dimensions.containsKey("accuracy"));

            @SuppressWarnings("unchecked")
            Map<String, Object> completeness = (Map<String, Object>) dimensions.get("completeness");
            assertEquals(99.5, completeness.get("score"));
            assertEquals("PASS", completeness.get("status"));
        }

        // --- Unknown Type ---

        @Test
        @DisplayName("Unknown report type returns error with supported types")
        void unknownType_returnsError() {
            Map<String, Object> report = service.queryReport("finance", null);

            assertTrue(report.containsKey("error"));
            assertEquals("未知报表类型: finance", report.get("error"));
            assertTrue(report.containsKey("supported"));

            @SuppressWarnings("unchecked")
            List<String> supported = (List<String>) report.get("supported");
            assertEquals(List.of("business", "risk", "channel", "quality"), supported);
        }

        // --- Null Date ---

        @Test
        @DisplayName("Report with null date resolves to today and returns mock data")
        void report_nullDate_resolvesToToday() throws Exception {
            when(trinoQueryService.queryOne(anyString()))
                    .thenThrow(new RuntimeException("Trino unavailable"));

            Map<String, Object> report = service.queryReport("business", null);

            assertNotNull(report);
            // statDate should be today's date since resolveDate falls back to LocalDate.now()
            assertNotNull(report.get("statDate"));
            String statDate = (String) report.get("statDate");
            assertFalse(statDate.isEmpty());
            // Verify format yyyy-MM-dd
            assertTrue(statDate.matches("\\d{4}-\\d{2}-\\d{2}"));
        }
    }
}
