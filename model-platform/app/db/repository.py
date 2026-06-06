"""
数据库仓库层

为每种 ORM 模型提供 CRUD 操作，所有 DB 操作均包裹在 try/except 中，
确保数据库不可用时不会影响应用正常运行。
"""

from __future__ import annotations

import json
from datetime import datetime
from typing import Any, Optional

from loguru import logger
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.db.models import (
    ModelRecord,
    DatasetRecord,
    EvaluationReport,
    MonitoringSnapshot,
)
from app.db.session import get_session


# ──────────────────────────────────────────────
# 辅助函数
# ──────────────────────────────────────────────

def _json_dumps(obj: Any) -> str | None:
    """安全序列化为 JSON"""
    if obj is None:
        return None
    return json.dumps(obj, ensure_ascii=False, default=str)


def _json_loads(s: str | None) -> Any:
    """安全反序列化 JSON"""
    if not s:
        return None
    try:
        return json.loads(s)
    except (json.JSONDecodeError, TypeError):
        return None


# ──────────────────────────────────────────────
# ModelRepository
# ──────────────────────────────────────────────

class ModelRepository:
    """模型记录 CRUD"""

    @staticmethod
    def save(model_record: dict[str, Any]) -> bool:
        """
        保存模型记录到数据库。

        如果 model_id 已存在则更新，否则插入新记录。

        Args:
            model_record: 模型训练结果字典，包含 model_id / name / algorithm / status 等

        Returns:
            是否保存成功
        """
        session: Session | None = get_session()
        if session is None:
            return False
        try:
            model_id = model_record["model_id"]
            existing = session.get(ModelRecord, model_id)

            # 组装 metrics JSON
            metrics = {
                "cv_scores": model_record.get("cv_scores"),
                "cv_mean": model_record.get("cv_mean"),
                "cv_std": model_record.get("cv_std"),
            }

            if existing is not None:
                # 更新
                existing.name = model_record.get("name", existing.name)
                existing.algorithm = model_record.get("algorithm", existing.algorithm)
                existing.status = model_record.get("status", existing.status)
                existing.parameters = _json_dumps(model_record.get("best_params"))
                existing.metrics = _json_dumps(metrics)
                existing.feature_importance = _json_dumps(model_record.get("feature_importance"))
                existing.features = _json_dumps(model_record.get("features"))
                existing.target_column = model_record.get("target_column")
                existing.dataset_id = model_record.get("dataset_id")
                existing.description = model_record.get("description")
                existing.training_time_seconds = model_record.get("training_time_seconds")
                existing.updated_at = datetime.now()
            else:
                # 新增
                record = ModelRecord(
                    model_id=model_id,
                    name=model_record.get("name", ""),
                    algorithm=model_record.get("algorithm", ""),
                    status=model_record.get("status", "DRAFT"),
                    parameters=_json_dumps(model_record.get("best_params")),
                    metrics=_json_dumps(metrics),
                    feature_importance=_json_dumps(model_record.get("feature_importance")),
                    features=_json_dumps(model_record.get("features")),
                    target_column=model_record.get("target_column"),
                    dataset_id=model_record.get("dataset_id"),
                    description=model_record.get("description"),
                    training_time_seconds=model_record.get("training_time_seconds"),
                    created_at=datetime.now(),
                    updated_at=datetime.now(),
                )
                session.add(record)

            session.commit()
            return True
        except Exception as e:
            logger.error(f"保存模型记录失败: {e}")
            session.rollback()
            return False
        finally:
            session.close()

    @staticmethod
    def get(model_id: str) -> dict[str, Any] | None:
        """
        根据 model_id 获取模型记录。

        Returns:
            模型信息字典 (从 ORM 转换), 不存在或 DB 不可用时返回 None
        """
        session: Session | None = get_session()
        if session is None:
            return None
        try:
            record = session.get(ModelRecord, model_id)
            if record is None:
                return None
            return ModelRepository._to_dict(record)
        except Exception as e:
            logger.error(f"查询模型记录失败: {e}")
            return None
        finally:
            session.close()

    @staticmethod
    def list_all() -> list[dict[str, Any]]:
        """
        列出所有模型记录。
        """
        session: Session | None = get_session()
        if session is None:
            return []
        try:
            records = session.query(ModelRecord).order_by(ModelRecord.created_at.desc()).all()
            return [ModelRepository._to_dict(r) for r in records]
        except Exception as e:
            logger.error(f"列出模型记录失败: {e}")
            return []
        finally:
            session.close()

    @staticmethod
    def update_status(model_id: str, status: str) -> bool:
        """更新模型状态"""
        session: Session | None = get_session()
        if session is None:
            return False
        try:
            record = session.get(ModelRecord, model_id)
            if record is None:
                return False
            record.status = status
            record.updated_at = datetime.now()
            session.commit()
            return True
        except Exception as e:
            logger.error(f"更新模型状态失败: {e}")
            session.rollback()
            return False
        finally:
            session.close()

    @staticmethod
    def load_index() -> dict[str, dict[str, Any]]:
        """
        加载所有模型记录为字典索引。

        Returns:
            {model_id: model_dict} 用于初始化内存缓存
        """
        session: Session | None = get_session()
        if session is None:
            return {}
        try:
            records = session.query(ModelRecord).all()
            return {r.model_id: ModelRepository._to_dict(r) for r in records}
        except Exception as e:
            logger.error(f"加载模型索引失败: {e}")
            return {}
        finally:
            session.close()

    @staticmethod
    def _to_dict(record: ModelRecord) -> dict[str, Any]:
        """ORM 对象转字典"""
        return {
            "model_id": record.model_id,
            "name": record.name,
            "algorithm": record.algorithm,
            "status": record.status,
            "best_params": _json_loads(record.parameters),
            "metrics": _json_loads(record.metrics),
            "feature_importance": _json_loads(record.feature_importance),
            "features": _json_loads(record.features),
            "target_column": record.target_column,
            "dataset_id": record.dataset_id,
            "description": record.description,
            "training_time_seconds": record.training_time_seconds,
            "created_at": record.created_at.strftime("%Y-%m-%d %H:%M:%S") if record.created_at else "",
            "updated_at": record.updated_at.strftime("%Y-%m-%d %H:%M:%S") if record.updated_at else "",
        }


