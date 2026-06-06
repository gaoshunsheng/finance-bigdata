"""Model Platform API Integration Tests

End-to-end tests exercising the full FastAPI request/response cycle using
TestClient.  All service modules use in-memory stores, so no external
infrastructure (MySQL, Redis, ES) is required.
"""

import sys

# Ensure the project root is importable when running from the tests/ directory.
sys.path.insert(0, ".")

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.core.data_prep.sample_manager import sample_manager
from app.core.training.trainer import model_trainer
from app.core.evaluation.evaluator import model_evaluator
from app.core.inference.inference_service import inference_service
from app.core.export.exporter import model_exporter
from app.core.monitoring.monitor import model_monitor
from app.core.monitoring.audit import audit_logger


client = TestClient(app)

# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture(autouse=True)
def _reset_state():
    """Reset all in-memory singletons between tests so they don't leak.

    The global ``model_trainer`` creates its own ``SampleManager`` at import
    time which is a *different* instance from the ``sample_manager`` singleton
    used by the data-prep endpoints.  We fix this by pointing the trainer at
    the shared singleton so datasets created via the API are visible during
    training.
    """
    # Wire the trainer to the same SampleManager used by the API endpoints.
    model_trainer._sample_manager = sample_manager

    sample_manager._datasets.clear()
    model_trainer._models.clear()
    model_trainer._id_counter = 0
    model_evaluator._reports.clear()
    inference_service.clear_cache()
    model_exporter._exports.clear()
    model_exporter._id_counter = 0
    model_monitor._monitoring_data.clear()
    with audit_logger._lock:
        audit_logger._store.clear()
    yield
    # Cleanup after test as well
    sample_manager._datasets.clear()
    model_trainer._models.clear()
    model_trainer._id_counter = 0
    model_evaluator._reports.clear()
    inference_service.clear_cache()
    model_exporter._exports.clear()
    model_exporter._id_counter = 0
    model_monitor._monitoring_data.clear()
    with audit_logger._lock:
        audit_logger._store.clear()


def _create_and_split_dataset() -> dict:
    """Helper: POST /api/v1/data/datasets then split it. Returns the API
    response dicts for both the created dataset and the split result."""
    create_resp = client.post(
        "/api/v1/data/datasets",
        json={
            "label_column": "label",
            "positive_label": 1,
            "negative_label": 0,
        },
    )
    assert create_resp.status_code == 200
    dataset_body = create_resp.json()["data"]
    dataset_id = dataset_body["dataset_id"]

    split_resp = client.post(
        f"/api/v1/data/datasets/{dataset_id}/split",
        json={
            "dataset_id": dataset_id,
            "strategy": "STRATIFIED",
            "train_ratio": 0.6,
            "val_ratio": 0.2,
            "test_ratio": 0.2,
            "random_seed": 42,
        },
    )
    assert split_resp.status_code == 200
    return dataset_body, split_resp.json()["data"]


NUMERIC_FEATURES = [
    "age", "income", "loan_amount", "credit_score",
    "overdue_count_6m", "credit_query_count_3m", "debt_ratio",
    "employment_years",
]


def _train_model(dataset_id: str) -> dict:
    """Helper: train an LR model on the given dataset. Returns the API
    response data.

    Only numeric features are passed so that LogisticRegression can fit
    without needing categorical encoding.
    """
    resp = client.post(
        "/api/v1/training/train",
        json={
            "name": "integration_test_lr",
            "algorithm": "LR",
            "dataset_id": dataset_id,
            "target_column": "label",
            "features": NUMERIC_FEATURES,
            "hyperparams": {"C": [0.1], "penalty": ["l2"], "solver": ["saga"]},
            "cv_folds": 2,
            "random_seed": 42,
        },
    )
    assert resp.status_code == 200
    return resp.json()["data"]


# ===========================================================================
# Health
# ===========================================================================


class TestHealthEndpoints:
    def test_health_check(self):
        response = client.get("/health")
        assert response.status_code == 200
        body = response.json()
        assert body["status"] == "UP"
        assert body["service"] == "model-platform"


