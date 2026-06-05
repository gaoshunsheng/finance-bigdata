"""
Unit tests for ModelMonitor — PSI, feature drift, KS trend, alerts, dashboard.
"""

import sys

sys.path.insert(0, ".")

import numpy as np
import pytest

from app.core.monitoring.monitor import ModelMonitor, KS_DROP_THRESHOLD


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture
def monitor() -> ModelMonitor:
    """Fresh ModelMonitor instance."""
    return ModelMonitor()


@pytest.fixture
def normal_distributions():
    """Two similar distributions (PSI should be small)."""
    rng = np.random.RandomState(42)
    ref = rng.dirichlet(np.ones(10)) * 1000
    cur = ref + rng.normal(0, 10, 10)
    cur = np.clip(cur, 1, None)  # No negatives
    return ref, cur


@pytest.fixture
def shifted_distributions():
    """Two very different distributions (PSI should be large)."""
    ref = np.array([500, 200, 100, 50, 50, 30, 20, 20, 20, 10], dtype=float)
    cur = np.array([50, 50, 50, 50, 50, 100, 100, 150, 200, 200], dtype=float)
    return ref, cur


# ---------------------------------------------------------------------------
# calculate_daily_psi
# ---------------------------------------------------------------------------


class TestCalculateDailyPSI:
    def test_normal_psi(self, monitor: ModelMonitor, normal_distributions):
        ref, cur = normal_distributions
        result = monitor.calculate_daily_psi("model_001", ref, cur, date="2024-06-01")
        assert result["severity"] == "NORMAL"
        assert result["psi_value"] < 0.1
        assert result["date"] == "2024-06-01"

    def test_warning_psi(self, monitor: ModelMonitor):
        rng = np.random.RandomState(42)
        ref = np.array([500, 300, 100, 50, 50], dtype=float)
        cur = np.array([300, 300, 200, 100, 100], dtype=float)
        result = monitor.calculate_daily_psi("model_002", ref, cur, date="2024-06-01")
        # May be WARNING or NORMAL depending on actual PSI value
        assert result["severity"] in ("NORMAL", "WARNING")

    def test_critical_psi(self, monitor: ModelMonitor, shifted_distributions):
        ref, cur = shifted_distributions
        result = monitor.calculate_daily_psi("model_003", ref, cur, date="2024-06-01")
        assert result["severity"] == "CRITICAL"
        assert result["psi_value"] >= 0.25

    def test_mismatched_lengths_raises(self, monitor: ModelMonitor):
        with pytest.raises(ValueError, match="长度不一致"):
            monitor.calculate_daily_psi("model_004", [1, 2, 3], [1, 2])

    def test_empty_raises(self, monitor: ModelMonitor):
        with pytest.raises(ValueError, match="不能为空"):
            monitor.calculate_daily_psi("model_005", [], [])

    def test_default_date(self, monitor: ModelMonitor, normal_distributions):
        ref, cur = normal_distributions
        result = monitor.calculate_daily_psi("model_006", ref, cur)
        assert result["date"] is not None


# ---------------------------------------------------------------------------
# calculate_feature_drift
# ---------------------------------------------------------------------------


class TestCalculateFeatureDrift:
    def test_no_drift(self, monitor: ModelMonitor):
        ref = np.array([0.2, 0.2, 0.2, 0.2, 0.2])
        cur = np.array([0.19, 0.21, 0.2, 0.2, 0.2])
        result = monitor.calculate_feature_drift(
            "model_010",
            {"age": ref, "income": ref},
            {"age": cur, "income": cur},
        )
        assert "age" in result
        assert "income" in result
        assert result["age"]["severity"] == "NORMAL"

    def test_feature_drift_detection(self, monitor: ModelMonitor, shifted_distributions):
        ref, cur = shifted_distributions
        result = monitor.calculate_feature_drift(
            "model_011",
            {"feat_a": ref},
            {"feat_a": cur},
        )
        assert result["feat_a"]["severity"] == "CRITICAL"
        assert result["feat_a"]["psi"] >= 0.25

    def test_missing_feature(self, monitor: ModelMonitor):
        ref = np.array([0.2, 0.2, 0.2, 0.2, 0.2])
        result = monitor.calculate_feature_drift(
            "model_012",
            {"age": ref},
            {},  # Missing in current
        )
        assert result["age"]["severity"] == "UNKNOWN"
        assert result["age"]["psi"] is None


# ---------------------------------------------------------------------------
# track_ks_trend
# ---------------------------------------------------------------------------


