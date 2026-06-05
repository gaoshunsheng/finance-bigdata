"""
Unit tests for ModelEvaluator — KS, AUC, Gini, confusion matrix, Lift, PSI, full evaluation.
"""

import sys

sys.path.insert(0, ".")

import numpy as np
import pandas as pd
import pytest
from sklearn.linear_model import LogisticRegression

from app.core.evaluation.evaluator import ModelEvaluator


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture
def evaluator() -> ModelEvaluator:
    """Fresh ModelEvaluator instance."""
    return ModelEvaluator()


@pytest.fixture
def prediction_data():
    """Realistic y_true and y_prob arrays for metric calculation."""
    rng = np.random.RandomState(42)
    n = 500
    y_true = rng.choice([0, 1], n, p=[0.7, 0.3])
    # Generate probabilities that correlate with labels
    y_prob = np.where(
        y_true == 1,
        rng.beta(5, 2, n),   # Positive: higher probabilities
        rng.beta(2, 5, n),   # Negative: lower probabilities
    )
    return y_true.astype(float), y_prob


@pytest.fixture
def trained_lr_model():
    """Train a simple LogisticRegression model for full evaluate() test."""
    rng = np.random.RandomState(42)
    n = 300
    X = pd.DataFrame(
        {
            "feat_a": rng.randn(n),
            "feat_b": rng.uniform(0, 10, n),
        }
    )
    logits = 0.5 * X["feat_a"] - 0.3 * X["feat_b"]
    probs = 1 / (1 + np.exp(-logits))
    y = (probs > 0.5).astype(int)

    model = LogisticRegression(random_state=42, max_iter=500, solver="saga")
    model.fit(X[:200], y[:200])

    model_result = {
        "model_id": "model_test_001",
        "algorithm": "LR",
        "_best_estimator": model,
    }

    return model_result, X[200:], y[200:]


# ---------------------------------------------------------------------------
# calculate_ks
# ---------------------------------------------------------------------------


class TestCalculateKS:
    def test_basic_ks(self, evaluator: ModelEvaluator, prediction_data):
        y_true, y_prob = prediction_data
        ks_value, ks_threshold = evaluator.calculate_ks(y_true, y_prob)
        assert 0.0 <= ks_value <= 1.0
        assert 0.0 <= ks_threshold <= 1.0

    def test_perfect_separation(self, evaluator: ModelEvaluator):
        y_true = np.array([0, 0, 0, 1, 1, 1])
        y_prob = np.array([0.1, 0.2, 0.3, 0.8, 0.9, 0.95])
        ks_value, _ = evaluator.calculate_ks(y_true, y_prob)
        assert ks_value == 1.0

    def test_empty_arrays(self, evaluator: ModelEvaluator):
        ks_value, ks_threshold = evaluator.calculate_ks([], [])
        assert ks_value == 0.0
        assert ks_threshold == 0.5

    def test_single_class(self, evaluator: ModelEvaluator):
        y_true = np.array([0, 0, 0, 0])
        y_prob = np.array([0.1, 0.2, 0.3, 0.4])
        ks_value, _ = evaluator.calculate_ks(y_true, y_prob)
        assert ks_value == 0.0


# ---------------------------------------------------------------------------
# calculate_auc
# ---------------------------------------------------------------------------


class TestCalculateAUC:
    def test_basic_auc(self, evaluator: ModelEvaluator, prediction_data):
        y_true, y_prob = prediction_data
        auc_value = evaluator.calculate_auc(y_true, y_prob)
        assert 0.0 <= auc_value <= 1.0
        assert auc_value > 0.5  # Should be better than random

    def test_perfect_auc(self, evaluator: ModelEvaluator):
        y_true = np.array([0, 0, 0, 1, 1, 1])
        y_prob = np.array([0.1, 0.2, 0.3, 0.8, 0.9, 0.95])
        auc_value = evaluator.calculate_auc(y_true, y_prob)
        assert auc_value == 1.0

    def test_single_class_auc(self, evaluator: ModelEvaluator):
        y_true = np.array([0, 0, 0])
        y_prob = np.array([0.1, 0.2, 0.3])
        assert evaluator.calculate_auc(y_true, y_prob) == 0.0


# ---------------------------------------------------------------------------
# calculate_gini
# ---------------------------------------------------------------------------


