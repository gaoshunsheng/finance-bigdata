"""
Unit tests for FeatureEngineer — IV, WOE, PSI, correlation filter, full pipeline.
"""

import sys

sys.path.insert(0, ".")

import warnings

import numpy as np
import pandas as pd
import pytest

from app.core.data_prep.feature_engineering import FeatureEngineer


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture
def fe() -> FeatureEngineer:
    """Fresh FeatureEngineer instance."""
    return FeatureEngineer()


@pytest.fixture
def sample_df() -> pd.DataFrame:
    """DataFrame with informative and useless features for IV / WOE testing."""
    rng = np.random.RandomState(42)
    n = 500
    # Create features with varying predictive power
    label = rng.choice([0, 1], n, p=[0.7, 0.3])
    # Strong predictor: different distributions for label 0 vs 1
    feat_strong = np.where(
        label == 1, rng.normal(2, 1, n), rng.normal(0, 1, n)
    )
    # Weak predictor
    feat_weak = rng.randn(n)
    # Constant column (no predictive power)
    feat_constant = np.ones(n) * 5.0
    # Null-heavy column
    feat_nulls = rng.randn(n)
    feat_nulls[:100] = np.nan

    return pd.DataFrame(
        {
            "feat_strong": feat_strong,
            "feat_weak": feat_weak,
            "feat_constant": feat_constant,
            "feat_nulls": feat_nulls,
            "label": label,
        }
    )


# ---------------------------------------------------------------------------
# calculate_iv
# ---------------------------------------------------------------------------


class TestCalculateIV:
    def test_numeric_feature_iv(self, fe: FeatureEngineer, sample_df: pd.DataFrame):
        result = fe.calculate_iv(sample_df, "feat_strong", "label")
        assert result["feature_name"] == "feat_strong"
        assert result["iv_value"] > 0.02  # Should be informative
        assert isinstance(result["woe_bins"], list)
        assert len(result["woe_bins"]) > 0

    def test_constant_column_iv(self, fe: FeatureEngineer, sample_df: pd.DataFrame):
        result = fe.calculate_iv(sample_df, "feat_constant", "label")
        # Constant column has no discriminatory power; IV should be ~0
        assert result["iv_value"] < 0.01
        assert result["selected"] is False

    def test_null_column_iv(self, fe: FeatureEngineer, sample_df: pd.DataFrame):
        result = fe.calculate_iv(sample_df, "feat_nulls", "label")
        assert isinstance(result["iv_value"], float)

    def test_missing_feature_raises(self, fe: FeatureEngineer, sample_df: pd.DataFrame):
        with pytest.raises(ValueError, match="不存在"):
            fe.calculate_iv(sample_df, "nonexistent", "label")

    def test_missing_target_raises(self, fe: FeatureEngineer, sample_df: pd.DataFrame):
        with pytest.raises(ValueError, match="不存在"):
            fe.calculate_iv(sample_df, "feat_strong", "nonexistent")


# ---------------------------------------------------------------------------
# iv_filter
# ---------------------------------------------------------------------------


class TestIVFilter:
    def test_threshold_filtering(self, fe: FeatureEngineer, sample_df: pd.DataFrame):
        results = fe.iv_filter(sample_df, "label", iv_threshold=0.02)
        assert len(results) > 0
        # Results should be sorted by IV descending
        ivs = [iv for _, iv, _ in results]
        assert ivs == sorted(ivs, reverse=True)
        # At least feat_strong should be selected
        selected_names = [name for name, iv, sel in results if sel]
        assert "feat_strong" in selected_names

    def test_all_below_threshold(self, fe: FeatureEngineer):
        rng = np.random.RandomState(42)
        n = 100
        df = pd.DataFrame(
            {
                "x": rng.randn(n),
                "label": rng.choice([0, 1], n, p=[0.5, 0.5]),
            }
        )
        # Very high threshold should exclude everything
        results = fe.iv_filter(df, "label", iv_threshold=999.0)
        selected = [name for name, iv, sel in results if sel]
        assert len(selected) == 0


# ---------------------------------------------------------------------------
# correlation_filter
# ---------------------------------------------------------------------------


