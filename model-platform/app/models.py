"""Data models for internal storage"""
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Optional


@dataclass
class DatasetInfo:
    """数据集信息"""
    id: str
    name: str
    full_df: Any = None  # pd.DataFrame
    train_df: Any = None
    val_df: Any = None
    test_df: Any = None
    filters: dict = field(default_factory=dict)
    split_strategy: Optional[str] = None
    train_count: int = 0
    val_count: int = 0
    test_count: int = 0
    created_at: str = ""


@dataclass
class ModelInfo:
    """模型信息"""
    id: str
    name: str
    algorithm: str
    status: str = "DRAFT"
    estimator: Any = None  # sklearn/xgb/lgbm model object
    best_params: dict = field(default_factory=dict)
    cv_scores: list = field(default_factory=list)
    cv_mean: float = 0.0
    cv_std: float = 0.0
    training_time_seconds: float = 0.0
    feature_importance: dict = field(default_factory=dict)
    features: list = field(default_factory=list)
    target_column: str = "label"
    dataset_id: Optional[str] = None
    _dataset: Any = None  # reference to DatasetInfo
    description: Optional[str] = None
    created_at: str = ""


@dataclass
class ExportInfo:
    """模型导出信息"""
    id: str
    model_id: str
    format: str
    file_path: str
    file_size_bytes: int = 0
    version: Optional[str] = None
    exported_at: str = ""


@dataclass
class AlertInfo:
    """监控告警信息"""
    id: str
    model_id: str
    feature_name: str
    psi_value: float
    severity: str  # WARNING / CRITICAL
    message: str
    detected_at: str
    acknowledged: bool = False