class TestCalculateGini:
    def test_basic_gini(self, evaluator: ModelEvaluator):
        assert abs(evaluator.calculate_gini(0.8) - 0.6) < 1e-10

    def test_perfect_gini(self, evaluator: ModelEvaluator):
        assert evaluator.calculate_gini(1.0) == 1.0

    def test_random_gini(self, evaluator: ModelEvaluator):
        assert evaluator.calculate_gini(0.5) == 0.0


# ---------------------------------------------------------------------------
# calculate_confusion_matrix
# ---------------------------------------------------------------------------


class TestCalculateConfusionMatrix:
    def test_basic_cm(self, evaluator: ModelEvaluator):
        y_true = [0, 0, 1, 1, 0, 1]
        y_pred = [0, 1, 1, 0, 0, 1]
        cm = evaluator.calculate_confusion_matrix(y_true, y_pred)
        # Format: [[TN, FP], [FN, TP]]
        assert len(cm) == 2
        assert len(cm[0]) == 2
        # TN + FP + FN + TP = 6
        assert sum(sum(row) for row in cm) == 6

    def test_perfect_predictions(self, evaluator: ModelEvaluator):
        y_true = [0, 0, 1, 1]
        y_pred = [0, 0, 1, 1]
        cm = evaluator.calculate_confusion_matrix(y_true, y_pred)
        # TN=2, FP=0, FN=0, TP=2
        assert cm == [[2, 0], [0, 2]]

    def test_empty_cm(self, evaluator: ModelEvaluator):
        cm = evaluator.calculate_confusion_matrix([], [])
        assert cm == [[0, 0], [0, 0]]


# ---------------------------------------------------------------------------
# calculate_lift
# ---------------------------------------------------------------------------


class TestCalculateLift:
    def test_basic_lift(self, evaluator: ModelEvaluator, prediction_data):
        y_true, y_prob = prediction_data
        lift = evaluator.calculate_lift(y_true, y_prob, percentile=10)
        assert lift > 1.0  # Top 10% should have higher positive rate

    def test_random_lift(self, evaluator: ModelEvaluator):
        rng = np.random.RandomState(42)
        y_true = rng.choice([0, 1], 1000, p=[0.5, 0.5])
        y_prob = rng.uniform(0, 1, 1000)  # Random predictions
        lift = evaluator.calculate_lift(y_true, y_prob, percentile=20)
        # With random predictions, lift should be close to 1
        assert 0.5 < lift < 2.0

    def test_zero_positive_rate(self, evaluator: ModelEvaluator):
        y_true = np.zeros(100)
        y_prob = np.random.rand(100)
        assert evaluator.calculate_lift(y_true, y_prob) == 0.0


# ---------------------------------------------------------------------------
# calculate_psi
# ---------------------------------------------------------------------------


class TestEvaluatorPSI:
    def test_same_distribution_psi(self, evaluator: ModelEvaluator):
        rng = np.random.RandomState(42)
        data = rng.randn(1000)
        psi = evaluator.calculate_psi(data, data)
        assert psi < 0.05  # Same dist => small PSI

    def test_different_distribution_psi(self, evaluator: ModelEvaluator):
        rng = np.random.RandomState(42)
        train = rng.normal(0, 1, 1000)
        test = rng.normal(5, 2, 1000)
        psi = evaluator.calculate_psi(train, test)
        assert psi > 0.1


# ---------------------------------------------------------------------------
# evaluate (full pipeline)
# ---------------------------------------------------------------------------


class TestFullEvaluate:
    def test_evaluate_lr_model(
        self, evaluator: ModelEvaluator, trained_lr_model
    ):
        model_result, X_test, y_test = trained_lr_model
        report = evaluator.evaluate(model_result, X_test, y_test)

        assert report["model_id"] == "model_test_001"
        assert report["algorithm"] == "LR"
        assert "metrics" in report

        metrics = report["metrics"]
        assert 0.0 <= metrics["auc"] <= 1.0
        assert 0.0 <= metrics["ks"] <= 1.0
        assert -1.0 <= metrics["gini"] <= 1.0
        assert 0.0 <= metrics["accuracy"] <= 1.0
        assert len(metrics["confusion_matrix"]) == 2

        # Visualization data
        assert "ks_curve" in report
        assert "roc_curve" in report
        assert "score_distribution" in report
        assert "feature_importance" in report
        assert "evaluated_at" in report

    def test_evaluate_caches_report(
        self, evaluator: ModelEvaluator, trained_lr_model
    ):
        model_result, X_test, y_test = trained_lr_model
        evaluator.evaluate(model_result, X_test, y_test)
        cached = evaluator.get_report("model_test_001")
        assert cached is not None
        assert cached["model_id"] == "model_test_001"
