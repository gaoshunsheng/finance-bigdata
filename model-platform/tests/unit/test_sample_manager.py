"""
Unit tests for SampleManager — dataset creation, filtering, and splitting.
"""

import sys

sys.path.insert(0, ".")

import numpy as np
import pandas as pd
import pytest

from app.core.data_prep.sample_manager import SampleManager
from app.schemas import SampleFilter, SampleSplitStrategy


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture
def sample_df() -> pd.DataFrame:
    """Create a realistic credit-risk-style DataFrame with 200 rows."""
    rng = np.random.RandomState(42)
    n = 200
    return pd.DataFrame(
        {
            "feature_a": rng.randn(n),
            "feature_b": rng.uniform(0, 100, n),
            "feature_c": rng.choice(["X", "Y", "Z"], n),
            "label": rng.choice([0, 1], n, p=[0.7, 0.3]),
        }
    )


@pytest.fixture
def sample_df_with_time() -> pd.DataFrame:
    """DataFrame with a date column for TIME_BASED split tests."""
    rng = np.random.RandomState(42)
    n = 200
    dates = pd.date_range("2024-01-01", periods=n, freq="D")
    return pd.DataFrame(
        {
            "feature_a": rng.randn(n),
            "feature_b": rng.uniform(0, 100, n),
            "apply_date": dates,
            "label": rng.choice([0, 1], n, p=[0.7, 0.3]),
        }
    )


@pytest.fixture
def manager() -> SampleManager:
    """Fresh SampleManager instance."""
    return SampleManager()


# ---------------------------------------------------------------------------
# create_dataset
# ---------------------------------------------------------------------------


class TestCreateDataset:
    def test_basic_creation(self, manager: SampleManager, sample_df: pd.DataFrame):
        ds = manager.create_dataset(sample_df, name="test_ds")
        assert ds["name"] == "test_ds"
        assert ds["id"].startswith("ds_")
        assert ds["df"] is not None
        assert len(ds["df"]) == 200
        assert ds["train_df"] is None
        assert ds["val_df"] is None
        assert ds["test_df"] is None

    def test_empty_df_raises(self, manager: SampleManager):
        with pytest.raises(ValueError, match="不能为空"):
            manager.create_dataset(pd.DataFrame(), name="empty")

    def test_none_df_raises(self, manager: SampleManager):
        with pytest.raises(ValueError, match="不能为空"):
            manager.create_dataset(None, name="none")

    def test_with_filters(self, manager: SampleManager):
        rng = np.random.RandomState(42)
        n = 200
        df = pd.DataFrame(
            {
                "feature_a": rng.randn(n),
                "label": rng.choice([0, 1], n, p=[0.7, 0.3]),
                "product": rng.choice(["A", "B"], n),
            }
        )
        filters = SampleFilter(
            label_column="label",
            positive_label=1,
            negative_label=0,
            products=["A"],
        )
        ds = manager.create_dataset(df, name="filtered", filters=filters)
        # Only product A rows remain
        assert len(ds["df"]) < n
        assert all(ds["df"]["product"] == "A")


# ---------------------------------------------------------------------------
# split_dataset
# ---------------------------------------------------------------------------


