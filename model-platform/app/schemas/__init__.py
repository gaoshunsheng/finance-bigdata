"""Pydantic schemas for API request/response models"""
from datetime import datetime
from enum import Enum
from typing import Any, Optional

from pydantic import BaseModel, Field


# ─── Enums ───

class ModelAlgorithm(str, Enum):
    LR = "LR"
    XGBOOST = "XGBOOST"
    LIGHTGBM = "LIGHTGBM"


class ExportFormat(str, Enum):
    PMML = "PMML"
    ONNX = "ONNX"


class ModelStatus(str, Enum):
    DRAFT = "DRAFT"
    TRAINING = "TRAINING"
    EVALUATED = "EVALUATED"
    DEPLOYED = "DEPLOYED"
    DEPRECATED = "DEPRECATED"
    FAILED = "FAILED"


class SampleSplitStrategy(str, Enum):
    RANDOM = "RANDOM"
    STRATIFIED = "STRATIFIED"
    TIME_BASED = "TIME_BASED"


class AlertSeverity(str, Enum):
    WARNING = "WARNING"
    CRITICAL = "CRITICAL"


# ─── Data Preparation ───

class SampleFilter(BaseModel):
    """Sample filter parameters"""
    start_date: Optional[str] = Field(None, description="Start date (yyyy-MM-dd)")
    end_date: Optional[str] = Field(None, description="End date (yyyy-MM-dd)")
    products: Optional[list[str]] = Field(None, description="Product type filter")
    channels: Optional[list[str]] = Field(None, description="Channel filter")
    label_column: str = Field(..., description="Label column name")
    positive_label: Any = Field(1, description="Positive label value")
    negative_label: Any = Field(0, description="Negative label value")


class SampleSplitRequest(BaseModel):
    """Sample split request"""
    dataset_id: str = Field(..., description="Dataset ID to split")
    strategy: SampleSplitStrategy = Field(SampleSplitStrategy.STRATIFIED)
    train_ratio: float = Field(0.6, ge=0.1, le=0.9)
    val_ratio: float = Field(0.2, ge=0.05, le=0.5)
    test_ratio: float = Field(0.2, ge=0.05, le=0.5)
    time_column: Optional[str] = Field(None, description="Required for TIME_BASED strategy")
    random_seed: int = Field(42)


class FeatureEngineeringRequest(BaseModel):
    """Feature engineering request"""
    dataset_id: str = Field(..., description="Dataset ID")
    iv_threshold: float = Field(0.02, description="Minimum IV value to keep feature")
    correlation_threshold: float = Field(0.7, description="Max correlation between features")
    max_bins: int = Field(10, ge=3, le=20, description="Max bins for WOE binning")
    target_column: str = Field("label", description="Target variable name")


class IVResult(BaseModel):
    feature_name: str
    iv_value: float
    woe_bins: list[dict[str, Any]]
    selected: bool


class FeatureEngineeringResult(BaseModel):
    dataset_id: str
    total_features: int
    selected_features: list[str]
    iv_results: list[IVResult]
    removed_by_iv: list[str]
    removed_by_correlation: list[str]
    psi_scores: dict[str, float]


# ─── Training ───

class TrainingRequest(BaseModel):
    """Model training request"""
    name: str = Field(..., description="Model name")
    algorithm: ModelAlgorithm = Field(..., description="Algorithm type")
    dataset_id: str = Field(..., description="Training dataset ID")
    target_column: str = Field("label")
    features: Optional[list[str]] = Field(None, description="Feature columns (None=all)")
    hyperparams: Optional[dict[str, Any]] = Field(None, description="Algorithm hyperparameters")
    cv_folds: int = Field(5, ge=2, le=10)
    random_seed: int = Field(42)
    description: Optional[str] = None


class TrainingResult(BaseModel):
    model_id: str
    name: str
    algorithm: ModelAlgorithm
    status: ModelStatus
    best_params: dict[str, Any]
    cv_scores: list[float]
    cv_mean: float
    cv_std: float
    training_time_seconds: float
    feature_importance: dict[str, float]
    created_at: datetime


# ─── Evaluation ───

class EvaluationRequest(BaseModel):
    """Model evaluation request"""
    model_id: str = Field(..., description="Model ID to evaluate")
    test_dataset_id: Optional[str] = Field(None, description="Test dataset (uses held-out if None)")
    threshold: float = Field(0.5, ge=0.0, le=1.0, description="Classification threshold")


class EvaluationMetrics(BaseModel):
    auc: float
    ks: float
    gini: float
    accuracy: float
    precision: float
    recall: float
    f1_score: float
    confusion_matrix: list[list[int]]
    psi: Optional[float] = None
    lift_at_10: float
    lift_at_20: float
    vif_scores: Optional[dict[str, float]] = None


class EvaluationReport(BaseModel):
    model_id: str
    algorithm: ModelAlgorithm
    metrics: EvaluationMetrics
    ks_curve: dict[str, list[float]]
    roc_curve: dict[str, list[float]]
    score_distribution: dict[str, Any]
    feature_importance: dict[str, float]
    evaluated_at: datetime


# ─── Export ───

class ExportRequest(BaseModel):
    """Model export request"""
    model_id: str = Field(..., description="Model ID to export")
    format: ExportFormat = Field(..., description="Export format (PMML/ONNX)")
    version: Optional[str] = Field(None, description="Model version tag")


class ExportResult(BaseModel):
    model_id: str
    format: ExportFormat
    file_path: str
    file_size_bytes: int
    exported_at: datetime


# ─── Inference ───

class InferenceRequest(BaseModel):
    """Single inference request"""
    model_id: str = Field(..., description="Model ID or version tag")
    features: dict[str, Any] = Field(..., description="Feature name -> value mapping")
    return_explanation: bool = Field(False, description="Whether to return SHAP explanation")


class BatchInferenceRequest(BaseModel):
    """Batch inference request"""
    model_id: str
    records: list[dict[str, Any]]
    return_explanation: bool = False


class InferenceResponse(BaseModel):
    model_id: str
    prediction: int
    probability: float
    score: float
    explanation: Optional[dict[str, float]] = None


class BatchInferenceResponse(BaseModel):
    model_id: str
    predictions: list[InferenceResponse]
    total_count: int
    latency_ms: float


# ─── Monitoring ───

class PSIAlert(BaseModel):
    alert_id: str
    model_id: str
    feature_name: str
    psi_value: float
    severity: AlertSeverity
    message: str
    detected_at: datetime


class MonitoringDashboard(BaseModel):
    model_id: str
    model_name: str
    status: ModelStatus
    daily_psi: list[dict[str, Any]]
    ks_trend: list[dict[str, Any]]
    feature_drift: dict[str, float]
    active_alerts: list[PSIAlert]
    last_updated: datetime


# ─── Common ───

class ApiResponse(BaseModel, from_attributes=True):
    code: int = 0
    message: str = "success"
    data: Any = None


class PageResult(BaseModel):
    total: int
    page: int
    page_size: int
    items: list[Any]
