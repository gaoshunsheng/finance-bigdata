package com.credit.platform.data.governance;

import com.credit.platform.data.governance.lifecycle.LifecycleManager;
import com.credit.platform.data.governance.lineage.LineageAnalyzer;
import com.credit.platform.data.governance.lineage.LineageEdge;
import com.credit.platform.data.governance.lineage.LineageGraph;
import com.credit.platform.data.governance.lineage.LineageNode;
import com.credit.platform.data.governance.metadata.ColumnMetadata;
import com.credit.platform.data.governance.metadata.MetadataCollector;
import com.credit.platform.data.governance.metadata.TableMetadata;
import com.credit.platform.data.governance.quality.QualityMonitor;
import com.credit.platform.data.governance.quality.QualityRule;
import com.credit.platform.data.governance.quality.QualityViolation;
import com.credit.platform.data.governance.security.DataClassifier;
import com.credit.platform.data.governance.security.DataMaskingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据治理框架单元测试
 */
class DataGovernanceTest {

    // ========== 元数据管理 ==========

    @Nested
    @DisplayName("元数据管理测试")
    class MetadataTest {

        private MetadataCollector collector;

        @BeforeEach
        void setUp() {
            collector = new MetadataCollector();
        }

        @Test
        @DisplayName("注册和查询表元数据")
        void testRegisterAndQuery() {
            TableMetadata table = createOdsTable("ods", "ods_loan_application");
            collector.register(table);

            Optional<TableMetadata> found = collector.getTable("ods", "ods_loan_application");
            assertTrue(found.isPresent());
            assertEquals("HIVE", found.get().getSource());
            assertEquals(3, found.get().getColumns().size());
        }

        @Test
        @DisplayName("按数据层查询")
        void testQueryByLayer() {
            collector.register(createOdsTable("ods", "ods_t1"));
            collector.register(createOdsTable("ods", "ods_t2"));
            collector.register(createDwdTable());

            List<TableMetadata> odsTables = collector.getTablesByLayer("ODS");
            assertEquals(2, odsTables.size());
            assertEquals(1, collector.getTablesByLayer("DWD").size());
        }

        @Test
        @DisplayName("检测新增列变更")
        void testDetectNewColumn() {
            TableMetadata existing = createOdsTable("ods", "ods_loan_application");
            collector.register(existing);

            TableMetadata updated = createOdsTable("ods", "ods_loan_application");
            updated.addColumn(new ColumnMetadata("new_column", "STRING", "新增列"));
            List<String> changes = collector.detectChanges(updated);

            assertEquals(1, changes.size());
            assertTrue(changes.get(0).contains("新增列"));
        }

        @Test
        @DisplayName("检测类型变更")
        void testDetectTypeChange() {
            TableMetadata existing = createOdsTable("ods", "ods_loan_application");
            collector.register(existing);

            TableMetadata updated = createOdsTable("ods", "ods_loan_application");
            updated.getColumns().get(0).setDataType("STRING"); // 修改类型 BIGINT → STRING
            List<String> changes = collector.detectChanges(updated);

            assertTrue(changes.stream().anyMatch(c -> c.contains("类型变更")));
        }

        @Test
        @DisplayName("检测新表")
        void testDetectNewTable() {
            TableMetadata newTable = createOdsTable("ods", "ods_new_table");
            List<String> changes = collector.detectChanges(newTable);
            assertEquals(1, changes.size());
            assertTrue(changes.get(0).contains("新表"));
        }

        @Test
        @DisplayName("标签查询")
        void testQueryByTag() {
            TableMetadata table = createOdsTable("ods", "ods_t1");
            table.addTag("credit");
            table.addTag("sensitive");
            collector.register(table);

            assertEquals(1, collector.getTablesByTag("credit").size());
            assertEquals(0, collector.getTablesByTag("unknown").size());
        }

        private TableMetadata createOdsTable(String db, String name) {
            TableMetadata t = new TableMetadata();
            t.setDatabase(db);
            t.setTableName(name);
            t.setSource("HIVE");
            t.setLayer("ODS");
            t.addColumn(new ColumnMetadata("id", "BIGINT", "主键"));
            t.addColumn(new ColumnMetadata("customer_id", "STRING", "客户ID"));
            t.addColumn(new ColumnMetadata("amount", "DECIMAL", "金额"));
            return t;
        }

        private TableMetadata createDwdTable() {
            TableMetadata t = new TableMetadata();
            t.setDatabase("dwd");
            t.setTableName("dwd_loan_detail");
            t.setSource("HIVE");
            t.setLayer("DWD");
            t.addColumn(new ColumnMetadata("id", "BIGINT", "主键"));
            return t;
        }
    }

