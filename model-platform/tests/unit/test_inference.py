"""
Unit tests for InferenceService — predict, explain, batch_predict.
"""

import sys

sys.path.insert(0, ".")

import numpy as np
import pandas as pd
import pytest
from sklearn.linear_model import LogisticRegression

from app.core.inference.inference_service import InferenceService
from app.core.training.trainer import ModelTrainer
from app.core.data_prep.sample_manager import SampleManager


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture
def trained_model_setup():
    """
    Train a model via ModelTrainer + SampleManager, and return
    (InferenceService, model_id, feature_names, trainer).
    """
    rng = np.random.RandomState(42)
    n = 300
    X = pd.DataFrame(
        {
            "feat_a": rng.randn(n),
            "feat_b": rng.uniform(0, 10, n),
            "feat_c": rng.randn(n),
        }
    )
    logits = 0.5 * X["feat_a"] - 0.3 * X["feat_b"] + 0.1 * X["feat_c"]
    probs = 1 / (1 + np.exp(-logits))
    y = (probs > 0.5).astype(int)

    df = X.copy()
    df["label"] = y.values

    sm = SampleManager()
    ds = sm.create_dataset(df, name="inference_ds")
    sm.split_dataset(ds["id"], train_ratio=0.6, val_ratio=0.2, test_ratio=0.2)

    trainer = ModelTrainer(sample_manager=sm)
    train_result = trainer.train(
        {
            "name": "inference_model",
            "algorithm": "LR",
            "dataset_id": ds["id"],
            "hyperparams": {"C": [0.1, 1.0], "penalty": ["l2"], "solver": ["saga"]},
            "cv_folds": 3,
        }
    )

    service = InferenceService()
    # Pre-warm the cache so _load_model can find it from the global trainer
    model_info = trainer.get_model(train_result["model_id"])
    service._model_cache[train_result["model_id"]] = model_info["_best_estimator"]

    return service, train_result["model_id"], list(X.columns), trainer


# ---------------------------------------------------------------------------
# predict
# ---------------------------------------------------------------------------


class TestPredict:
    def test_single_prediction(self, trained_model_setup):
        service, model_id, features, _ = trained_model_setup
        result = service.predict(
            model_id,
            {"feat_a": 1.0, "feat_b": 5.0, "feat_c": -0.5},
        )
        assert result["model_id"] == model_id
        assert result["prediction"] in (0, 1)
        assert 0.0 <= result["probability"] <= 1.0
        assert 0.0 <= result["score"] <= 1000.0

    def test_empty_features_raises(self, trained_model_setup):
        service, model_id, _, _ = trained_model_setup
        with pytest.raises(ValueError, match="不能为空"):
            service.predict(model_id, {})

    def test_nonexistent_model_raises(self, trained_model_setup):
        service, _, _, _ = trained_model_setup
        with pytest.raises(ValueError, match="未找到"):
            service.predict("model_nonexistent", {"feat_a": 1.0})

    def test_prediction_consistency(self, trained_model_setup):
        """Calling predict twice with same input should return same result."""
        service, model_id, _, _ = trained_model_setup
        features = {"feat_a": 2.0, "feat_b": 3.0, "feat_c": -1.0}
        r1 = service.predict(model_id, features)
        r2 = service.predict(model_id, features)
        assert r1["probability"] == r2["probability"]
        assert r1["prediction"] == r2["prediction"]


# ---------------------------------------------------------------------------
# explain
# ---------------------------------------------------------------------------


class TestExplain:
    def test_feature_importance(self, trained_model_setup):
        service, model_id, features, _ = trained_model_setup
        result = service.explain(
            model_id,
            {"feat_a": 1.0, "feat_b": 5.0, "feat_c": -0.5},
        )
        assert result["model_id"] == model_id
        assert "explanation" in result
        explanation = result["explanation"]
        assert len(explanation) == 3  # 3 features
        # Normalized values should sum to ~1
        total = sum(explanation.values())
        assert abs(total - 1.0) < 0.01

    def test_method_description(self, trained_model_setup):
        service, model_id, _, _ = trained_model_setup
        result = service.explain(
            model_id,
            {"feat_a": 1.0, "feat_b": 5.0, "feat_c": -0.5},
        )
        assert "method" in result
        # LR uses coef_
        assert "coef_" in result["method"]


# ---------------------------------------------------------------------------
# batch_predict
# ---------------------------------------------------------------------------


class TestBatchPredict:
    def test_batch_predictions(self, trained_model_setup):
        service, model_id, features, _ = trained_model_setup
        records = [
            {"feat_a": 1.0, "feat_b": 2.0, "feat_c": 0.5},
            {"feat_a": -1.0, "feat_b": 8.0, "feat_c": -0.5},
            {"feat_a": 0.0, "feat_b": 5.0, "feat_c": 0.0},
        ]
        result = service.batch_predict(model_id, records)
        assert result["model_id"] == model_id
        assert result["total_count"] == 3
        assert len(result["predictions"]) == 3
        assert result["latency_ms"] >= 0
        for pred in result["predictions"]:
            assert pred["prediction"] in (0, 1)
            assert 0.0 <= pred["probability"] <= 1.0

    def test_empty_batch(self, trained_model_setup):
        service, model_id, _, _ = trained_model_setup
        result = service.batch_predict(model_id, [])
        assert result["total_count"] == 0
        assert result["predictions"] == []