class TestCorrelationFilter:
    def test_removes_correlated_features(self, fe: FeatureEngineer):
        rng = np.random.RandomState(42)
        n = 200
        x1 = rng.randn(n)
        x2 = x1 * 0.99 + rng.randn(n) * 0.01  # Highly correlated with x1
        x3 = rng.randn(n)  # Independent
        df = pd.DataFrame({"x1": x1, "x2": x2, "x3": x3})

        iv_results = [("x1", 0.5, True), ("x2", 0.3, True), ("x3", 0.2, True)]
        result = fe.correlation_filter(df, ["x1", "x2", "x3"], iv_results, threshold=0.7)
        # x2 should be removed because it is highly correlated with x1 and has lower IV
        assert "x2" in [r["feature"] for r in result["removed"]]
        assert len(result["selected"]) >= 2

    def test_no_correlation(self, fe: FeatureEngineer):
        rng = np.random.RandomState(42)
        n = 200
        df = pd.DataFrame(
            {
                "a": rng.randn(n),
                "b": rng.randn(n),
                "c": rng.randn(n),
            }
        )
        result = fe.correlation_filter(df, ["a", "b", "c"], threshold=0.7)
        assert len(result["removed"]) == 0
        assert len(result["selected"]) == 3

    def test_empty_features(self, fe: FeatureEngineer):
        rng = np.random.RandomState(42)
        df = pd.DataFrame({"a": rng.randn(10)})
        result = fe.correlation_filter(df, [], threshold=0.7)
        assert result["selected"] == []
        assert result["removed"] == []


# ---------------------------------------------------------------------------
# woe_binning
# ---------------------------------------------------------------------------


class TestWOEBinning:
    def test_numeric_binning(self, fe: FeatureEngineer, sample_df: pd.DataFrame):
        result = fe.woe_binning(sample_df, "feat_strong", "label", max_bins=5)
        assert result["feature"] == "feat_strong"
        assert result["bin_type"] == "numeric"
        assert len(result["bins"]) > 0
        for b in result["bins"]:
            assert "woe" in b
            assert "iv" in b
            assert "count" in b
            assert "bad_rate" in b

    def test_empty_df_binning(self, fe: FeatureEngineer):
        df = pd.DataFrame({"x": pd.Series(dtype=float), "label": pd.Series(dtype=int)})
        result = fe.woe_binning(df, "x", "label")
        assert result["bins"] == []


# ---------------------------------------------------------------------------
# calculate_psi
# ---------------------------------------------------------------------------


class TestCalculatePSI:
    def test_same_distribution(self, fe: FeatureEngineer):
        rng = np.random.RandomState(42)
        data = rng.randn(1000)
        psi = fe.calculate_psi(data, data)
        assert psi < 0.01  # Should be near zero for identical distributions

    def test_different_distribution(self, fe: FeatureEngineer):
        rng = np.random.RandomState(42)
        expected = rng.normal(0, 1, 1000)
        actual = rng.normal(3, 2, 1000)  # Very different distribution
        psi = fe.calculate_psi(expected, actual)
        assert psi > 0.1  # Should detect significant difference

    def test_empty_arrays(self, fe: FeatureEngineer):
        psi = fe.calculate_psi(np.array([]), np.array([]))
        assert psi == 0.0

    def test_constant_array(self, fe: FeatureEngineer):
        psi = fe.calculate_psi(np.ones(100), np.ones(100))
        assert psi == 0.0


# ---------------------------------------------------------------------------
# run_feature_engineering (full pipeline)
# ---------------------------------------------------------------------------


class TestRunFeatureEngineering:
    def test_full_pipeline(self, fe: FeatureEngineer, sample_df: pd.DataFrame):
        result = fe.run_feature_engineering(
            sample_df,
            target="label",
            iv_threshold=0.02,
            corr_threshold=0.7,
            dataset_id="ds_test",
        )
        assert result["dataset_id"] == "ds_test"
        assert result["total_features"] == 4  # 4 features excluding label
        assert isinstance(result["selected_features"], list)
        assert isinstance(result["iv_results"], list)
        assert isinstance(result["removed_by_iv"], list)
        assert isinstance(result["removed_by_correlation"], list)
        assert isinstance(result["psi_scores"], dict)
        # Strong feature should be selected
        assert "feat_strong" in result["selected_features"]
