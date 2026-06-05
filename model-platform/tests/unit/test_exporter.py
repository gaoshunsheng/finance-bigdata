"""
Unit tests for ModelExporter — PMML export, list/get exports.
"""

import sys

sys.path.insert(0, ".")

import numpy as np
import pandas as pd
import pytest
from sklearn.linear_model import LogisticRegression

from app.core.export.exporter import ModelExporter


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture
def exporter() -> ModelExporter:
    """Fresh ModelExporter instance."""
    return ModelExporter()


@pytest.fixture
def trained_lr():
    """Train a simple LogisticRegression and return model_result dict."""
    rng = np.random.RandomState(42)
    n = 200
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
    model.fit(X, y)

    return {
        "model_id": "model_export_test",
        "algorithm": "LR",
        "_best_estimator": model,
    }


# ---------------------------------------------------------------------------
# export_pmml
# ---------------------------------------------------------------------------


class TestExportPMML:
    def test_export_lr_model(self, exporter: ModelExporter, trained_lr):
        result = exporter.export_pmml(trained_lr, version="v1")
        assert result["model_id"] == "model_export_test"
        assert result["format"] == "PMML"
        assert result["version"] == "v1"
        assert "file_path" in result
        assert result["file_size_bytes"] > 0
        assert "exported_at" in result
        assert result["export_id"].startswith("export_")

    def test_export_generates_xml(self, exporter: ModelExporter, trained_lr, tmp_path):
        result = exporter.export_pmml(trained_lr, version="v_xml")
        with open(result["file_path"], "r") as f:
            content = f.read()
        assert "PMML" in content
        assert "RegressionModel" in content

    def test_export_with_custom_version(self, exporter: ModelExporter, trained_lr):
        result = exporter.export_pmml(trained_lr, version="v2.0.1")
        assert result["version"] == "v2.0.1"

    def test_export_missing_estimator_raises(self, exporter: ModelExporter):
        with pytest.raises(ValueError, match="必须包含"):
            exporter.export_pmml({"model_id": "m1", "algorithm": "LR"})

    def test_export_missing_model_id_raises(self, exporter: ModelExporter, trained_lr):
        bad_result = dict(trained_lr)
        del bad_result["model_id"]
        with pytest.raises(ValueError, match="必须包含"):
            exporter.export_pmml(bad_result)

    def test_unsupported_algorithm_raises(self, exporter: ModelExporter, trained_lr):
        result = dict(trained_lr)
        result["algorithm"] = "UNSUPPORTED"
        with pytest.raises(ValueError, match="不支持"):
            exporter.export_pmml(result)


# ---------------------------------------------------------------------------
# list_exports / get_export
# ---------------------------------------------------------------------------


class TestExportQueries:
    def test_list_exports(self, exporter: ModelExporter, trained_lr):
        exporter.export_pmml(trained_lr, version="v_list1")
        exporter.export_pmml(trained_lr, version="v_list2")
        exports = exporter.list_exports()
        assert len(exports) >= 2

    def test_list_exports_filter_by_model_id(
        self, exporter: ModelExporter, trained_lr
    ):
        exporter.export_pmml(trained_lr, version="v_filter")
        exports = exporter.list_exports(model_id="model_export_test")
        assert all(e["model_id"] == "model_export_test" for e in exports)

    def test_list_exports_filter_by_format(
        self, exporter: ModelExporter, trained_lr
    ):
        exporter.export_pmml(trained_lr, version="v_fmt")
        exports = exporter.list_exports(fmt="PMML")
        assert all(e["format"] == "PMML" for e in exports)

    def test_get_export(self, exporter: ModelExporter, trained_lr):
        result = exporter.export_pmml(trained_lr, version="v_get")
        retrieved = exporter.get_export(result["export_id"])
        assert retrieved is not None
        assert retrieved["export_id"] == result["export_id"]

    def test_get_nonexistent_export(self, exporter: ModelExporter):
        assert exporter.get_export("export_999999") is None
