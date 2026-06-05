"""
模型监控模块 —— 负责模型上线后的稳定性监控，包括 PSI 计算、特征漂移检测、KS 趋势追踪和告警管理。

核心能力:
- calculate_daily_psi:      计算训练集与生产环境评分分布之间的 PSI
- calculate_feature_drift:  逐特征计算 PSI，检测特征漂移
- track_ks_trend:           追踪 KS 统计量随时间的趋势
- check_alerts:             综合评估并生成告警
- get_dashboard:            汇总监控面板数据

数据目前以内存字典存储，后续可替换为持久化方案。
"""

from __future__ import annotations

import uuid
from collections import defaultdict
from datetime import datetime
from typing import Any, Optional

import numpy as np

from app.config import PSI_CRITICAL_THRESHOLD, PSI_WARNING_THRESHOLD

# KS 统计量下降告警阈值（相对基线下降比例）
KS_DROP_THRESHOLD = 0.1


class ModelMonitor:
    """模型监控器，提供 PSI 计算、特征漂移检测、KS 趋势追踪和告警管理能力。"""

    def __init__(self) -> None:
        # model_id -> 监控数据
        self._monitoring_data: dict[str, dict[str, Any]] = defaultdict(
            lambda: {
                "psi_history": [],       # list[dict]: {date, psi_value, severity}
                "ks_history": [],        # list[dict]: {date, ks_value}
                "ks_baseline": None,     # float: KS 基线值（首次记录时设定）
                "feature_drift": {},     # dict[str, float]: feature_name -> psi
                "alerts": [],            # list[dict]: 告警列表
            }
        )

    # ────────────────────────────────────────────
    # PSI 计算
    # ────────────────────────────────────────────

    def calculate_daily_psi(
        self,
        model_id: str,
        reference_dist: np.ndarray | list[float],
        current_dist: np.ndarray | list[float],
        date: Optional[str] = None,
    ) -> dict[str, Any]:
        """计算训练集（reference）与生产环境（current）评分分布之间的 PSI。

        PSI（Population Stability Index）衡量两个分布之间的差异:
        - PSI < 0.1:  分布稳定，无需关注
        - 0.1 <= PSI < 0.25: 分布有轻微偏移，需要关注
        - PSI >= 0.25: 分布显著偏移，需要立即处理

        Args:
            model_id:       模型 ID
            reference_dist: 训练集评分分布（概率密度或频率，需与 current_dist 等长）
            current_dist:   生产环境评分分布（概率密度或频率，需与 reference_dist 等长）
            date:           监控日期，格式 yyyy-MM-dd，默认为当天

        Returns:
            字典包含:
            - date:      监控日期
            - psi_value: PSI 数值
            - severity:  严重等级 (NORMAL / WARNING / CRITICAL)

        Raises:
            ValueError: 两个分布长度不一致或包含非法值
        """
        ref = np.asarray(reference_dist, dtype=np.float64)
        cur = np.asarray(current_dist, dtype=np.float64)

        if len(ref) != len(cur):
            raise ValueError(
                f"参考分布与当前分布长度不一致: reference={len(ref)}, current={len(cur)}"
            )
        if len(ref) == 0:
            raise ValueError("分布不能为空")

        psi_value = self._compute_psi(ref, cur)

        if date is None:
            date = datetime.now().strftime("%Y-%m-%d")

        if psi_value >= PSI_CRITICAL_THRESHOLD:
            severity = "CRITICAL"
        elif psi_value >= PSI_WARNING_THRESHOLD:
            severity = "WARNING"
        else:
            severity = "NORMAL"

        result: dict[str, Any] = {
            "date": date,
            "psi_value": round(float(psi_value), 6),
            "severity": severity,
        }

        # 存入监控历史
        self._monitoring_data[model_id]["psi_history"].append(result)

        return result

    # ────────────────────────────────────────────
    # 特征漂移检测
    # ────────────────────────────────────────────

    def calculate_feature_drift(
        self,
        model_id: str,
        reference_features: dict[str, np.ndarray | list[float]],
        current_features: dict[str, np.ndarray | list[float]],
    ) -> dict[str, dict[str, Any]]:
        """逐特征计算 PSI，检测各特征的分布漂移。

        对每个特征分别计算训练集与生产环境分布之间的 PSI。
        PSI > 0.25 的特征标记为 CRITICAL。

        Args:
            model_id:           模型 ID
            reference_features: 训练集各特征分布 {feature_name: distribution}
            current_features:   生产环境各特征分布 {feature_name: distribution}

        Returns:
            字典: feature_name -> {psi, severity}
            例如: {"age": {"psi": 0.05, "severity": "NORMAL"}, ...}

        Raises:
            ValueError: 特征在参考集与当前集中均缺失
        """
        all_features = set(reference_features.keys()) | set(current_features.keys())
        drift_result: dict[str, dict[str, Any]] = {}

        for feat_name in sorted(all_features):
            ref_arr = reference_features.get(feat_name)
            cur_arr = current_features.get(feat_name)

            if ref_arr is None or cur_arr is None:
                drift_result[feat_name] = {
                    "psi": None,
                    "severity": "UNKNOWN",
                    "message": (
                        f"特征 '{feat_name}' 在"
                        f"{'参考集' if ref_arr is None else '当前集'}中缺失"
                    ),
                }
                continue

            ref = np.asarray(ref_arr, dtype=np.float64)
            cur = np.asarray(cur_arr, dtype=np.float64)

            if len(ref) != len(cur) or len(ref) == 0:
                drift_result[feat_name] = {
                    "psi": None,
                    "severity": "UNKNOWN",
                    "message": "分布长度不一致或为空",
                }
                continue

            psi_val = self._compute_psi(ref, cur)

            severity = "CRITICAL" if psi_val >= PSI_CRITICAL_THRESHOLD else "NORMAL"

            drift_result[feat_name] = {
                "psi": round(float(psi_val), 6),
                "severity": severity,
            }

        # 更新监控数据中的特征漂移摘要（仅保留 psi 数值用于面板展示）
        psi_summary: dict[str, float] = {}
        for feat_name, info in drift_result.items():
            if info["psi"] is not None:
                psi_summary[feat_name] = info["psi"]
        self._monitoring_data[model_id]["feature_drift"] = psi_summary

        return drift_result

    # ────────────────────────────────────────────
    # KS 趋势追踪
    # ────────────────────────────────────────────

    def track_ks_trend(
        self,
        model_id: str,
        ks_value: float,
        date: Optional[str] = None,
    ) -> dict[str, Any]:
        """追踪 KS 统计量随时间的趋势变化。

        首次记录的 KS 值自动设为基线（baseline）。
        后续记录时，若 KS 值相对基线下降超过 KS_DROP_THRESHOLD (10%)，
        则标记为显著下降。

        Args:
            model_id:  模型 ID
            ks_value:  当日 KS 统计量
            date:      监控日期，格式 yyyy-MM-dd，默认为当天

        Returns:
            字典包含:
            - date:         监控日期
            - ks_value:     KS 数值
            - baseline:     基线 KS 值
            - drop_pct:     相对基线下降百分比（0 表示未下降）
            - is_significant_drop: 是否为显著下降
        """
        if date is None:
            date = datetime.now().strftime("%Y-%m-%d")

        data = self._monitoring_data[model_id]

        # 设定或保持基线
        if data["ks_baseline"] is None:
            data["ks_baseline"] = ks_value

        baseline = data["ks_baseline"]

        # 计算下降百分比
        if baseline > 0:
            drop_pct = round((baseline - ks_value) / baseline, 4)
        else:
            drop_pct = 0.0
        drop_pct = max(drop_pct, 0.0)  # 不处理上升的情况

        is_significant = drop_pct > KS_DROP_THRESHOLD

        record: dict[str, Any] = {
            "date": date,
            "ks_value": round(float(ks_value), 6),
            "baseline": round(float(baseline), 6),
            "drop_pct": round(float(drop_pct), 4),
            "is_significant_drop": is_significant,
        }

        data["ks_history"].append(record)

        return record

    # ────────────────────────────────────────────
    # 告警管理
    # ────────────────────────────────────────────

    def check_alerts(self, model_id: str) -> list[dict[str, Any]]:
        """综合评估模型的所有监控指标并生成告警。

        检查规则:
        1. PSI > 0.25  → CRITICAL 告警
        2. PSI > 0.1   → WARNING 告警
        3. KS 下降 > 10%（相对基线）→ CRITICAL 告警
        4. 特征漂移 PSI > 0.25 → CRITICAL 告警

        每次调用会清理该模型的旧告警，重新生成当前状态下的告警列表。

        Args:
            model_id: 模型 ID

        Returns:
            告警字典列表，每条告警包含:
            - alert_id, model_id, feature_name, psi_value, severity, message, detected_at
        """
        data = self._monitoring_data[model_id]
        new_alerts: list[dict[str, Any]] = []
        now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        # 1. 检查 PSI 历史记录
        psi_history = data["psi_history"]
        if psi_history:
            latest_psi = psi_history[-1]
            psi_val = latest_psi["psi_value"]
            psi_date = latest_psi["date"]

            if psi_val >= PSI_CRITICAL_THRESHOLD:
                new_alerts.append({
                    "alert_id": self._generate_alert_id(),
                    "model_id": model_id,
                    "feature_name": "__overall__",
                    "psi_value": psi_val,
                    "severity": "CRITICAL",
                    "message": (
                        f"模型整体 PSI={psi_val:.4f} >= {PSI_CRITICAL_THRESHOLD}，"
                        f"评分分布发生显著偏移（日期: {psi_date}）"
                    ),
                    "detected_at": now_str,
                })
            elif psi_val >= PSI_WARNING_THRESHOLD:
                new_alerts.append({
                    "alert_id": self._generate_alert_id(),
                    "model_id": model_id,
                    "feature_name": "__overall__",
                    "psi_value": psi_val,
                    "severity": "WARNING",
                    "message": (
                        f"模型整体 PSI={psi_val:.4f} >= {PSI_WARNING_THRESHOLD}，"
                        f"评分分布存在轻微偏移，请关注（日期: {psi_date}）"
                    ),
                    "detected_at": now_str,
                })

        # 2. 检查 KS 趋势
        ks_history = data["ks_history"]
        if ks_history:
            latest_ks = ks_history[-1]
            if latest_ks.get("is_significant_drop", False):
                drop_pct = latest_ks["drop_pct"]
                ks_val = latest_ks["ks_value"]
                baseline = latest_ks["baseline"]
                new_alerts.append({
                    "alert_id": self._generate_alert_id(),
                    "model_id": model_id,
                    "feature_name": "__ks_statistic__",
                    "psi_value": 0.0,
                    "severity": "CRITICAL",
                    "message": (
                        f"KS 统计量从基线 {baseline:.4f} 下降至 {ks_val:.4f}，"
                        f"降幅 {drop_pct * 100:.1f}% 超过阈值 {KS_DROP_THRESHOLD * 100:.0f}%，"
                        f"模型区分能力显著衰退"
                    ),
                    "detected_at": now_str,
                })

        # 3. 检查特征漂移
        feature_drift = data["feature_drift"]
        for feat_name, drift_psi in feature_drift.items():
            if drift_psi >= PSI_CRITICAL_THRESHOLD:
                new_alerts.append({
                    "alert_id": self._generate_alert_id(),
                    "model_id": model_id,
                    "feature_name": feat_name,
                    "psi_value": drift_psi,
                    "severity": "CRITICAL",
                    "message": (
                        f"特征 '{feat_name}' PSI={drift_psi:.4f} >= {PSI_CRITICAL_THRESHOLD}，"
                        f"特征分布发生显著漂移，建议排查数据源或重新训练模型"
                    ),
                    "detected_at": now_str,
                })

        # 更新告警列表（替换旧告警）
        data["alerts"] = new_alerts

        return new_alerts

    def acknowledge_alert(self, alert_id: str) -> bool:
        """确认告警，将其从活跃列表中移除。

        Args:
            alert_id: 告警 ID

        Returns:
            是否成功确认（True 表示找到并移除，False 表示告警不存在）
        """
        for model_id, data in self._monitoring_data.items():
            alerts = data["alerts"]
            for i, alert in enumerate(alerts):
                if alert["alert_id"] == alert_id:
                    alerts.pop(i)
                    return True
        return False

    def list_alerts(self, model_id: Optional[str] = None) -> list[dict[str, Any]]:
        """列出告警信息，可按模型 ID 过滤。

        Args:
            model_id: 可选，指定模型 ID 则只返回该模型的告警

        Returns:
            告警字典列表
        """
        if model_id is not None:
            data = self._monitoring_data.get(model_id)
            if data is None:
                return []
            return list(data["alerts"])

        # 汇总所有模型的告警
        all_alerts: list[dict[str, Any]] = []
        for mid, data in self._monitoring_data.items():
            all_alerts.extend(data["alerts"])
        return all_alerts

    # ────────────────────────────────────────────
    # 监控面板
    # ────────────────────────────────────────────

    def get_dashboard(
        self,
        model_id: str,
        model_name: Optional[str] = None,
    ) -> dict[str, Any]:
        """汇总模型的监控面板数据。

        返回字典结构符合 MonitoringDashboard schema:
        - model_id:       模型 ID
        - model_name:     模型名称
        - status:         模型状态 (ACTIVE / WARNING / CRITICAL)
        - daily_psi:      PSI 历史趋势
        - ks_trend:       KS 趋势
        - feature_drift:  特征漂移 PSI 汇总
        - active_alerts:  当前活跃告警
        - last_updated:   最后更新时间

        Args:
            model_id:   模型 ID
            model_name: 模型名称（可选，用于展示）

        Returns:
            监控面板字典
        """
        data = self._monitoring_data[model_id]

        # 判断模型状态
        has_critical = any(
            alert["severity"] == "CRITICAL" for alert in data["alerts"]
        )
        has_warning = any(
            alert["severity"] == "WARNING" for alert in data["alerts"]
        )

        if has_critical:
            status = "CRITICAL"
        elif has_warning:
            status = "WARNING"
        else:
            status = "ACTIVE"

        dashboard: dict[str, Any] = {
            "model_id": model_id,
            "model_name": model_name or model_id,
            "status": status,
            "daily_psi": list(data["psi_history"]),
            "ks_trend": [
                {"date": ks["date"], "ks_value": ks["ks_value"]}
                for ks in data["ks_history"]
            ],
            "feature_drift": dict(data["feature_drift"]),
            "active_alerts": list(data["alerts"]),
            "last_updated": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        }

        return dashboard

    # ────────────────────────────────────────────
    # 私有方法
    # ────────────────────────────────────────────

    @staticmethod
    def _compute_psi(
        reference: np.ndarray, current: np.ndarray
    ) -> float:
        """计算 PSI（Population Stability Index）。

        公式: PSI = sum((current_i - reference_i) * ln(current_i / reference_i))

        为避免除零和对数计算异常，对零值添加极小偏移量 epsilon。

        Args:
            reference: 参考分布（训练集），概率密度或频率
            current:   当前分布（生产环境），概率密度或频率

        Returns:
            PSI 数值
        """
        epsilon = 1e-8

        # 确保分布归一化
        ref_norm = reference / (reference.sum() + epsilon)
        cur_norm = current / (current.sum() + epsilon)

        # 防止零值
        ref_norm = np.clip(ref_norm, epsilon, None)
        cur_norm = np.clip(cur_norm, epsilon, None)

        # PSI = sum((cur - ref) * ln(cur / ref))
        psi = float(np.sum((cur_norm - ref_norm) * np.log(cur_norm / ref_norm)))

        return max(psi, 0.0)

    @staticmethod
    def _generate_alert_id() -> str:
        """生成告警唯一标识。"""
        return f"alert_{uuid.uuid4().hex[:12]}"


# 全局单例
model_monitor = ModelMonitor()
