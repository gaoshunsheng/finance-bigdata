package com.credit.platform.data.governance;

import com.credit.platform.data.governance.lifecycle.LifecycleManager;
import com.credit.platform.data.governance.lineage.LineageAnalyzer;
import com.credit.platform.data.governance.lineage.LineageEdge;
import com.credit.platform.data.governance.lineage.LineageGraph;
import com.credit.platform.data.governance.lineage.LineageNode;
import com.credit.platform.data.governance.metadata.ColumnMetadata;
import com.credit.platform.data.governance.metadata.MetadataCollector;
import com.credit.platform.data.governance.metadata.TableMetadata;
import com.credit.platform.data.governance.security.DataClassifier;
import com.credit.platform.data.governance.security.DataMaskingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Per-component unit tests for the data-governance module.
 * Covers MetadataCollector, LineageAnalyzer, DataClassifier,
 * DataMaskingService, and LifecycleManager.
 */
class GovernanceComponentTest {

    // ===================== MetadataCollector =====================

    @Nested
    @DisplayName("MetadataCollector 组件测试")
    class MetadataCollectorTest {

        private MetadataCollector collector;

        @BeforeEach
        void setUp() {
            collector = new MetadataCollector();
        }

        // --- register / getTable ---

        @Test
        @DisplayName("register 后可通过 getTable 查到")
        void testRegisterAndGetTable() {
            TableMetadata table = buildTable("finance", "loan_application", "HIVE", "ODS");
            table.addColumn(new ColumnMetadata("id", "BIGINT", "PK"));
            collector.register(table);

            Optional<TableMetadata> found = collector.getTable("finance", "loan_application");
            assertTrue(found.isPresent());
            assertEquals("HIVE", found.get().getSource());
            assertEquals("ODS", found.get().getLayer());
            assertEquals(1, found.get().getColumns().size());
        }

        @Test
        @DisplayName("register 后 discoveredTime 被设置")
        void testRegisterSetsDiscoveredTime() {
            TableMetadata table = buildTable("db1", "t1", "MYSQL", "DWD");
            assertNull(table.getDiscoveredTime());
            collector.register(table);

            TableMetadata stored = collector.getTable("db1", "t1").orElseThrow();
            assertNotNull(stored.getDiscoveredTime());
        }

        @Test
        @DisplayName("查询不存在的表返回 empty Optional")
        void testGetTableNotFound() {
            assertTrue(collector.getTable("missing", "no_table").isEmpty());
        }

        // --- getTablesByLayer ---

        @Test
        @DisplayName("按数据层过滤表")
        void testGetTablesByLayer() {
            collector.register(buildTable("ods_db", "ods_t1", "HIVE", "ODS"));
            collector.register(buildTable("ods_db", "ods_t2", "HIVE", "ODS"));
            collector.register(buildTable("dwd_db", "dwd_t1", "HIVE", "DWD"));
            collector.register(buildTable("ads_db", "ads_t1", "HIVE", "ADS"));

            assertEquals(2, collector.getTablesByLayer("ODS").size());
            assertEquals(1, collector.getTablesByLayer("DWD").size());
            assertEquals(1, collector.getTablesByLayer("ADS").size());
            assertTrue(collector.getTablesByLayer("DWS").isEmpty());
        }

        // --- getTablesByTag ---

        @Test
        @DisplayName("按标签过滤表")
        void testGetTablesByTag() {
            TableMetadata t1 = buildTable("db", "t1", "HIVE", "ODS");
            t1.addTag("credit");
            t1.addTag("sensitive");
            collector.register(t1);

            TableMetadata t2 = buildTable("db", "t2", "HIVE", "ODS");
            t2.addTag("marketing");
            collector.register(t2);

            assertEquals(1, collector.getTablesByTag("credit").size());
            assertEquals(1, collector.getTablesByTag("marketing").size());
            assertTrue(collector.getTablesByTag("unknown").isEmpty());
        }

        // --- getTablesBySource ---

        @Test
        @DisplayName("按数据源过滤表")
        void testGetTablesBySource() {
            collector.register(buildTable("db1", "hive_t1", "HIVE", "ODS"));
            collector.register(buildTable("db1", "hive_t2", "HIVE", "ODS"));
            collector.register(buildTable("db2", "mysql_t1", "MYSQL", "DWD"));
            collector.register(buildTable("db3", "hbase_t1", "HBASE", "DWS"));

            assertEquals(2, collector.getTablesBySource("HIVE").size());
            assertEquals(1, collector.getTablesBySource("MYSQL").size());
            assertEquals(1, collector.getTablesBySource("HBASE").size());
            assertTrue(collector.getTablesBySource("KAFKA").isEmpty());
        }

        // --- getAllTables / size ---

        @Test
        @DisplayName("getAllTables 返回全量且 size 一致")
        void testGetAllTablesAndSize() {
            assertTrue(collector.getAllTables().isEmpty());
            assertEquals(0, collector.size());

            collector.register(buildTable("db", "t1", "HIVE", "ODS"));
            collector.register(buildTable("db", "t2", "HIVE", "ODS"));
            collector.register(buildTable("db", "t3", "HIVE", "ODS"));

            assertEquals(3, collector.getAllTables().size());
            assertEquals(3, collector.size());
        }

        @Test
        @DisplayName("getAllTables 返回的是副本，不影响内部状态")
        void testGetAllTablesReturnsCopy() {
            collector.register(buildTable("db", "t1", "HIVE", "ODS"));
            List<TableMetadata> all = collector.getAllTables();
            all.clear();
            assertEquals(1, collector.size());
        }

        // --- registerAll ---

        @Test
        @DisplayName("registerAll 批量注册并设置 lastCollectionTime")
        void testRegisterAll() {
            List<TableMetadata> batch = List.of(
                    buildTable("db", "t1", "HIVE", "ODS"),
                    buildTable("db", "t2", "HIVE", "ODS")
            );
            collector.registerAll(batch);
            assertEquals(2, collector.size());
            assertNotNull(collector.getLastCollectionTime());
        }