    // ========== 血缘分析 ==========

    @Nested
    @DisplayName("血缘分析测试")
    class LineageTest {

        private LineageAnalyzer analyzer;

        @BeforeEach
        void setUp() {
            analyzer = new LineageAnalyzer();
        }

        @Test
        @DisplayName("解析 INSERT OVERWRITE 血缘")
        void testParseInsertOverwrite() {
            String sql = "INSERT OVERWRITE TABLE dwd.dwd_loan_detail SELECT a.id, b.name " +
                    "FROM ods.ods_loan_application a JOIN ods.ods_customer_info b ON a.customer_id = b.customer_id";

            List<LineageEdge> edges = analyzer.analyzeSql(sql, "etl_ods_to_dwd");

            assertEquals(2, edges.size());
            assertTrue(edges.stream().anyMatch(e -> e.getSourceId().contains("ods_loan_application")));
            assertTrue(edges.stream().anyMatch(e -> e.getSourceId().contains("ods_customer_info")));
            assertTrue(edges.stream().allMatch(e -> e.getTargetId().contains("dwd_loan_detail")));
        }

        @Test
        @DisplayName("解析多条 SQL 并构建完整血缘图")
        void testBuildLineageGraph() {
            Map<String, String> scripts = new LinkedHashMap<>();
            scripts.put("etl_ods_to_dwd",
                    "INSERT OVERWRITE TABLE dwd.dwd_detail SELECT * FROM ods.ods_t1");
            scripts.put("etl_dwd_to_dws",
                    "INSERT OVERWRITE TABLE dws.dws_summary SELECT * FROM dwd.dwd_detail");

            LineageGraph graph = analyzer.buildLineageGraph(scripts);

            assertEquals(3, graph.getNodes().size());
            assertEquals(2, graph.getEdges().size());
        }

        @Test
        @DisplayName("上游和下游查询正确")
        void testUpstreamDownstream() {
            LineageGraph graph = new LineageGraph();
            graph.addNode(new LineageNode("ods.t1", "ods.t1", LineageNode.NodeType.TABLE, "ODS", "HIVE"));
            graph.addNode(new LineageNode("dwd.t2", "dwd.t2", LineageNode.NodeType.TABLE, "DWD", "HIVE"));
            graph.addNode(new LineageNode("dws.t3", "dws.t3", LineageNode.NodeType.TABLE, "DWS", "HIVE"));
            graph.addEdge(new LineageEdge("ods.t1", "dwd.t2", "ETL", "job1"));
            graph.addEdge(new LineageEdge("dwd.t2", "dws.t3", "ETL", "job2"));

            assertEquals(1, graph.getUpstream("dwd.t2").size());
            assertEquals("ods.t1", graph.getUpstream("dwd.t2").get(0).getNodeId());
            assertEquals(1, graph.getDownstream("dwd.t2").size());
            assertEquals("dws.t3", graph.getDownstream("dwd.t2").get(0).getNodeId());
        }

        @Test
        @DisplayName("影响分析 — 找出所有下游")
        void testImpactAnalysis() {
            LineageGraph graph = new LineageGraph();
            graph.addNode(new LineageNode("ods.t1", "ods.t1", LineageNode.NodeType.TABLE, "ODS", "HIVE"));
            graph.addNode(new LineageNode("dwd.t2", "dwd.t2", LineageNode.NodeType.TABLE, "DWD", "HIVE"));
            graph.addNode(new LineageNode("dws.t3", "dws.t3", LineageNode.NodeType.TABLE, "DWS", "HIVE"));
            graph.addEdge(new LineageEdge("ods.t1", "dwd.t2", "ETL", "job1"));
            graph.addEdge(new LineageEdge("dwd.t2", "dws.t3", "ETL", "job2"));

            List<String> impacted = graph.impactAnalysis("ods.t1");
            assertEquals(2, impacted.size());
            assertTrue(impacted.contains("dwd.t2"));
            assertTrue(impacted.contains("dws.t3"));
        }
    }

    // ========== 数据质量 ==========

    @Nested
    @DisplayName("数据质量监控测试")
    class QualityTest {

        private QualityMonitor monitor;

        @BeforeEach
        void setUp() {
            monitor = new QualityMonitor();
        }

        @Test
        @DisplayName("完整性检测 — null 值")
        void testCompletenessNull() {
            monitor.addRule(new QualityRule("R001", "客户ID非空", QualityRule.QualityDimension.COMPLETENESS,
                    "ods_loan_application", "customer_id", "NOT NULL", 0, QualityRule.Severity.CRITICAL));

            List<QualityViolation> violations = monitor.check("ods_loan_application", "customer_id", null);
            assertEquals(1, violations.size());
            assertEquals(QualityRule.QualityDimension.COMPLETENESS, violations.get(0).getDimension());
        }