# ===========================================================================
# Data Preparation  (/api/v1/data)
# ===========================================================================


class TestDataPrepEndpoints:
    def test_create_dataset(self):
        resp = client.post(
            "/api/v1/data/datasets",
            json={
                "label_column": "label",
                "positive_label": 1,
                "negative_label": 0,
            },
        )
        assert resp.status_code == 200
        body = resp.json()
        assert body["code"] == 0
        data = body["data"]
        assert "dataset_id" in data
        assert data["total_rows"] == 1000
        assert isinstance(data["features"], list)
        assert len(data["features"]) > 0

    def test_list_datasets_empty(self):
        resp = client.get("/api/v1/data/datasets")
        assert resp.status_code == 200
        body = resp.json()
        assert body["code"] == 0
        assert isinstance(body["data"], list)
        assert len(body["data"]) == 0

    def test_list_datasets_after_create(self):
        client.post(
            "/api/v1/data/datasets",
            json={"label_column": "label"},
        )
        resp = client.get("/api/v1/data/datasets")
        assert resp.status_code == 200
        assert len(resp.json()["data"]) >= 1

    def test_get_dataset(self):
        create_resp = client.post(
            "/api/v1/data/datasets",
            json={"label_column": "label"},
        )
        dataset_id = create_resp.json()["data"]["dataset_id"]

        resp = client.get(f"/api/v1/data/datasets/{dataset_id}")
        assert resp.status_code == 200
        assert resp.json()["data"]["id"] == dataset_id

    def test_get_dataset_not_found(self):
        resp = client.get("/api/v1/data/datasets/ds_nonexistent")
        assert resp.status_code == 404

    def test_split_dataset(self):
        create_resp = client.post(
            "/api/v1/data/datasets",
            json={"label_column": "label"},
        )
        dataset_id = create_resp.json()["data"]["dataset_id"]

        resp = client.post(
            f"/api/v1/data/datasets/{dataset_id}/split",
            json={
                "dataset_id": dataset_id,
                "strategy": "STRATIFIED",
                "train_ratio": 0.6,
                "val_ratio": 0.2,
                "test_ratio": 0.2,
            },
        )
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert data["train_count"] > 0
        assert data["val_count"] > 0
        assert data["test_count"] > 0
        assert data["strategy"] == "STRATIFIED"

    def test_split_dataset_not_found(self):
        resp = client.post(
            "/api/v1/data/datasets/ds_fake/split",
            json={
                "dataset_id": "ds_fake",
                "strategy": "RANDOM",
                "train_ratio": 0.6,
                "val_ratio": 0.2,
                "test_ratio": 0.2,
            },
        )
        assert resp.status_code == 404


# ===========================================================================
# Training  (/api/v1/training)
# ===========================================================================


class TestTrainingEndpoints:
    def test_train_model(self):
        dataset_body, _ = _create_and_split_dataset()
        dataset_id = dataset_body["dataset_id"]

        resp = client.post(
            "/api/v1/training/train",
            json={
                "name": "test_lr_model",
                "algorithm": "LR",
                "dataset_id": dataset_id,
                "target_column": "label",
                "features": NUMERIC_FEATURES,
                "hyperparams": {"C": [0.1], "penalty": ["l2"], "solver": ["saga"]},
                "cv_folds": 2,
                "random_seed": 42,
            },
        )
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert data["model_id"].startswith("model_")
        assert data["algorithm"] == "LR"
        assert "cv_mean" in data
        assert isinstance(data["feature_importance"], dict)

    def test_train_model_dataset_not_found(self):
        resp = client.post(
            "/api/v1/training/train",
            json={
                "name": "bad",
                "algorithm": "LR",
                "dataset_id": "ds_nonexistent",
                "target_column": "label",
            },
        )
        assert resp.status_code == 400

    def test_list_models_empty(self):
        resp = client.get("/api/v1/training/models")
        assert resp.status_code == 200
        assert resp.json()["data"] == []

    def test_list_models_after_train(self):
        dataset_body, _ = _create_and_split_dataset()
        _train_model(dataset_body["dataset_id"])

        resp = client.get("/api/v1/training/models")
        assert resp.status_code == 200
        assert len(resp.json()["data"]) >= 1

    def test_get_model(self):
        dataset_body, _ = _create_and_split_dataset()
        train_data = _train_model(dataset_body["dataset_id"])
        model_id = train_data["model_id"]

        resp = client.get(f"/api/v1/training/models/{model_id}")
        assert resp.status_code == 200
        assert resp.json()["data"]["model_id"] == model_id

    def test_get_model_not_found(self):
        resp = client.get("/api/v1/training/models/model_999")
        assert resp.status_code == 404