        // --- detectChanges ---

        @Test
        @DisplayName("detectChanges — 新表")
        void testDetectChanges_newTable() {
            TableMetadata newTable = buildTable("finance", "new_table", "HIVE", "ODS");
            List<String> changes = collector.detectChanges(newTable);
            assertEquals(1, changes.size());
            assertTrue(changes.get(0).contains("新表"));
            assertTrue(changes.get(0).contains("finance.new_table"));
        }

        @Test
        @DisplayName("detectChanges — 新增列")
        void testDetectChanges_newColumns() {
            TableMetadata existing = buildTable("finance", "loan_app", "HIVE", "ODS");
            existing.setColumns(List.of(
                    new ColumnMetadata("id", "BIGINT", "PK"),
                    new ColumnMetadata("name", "STRING", "name")
            ));
            collector.register(existing);

            TableMetadata updated = buildTable("finance", "loan_app", "HIVE", "ODS");
            updated.setColumns(List.of(
                    new ColumnMetadata("id", "BIGINT", "PK"),
                    new ColumnMetadata("name", "STRING", "name"),
                    new ColumnMetadata("phone", "STRING", "phone")
            ));

            List<String> changes = collector.detectChanges(updated);
            assertEquals(1, changes.size());
            assertTrue(changes.get(0).contains("新增列"));
            assertTrue(changes.get(0).contains("phone"));
            assertTrue(changes.get(0).contains("STRING"));
        }

        @Test
        @DisplayName("detectChanges — 删除列")
        void testDetectChanges_deletedColumns() {
            TableMetadata existing = buildTable("finance", "loan_app", "HIVE", "ODS");
            existing.setColumns(List.of(
                    new ColumnMetadata("id", "BIGINT", "PK"),
                    new ColumnMetadata("name", "STRING", "name"),
                    new ColumnMetadata("old_col", "STRING", "deprecated")
            ));
            collector.register(existing);

            TableMetadata updated = buildTable("finance", "loan_app", "HIVE", "ODS");
            updated.setColumns(List.of(
                    new ColumnMetadata("id", "BIGINT", "PK"),
                    new ColumnMetadata("name", "STRING", "name")
            ));

            List<String> changes = collector.detectChanges(updated);
            assertEquals(1, changes.size());
            assertTrue(changes.get(0).contains("删除列"));
            assertTrue(changes.get(0).contains("old_col"));
        }

        @Test
        @DisplayName("detectChanges — 类型变更")
        void testDetectChanges_typeChange() {
            TableMetadata existing = buildTable("finance", "loan_app", "HIVE", "ODS");
            existing.setColumns(List.of(
                    new ColumnMetadata("id", "BIGINT", "PK"),
                    new ColumnMetadata("amount", "DECIMAL", "amount")
            ));
            collector.register(existing);

            TableMetadata updated = buildTable("finance", "loan_app", "HIVE", "ODS");
            updated.setColumns(List.of(
                    new ColumnMetadata("id", "BIGINT", "PK"),
                    new ColumnMetadata("amount", "STRING", "amount")
            ));

            List<String> changes = collector.detectChanges(updated);
            assertEquals(1, changes.size());
            assertTrue(changes.get(0).contains("类型变更"));
            assertTrue(changes.get(0).contains("amount"));
            assertTrue(changes.get(0).contains("DECIMAL"));
            assertTrue(changes.get(0).contains("STRING"));
        }

        @Test
        @DisplayName("detectChanges — 无变更")
        void testDetectChanges_noChange() {
            TableMetadata existing = buildTable("finance", "loan_app", "HIVE", "ODS");
            existing.setColumns(List.of(
                    new ColumnMetadata("id", "BIGINT", "PK"),
                    new ColumnMetadata("name", "STRING", "name")
            ));
            collector.register(existing);

            TableMetadata same = buildTable("finance", "loan_app", "HIVE", "ODS");
            same.setColumns(List.of(
                    new ColumnMetadata("id", "BIGINT", "PK"),
                    new ColumnMetadata("name", "STRING", "name")
            ));

            List<String> changes = collector.detectChanges(same);
            assertTrue(changes.isEmpty());
        }

        @Test
        @DisplayName("detectChanges — 同时存在新增列、删除列和类型变更")
        void testDetectChanges_mixedChanges() {
            TableMetadata existing = buildTable("finance", "loan_app", "HIVE", "ODS");
            existing.setColumns(List.of(
                    new ColumnMetadata("id", "BIGINT", "PK"),
                    new ColumnMetadata("amount", "DECIMAL", "amount"),
                    new ColumnMetadata("old_col", "STRING", "old")
            ));
            collector.register(existing);

            TableMetadata updated = buildTable("finance", "loan_app", "HIVE", "ODS");
            updated.setColumns(List.of(
                    new ColumnMetadata("id", "BIGINT", "PK"),
                    new ColumnMetadata("amount", "STRING", "amount"),
                    new ColumnMetadata("new_col", "STRING", "new")
            ));

            List<String> changes = collector.detectChanges(updated);
            assertEquals(3, changes.size());
            assertTrue(changes.stream().anyMatch(c -> c.contains("类型变更") && c.contains("amount")));
            assertTrue(changes.stream().anyMatch(c -> c.contains("新增列") && c.contains("new_col")));
            assertTrue(changes.stream().anyMatch(c -> c.contains("删除列") && c.contains("old_col")));
        }

        // --- helper ---

        private TableMetadata buildTable(String database, String tableName, String source, String layer) {
            TableMetadata t = new TableMetadata();
            t.setDatabase(database);
            t.setTableName(tableName);
            t.setSource(source);
            t.setLayer(layer);
            return t;
        }
    }