        @Test
        @DisplayName("完整性检测 — 非空值通过")
        void testCompletenessPass() {
            monitor.addRule(new QualityRule("R001", "客户ID非空", QualityRule.QualityDimension.COMPLETENESS,
                    "ods_loan_application", "customer_id", "NOT NULL", 0, QualityRule.Severity.CRITICAL));

            List<QualityViolation> violations = monitor.check("ods_loan_application", "customer_id", "C001");
            assertEquals(0, violations.size());
        }

        @Test
        @DisplayName("准确性检测 — 数值范围 >= 0")
        void testAccuracyRange() {
            monitor.addRule(new QualityRule("R002", "金额非负", QualityRule.QualityDimension.ACCURACY,
                    "ods_loan_application", "amount", ">= 0", 0, QualityRule.Severity.WARNING));

            List<QualityViolation> violations = monitor.check("ods_loan_application", "amount", -100.0);
            assertEquals(1, violations.size());
        }

        @Test
        @DisplayName("准确性检测 — 正值通过")
        void testAccuracyRangePass() {
            monitor.addRule(new QualityRule("R002", "金额非负", QualityRule.QualityDimension.ACCURACY,
                    "ods_loan_application", "amount", ">= 0", 0, QualityRule.Severity.WARNING));

            List<QualityViolation> violations = monitor.check("ods_loan_application", "amount", 1000.0);
            assertEquals(0, violations.size());
        }

        @Test
        @DisplayName("批量记录检查")
        void testRecordCheck() {
            monitor.addRule(new QualityRule("R001", "ID非空", QualityRule.QualityDimension.COMPLETENESS,
                    "t1", "id", "NOT NULL", 0, QualityRule.Severity.CRITICAL));
            monitor.addRule(new QualityRule("R002", "金额非负", QualityRule.QualityDimension.ACCURACY,
                    "t1", "amount", ">= 0", 0, QualityRule.Severity.WARNING));

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", null);
            record.put("amount", -50);

            List<QualityViolation> violations = monitor.checkRecord("t1", record);
            assertEquals(2, violations.size());
        }

        @Test
        @DisplayName("按维度查询违规")
        void testQueryByDimension() {
            monitor.addRule(new QualityRule("R001", "ID非空", QualityRule.QualityDimension.COMPLETENESS,
                    "t1", "id", "NOT NULL", 0, QualityRule.Severity.CRITICAL));
            monitor.check("t1", "id", null);

            assertEquals(1, monitor.getViolationsByDimension(QualityRule.QualityDimension.COMPLETENESS).size());
            assertEquals(0, monitor.getViolationsByDimension(QualityRule.QualityDimension.ACCURACY).size());
        }
    }

    // ========== 数据分级 ==========

    @Nested
    @DisplayName("数据分级分类测试")
    class ClassifierTest {

        private DataClassifier classifier;

        @BeforeEach
        void setUp() {
            classifier = new DataClassifier();
        }

        @Test
        @DisplayName("银行卡号字段 → 绝密")
        void testBankCard() {
            assertEquals(DataClassifier.SensitivityLevel.TOP_SECRET, classifier.classify("bank_card_no"));
            assertEquals(DataClassifier.SensitivityLevel.TOP_SECRET, classifier.classify("card_no"));
        }

        @Test
        @DisplayName("身份证/手机号字段 → 机密")
        void testConfidential() {
            assertEquals(DataClassifier.SensitivityLevel.CONFIDENTIAL, classifier.classify("id_card"));
            assertEquals(DataClassifier.SensitivityLevel.CONFIDENTIAL, classifier.classify("phone_number"));
            assertEquals(DataClassifier.SensitivityLevel.CONFIDENTIAL, classifier.classify("mobile"));
        }

        @Test
        @DisplayName("金额字段 → 内部")
        void testInternal() {
            assertEquals(DataClassifier.SensitivityLevel.INTERNAL, classifier.classify("loan_amount"));
            assertEquals(DataClassifier.SensitivityLevel.INTERNAL, classifier.classify("income"));
        }

        @Test
        @DisplayName("普通字段 → 公开")
        void testPublic() {
            assertEquals(DataClassifier.SensitivityLevel.PUBLIC, classifier.classify("customer_id"));
            assertEquals(DataClassifier.SensitivityLevel.PUBLIC, classifier.classify("status"));
        }