class TestSplitDataset:
    def test_stratified_split(self, manager: SampleManager, sample_df: pd.DataFrame):
        ds = manager.create_dataset(sample_df, name="strat_test")
        result = manager.split_dataset(ds["id"], strategy=SampleSplitStrategy.STRATIFIED)
        assert result["train_df"] is not None
        assert result["val_df"] is not None
        assert result["test_df"] is not None

        total = len(result["train_df"]) + len(result["val_df"]) + len(result["test_df"])
        assert total == 200

        # Verify stratification: both train and test should contain 0 and 1 labels
        assert set(result["train_df"]["label"].unique()).issubset({0, 1})
        assert set(result["test_df"]["label"].unique()).issubset({0, 1})

    def test_random_split(self, manager: SampleManager, sample_df: pd.DataFrame):
        ds = manager.create_dataset(sample_df, name="random_test")
        result = manager.split_dataset(ds["id"], strategy=SampleSplitStrategy.RANDOM)
        total = len(result["train_df"]) + len(result["val_df"]) + len(result["test_df"])
        assert total == 200
        assert result["split_strategy"] == "RANDOM"

    def test_time_based_split(
        self, manager: SampleManager, sample_df_with_time: pd.DataFrame
    ):
        ds = manager.create_dataset(sample_df_with_time, name="time_test")
        result = manager.split_dataset(
            ds["id"],
            strategy=SampleSplitStrategy.TIME_BASED,
            time_column="apply_date",
            train_ratio=0.6,
            val_ratio=0.2,
            test_ratio=0.2,
        )
        total = len(result["train_df"]) + len(result["val_df"]) + len(result["test_df"])
        assert total == 200
        # Training data should be earlier than test data
        train_max = pd.to_datetime(result["train_df"]["apply_date"]).max()
        test_min = pd.to_datetime(result["test_df"]["apply_date"]).min()
        assert train_max <= test_min

    def test_time_based_without_time_column_raises(
        self, manager: SampleManager, sample_df: pd.DataFrame
    ):
        ds = manager.create_dataset(sample_df, name="no_time")
        with pytest.raises(ValueError, match="必须指定 time_column"):
            manager.split_dataset(ds["id"], strategy=SampleSplitStrategy.TIME_BASED)

    def test_invalid_ratios_raises(self, manager: SampleManager, sample_df: pd.DataFrame):
        ds = manager.create_dataset(sample_df, name="bad_ratio")
        with pytest.raises(ValueError, match="比例之和必须等于 1.0"):
            manager.split_dataset(
                ds["id"], train_ratio=0.5, val_ratio=0.3, test_ratio=0.3
            )

    def test_nonexistent_dataset_raises(self, manager: SampleManager):
        with pytest.raises(ValueError, match="不存在"):
            manager.split_dataset("ds_nonexistent")

    def test_custom_ratios(self, manager: SampleManager, sample_df: pd.DataFrame):
        ds = manager.create_dataset(sample_df, name="custom_ratio")
        result = manager.split_dataset(
            ds["id"],
            strategy=SampleSplitStrategy.RANDOM,
            train_ratio=0.7,
            val_ratio=0.15,
            test_ratio=0.15,
        )
        n_train = len(result["train_df"])
        n_val = len(result["val_df"])
        n_test = len(result["test_df"])
        assert n_train + n_val + n_test == 200
        # Train should be roughly 70% of data
        assert n_train > n_val
        assert n_train > n_test


# ---------------------------------------------------------------------------
# get_dataset / list_datasets
# ---------------------------------------------------------------------------


class TestQueryDataset:
    def test_get_dataset(self, manager: SampleManager, sample_df: pd.DataFrame):
        ds = manager.create_dataset(sample_df, name="query_test")
        retrieved = manager.get_dataset(ds["id"])
        assert retrieved is not None
        assert retrieved["id"] == ds["id"]
        assert retrieved["name"] == "query_test"

    def test_get_nonexistent_dataset(self, manager: SampleManager):
        assert manager.get_dataset("ds_fake") is None

    def test_list_datasets(self, manager: SampleManager, sample_df: pd.DataFrame):
        manager.create_dataset(sample_df, name="list_a")
        manager.create_dataset(sample_df, name="list_b")
        datasets = manager.list_datasets()
        assert len(datasets) >= 2
        names = [d["name"] for d in datasets]
        assert "list_a" in names
        assert "list_b" in names

    def test_list_datasets_returns_sanitized(
        self, manager: SampleManager, sample_df: pd.DataFrame
    ):
        ds = manager.create_dataset(sample_df, name="san_test")
        datasets = manager.list_datasets()
        found = [d for d in datasets if d["id"] == ds["id"]]
        assert len(found) == 1
        # Should have metadata but not raw 'df' key in the list output
        assert "metadata" in found[0]