    // ===================== LineageAnalyzer =====================

    @Nested
    @DisplayName("LineageAnalyzer 组件测试")
    class LineageAnalyzerTest {

        private LineageAnalyzer analyzer;

        @BeforeEach
        void setUp() {
            analyzer = new LineageAnalyzer();
        }

        // --- analyzeSql ---

        @Test
        @DisplayName("解析 INSERT OVERWRITE ... SELECT ... FROM 单表")
        void testAnalyzeSql_insertOverwriteSingleSource() {
            String sql = "INSERT OVERWRITE TABLE dwd.dwd_loan SELECT * FROM ods.ods_loan";
            List<LineageEdge> edges = analyzer.analyzeSql(sql, "etl_ods_dwd");

            assertEquals(1, edges.size());
            assertEquals("ods.ods_loan", edges.get(0).getSourceId());
            assertEquals("dwd.dwd_loan", edges.get(0).getTargetId());
            assertEquals("ETL", edges.get(0).getTransformType());
            assertEquals("etl_ods_dwd", edges.get(0).getTransformName());
        }

        @Test
        @DisplayName("解析 INSERT OVERWRITE ... JOIN 多源表")
        void testAnalyzeSql_insertWithJoin() {
            String sql = "INSERT OVERWRITE TABLE dwd.dwd_detail " +
                    "SELECT a.id, b.name " +
                    "FROM ods.ods_loan a JOIN ods.ods_customer b ON a.cid = b.cid";

            List<LineageEdge> edges = analyzer.analyzeSql(sql, "etl_join");

            assertEquals(2, edges.size());
            assertTrue(edges.stream().anyMatch(e -> e.getSourceId().equals("ods.ods_loan")));
            assertTrue(edges.stream().anyMatch(e -> e.getSourceId().equals("ods.ods_customer")));
            assertTrue(edges.stream().allMatch(e -> e.getTargetId().equals("dwd.dwd_detail")));
        }

        @Test
        @DisplayName("解析多条分号分隔的 SQL 语句")
        void testAnalyzeSql_multipleStatements() {
            String sql = "INSERT OVERWRITE TABLE dwd.dwd_t1 SELECT * FROM ods.ods_s1;" +
                    "INSERT OVERWRITE TABLE dws.dws_t2 SELECT * FROM dwd.dwd_t1;" +
                    "INSERT OVERWRITE TABLE ads.ads_t3 SELECT * FROM dws.dws_t2";

            List<LineageEdge> edges = analyzer.analyzeSql(sql, "multi_etl");

            assertEquals(3, edges.size());
            assertTrue(edges.stream().anyMatch(e ->
                    e.getSourceId().equals("ods.ods_s1") && e.getTargetId().equals("dwd.dwd_t1")));
            assertTrue(edges.stream().anyMatch(e ->
                    e.getSourceId().equals("dwd.dwd_t1") && e.getTargetId().equals("dws.dws_t2")));
            assertTrue(edges.stream().anyMatch(e ->
                    e.getSourceId().equals("dws.dws_t2") && e.getTargetId().equals("ads.ads_t3")));
        }

        @Test
        @DisplayName("非 INSERT 语句不产生血缘边")
        void testAnalyzeSql_nonInsertSkipped() {
            String sql = "SELECT * FROM ods.ods_loan WHERE id > 100";
            List<LineageEdge> edges = analyzer.analyzeSql(sql, "query_only");
            assertTrue(edges.isEmpty());
        }

        @Test
        @DisplayName("空字符串和空白语句不产生边")
        void testAnalyzeSql_emptyAndBlank() {
            String sql = "  ; ;  ";
            List<LineageEdge> edges = analyzer.analyzeSql(sql, "empty");
            assertTrue(edges.isEmpty());
        }

        @Test
        @DisplayName("表名含反引号时正确标准化")
        void testAnalyzeSql_backtickNormalization() {
            String sql = "INSERT OVERWRITE TABLE `dwd`.`dwd_t1` SELECT * FROM `ods`.`ods_s1`";
            List<LineageEdge> edges = analyzer.analyzeSql(sql, "backtick");
            assertEquals(1, edges.size());
            assertEquals("ods.ods_s1", edges.get(0).getSourceId());
            assertEquals("dwd.dwd_t1", edges.get(0).getTargetId());
        }

        // --- buildLineageGraph ---

        @Test
        @DisplayName("buildLineageGraph 构建完整图（节点+边）")
        void testBuildLineageGraph() {
            Map<String, String> scripts = new LinkedHashMap<>();
            scripts.put("etl1", "INSERT OVERWRITE TABLE dwd.dwd_t1 SELECT * FROM ods.ods_s1");
            scripts.put("etl2", "INSERT OVERWRITE TABLE dws.dws_t2 SELECT * FROM dwd.dwd_t1");

            LineageGraph graph = analyzer.buildLineageGraph(scripts);

            assertEquals(3, graph.getNodes().size());
            assertEquals(2, graph.getEdges().size());
            assertTrue(graph.getNodes().containsKey("ods.ods_s1"));
            assertTrue(graph.getNodes().containsKey("dwd.dwd_t1"));
            assertTrue(graph.getNodes().containsKey("dws.dws_t2"));
        }

        @Test
        @DisplayName("buildLineageGraph — 节点层推断正确")
        void testBuildLineageGraph_layerInference() {
            Map<String, String> scripts = new LinkedHashMap<>();
            scripts.put("etl1", "INSERT OVERWRITE TABLE dwd.dwd_t1 SELECT * FROM ods.ods_s1");

            LineageGraph graph = analyzer.buildLineageGraph(scripts);

            LineageNode odsNode = graph.getNodes().get("ods.ods_s1");
            LineageNode dwdNode = graph.getNodes().get("dwd.dwd_t1");
            assertNotNull(odsNode);
            assertNotNull(dwdNode);
            assertEquals("ODS", odsNode.getLayer());
            assertEquals("DWD", dwdNode.getLayer());
        }