# ──────────────────────────────────────────────
# DatasetRepository
# ──────────────────────────────────────────────

class DatasetRepository:
    """数据集记录 CRUD"""

    @staticmethod
    def save(dataset_dict: dict[str, Any]) -> bool:
        """
        保存数据集元数据。

        Args:
            dataset_dict: 数据集字典，需包含 id / name / metadata 等
        """
        session: Session | None = get_session()
        if session is None:
            return False
        try:
            dataset_id = dataset_dict["id"]
            existing = session.get(DatasetRecord, dataset_id)

            metadata = dataset_dict.get("metadata", {})
            row_count = metadata.get("row_count_filtered", metadata.get("row_count_raw", 0))
            feature_count = metadata.get("column_count", 0)
            split_strategy = dataset_dict.get("split_strategy")

            if existing is not None:
                existing.name = dataset_dict.get("name", existing.name)
                existing.row_count = row_count
                existing.feature_count = feature_count
                existing.split_strategy = split_strategy
                existing.metadata_json = _json_dumps(metadata)
            else:
                record = DatasetRecord(
                    dataset_id=dataset_id,
                    name=dataset_dict.get("name", ""),
                    row_count=row_count,
                    feature_count=feature_count,
                    split_strategy=split_strategy,
                    metadata_json=_json_dumps(metadata),
                    created_at=datetime.now(),
                )
                session.add(record)

            session.commit()
            return True
        except Exception as e:
            logger.error(f"保存数据集记录失败: {e}")
            session.rollback()
            return False
        finally:
            session.close()

    @staticmethod
    def get(dataset_id: str) -> dict[str, Any] | None:
        """获取数据集元数据"""
        session: Session | None = get_session()
        if session is None:
            return None
        try:
            record = session.get(DatasetRecord, dataset_id)
            if record is None:
                return None
            return DatasetRepository._to_dict(record)
        except Exception as e:
            logger.error(f"查询数据集记录失败: {e}")
            return None
        finally:
            session.close()

    @staticmethod
    def list_all() -> list[dict[str, Any]]:
        """列出所有数据集"""
        session: Session | None = get_session()
        if session is None:
            return []
        try:
            records = session.query(DatasetRecord).order_by(DatasetRecord.created_at.desc()).all()
            return [DatasetRepository._to_dict(r) for r in records]
        except Exception as e:
            logger.error(f"列出数据集记录失败: {e}")
            return []
        finally:
            session.close()

    @staticmethod
    def _to_dict(record: DatasetRecord) -> dict[str, Any]:
        return {
            "dataset_id": record.dataset_id,
            "name": record.name,
            "row_count": record.row_count,
            "feature_count": record.feature_count,
            "split_strategy": record.split_strategy,
            "metadata": _json_loads(record.metadata_json),
            "created_at": record.created_at.strftime("%Y-%m-%d %H:%M:%S") if record.created_at else "",
        }


# ──────────────────────────────────────────────
# EvaluationRepository
# ──────────────────────────────────────────────