# ===========================================================================
# Evaluation  (/api/v1/evaluation)
# ===========================================================================


class TestEvaluationEndpoints:
    def test_evaluate_model(self):
        dataset_body, _ = _create_and_split_dataset()
        train_data = _train_model(dataset_body["dataset_id"])
        model_id = train_data["model_id"]

        resp = client.post(
            "/api/v1/evaluation/evaluate",
            json={"model_id": model_id, "threshold": 0.5},
        )
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert "metrics" in data
        metrics = data["metrics"]
        assert "auc" in metrics
        assert "ks" in metrics
        assert "gini" in metrics
        assert "confusion_matrix" in metrics

    def test_evaluate_model_not_found(self):
        resp = client.post(
            "/api/v1/evaluation/evaluate",
            json={"model_id": "model_999"},
        )
        assert resp.status_code == 404

    def test_list_reports(self):
        resp = client.get("/api/v1/evaluation/reports")
        assert resp.status_code == 200
        assert isinstance(resp.json()["data"], list)

    def test_get_report(self):
        dataset_body, _ = _create_and_split_dataset()
        train_data = _train_model(dataset_body["dataset_id"])
        model_id = train_data["model_id"]

        # Trigger evaluation
        client.post(
            "/api/v1/evaluation/evaluate",
            json={"model_id": model_id},
        )

        resp = client.get(f"/api/v1/evaluation/reports/{model_id}")
        assert resp.status_code == 200
        assert resp.json()["data"]["model_id"] == model_id

    def test_get_report_not_found(self):
        resp = client.get("/api/v1/evaluation/reports/model_999")
        assert resp.status_code == 404


# ===========================================================================
# Inference  (/api/v1/inference)
# ===========================================================================


class TestInferenceEndpoints:
    @pytest.fixture()
    def trained_model(self):
        """Provide a trained model for inference tests."""
        dataset_body, _ = _create_and_split_dataset()
        train_data = _train_model(dataset_body["dataset_id"])
        return train_data

    def test_predict(self, trained_model):
        model_id = trained_model["model_id"]
        # Extract feature names from the trained model
        features = list(trained_model["feature_importance"].keys())

        resp = client.post(
            "/api/v1/inference/predict",
            json={
                "model_id": model_id,
                "features": {f: 0.5 for f in features},
                "return_explanation": False,
            },
        )
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert "prediction" in data
        assert "probability" in data
        assert "score" in data

    def test_predict_with_explanation(self, trained_model):
        model_id = trained_model["model_id"]
        features = list(trained_model["feature_importance"].keys())

        resp = client.post(
            "/api/v1/inference/predict",
            json={
                "model_id": model_id,
                "features": {f: 0.5 for f in features},
                "return_explanation": True,
            },
        )
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert "explanation" in data

    def test_predict_model_not_found(self):
        resp = client.post(
            "/api/v1/inference/predict",
            json={
                "model_id": "model_999",
                "features": {"x": 1.0},
            },
        )
        assert resp.status_code == 404

    def test_batch_predict(self, trained_model):
        model_id = trained_model["model_id"]
        features = list(trained_model["feature_importance"].keys())

        records = [{f: float(i % 100) for f in features} for i in range(5)]
        resp = client.post(
            "/api/v1/inference/batch-predict",
            json={
                "model_id": model_id,
                "records": records,
                "return_explanation": False,
            },
        )
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert data["total_count"] == 5
        assert len(data["predictions"]) == 5
        assert data["latency_ms"] >= 0

    def test_batch_predict_with_explanation(self, trained_model):
        model_id = trained_model["model_id"]
        features = list(trained_model["feature_importance"].keys())

        records = [{f: float(i) for f in features} for i in range(3)]
        resp = client.post(
            "/api/v1/inference/batch-predict",
            json={
                "model_id": model_id,
                "records": records,
                "return_explanation": True,
            },
        )
        assert resp.status_code == 200
        predictions = resp.json()["data"]["predictions"]
        for pred in predictions:
            assert "explanation" in pred