        @Test
        @DisplayName("buildLineageGraph — 多源汇聚到同一目标，去重节点")
        void testBuildLineageGraph_mergeSources() {
            Map<String, String> scripts = new LinkedHashMap<>();
            scripts.put("etl1",
                    "INSERT OVERWRITE TABLE dws.dws_summary SELECT * FROM ods.ods_a;" +
                    "INSERT OVERWRITE TABLE dws.dws_summary SELECT * FROM ods.ods_b");

            LineageGraph graph = analyzer.buildLineageGraph(scripts);

            assertEquals(3, graph.getNodes().size());
            assertTrue(graph.getNodes().containsKey("ods.ods_a"));
            assertTrue(graph.getNodes().containsKey("ods.ods_b"));
            assertTrue(graph.getNodes().containsKey("dws.dws_summary"));
            assertEquals(2, graph.getEdges().size());
        }

        // --- layer inference (via buildLineageGraph) ---

        @Test
        @DisplayName("层推断 — ODS/DWD/DWS/ADS/UNKNOWN")
        void testLayerInference() {
            Map<String, String> scripts = new LinkedHashMap<>();
            scripts.put("e1", "INSERT OVERWRITE TABLE ods.ods_t SELECT * FROM ext.src");
            scripts.put("e2", "INSERT OVERWRITE TABLE dwd.dwd_t SELECT * FROM ods.ods_t");
            scripts.put("e3", "INSERT OVERWRITE TABLE dws.dws_t SELECT * FROM dwd.dwd_t");
            scripts.put("e4", "INSERT OVERWRITE TABLE ads.ads_t SELECT * FROM dws.dws_t");

            LineageGraph graph = analyzer.buildLineageGraph(scripts);

            assertEquals("ODS", graph.getNodes().get("ods.ods_t").getLayer());
            assertEquals("DWD", graph.getNodes().get("dwd.dwd_t").getLayer());
            assertEquals("DWS", graph.getNodes().get("dws.dws_t").getLayer());
            assertEquals("ADS", graph.getNodes().get("ads.ads_t").getLayer());
            assertEquals("UNKNOWN", graph.getNodes().get("ext.src").getLayer());
        }
    }

    // ===================== DataClassifier =====================

    @Nested
    @DisplayName("DataClassifier 组件测试")
    class DataClassifierTest {

        private DataClassifier classifier;

        @BeforeEach
        void setUp() {
            classifier = new DataClassifier();
        }

        // --- TOP_SECRET ---

        @Test
        @DisplayName("bank_card 关键字 → TOP_SECRET")
        void testClassify_bankCard() {
            assertEquals(DataClassifier.SensitivityLevel.TOP_SECRET, classifier.classify("bank_card"));
        }

        @Test
        @DisplayName("bankcard 关键字 → TOP_SECRET")
        void testClassify_bankcard() {
            assertEquals(DataClassifier.SensitivityLevel.TOP_SECRET, classifier.classify("bankcard"));
        }

        @Test
        @DisplayName("card_no 关键字 → TOP_SECRET")
        void testClassify_cardNo() {
            assertEquals(DataClassifier.SensitivityLevel.TOP_SECRET, classifier.classify("card_no"));
        }

        @Test
        @DisplayName("password 关键字 → TOP_SECRET")
        void testClassify_password() {
            assertEquals(DataClassifier.SensitivityLevel.TOP_SECRET, classifier.classify("password"));
        }

        @Test
        @DisplayName("secret 关键字 → TOP_SECRET")
        void testClassify_secret() {
            assertEquals(DataClassifier.SensitivityLevel.TOP_SECRET, classifier.classify("secret"));
        }

        // --- CONFIDENTIAL ---

        @Test
        @DisplayName("id_card 关键字 → CONFIDENTIAL")
        void testClassify_idCard() {
            assertEquals(DataClassifier.SensitivityLevel.CONFIDENTIAL, classifier.classify("id_card"));
        }

        @Test
        @DisplayName("phone 关键字 → CONFIDENTIAL")
        void testClassify_phone() {
            assertEquals(DataClassifier.SensitivityLevel.CONFIDENTIAL, classifier.classify("phone_number"));
        }

        @Test
        @DisplayName("mobile 关键字 → CONFIDENTIAL")
        void testClassify_mobile() {
            assertEquals(DataClassifier.SensitivityLevel.CONFIDENTIAL, classifier.classify("mobile"));
        }

        @Test
        @DisplayName("email 关键字 → CONFIDENTIAL")
        void testClassify_email() {
            assertEquals(DataClassifier.SensitivityLevel.CONFIDENTIAL, classifier.classify("email"));
        }

        @Test
        @DisplayName("name 关键字 → CONFIDENTIAL")
        void testClassify_name() {
            assertEquals(DataClassifier.SensitivityLevel.CONFIDENTIAL, classifier.classify("customer_name"));
        }

        @Test
        @DisplayName("address 关键字 → CONFIDENTIAL")
        void testClassify_address() {
            assertEquals(DataClassifier.SensitivityLevel.CONFIDENTIAL, classifier.classify("home_address"));
        }

        // --- INTERNAL ---

        @Test
        @DisplayName("amount 关键字 → INTERNAL")
        void testClassify_amount() {
            assertEquals(DataClassifier.SensitivityLevel.INTERNAL, classifier.classify("loan_amount"));
        }

        @Test
        @DisplayName("income 关键字 → INTERNAL")
        void testClassify_income() {
            assertEquals(DataClassifier.SensitivityLevel.INTERNAL, classifier.classify("monthly_income"));
        }

        @Test
        @DisplayName("salary 关键字 → INTERNAL")
        void testClassify_salary() {
            assertEquals(DataClassifier.SensitivityLevel.INTERNAL, classifier.classify("salary"));
        }