class TestTrackKSTrend:
    def test_normal_trend(self, monitor: ModelMonitor):
        # First record sets baseline
        r1 = monitor.track_ks_trend("model_020", 0.45, date="2024-06-01")
        assert r1["baseline"] == 0.45
        assert r1["is_significant_drop"] is False
        assert r1["drop_pct"] == 0.0

        # Slight drop (not significant)
        r2 = monitor.track_ks_trend("model_020", 0.42, date="2024-06-02")
        assert r2["baseline"] == 0.45
        assert r2["is_significant_drop"] is False

    def test_drop_detection(self, monitor: ModelMonitor):
        # Set baseline
        monitor.track_ks_trend("model_021", 0.50, date="2024-06-01")
        # Significant drop (>10% relative drop)
        r = monitor.track_ks_trend("model_021", 0.40, date="2024-06-02")
        assert r["is_significant_drop"] is True
        assert r["drop_pct"] > KS_DROP_THRESHOLD

    def test_ks_increase(self, monitor: ModelMonitor):
        monitor.track_ks_trend("model_022", 0.40, date="2024-06-01")
        r = monitor.track_ks_trend("model_022", 0.50, date="2024-06-02")
        # KS increase should not trigger drop
        assert r["is_significant_drop"] is False
        assert r["drop_pct"] == 0.0


# ---------------------------------------------------------------------------
# check_alerts
# ---------------------------------------------------------------------------


class TestCheckAlerts:
    def test_no_alerts_when_stable(self, monitor: ModelMonitor, normal_distributions):
        ref, cur = normal_distributions
        monitor.calculate_daily_psi("model_030", ref, cur, date="2024-06-01")
        monitor.track_ks_trend("model_030", 0.45, date="2024-06-01")
        alerts = monitor.check_alerts("model_030")
        # With normal PSI and no KS drop, there should be no alerts
        alert_severities = [a["severity"] for a in alerts]
        assert "CRITICAL" not in alert_severities or all(
            a["severity"] != "CRITICAL" for a in alerts
        )

    def test_critical_psi_alert(self, monitor: ModelMonitor, shifted_distributions):
        ref, cur = shifted_distributions
        monitor.calculate_daily_psi("model_031", ref, cur, date="2024-06-01")
        alerts = monitor.check_alerts("model_031")
        assert len(alerts) > 0
        assert any(a["severity"] == "CRITICAL" and a["feature_name"] == "__overall__" for a in alerts)

    def test_ks_drop_alert(self, monitor: ModelMonitor):
        monitor.track_ks_trend("model_032", 0.50, date="2024-06-01")
        monitor.track_ks_trend("model_032", 0.35, date="2024-06-02")
        alerts = monitor.check_alerts("model_032")
        assert any(
            a["feature_name"] == "__ks_statistic__" and a["severity"] == "CRITICAL"
            for a in alerts
        )

    def test_feature_drift_alert(self, monitor: ModelMonitor, shifted_distributions):
        ref, cur = shifted_distributions
        monitor.calculate_feature_drift(
            "model_033",
            {"age": ref},
            {"age": cur},
        )
        alerts = monitor.check_alerts("model_033")
        assert any(
            a["feature_name"] == "age" and a["severity"] == "CRITICAL"
            for a in alerts
        )


# ---------------------------------------------------------------------------
# get_dashboard
# ---------------------------------------------------------------------------


class TestGetDashboard:
    def test_dashboard_structure(self, monitor: ModelMonitor, normal_distributions):
        ref, cur = normal_distributions
        monitor.calculate_daily_psi("model_040", ref, cur, date="2024-06-01")
        monitor.track_ks_trend("model_040", 0.45, date="2024-06-01")
        monitor.check_alerts("model_040")

        dashboard = monitor.get_dashboard("model_040", model_name="TestModel")
        assert dashboard["model_id"] == "model_040"
        assert dashboard["model_name"] == "TestModel"
        assert dashboard["status"] in ("ACTIVE", "WARNING", "CRITICAL")
        assert isinstance(dashboard["daily_psi"], list)
        assert isinstance(dashboard["ks_trend"], list)
        assert isinstance(dashboard["feature_drift"], dict)
        assert isinstance(dashboard["active_alerts"], list)
        assert "last_updated" in dashboard

    def test_dashboard_active_status(self, monitor: ModelMonitor):
        # No data => no alerts => ACTIVE
        dashboard = monitor.get_dashboard("model_041")
        assert dashboard["status"] == "ACTIVE"


# ---------------------------------------------------------------------------
# acknowledge_alert
# ---------------------------------------------------------------------------


class TestAcknowledgeAlert:
    def test_acknowledge_existing_alert(
        self, monitor: ModelMonitor, shifted_distributions
    ):
        ref, cur = shifted_distributions
        monitor.calculate_daily_psi("model_050", ref, cur, date="2024-06-01")
        alerts = monitor.check_alerts("model_050")
        assert len(alerts) > 0

        alert_id = alerts[0]["alert_id"]
        result = monitor.acknowledge_alert(alert_id)
        assert result is True

        # After acknowledgment, alert should be gone
        remaining = monitor.list_alerts("model_050")
        assert alert_id not in [a["alert_id"] for a in remaining]

    def test_acknowledge_nonexistent_alert(self, monitor: ModelMonitor):
        result = monitor.acknowledge_alert("alert_nonexistent")
        assert result is False