class EvaluationRepository:
    """评估报告 CRUD"""

    @staticmethod
    def save(report: dict[str, Any]) -> bool:
        """
        保存评估报告到数据库。

        Args:
            report: 评估报告字典，需包含 model_id / metrics 等
        """
        session: Session | None = get_session()
        if session is None:
            return False
        try:
            import uuid
            model_id = report["model_id"]
            report_id = f"rpt_{uuid.uuid4().hex[:12]}"

            metrics = report.get("metrics", {})

            record = EvaluationReport(
                report_id=report_id,
                model_id=model_id,
                algorithm=report.get("algorithm"),
                auc=metrics.get("auc"),
                ks=metrics.get("ks"),
                gini=metrics.get("gini"),
                psi=metrics.get("psi"),
                accuracy=metrics.get("accuracy"),
                precision_score=metrics.get("precision"),
                recall=metrics.get("recall"),
                f1_score=metrics.get("f1_score"),
                full_report=_json_dumps(report),
                created_at=datetime.now(),
            )
            session.add(record)
            session.commit()
            return True
        except Exception as e:
            logger.error(f"保存评估报告失败: {e}")
            session.rollback()
            return False
        finally:
            session.close()

    @staticmethod
    def get_by_model(model_id: str) -> dict[str, Any] | None:
        """获取指定模型的最新评估报告"""
        session: Session | None = get_session()
        if session is None:
            return None
        try:
            record = (
                session.query(EvaluationReport)
                .filter(EvaluationReport.model_id == model_id)
                .order_by(EvaluationReport.created_at.desc())
                .first()
            )
            if record is None:
                return None
            # 返回完整报告 JSON
            full = _json_loads(record.full_report)
            if full:
                return full
            # 回退: 从字段组装
            return EvaluationRepository._to_dict(record)
        except Exception as e:
            logger.error(f"查询评估报告失败: {e}")
            return None
        finally:
            session.close()

    @staticmethod
    def list_all() -> list[dict[str, Any]]:
        """列出所有评估报告"""
        session: Session | None = get_session()
        if session is None:
            return []
        try:
            records = session.query(EvaluationReport).order_by(EvaluationReport.created_at.desc()).all()
            return [EvaluationRepository._to_dict(r) for r in records]
        except Exception as e:
            logger.error(f"列出评估报告失败: {e}")
            return []
        finally:
            session.close()

    @staticmethod
    def _to_dict(record: EvaluationReport) -> dict[str, Any]:
        return {
            "report_id": record.report_id,
            "model_id": record.model_id,
            "algorithm": record.algorithm,
            "auc": record.auc,
            "ks": record.ks,
            "gini": record.gini,
            "psi": record.psi,
            "accuracy": record.accuracy,
            "precision": record.precision_score,
            "recall": record.recall,
            "f1_score": record.f1_score,
            "created_at": record.created_at.strftime("%Y-%m-%d %H:%M:%S") if record.created_at else "",
        }


# ──────────────────────────────────────────────
# MonitoringRepository
# ──────────────────────────────────────────────

class MonitoringRepository:
    """监控快照 CRUD"""

    @staticmethod
    def save(snapshot: dict[str, Any], model_id: str) -> bool:
        """
        保存监控快照。

        Args:
            snapshot: 监控快照字典（PSI 计算 / KS 趋势 / 特征漂移 等结果）
            model_id: 关联模型 ID
        """
        session: Session | None = get_session()
        if session is None:
            return False
        try:
            import uuid
            snapshot_id = f"snap_{uuid.uuid4().hex[:12]}"

            psi_value = snapshot.get("psi_value")
            severity = snapshot.get("severity")
            drift_detected = severity in ("WARNING", "CRITICAL") if severity else False
            monitored_at = snapshot.get("date")

            record = MonitoringSnapshot(
                snapshot_id=snapshot_id,
                model_id=model_id,
                psi_value=psi_value,
                severity=severity,
                drift_detected=drift_detected,
                full_data=_json_dumps(snapshot),
                monitored_at=monitored_at,
                created_at=datetime.now(),
            )
            session.add(record)
            session.commit()
            return True
        except Exception as e:
            logger.error(f"保存监控快照失败: {e}")
            session.rollback()
            return False
        finally:
            session.close()

    @staticmethod
    def get_by_model(model_id: str, limit: int = 30) -> list[dict[str, Any]]:
        """获取指定模型的最近监控快照"""
        session: Session | None = get_session()
        if session is None:
            return []
        try:
            records = (
                session.query(MonitoringSnapshot)
                .filter(MonitoringSnapshot.model_id == model_id)
                .order_by(MonitoringSnapshot.created_at.desc())
                .limit(limit)
                .all()
            )
            return [MonitoringRepository._to_dict(r) for r in records]
        except Exception as e:
            logger.error(f"查询监控快照失败: {e}")
            return []
        finally:
            session.close()

    @staticmethod
    def list_recent_drifts(limit: int = 50) -> list[dict[str, Any]]:
        """列出最近检测到漂移的快照"""
        session: Session | None = get_session()
        if session is None:
            return []
        try:
            records = (
                session.query(MonitoringSnapshot)
                .filter(MonitoringSnapshot.drift_detected == True)
                .order_by(MonitoringSnapshot.created_at.desc())
                .limit(limit)
                .all()
            )
            return [MonitoringRepository._to_dict(r) for r in records]
        except Exception as e:
            logger.error(f"列出漂移快照失败: {e}")
            return []
        finally:
            session.close()

    @staticmethod
    def _to_dict(record: MonitoringSnapshot) -> dict[str, Any]:
        return {
            "snapshot_id": record.snapshot_id,
            "model_id": record.model_id,
            "psi_value": record.psi_value,
            "severity": record.severity,
            "drift_detected": record.drift_detected,
            "monitored_at": record.monitored_at,
            "full_data": _json_loads(record.full_data),
            "created_at": record.created_at.strftime("%Y-%m-%d %H:%M:%S") if record.created_at else "",
        }