        @Test
        @DisplayName("balance 关键字 → INTERNAL")
        void testClassify_balance() {
            assertEquals(DataClassifier.SensitivityLevel.INTERNAL, classifier.classify("balance"));
        }

        @Test
        @DisplayName("score 关键字 → INTERNAL")
        void testClassify_score() {
            assertEquals(DataClassifier.SensitivityLevel.INTERNAL, classifier.classify("credit_score"));
        }

        // --- PUBLIC ---

        @Test
        @DisplayName("无敏感关键字 → PUBLIC")
        void testClassify_public() {
            assertEquals(DataClassifier.SensitivityLevel.PUBLIC, classifier.classify("customer_id"));
            assertEquals(DataClassifier.SensitivityLevel.PUBLIC, classifier.classify("status"));
            assertEquals(DataClassifier.SensitivityLevel.PUBLIC, classifier.classify("create_time"));
        }

        // --- null ---

        @Test
        @DisplayName("null 输入 → PUBLIC")
        void testClassify_null() {
            assertEquals(DataClassifier.SensitivityLevel.PUBLIC, classifier.classify(null));
        }

        // --- classifyAll ---

        @Test
        @DisplayName("classifyAll 批量分类")
        void testClassifyAll() {
            Map<String, DataClassifier.SensitivityLevel> result = classifier.classifyAll(
                    List.of("bank_card_no", "id_card", "amount", "status"));

            assertEquals(DataClassifier.SensitivityLevel.TOP_SECRET, result.get("bank_card_no"));
            assertEquals(DataClassifier.SensitivityLevel.CONFIDENTIAL, result.get("id_card"));
            assertEquals(DataClassifier.SensitivityLevel.INTERNAL, result.get("amount"));
            assertEquals(DataClassifier.SensitivityLevel.PUBLIC, result.get("status"));
            assertEquals(4, result.size());
        }

        // --- requiresEncryption ---

        @Test
        @DisplayName("requiresEncryption — CONFIDENTIAL 和 TOP_SECRET 需加密")
        void testRequiresEncryption() {
            assertTrue(classifier.requiresEncryption(DataClassifier.SensitivityLevel.CONFIDENTIAL));
            assertTrue(classifier.requiresEncryption(DataClassifier.SensitivityLevel.TOP_SECRET));
            assertFalse(classifier.requiresEncryption(DataClassifier.SensitivityLevel.PUBLIC));
            assertFalse(classifier.requiresEncryption(DataClassifier.SensitivityLevel.INTERNAL));
        }

        // --- requiresMasking ---

        @Test
        @DisplayName("requiresMasking — CONFIDENTIAL 和 TOP_SECRET 需脱敏")
        void testRequiresMasking() {
            assertTrue(classifier.requiresMasking(DataClassifier.SensitivityLevel.CONFIDENTIAL));
            assertTrue(classifier.requiresMasking(DataClassifier.SensitivityLevel.TOP_SECRET));
            assertFalse(classifier.requiresMasking(DataClassifier.SensitivityLevel.PUBLIC));
            assertFalse(classifier.requiresMasking(DataClassifier.SensitivityLevel.INTERNAL));
        }

        // --- SensitivityLevel enum ---

        @Test
        @DisplayName("SensitivityLevel 等级顺序正确")
        void testSensitivityLevelOrder() {
            assertTrue(DataClassifier.SensitivityLevel.PUBLIC.getLevel() <
                    DataClassifier.SensitivityLevel.INTERNAL.getLevel());
            assertTrue(DataClassifier.SensitivityLevel.INTERNAL.getLevel() <
                    DataClassifier.SensitivityLevel.CONFIDENTIAL.getLevel());
            assertTrue(DataClassifier.SensitivityLevel.CONFIDENTIAL.getLevel() <
                    DataClassifier.SensitivityLevel.TOP_SECRET.getLevel());
        }
    }

    // ===================== DataMaskingService =====================

    @Nested
    @DisplayName("DataMaskingService 组件测试")
    class DataMaskingServiceTest {

        private DataMaskingService maskingService;

        @BeforeEach
        void setUp() {
            maskingService = new DataMaskingService();
        }

        // --- inferSensitiveType ---

        @Test
        @DisplayName("inferSensitiveType — 身份证相关字段返回 ID_CARD")
        void testInferSensitiveType_idCard() {
            assertEquals(com.credit.platform.engine.common.masking.SensitiveType.ID_CARD,
                    maskingService.inferSensitiveType("id_card"));
            assertEquals(com.credit.platform.engine.common.masking.SensitiveType.ID_CARD,
                    maskingService.inferSensitiveType("idcard_number"));
            assertEquals(com.credit.platform.engine.common.masking.SensitiveType.ID_CARD,
                    maskingService.inferSensitiveType("identity"));
        }

        @Test
        @DisplayName("inferSensitiveType — 手机号字段返回 MOBILE")
        void testInferSensitiveType_mobile() {
            assertEquals(com.credit.platform.engine.common.masking.SensitiveType.MOBILE,
                    maskingService.inferSensitiveType("phone"));
            assertEquals(com.credit.platform.engine.common.masking.SensitiveType.MOBILE,
                    maskingService.inferSensitiveType("mobile"));
            assertEquals(com.credit.platform.engine.common.masking.SensitiveType.MOBILE,
                    maskingService.inferSensitiveType("tel_number"));
        }

        @Test
        @DisplayName("inferSensitiveType — 银行卡字段返回 BANK_CARD")
        void testInferSensitiveType_bankCard() {
            assertEquals(com.credit.platform.engine.common.masking.SensitiveType.BANK_CARD,
                    maskingService.inferSensitiveType("bank_card"));
            assertEquals(com.credit.platform.engine.common.masking.SensitiveType.BANK_CARD,
                    maskingService.inferSensitiveType("card_no"));
        }