        @Test
        @DisplayName("null 值安全处理")
        void testNull() {
            assertEquals(DataClassifier.SensitivityLevel.PUBLIC, classifier.classify(null));
        }

        @Test
        @DisplayName("加密/脱敏判断")
        void testRequiresEncryption() {
            assertTrue(classifier.requiresEncryption(DataClassifier.SensitivityLevel.CONFIDENTIAL));
            assertTrue(classifier.requiresEncryption(DataClassifier.SensitivityLevel.TOP_SECRET));
            assertFalse(classifier.requiresEncryption(DataClassifier.SensitivityLevel.PUBLIC));
        }

        @Test
        @DisplayName("批量分类")
        void testClassifyAll() {
            Map<String, DataClassifier.SensitivityLevel> result = classifier.classifyAll(
                    List.of("id_card", "amount", "status"));
            assertEquals(DataClassifier.SensitivityLevel.CONFIDENTIAL, result.get("id_card"));
            assertEquals(DataClassifier.SensitivityLevel.INTERNAL, result.get("amount"));
            assertEquals(DataClassifier.SensitivityLevel.PUBLIC, result.get("status"));
        }
    }

    // ========== 生命周期管理 ==========

    @Nested
    @DisplayName("生命周期管理测试")
    class LifecycleTest {

        private LifecycleManager manager;

        @BeforeEach
        void setUp() {
            manager = new LifecycleManager();
        }

        @Test
        @DisplayName("ODS 层 10 天数据为热数据")
        void testOdsHot() {
            LocalDate created = LocalDate.now().minusDays(10);
            assertEquals(LifecycleManager.TemperatureTier.HOT,
                    manager.determineTier("ODS", created));
        }

        @Test
        @DisplayName("ODS 层 60 天数据为温数据")
        void testOdsWarm() {
            LocalDate created = LocalDate.now().minusDays(60);
            assertEquals(LifecycleManager.TemperatureTier.WARM,
                    manager.determineTier("ODS", created));
        }

        @Test
        @DisplayName("ODS 层 400 天数据为冷数据")
        void testOdsCold() {
            LocalDate created = LocalDate.now().minusDays(400);
            assertEquals(LifecycleManager.TemperatureTier.COLD,
                    manager.determineTier("ODS", created));
        }

        @Test
        @DisplayName("ODS 层超过 2 年数据需归档")
        void testOdsArchive() {
            LocalDate created = LocalDate.now().minusDays(800);
            assertEquals(LifecycleManager.TemperatureTier.ARCHIVE,
                    manager.determineTier("ODS", created));
        }

        @Test
        @DisplayName("ADS 层保留时间更长")
        void testAdsLongerRetention() {
            LocalDate created = LocalDate.now().minusDays(200);
            // ADS 热数据 365 天，200 天仍是热数据
            assertEquals(LifecycleManager.TemperatureTier.HOT,
                    manager.determineTier("ADS", created));
        }

        @Test
        @DisplayName("ES 日志 7 天后变温数据")
        void testEsLogWarm() {
            LocalDate created = LocalDate.now().minusDays(10);
            assertEquals(LifecycleManager.TemperatureTier.WARM,
                    manager.determineTier("ES_LOG", created));
        }

        @Test
        @DisplayName("扫描分区按温度分组")
        void testScanPartitions() {
            List<LocalDate> partitions = List.of(
                    LocalDate.now().minusDays(5),
                    LocalDate.now().minusDays(40),
                    LocalDate.now().minusDays(200),
                    LocalDate.now().minusDays(800)
            );

            Map<LifecycleManager.TemperatureTier, List<LocalDate>> result =
                    manager.scanPartitions("ODS", partitions);

            assertEquals(1, result.get(LifecycleManager.TemperatureTier.HOT).size());
            assertEquals(1, result.get(LifecycleManager.TemperatureTier.WARM).size());
            assertEquals(1, result.get(LifecycleManager.TemperatureTier.COLD).size());
            assertEquals(1, result.get(LifecycleManager.TemperatureTier.ARCHIVE).size());
        }

        @Test
        @DisplayName("未知层默认热数据")
        void testUnknownLayer() {
            assertEquals(LifecycleManager.TemperatureTier.HOT,
                    manager.determineTier("UNKNOWN", LocalDate.now().minusDays(1000)));
        }

        @Test
        @DisplayName("获取各层策略配置")
        void testGetPolicy() {
            LifecycleManager.LifecyclePolicy ods = manager.getPolicy("ODS");
            assertNotNull(ods);
            assertEquals(30, ods.getHotDays());
            assertEquals(180, ods.getWarmDays());
            assertEquals(730, ods.getColdDays());
        }
    }
}
