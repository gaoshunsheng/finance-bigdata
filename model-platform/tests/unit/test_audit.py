"""审计日志模块测试"""
import pytest
from app.core.monitoring.audit import AuditLogger, audit_logger


class TestAuditLogger:
    def test_log_basic(self):
        logger = AuditLogger()
        entry = logger.log(action="TRAIN", target_type="MODEL", target_id="m1")
        assert entry["action"] == "TRAIN"
        assert entry["target_id"] == "m1"
        assert entry["retention_years"] == 5

    def test_log_with_snapshots(self):
        logger = AuditLogger()
        entry = logger.log(
            action="UPDATE", target_type="MODEL", target_id="m1",
            before_snapshot={"name": "old"}, after_snapshot={"name": "new"}
        )
        assert entry["before_hash"] is not None
        assert entry["after_hash"] is not None

    def test_log_training(self):
        logger = AuditLogger()
        entry = logger.log_training("m1", "XGBOOST")
        assert entry["action"] == "TRAIN"
        assert entry["details"] == "模型训练: XGBOOST"

    def test_log_prediction(self):
        logger = AuditLogger()
        entry = logger.log_prediction("m1", features_count=10)
        assert entry["action"] == "PREDICT"

    def test_log_export(self):
        logger = AuditLogger()
        entry = logger.log_export("m1", "ONNX")
        assert entry["action"] == "EXPORT"

    def test_query_by_action(self):
        logger = AuditLogger()
        logger.log(action="CREATE", target_type="DATASET", target_id="d1")
        logger.log(action="DELETE", target_type="DATASET", target_id="d2")
        results = logger.query(action="CREATE")
        assert all(r["action"] == "CREATE" for r in results)

    def test_query_by_target(self):
        logger = AuditLogger()
        logger.log(action="UPDATE", target_type="MODEL", target_id="m_special")
        results = logger.query(target_id="m_special")
        assert len(results) >= 1

    def test_query_limit_offset(self):
        logger = AuditLogger()
        for i in range(10):
            logger.log(action="TEST", target_type="X", target_id=f"x{i}")
        page1 = logger.query(limit=5, offset=0)
        page2 = logger.query(limit=5, offset=5)
        assert len(page1) == 5
        assert len(page2) == 5

    def test_count(self):
        logger = AuditLogger()
        initial = logger.count()
        logger.log(action="TEST", target_type="X", target_id="cnt")
        assert logger.count() == initial + 1