        @Test
        @DisplayName("inferSensitiveType — 姓名字段返回 NAME")
        void testInferSensitiveType_name() {
            assertEquals(com.credit.platform.engine.common.masking.SensitiveType.NAME,
                    maskingService.inferSensitiveType("customer_name"));
            assertEquals(com.credit.platform.engine.common.masking.SensitiveType.NAME,
                    maskingService.inferSensitiveType("name"));
        }

        @Test
        @DisplayName("inferSensitiveType — 邮箱字段返回 EMAIL")
        void testInferSensitiveType_email() {
            assertEquals(com.credit.platform.engine.common.masking.SensitiveType.EMAIL,
                    maskingService.inferSensitiveType("email"));
        }

        @Test
        @DisplayName("inferSensitiveType — 地址字段返回 ADDRESS")
        void testInferSensitiveType_address() {
            assertEquals(com.credit.platform.engine.common.masking.SensitiveType.ADDRESS,
                    maskingService.inferSensitiveType("address"));
        }

        @Test
        @DisplayName("inferSensitiveType — 普通字段返回 null")
        void testInferSensitiveType_unknown() {
            assertNull(maskingService.inferSensitiveType("status"));
            assertNull(maskingService.inferSensitiveType("amount"));
        }

        @Test
        @DisplayName("inferSensitiveType — null 返回 null")
        void testInferSensitiveType_null() {
            assertNull(maskingService.inferSensitiveType(null));
        }

        // --- mask (single field) ---

        @Test
        @DisplayName("mask — null 值返回 null")
        void testMask_nullValue() {
            assertNull(maskingService.mask("id_card", null));
        }

        @Test
        @DisplayName("mask — 公开字段原样返回")
        void testMask_publicField() {
            assertEquals("ACTIVE", maskingService.mask("status", "ACTIVE"));
        }

        @Test
        @DisplayName("mask — 内部字段不脱敏（仅 CONFIDENTIAL 及以上才脱敏）")
        void testMask_internalFieldNoMask() {
            // "salary" is INTERNAL level — requiresMasking returns false for INTERNAL,
            // so the value is returned as-is without masking
            String masked = maskingService.mask("salary", "ABCDEFGH");
            assertEquals("ABCDEFGH", masked);
        }

        @Test
        @DisplayName("mask — 身份证号脱敏")
        void testMask_idCard() {
            String masked = maskingService.mask("id_card", "110101199001011234");
            assertNotNull(masked);
            assertNotEquals("110101199001011234", masked);
            // ID_CARD keeps first 3 and last 4
            assertTrue(masked.startsWith("110"));
            assertTrue(masked.endsWith("1234"));
            assertTrue(masked.contains("*"));
        }

        @Test
        @DisplayName("mask — 手机号脱敏")
        void testMask_mobile() {
            String masked = maskingService.mask("phone", "13812345678");
            assertNotNull(masked);
            assertNotEquals("13812345678", masked);
            assertTrue(masked.startsWith("138"));
            assertTrue(masked.endsWith("5678"));
            assertTrue(masked.contains("*"));
        }

        @Test
        @DisplayName("mask — 银行卡号脱敏")
        void testMask_bankCard() {
            String masked = maskingService.mask("bank_card", "6228480000001234567");
            assertNotNull(masked);
            assertNotEquals("6228480000001234567", masked);
            assertTrue(masked.contains("*"));
        }

        @Test
        @DisplayName("mask — 姓名脱敏")
        void testMask_name() {
            String masked = maskingService.mask("name", "张三丰");
            assertNotNull(masked);
            assertTrue(masked.startsWith("张"));
            assertTrue(masked.contains("*"));
        }

        @Test
        @DisplayName("mask — 邮箱脱敏")
        void testMask_email() {
            String masked = maskingService.mask("email", "zhangsan@example.com");
            assertNotNull(masked);
            assertTrue(masked.contains("@"));
            assertTrue(masked.contains("*"));
            assertTrue(masked.endsWith("example.com"));
        }

        @Test
        @DisplayName("mask — 地址脱敏")
        void testMask_address() {
            String masked = maskingService.mask("address", "北京市海淀区中关村大街1号");
            assertNotNull(masked);
            assertTrue(masked.startsWith("北京市海淀区"));
            assertTrue(masked.contains("*"));
        }

        // --- maskRecord (batch) ---

        @Test
        @DisplayName("maskRecord — 批量脱敏整行记录")
        void testMaskRecord() {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", 1001);                       // Long, not String
            record.put("name", "张三");                    // NAME → should mask
            record.put("phone", "13812345678");            // MOBILE → should mask
            record.put("status", "ACTIVE");                // PUBLIC → no mask

            Map<String, Object> masked = maskingService.maskRecord(record);

            assertEquals(1001, masked.get("id"));                          // non-String unchanged
            assertEquals("ACTIVE", masked.get("status"));                  // public unchanged
            assertNotEquals("张三", masked.get("name"));                    // masked
            assertNotEquals("13812345678", masked.get("phone"));           // masked
        }

        @Test
        @DisplayName("maskRecord — null 值保留")
        void testMaskRecord_nullValue() {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("name", null);
            record.put("status", "OK");

            Map<String, Object> masked = maskingService.maskRecord(record);
            assertNull(masked.get("name"));
            assertEquals("OK", masked.get("status"));
        }
    }

    // ===================== LifecycleManager =====================

    @Nested
    @DisplayName("LifecycleManager 组件测试")
    class LifecycleManagerTest {

        private LifecycleManager manager;

        @BeforeEach
        void setUp() {
            manager = new LifecycleManager();
        }

        // --- ODS tier boundaries ---

        @Test
        @DisplayName("ODS — 0 天为热数据")
        void testOds_hot_today() {
            assertEquals(LifecycleManager.TemperatureTier.HOT,
                    manager.determineTier("ODS", LocalDate.now()));
        }

