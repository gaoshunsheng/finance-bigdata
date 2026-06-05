"""
Unit tests for ModelTrainer — LR, XGBoost, LightGBM training and query.
"""

import sys

sys.path.insert(0, ".")

import numpy as np
import pandas as pd
import pytest

from app.core.data_prep.sample_manager import SampleManager
from app.core.training.trainer import ModelTrainer


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture
def train_data():
    """Create a small but viable training dataset for model training."""
    rng = np.random.RandomState(42)
    n = 300
    X = pd.DataFrame(
        {
            "feat_a": rng.randn(n),
            "feat_b": rng.uniform(0, 10, n),
            "feat_c": rng.randn(n),
        }
    )
    # Make labels somewhat dependent on features so models can learn
    logits = 0.5 * X["feat_a"] - 0.3 * X["feat_b"] + 0.1 * X["feat_c"]
    probs = 1 / (1 + np.exp(-logits))
    y = (probs > 0.5).astype(int)
    return X, y


@pytest.fixture
def trainer() -> ModelTrainer:
    """Fresh ModelTrainer instance."""
    return ModelTrainer()


@pytest.fixture
def trainer_with_data(train_data):
    """Trainer with a sample_manager that has a split dataset ready."""
    X, y = train_data
    sm = SampleManager()
    df = X.copy()
    df["label"] = y.values
    ds = sm.create_dataset(df, name="train_ds")
    sm.split_dataset(ds["id"], train_ratio=0.6, val_ratio=0.2, test_ratio=0.2)
    t = ModelTrainer(sample_manager=sm)
    return t, ds["id"]


# ---------------------------------------------------------------------------
# train_lr
# ---------------------------------------------------------------------------


class TestTrainLR:
    def test_basic_training(self, trainer: ModelTrainer, train_data):
        X, y = train_data
        # Use small grid for speed
        hp = {"C": [0.1, 1.0], "penalty": ["l2"], "solver": ["saga"]}
        result = trainer.train_lr(
            X_train=X[:200],
            y_train=y[:200],
            X_val=X[200:],
            y_val=y[200:],
            hyperparams=hp,
            cv_folds=3,
        )
        assert "best_estimator" in result
        assert "best_params" in result
        assert "cv_scores" in result
        assert "cv_mean" in result
        assert "cv_std" in result
        assert "feature_importance" in result
        assert isinstance(result["feature_importance"], dict)
        assert len(result["feature_importance"]) == 3  # 3 features

    def test_output_keys(self, trainer: ModelTrainer, train_data):
        X, y = train_data
        hp = {"C": [0.1], "penalty": ["l2"], "solver": ["saga"]}
        result = trainer.train_lr(
            X_train=X[:200],
            y_train=y[:200],
            X_val=X[200:],
            y_val=y[200:],
            hyperparams=hp,
            cv_folds=2,
        )
        expected_keys = {
            "best_estimator",
            "best_params",
            "cv_results",
            "cv_scores",
            "cv_mean",
            "cv_std",
            "feature_importance",
        }
        assert set(result.keys()) == expected_keys


# ---------------------------------------------------------------------------
# train_xgboost
# ---------------------------------------------------------------------------


class TestTrainXGBoost:
    def test_basic_training(self, trainer: ModelTrainer, train_data):
        X, y = train_data
        hp = {"n_estimators": [50], "max_depth": [3], "learning_rate": [0.1]}
        result = trainer.train_xgboost(
            X_train=X[:200],
            y_train=y[:200],
            X_val=X[200:],
            y_val=y[200:],
            hyperparams=hp,
            cv_folds=3,
        )
        assert "best_estimator" in result
        assert result["cv_mean"] > 0  # AUC should be positive
        assert isinstance(result["feature_importance"], dict)


# ---------------------------------------------------------------------------
# train_lightgbm
# ---------------------------------------------------------------------------


class TestTrainLightGBM:
    def test_basic_training(self, trainer: ModelTrainer, train_data):
        X, y = train_data
        hp = {"n_estimators": [50], "max_depth": [3], "learning_rate": [0.1], "num_leaves": [15]}
        result = trainer.train_lightgbm(
            X_train=X[:200],
            y_train=y[:200],
            X_val=X[200:],
            y_val=y[200:],
            hyperparams=hp,
            cv_folds=3,
        )
        assert "best_estimator" in result
        assert result["cv_mean"] > 0
        assert isinstance(result["feature_importance"], dict)


# ---------------------------------------------------------------------------
# train (main entry point)
# ---------------------------------------------------------------------------


class TestTrainMainEntry:
    def test_train_lr_via_entry(
        self, trainer_with_data: tuple[ModelTrainer, str]
    ):
        trainer, dataset_id = trainer_with_data
        result = trainer.train(
            {
                "name": "lr_model",
                "algorithm": "LR",
                "dataset_id": dataset_id,
                "target_column": "label",
                "hyperparams": {"C": [0.1, 1.0], "penalty": ["l2"], "solver": ["saga"]},
                "cv_folds": 3,
            }
        )
        assert result["model_id"].startswith("model_")
        assert result["algorithm"] == "LR"
        assert result["name"] == "lr_model"
        assert "best_params" in result
        assert "cv_scores" in result

    def test_train_nonexistent_dataset_raises(self, trainer: ModelTrainer):
        with pytest.raises(ValueError, match="不存在"):
            trainer.train(
                {
                    "name": "bad",
                    "algorithm": "LR",
                    "dataset_id": "ds_nonexistent",
                }
            )


# ---------------------------------------------------------------------------
# get_model / list_models
# ---------------------------------------------------------------------------


class TestModelQueries:
    def test_get_model(
        self, trainer_with_data: tuple[ModelTrainer, str]
    ):
        trainer, dataset_id = trainer_with_data
        result = trainer.train(
            {
                "name": "query_model",
                "algorithm": "LR",
                "dataset_id": dataset_id,
                "hyperparams": {"C": [0.1], "penalty": ["l2"], "solver": ["saga"]},
                "cv_folds": 2,
            }
        )
        model = trainer.get_model(result["model_id"])
        assert model is not None
        assert model["model_id"] == result["model_id"]
        assert "_best_estimator" in model  # Internal field present

    def test_get_nonexistent_model(self, trainer: ModelTrainer):
        assert trainer.get_model("model_999") is None

    def test_list_models(
        self, trainer_with_data: tuple[ModelTrainer, str]
    ):
        trainer, dataset_id = trainer_with_data
        trainer.train(
            {
                "name": "m1",
                "algorithm": "LR",
                "dataset_id": dataset_id,
                "hyperparams": {"C": [0.1], "penalty": ["l2"], "solver": ["saga"]},
                "cv_folds": 2,
            }
        )
        models = trainer.list_models()
        assert len(models) >= 1
        # list_models should NOT include internal fields
        for m in models:
            assert "_best_estimator" not in m
            assert "model_id" in m
