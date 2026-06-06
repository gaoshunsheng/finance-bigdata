"""
ORM 模型定义

定义数据库表结构，与业务 dataclass (app/models.py) 对应。
JSON 字段用于存储结构化数据（超参数、评估指标等），
避免频繁变更 schema 带来的迁移负担。
"""

from datetime import datetime

from sqlalchemy import (
    Column,
    String,
    Integer,
    Float,
    Text,
    DateTime,
    Boolean,
    Index,
)
from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    """SQLAlchemy 声明式基类"""
    pass


class ModelRecord(Base):
    """模型元数据记录"""
    __tablename__ = "model_records"

    # 主键: 业务 ID (如 model_001)
    model_id = Column(String(64), primary_key=True, comment="模型唯一标识")
    name = Column(String(255), nullable=False, comment="模型名称")
    algorithm = Column(String(32), nullable=False, comment="算法类型: LR / XGBOOST / LIGHTGBM")
    status = Column(String(32), nullable=False, default="DRAFT", comment="模型状态")
    # 超参数、CV 分数、特征重要性等以 JSON 文本存储
    parameters = Column(Text, nullable=True, comment="模型参数 (JSON)")
    metrics = Column(Text, nullable=True, comment="评估指标 (JSON: cv_mean, cv_std 等)")
    feature_importance = Column(Text, nullable=True, comment="特征重要性 (JSON)")
    features = Column(Text, nullable=True, comment="特征列列表 (JSON)")
    target_column = Column(String(128), nullable=True, comment="目标列名")
    dataset_id = Column(String(64), nullable=True, comment="关联数据集 ID")
    description = Column(Text, nullable=True, comment="模型描述")
    training_time_seconds = Column(Float, nullable=True, comment="训练耗时(秒)")
    created_at = Column(DateTime, nullable=False, default=datetime.now, comment="创建时间")
    updated_at = Column(DateTime, nullable=False, default=datetime.now, onupdate=datetime.now, comment="更新时间")

    __table_args__ = (
        Index("idx_model_status", "status"),
        Index("idx_model_algorithm", "algorithm"),
    )

    def __repr__(self) -> str:
        return f"<ModelRecord(model_id={self.model_id}, name={self.name}, status={self.status})>"


class DatasetRecord(Base):
    """数据集元数据记录"""
    __tablename__ = "dataset_records"

    dataset_id = Column(String(64), primary_key=True, comment="数据集唯一标识")
    name = Column(String(255), nullable=False, comment="数据集名称")
    row_count = Column(Integer, nullable=True, comment="行数")
    feature_count = Column(Integer, nullable=True, comment="特征列数")
    split_strategy = Column(String(32), nullable=True, comment="拆分策略")
    metadata_json = Column(Text, nullable=True, comment="完整元数据 (JSON)")
    created_at = Column(DateTime, nullable=False, default=datetime.now, comment="创建时间")

    __table_args__ = (
        Index("idx_dataset_name", "name"),
    )

    def __repr__(self) -> str:
        return f"<DatasetRecord(dataset_id={self.dataset_id}, name={self.name})>"


class EvaluationReport(Base):
    """模型评估报告记录"""
    __tablename__ = "evaluation_reports"

    id = Column(Integer, primary_key=True, autoincrement=True, comment="自增主键")
    report_id = Column(String(64), nullable=False, unique=True, comment="报告唯一标识")
    model_id = Column(String(64), nullable=False, index=True, comment="关联模型 ID")
    algorithm = Column(String(32), nullable=True, comment="算法类型")
    # 核心指标
    auc = Column(Float, nullable=True, comment="AUC 值")
    ks = Column(Float, nullable=True, comment="KS 统计量")
    gini = Column(Float, nullable=True, comment="Gini 系数")
    psi = Column(Float, nullable=True, comment="PSI 值")
    accuracy = Column(Float, nullable=True, comment="准确率")
    precision_score = Column("precision_val", Float, nullable=True, comment="精确率")
    recall = Column(Float, nullable=True, comment="召回率")
    f1_score = Column(Float, nullable=True, comment="F1 分数")
    # 完整报告数据 (JSON)
    full_report = Column(Text, nullable=True, comment="完整评估报告 (JSON)")
    created_at = Column(DateTime, nullable=False, default=datetime.now, comment="创建时间")

    __table_args__ = (
        Index("idx_eval_model_id", "model_id"),
    )

    def __repr__(self) -> str:
        return f"<EvaluationReport(report_id={self.report_id}, model_id={self.model_id})>"


class MonitoringSnapshot(Base):
    """模型监控快照记录"""
    __tablename__ = "monitoring_snapshots"

    id = Column(Integer, primary_key=True, autoincrement=True, comment="自增主键")
    snapshot_id = Column(String(64), nullable=False, unique=True, comment="快照唯一标识")
    model_id = Column(String(64), nullable=False, index=True, comment="关联模型 ID")
    # PSI / 漂移检测
    psi_value = Column(Float, nullable=True, comment="PSI 值")
    severity = Column(String(16), nullable=True, comment="严重等级: NORMAL / WARNING / CRITICAL")
    drift_detected = Column(Boolean, nullable=True, default=False, comment="是否检测到漂移")
    # 完整监控数据 (JSON)
    full_data = Column(Text, nullable=True, comment="完整监控数据 (JSON)")
    monitored_at = Column(String(32), nullable=True, comment="监控日期 yyyy-MM-dd")
    created_at = Column(DateTime, nullable=False, default=datetime.now, comment="创建时间")

    __table_args__ = (
        Index("idx_snapshot_model_id", "model_id"),
    )

    def __repr__(self) -> str:
        return f"<MonitoringSnapshot(snapshot_id={self.snapshot_id}, model_id={self.model_id})>"