        @Test
        @DisplayName("ODS — 30 天边界为热数据")
        void testOds_hotBoundary() {
            assertEquals(LifecycleManager.TemperatureTier.HOT,
                    manager.determineTier("ODS", LocalDate.now().minusDays(30)));
        }

        @Test
        @DisplayName("ODS — 31 天为温数据")
        void testOds_warm() {
            assertEquals(LifecycleManager.TemperatureTier.WARM,
                    manager.determineTier("ODS", LocalDate.now().minusDays(31)));
        }

        @Test
        @DisplayName("ODS — 180 天边界为温数据")
        void testOds_warmBoundary() {
            assertEquals(LifecycleManager.TemperatureTier.WARM,
                    manager.determineTier("ODS", LocalDate.now().minusDays(180)));
        }

        @Test
        @DisplayName("ODS — 181 天为冷数据")
        void testOds_cold() {
            assertEquals(LifecycleManager.TemperatureTier.COLD,
                    manager.determineTier("ODS", LocalDate.now().minusDays(181)));
        }

        @Test
        @DisplayName("ODS — 730 天边界为冷数据")
        void testOds_coldBoundary() {
            assertEquals(LifecycleManager.TemperatureTier.COLD,
                    manager.determineTier("ODS", LocalDate.now().minusDays(730)));
        }

        @Test
        @DisplayName("ODS — 731 天为归档数据")
        void testOds_archive() {
            assertEquals(LifecycleManager.TemperatureTier.ARCHIVE,
                    manager.determineTier("ODS", LocalDate.now().minusDays(731)));
        }

        // --- DWD tier boundaries ---

        @Test
        @DisplayName("DWD — 90 天为热数据")
        void testDwd_hot() {
            assertEquals(LifecycleManager.TemperatureTier.HOT,
                    manager.determineTier("DWD", LocalDate.now().minusDays(90)));
        }

        @Test
        @DisplayName("DWD — 91 天为温数据")
        void testDwd_warm() {
            assertEquals(LifecycleManager.TemperatureTier.WARM,
                    manager.determineTier("DWD", LocalDate.now().minusDays(91)));
        }

        @Test
        @DisplayName("DWD — 365 天边界为温数据")
        void testDwd_warmBoundary() {
            assertEquals(LifecycleManager.TemperatureTier.WARM,
                    manager.determineTier("DWD", LocalDate.now().minusDays(365)));
        }

        @Test
        @DisplayName("DWD — 366 天为冷数据")
        void testDwd_cold() {
            assertEquals(LifecycleManager.TemperatureTier.COLD,
                    manager.determineTier("DWD", LocalDate.now().minusDays(366)));
        }

        // --- DWS tier boundaries ---

        @Test
        @DisplayName("DWS — 180 天为热数据")
        void testDws_hot() {
            assertEquals(LifecycleManager.TemperatureTier.HOT,
                    manager.determineTier("DWS", LocalDate.now().minusDays(180)));
        }

        @Test
        @DisplayName("DWS — 181 天为温数据")
        void testDws_warm() {
            assertEquals(LifecycleManager.TemperatureTier.WARM,
                    manager.determineTier("DWS", LocalDate.now().minusDays(181)));
        }

        // --- ADS tier boundaries ---

        @Test
        @DisplayName("ADS — 365 天为热数据")
        void testAds_hot() {
            assertEquals(LifecycleManager.TemperatureTier.HOT,
                    manager.determineTier("ADS", LocalDate.now().minusDays(365)));
        }

        @Test
        @DisplayName("ADS — 366 天为温数据")
        void testAds_warm() {
            assertEquals(LifecycleManager.TemperatureTier.WARM,
                    manager.determineTier("ADS", LocalDate.now().minusDays(366)));
        }

        // --- ES_LOG tier boundaries ---

        @Test
        @DisplayName("ES_LOG — 7 天为热数据")
        void testEsLog_hot() {
            assertEquals(LifecycleManager.TemperatureTier.HOT,
                    manager.determineTier("ES_LOG", LocalDate.now().minusDays(7)));
        }

        @Test
        @DisplayName("ES_LOG — 8 天为温数据")
        void testEsLog_warm() {
            assertEquals(LifecycleManager.TemperatureTier.WARM,
                    manager.determineTier("ES_LOG", LocalDate.now().minusDays(8)));
        }

        @Test
        @DisplayName("ES_LOG — 90 天边界为温数据")
        void testEsLog_warmBoundary() {
            assertEquals(LifecycleManager.TemperatureTier.WARM,
                    manager.determineTier("ES_LOG", LocalDate.now().minusDays(90)));
        }

        @Test
        @DisplayName("ES_LOG — 91 天为冷数据")
        void testEsLog_cold() {
            assertEquals(LifecycleManager.TemperatureTier.COLD,
                    manager.determineTier("ES_LOG", LocalDate.now().minusDays(91)));
        }

        // --- Unknown layer ---

        @Test
        @DisplayName("未知层默认返回 HOT")
        void testUnknownLayer_defaultsToHot() {
            assertEquals(LifecycleManager.TemperatureTier.HOT,
                    manager.determineTier("CUSTOM_LAYER", LocalDate.now().minusDays(9999)));
        }

        // --- getPolicy ---

        @Test
        @DisplayName("getPolicy — ODS 策略参数正确")
        void testGetPolicy_ods() {
            LifecycleManager.LifecyclePolicy policy = manager.getPolicy("ODS");
            assertNotNull(policy);
            assertEquals("ODS", policy.getLayer());
            assertEquals(30, policy.getHotDays());
            assertEquals(180, policy.getWarmDays());
            assertEquals(730, policy.getColdDays());
            assertEquals(730, policy.getRetentionDays());
        }