# ===========================================================================
# Export  (/api/v1/export)
# ===========================================================================


class TestExportEndpoints:
    @pytest.fixture()
    def trained_model(self):
        dataset_body, _ = _create_and_split_dataset()
        return _train_model(dataset_body["dataset_id"])

    def test_export_pmml(self, trained_model):
        model_id = trained_model["model_id"]
        resp = client.post(
            "/api/v1/export/export",
            json={
                "model_id": model_id,
                "format": "PMML",
                "version": "v1_integration",
            },
        )
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert data["format"] == "PMML"
        assert "file_path" in data
        assert data["file_size_bytes"] > 0

    def test_export_onnx(self, trained_model):
        model_id = trained_model["model_id"]
        resp = client.post(
            "/api/v1/export/export",
            json={
                "model_id": model_id,
                "format": "ONNX",
            },
        )
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert data["format"] == "ONNX"
        assert data["file_size_bytes"] > 0

    def test_export_model_not_found(self):
        resp = client.post(
            "/api/v1/export/export",
            json={"model_id": "model_999", "format": "PMML"},
        )
        assert resp.status_code == 404

    def test_list_exports(self, trained_model):
        model_id = trained_model["model_id"]
        client.post(
            "/api/v1/export/export",
            json={"model_id": model_id, "format": "PMML"},
        )
        resp = client.get("/api/v1/export/exports")
        assert resp.status_code == 200
        assert len(resp.json()["data"]) >= 1

    def test_get_export(self, trained_model):
        model_id = trained_model["model_id"]
        export_resp = client.post(
            "/api/v1/export/export",
            json={"model_id": model_id, "format": "PMML"},
        )
        export_id = export_resp.json()["data"]["export_id"]

        resp = client.get(f"/api/v1/export/exports/{export_id}")
        assert resp.status_code == 200
        assert resp.json()["data"]["export_id"] == export_id

    def test_get_export_not_found(self):
        resp = client.get("/api/v1/export/exports/export_999999")
        assert resp.status_code == 404


# ===========================================================================
# Monitoring  (/api/v1/monitoring)
# ===========================================================================


class TestMonitoringEndpoints:
    def test_get_dashboard(self):
        resp = client.get("/api/v1/monitoring/dashboard/model_001")
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert data["model_id"] == "model_001"
        assert "daily_psi" in data
        assert "ks_trend" in data
        assert "active_alerts" in data

    def test_check_psi(self):
        resp = client.post(
            "/api/v1/monitoring/psi/check?model_id=model_001",
            json={
                "reference_dist": [0.1, 0.2, 0.3, 0.2, 0.2],
                "current_dist": [0.15, 0.2, 0.25, 0.2, 0.2],
            },
        )
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert "psi_value" in data
        assert "severity" in data

    def test_check_feature_drift(self):
        resp = client.post(
            "/api/v1/monitoring/drift/check"
            "?model_id=model_001",
            json={
                "reference_features": {"age": [0.1, 0.2, 0.3, 0.2, 0.2]},
                "current_features": {"age": [0.15, 0.2, 0.25, 0.2, 0.2]},
            },
        )
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert "age" in data
        assert "psi" in data["age"]

    def test_track_ks(self):
        resp = client.post(
            "/api/v1/monitoring/ks/track"
            "?model_id=model_001&ks_value=0.45",
        )
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert data["ks_value"] == 0.45
        assert data["baseline"] == 0.45  # first record sets baseline

    def test_list_alerts(self):
        resp = client.get("/api/v1/monitoring/alerts")
        assert resp.status_code == 200
        assert isinstance(resp.json()["data"], list)

    def test_list_alerts_by_model(self):
        resp = client.get("/api/v1/monitoring/alerts?model_id=model_001")
        assert resp.status_code == 200

    def test_acknowledge_alert_not_found(self):
        resp = client.post("/api/v1/monitoring/alerts/alert_nonexistent/acknowledge")
        assert resp.status_code == 404