        @Test
        @DisplayName("getPolicy — DWD 策略参数正确")
        void testGetPolicy_dwd() {
            LifecycleManager.LifecyclePolicy policy = manager.getPolicy("DWD");
            assertNotNull(policy);
            assertEquals(90, policy.getHotDays());
            assertEquals(365, policy.getWarmDays());
            assertEquals(1095, policy.getColdDays());
        }

        @Test
        @DisplayName("getPolicy — DWS 策略参数正确")
        void testGetPolicy_dws() {
            LifecycleManager.LifecyclePolicy policy = manager.getPolicy("DWS");
            assertNotNull(policy);
            assertEquals(180, policy.getHotDays());
            assertEquals(730, policy.getWarmDays());
            assertEquals(1095, policy.getColdDays());
        }

        @Test
        @DisplayName("getPolicy — ADS 策略参数正确")
        void testGetPolicy_ads() {
            LifecycleManager.LifecyclePolicy policy = manager.getPolicy("ADS");
            assertNotNull(policy);
            assertEquals(365, policy.getHotDays());
            assertEquals(1095, policy.getWarmDays());
            assertEquals(1825, policy.getColdDays());
        }

        @Test
        @DisplayName("getPolicy — ES_LOG 策略参数正确")
        void testGetPolicy_esLog() {
            LifecycleManager.LifecyclePolicy policy = manager.getPolicy("ES_LOG");
            assertNotNull(policy);
            assertEquals(7, policy.getHotDays());
            assertEquals(90, policy.getWarmDays());
            assertEquals(365, policy.getColdDays());
        }

        @Test
        @DisplayName("getPolicy — 未知层返回 null")
        void testGetPolicy_unknown() {
            assertNull(manager.getPolicy("NONEXISTENT"));
        }

        @Test
        @DisplayName("getPolicy — 大小写不敏感")
        void testGetPolicy_caseInsensitive() {
            assertNotNull(manager.getPolicy("ods"));
            assertNotNull(manager.getPolicy("Ods"));
        }

        // --- getArchiveThreshold ---

        @Test
        @DisplayName("getArchiveThreshold — ODS 归档阈值正确")
        void testGetArchiveThreshold_ods() {
            LocalDate threshold = manager.getArchiveThreshold("ODS");
            assertNotNull(threshold);
            assertEquals(LocalDate.now().minusDays(730), threshold);
        }

        @Test
        @DisplayName("getArchiveThreshold — 未知层返回 null")
        void testGetArchiveThreshold_unknown() {
            assertNull(manager.getArchiveThreshold("UNKNOWN_LAYER"));
        }

        // --- scanPartitions ---

        @Test
        @DisplayName("scanPartitions — ODS 各温度层分区数正确")
        void testScanPartitions_ods() {
            List<LocalDate> partitions = List.of(
                    LocalDate.now().minusDays(5),       // HOT  (5 <= 30)
                    LocalDate.now().minusDays(100),     // WARM (31 <= 100 <= 180)
                    LocalDate.now().minusDays(500),     // COLD (181 <= 500 <= 730)
                    LocalDate.now().minusDays(800)      // ARCHIVE (800 > 730)
            );

            Map<LifecycleManager.TemperatureTier, List<LocalDate>> result =
                    manager.scanPartitions("ODS", partitions);

            assertEquals(1, result.get(LifecycleManager.TemperatureTier.HOT).size());
            assertEquals(1, result.get(LifecycleManager.TemperatureTier.WARM).size());
            assertEquals(1, result.get(LifecycleManager.TemperatureTier.COLD).size());
            assertEquals(1, result.get(LifecycleManager.TemperatureTier.ARCHIVE).size());
        }

        @Test
        @DisplayName("scanPartitions — 全部热数据")
        void testScanPartitions_allHot() {
            List<LocalDate> partitions = List.of(
                    LocalDate.now(),
                    LocalDate.now().minusDays(1),
                    LocalDate.now().minusDays(10)
            );

            Map<LifecycleManager.TemperatureTier, List<LocalDate>> result =
                    manager.scanPartitions("ADS", partitions);

            assertEquals(3, result.get(LifecycleManager.TemperatureTier.HOT).size());
            assertTrue(result.get(LifecycleManager.TemperatureTier.WARM).isEmpty());
            assertTrue(result.get(LifecycleManager.TemperatureTier.COLD).isEmpty());
            assertTrue(result.get(LifecycleManager.TemperatureTier.ARCHIVE).isEmpty());
        }

        @Test
        @DisplayName("scanPartitions — 空分区列表")
        void testScanPartitions_empty() {
            Map<LifecycleManager.TemperatureTier, List<LocalDate>> result =
                    manager.scanPartitions("ODS", List.of());

            assertTrue(result.get(LifecycleManager.TemperatureTier.HOT).isEmpty());
            assertTrue(result.get(LifecycleManager.TemperatureTier.WARM).isEmpty());
            assertTrue(result.get(LifecycleManager.TemperatureTier.COLD).isEmpty());
            assertTrue(result.get(LifecycleManager.TemperatureTier.ARCHIVE).isEmpty());
        }

        // --- LifecyclePolicy toString ---

        @Test
        @DisplayName("LifecyclePolicy toString 包含层名和天数")
        void testPolicyToString() {
            LifecycleManager.LifecyclePolicy policy = manager.getPolicy("ODS");
            String str = policy.toString();
            assertTrue(str.contains("ODS"));
            assertTrue(str.contains("30"));
        }

        // --- TemperatureTier enum ---

        @Test
        @DisplayName("TemperatureTier 枚举值完整")
        void testTemperatureTierValues() {
            LifecycleManager.TemperatureTier[] tiers = LifecycleManager.TemperatureTier.values();
            assertEquals(4, tiers.length);
            assertNotNull(LifecycleManager.TemperatureTier.HOT.getName());
            assertNotNull(LifecycleManager.TemperatureTier.WARM.getStorageType());
        }
    }
}