# ===========================================================================
# Audit  (/api/v1/audit)
# ===========================================================================


class TestAuditEndpoints:
    def test_list_audit_logs_empty(self):
        resp = client.get("/api/v1/audit/logs")
        assert resp.status_code == 200
        body = resp.json()
        assert body["code"] == 0
        assert isinstance(body["data"]["logs"], list)

    def test_list_audit_logs_with_params(self):
        resp = client.get(
            "/api/v1/audit/logs?action=TRAIN&limit=10&offset=0"
        )
        assert resp.status_code == 200

    def test_audit_stats(self):
        resp = client.get("/api/v1/audit/stats")
        assert resp.status_code == 200
        body = resp.json()
        assert body["code"] == 0
        assert "total" in body["data"]
        assert "by_action" in body["data"]
        assert "by_target_type" in body["data"]


# ===========================================================================
# Full Pipeline Integration Test
# ===========================================================================


class TestFullPipeline:
    """Exercise the complete workflow:
    create dataset -> split -> train -> evaluate -> predict -> export -> monitor
    """

    def test_end_to_end_pipeline(self):
        # 1. Create dataset
        create_resp = client.post(
            "/api/v1/data/datasets",
            json={"label_column": "label"},
        )
        assert create_resp.status_code == 200
        dataset_id = create_resp.json()["data"]["dataset_id"]

        # 2. Split dataset
        split_resp = client.post(
            f"/api/v1/data/datasets/{dataset_id}/split",
            json={
                "dataset_id": dataset_id,
                "strategy": "STRATIFIED",
                "train_ratio": 0.6,
                "val_ratio": 0.2,
                "test_ratio": 0.2,
            },
        )
        assert split_resp.status_code == 200

        # 3. Train a fast LR model
        train_resp = client.post(
            "/api/v1/training/train",
            json={
                "name": "e2e_lr",
                "algorithm": "LR",
                "dataset_id": dataset_id,
                "target_column": "label",
                "features": NUMERIC_FEATURES,
                "hyperparams": {"C": [0.1], "penalty": ["l2"], "solver": ["saga"]},
                "cv_folds": 2,
            },
        )
        assert train_resp.status_code == 200
        model_id = train_resp.json()["data"]["model_id"]

        # 4. Evaluate
        eval_resp = client.post(
            "/api/v1/evaluation/evaluate",
            json={"model_id": model_id, "threshold": 0.5},
        )
        assert eval_resp.status_code == 200
        metrics = eval_resp.json()["data"]["metrics"]
        assert metrics["auc"] >= 0.0

        # 5. Predict
        features = list(train_resp.json()["data"]["feature_importance"].keys())
        predict_resp = client.post(
            "/api/v1/inference/predict",
            json={
                "model_id": model_id,
                "features": {f: 0.5 for f in features},
                "return_explanation": True,
            },
        )
        assert predict_resp.status_code == 200
        assert "explanation" in predict_resp.json()["data"]

        # 6. Export PMML
        export_resp = client.post(
            "/api/v1/export/export",
            json={"model_id": model_id, "format": "PMML"},
        )
        assert export_resp.status_code == 200

        # 7. Check monitoring dashboard
        dashboard_resp = client.get(
            f"/api/v1/monitoring/dashboard/{model_id}?model_name=e2e_lr"
        )
        assert dashboard_resp.status_code == 200
        assert dashboard_resp.json()["data"]["model_id"] == model_id
