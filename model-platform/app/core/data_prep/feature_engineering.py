"""
特征工程模块 —— 提供 IV / WOE / PSI 计算及特征筛选全流程。

核心能力:
- calculate_iv:       单特征 IV 及 WOE 分箱
- iv_filter:          按 IV 阈值批量筛选特征
- correlation_filter: 基于相关性去除冗余特征（保留 IV 较高者）
- woe_binning:        自动 WOE 分箱
- calculate_psi:      计算群体稳定性指数
- run_feature_engineering: 一站式流水线 (IV筛选 → 相关性筛选)
"""

from __future__ import annotations

import warnings
from typing import Any, Optional

import numpy as np
import pandas as pd
from scipy import stats


class FeatureEngineer:
    """特征工程器，封装 IV、WOE、PSI 等评分卡常用特征分析方法。"""

    # ────────────────────────────────────────────
    # IV / WOE 计算
    # ────────────────────────────────────────────

    @staticmethod
    def calculate_iv(
        df: pd.DataFrame,
        feature: str,
        target: str,
        max_bins: int = 10,
    ) -> dict[str, Any]:
        """计算单个特征的 IV (Information Value) 及 WOE 分箱结果。

        Args:
            df:       包含特征列和目标列的 DataFrame
            feature:  特征列名
            target:   目标列名 (二分类 0/1)
            max_bins: 最大分箱数

        Returns:
            字典，包含:
            - feature_name: 特征名
            - iv_value:     IV 值
            - woe_bins:     各箱的统计信息 (bin边界、WOE、IV、好坏样本数)
            - selected:     是否达到 IV 阈值 (暂按 0.02 判断)

        Raises:
            ValueError: 特征或目标列不存在 / 目标非二分类
        """
        if feature not in df.columns:
            raise ValueError(f"特征列 '{feature}' 不存在")
        if target not in df.columns:
            raise ValueError(f"目标列 '{target}' 不存在")

        work = df[[feature, target]].dropna()
        if work.empty:
            return {
                "feature_name": feature,
                "iv_value": 0.0,
                "woe_bins": [],
                "selected": False,
            }

        # 校验目标列
        unique_targets = work[target].nunique()
        if unique_targets < 2:
            warnings.warn(f"特征 '{feature}' 对应目标列只有单一取值，IV 设为 0", stacklevel=2)
            return {
                "feature_name": feature,
                "iv_value": 0.0,
                "woe_bins": [],
                "selected": False,
            }

        n_total = len(work)
        n_good = int((work[target] == 0).sum())
        n_bad = int((work[target] == 1).sum())

        if n_good == 0 or n_bad == 0:
            warnings.warn(f"特征 '{feature}' 的正/负样本数为 0，IV 设为 0", stacklevel=2)
            return {
                "feature_name": feature,
                "iv_value": 0.0,
                "woe_bins": [],
                "selected": False,
            }

        col_data = work[feature]

        if pd.api.types.is_numeric_dtype(col_data):
            bins = FeatureEngineer._auto_numeric_bins(col_data, max_bins)
        else:
            bins = FeatureEngineer._auto_category_bins(col_data, max_bins)

        woe_bins = FeatureEngineer._compute_woe_bins(
            work, feature, target, bins, n_good, n_bad
        )

        # 汇总 IV
        total_iv = sum(b["iv"] for b in woe_bins)

        return {
            "feature_name": feature,
            "iv_value": round(total_iv, 6),
            "woe_bins": woe_bins,
            "selected": total_iv >= 0.02,
        }

    # ────────────────────────────────────────────
    # IV 批量筛选
    # ────────────────────────────────────────────

    def iv_filter(
        self,
        df: pd.DataFrame,
        target: str,
        iv_threshold: float = 0.02,
        max_bins: int = 10,
    ) -> list[tuple[str, float, bool]]:
        """对全部数值 / 类别特征执行 IV 计算，按阈值筛选。

        Args:
            df:           数据集
            target:       目标列名
            iv_threshold: IV 阈值，低于此值的特征被剔除
            max_bins:     最大分箱数

        Returns:
            [(feature_name, iv_value, selected), ...] 按IV降序排列
        """
        feature_cols = self._get_feature_columns(df, target)
        results: list[tuple[str, float, bool]] = []

        for col in feature_cols:
            try:
                iv_result = self.calculate_iv(df, col, target, max_bins)
                iv_val = iv_result["iv_value"]
                selected = iv_val >= iv_threshold
                results.append((col, iv_val, selected))
            except Exception as exc:
                warnings.warn(f"特征 '{col}' IV 计算失败: {exc}", stacklevel=2)
                results.append((col, 0.0, False))

        results.sort(key=lambda x: x[1], reverse=True)
        return results

    # ────────────────────────────────────────────
    # 相关性筛选
    # ────────────────────────────────────────────

    def correlation_filter(
        self,
        df: pd.DataFrame,
        features: list[str],
        iv_results: Optional[list[tuple[str, float, bool]]] = None,
        threshold: float = 0.7,
    ) -> dict[str, Any]:
        """去除高相关特征，保留 IV 较高者。

        算法:
        1. 计算特征间 Pearson 相关系数矩阵
        2. 找到相关系数绝对值 > threshold 的特征对
        3. 对于每对高相关特征，移除 IV 较低的那个

        Args:
            df:         数据集
            features:   候选特征列表
            iv_results: IV 计算结果 [(name, iv, selected)]，用于决定保留哪个
            threshold:  相关系数阈值

        Returns:
            {
                "selected": [保留的特征],
                "removed":  [{"feature": name, "correlated_with": name, "corr": value}, ...],
            }
        """
        if not features:
            return {"selected": [], "removed": []}

        # 建立 IV 字典
        iv_map: dict[str, float] = {}
        if iv_results is not None:
            iv_map = {name: iv for name, iv, _ in iv_results}

        numeric_features = [f for f in features if pd.api.types.is_numeric_dtype(df[f])]
        if len(numeric_features) <= 1:
            return {"selected": features, "removed": []}

        corr_matrix = df[numeric_features].corr(method="pearson").abs()

        # 贪心去相关
        selected = set(numeric_features)
        removed: list[dict[str, Any]] = []

        # 按 IV 降序排列，IV 高的优先保留
        sorted_features = sorted(
            numeric_features,
            key=lambda f: iv_map.get(f, 0.0),
            reverse=True,
        )

        for i, feat_high in enumerate(sorted_features):
            if feat_high not in selected:
                continue
            for feat_low in sorted_features[i + 1:]:
                if feat_low not in selected:
                    continue
                if feat_high not in corr_matrix.columns or feat_low not in corr_matrix.columns:
                    continue
                corr_val = corr_matrix.loc[feat_high, feat_low]
                if np.isnan(corr_val):
                    continue
                if corr_val > threshold:
                    # 移除 IV 较低的特征
                    selected.discard(feat_low)
                    removed.append({
                        "feature": feat_low,
                        "correlated_with": feat_high,
                        "corr": round(float(corr_val), 6),
                    })

        return {
            "selected": list(selected),
            "removed": removed,
        }

    # ────────────────────────────────────────────
    # WOE 分箱
    # ────────────────────────────────────────────

    @staticmethod
    def woe_binning(
        df: pd.DataFrame,
        feature: str,
        target: str,
        max_bins: int = 10,
    ) -> dict[str, Any]:
        """对单个特征进行自动 WOE 分箱。

        Returns:
            {
                "feature":    特征名,
                "bin_type":   "numeric" | "category",
                "bins": [
                    {
                        "bin":       分箱标签/范围,
                        "boundaries": (left, right) 或类别列表,
                        "woe":       WOE 值,
                        "iv":        该箱 IV,
                        "count":     样本数,
                        "bad_rate":  坏样本率,
                    },
                    ...
                ],
            }
        """
        work = df[[feature, target]].dropna()
        if work.empty:
            return {
                "feature": feature,
                "bin_type": "unknown",
                "bins": [],
            }

        col_data = work[feature]
        n_good = int((work[target] == 0).sum())
        n_bad = int((work[target] == 1).sum())

        if n_good == 0 or n_bad == 0:
            return {
                "feature": feature,
                "bin_type": "unknown",
                "bins": [],
            }

        if pd.api.types.is_numeric_dtype(col_data):
            bin_type = "numeric"
            bins = FeatureEngineer._auto_numeric_bins(col_data, max_bins)
        else:
            bin_type = "category"
            bins = FeatureEngineer._auto_category_bins(col_data, max_bins)

        woe_bins = FeatureEngineer._compute_woe_bins(
            work, feature, target, bins, n_good, n_bad
        )

        return {
            "feature": feature,
            "bin_type": bin_type,
            "bins": woe_bins,
        }

    # ────────────────────────────────────────────
    # PSI 计算
    # ────────────────────────────────────────────

    @staticmethod
    def calculate_psi(
        expected: np.ndarray | pd.Series,
        actual: np.ndarray | pd.Series,
        bins: int = 10,
    ) -> float:
        """计算群体稳定性指数 (Population Stability Index)。

        PSI 衡量两个分布之间的差异，常用于监控模型特征漂移。

        参考判别标准:
        - PSI < 0.1:    分布稳定
        - 0.1 <= PSI < 0.25: 需要关注
        - PSI >= 0.25:  分布显著变化

        Args:
            expected: 期望分布 (训练集 / 基准数据)
            actual:   实际分布 (线上 / 新数据)
            bins:     分箱数

        Returns:
            PSI 值
        """
        exp_arr = np.asarray(expected, dtype=float)
        act_arr = np.asarray(actual, dtype=float)

        # 去除 NaN
        exp_arr = exp_arr[~np.isnan(exp_arr)]
        act_arr = act_arr[~np.isnan(act_arr)]

        if len(exp_arr) == 0 or len(act_arr) == 0:
            return 0.0

        # 基于期望分布的分位数切分
        breakpoints = np.arange(0, bins + 1) / bins * 100
        bin_edges = np.percentile(exp_arr, breakpoints)
        # 去重并排序（避免常量列导致的重复边界）
        bin_edges = np.unique(bin_edges)

        if len(bin_edges) <= 1:
            # 常量列
            return 0.0

        # 确保首尾覆盖
        bin_edges[0] = -np.inf
        bin_edges[-1] = np.inf

        exp_counts, _ = np.histogram(exp_arr, bins=bin_edges)
        act_counts, _ = np.histogram(act_arr, bins=bin_edges)

        exp_pct = exp_counts / len(exp_arr)
        act_pct = act_counts / len(act_arr)

        # 避免 log(0): 添加极小值
        eps = 1e-6
        exp_pct = np.clip(exp_pct, eps, None)
        act_pct = np.clip(act_pct, eps, None)

        psi = float(np.sum((act_pct - exp_pct) * np.log(act_pct / exp_pct)))
        return round(psi, 6)

    # ────────────────────────────────────────────
    # 全流程流水线
    # ────────────────────────────────────────────

    def run_feature_engineering(
        self,
        df: pd.DataFrame,
        target: str,
        iv_threshold: float = 0.02,
        corr_threshold: float = 0.7,
        max_bins: int = 10,
        dataset_id: str = "",
    ) -> dict[str, Any]:
        """执行完整的特征工程流水线: IV 筛选 → 相关性筛选 → PSI 计算。

        Args:
            df:              包含特征与目标列的 DataFrame
            target:          目标列名
            iv_threshold:    IV 最低阈值
            corr_threshold:  相关系数最高阈值
            max_bins:        最大分箱数
            dataset_id:      关联的数据集 ID

        Returns:
            字典，结构同 schemas.FeatureEngineeringResult:
            {
                "dataset_id":             str,
                "total_features":         int,
                "selected_features":      [str],
                "iv_results":             [{feature_name, iv_value, woe_bins, selected}],
                "removed_by_iv":          [str],
                "removed_by_correlation": [str],
                "psi_scores":             {feature: psi},
            }
        """
        feature_cols = self._get_feature_columns(df, target)
        total_features = len(feature_cols)

        # ── Step 1: IV 筛选 ──
        iv_results_raw = self.iv_filter(df, target, iv_threshold, max_bins)

        # 保留 IV 达标的特征
        iv_passed = [name for name, iv, selected in iv_results_raw if selected]
        removed_by_iv = [name for name, iv, selected in iv_results_raw if not selected]

        # 构造详细 IV 结果
        iv_results: list[dict[str, Any]] = []
        for name, iv_val, selected in iv_results_raw:
            try:
                detail = self.calculate_iv(df, name, target, max_bins)
                iv_results.append({
                    "feature_name": name,
                    "iv_value": iv_val,
                    "woe_bins": detail.get("woe_bins", []),
                    "selected": selected,
                })
            except Exception:
                iv_results.append({
                    "feature_name": name,
                    "iv_value": iv_val,
                    "woe_bins": [],
                    "selected": selected,
                })

        # ── Step 2: 相关性筛选 ──
        corr_result = self.correlation_filter(
            df, iv_passed, iv_results_raw, corr_threshold
        )
        final_features = corr_result["selected"]
        removed_by_corr = corr_result["removed"]

        # ── Step 3: PSI 计算（如果有训练 / 测试拆分则可在调用方传入） ──
        # 此处对全量数据做自比较作为基准 PSI (≈0)
        psi_scores: dict[str, float] = {}
        for feat in final_features:
            if pd.api.types.is_numeric_dtype(df[feat]):
                psi_val = self.calculate_psi(df[feat], df[feat])
                psi_scores[feat] = psi_val

        return {
            "dataset_id": dataset_id,
            "total_features": total_features,
            "selected_features": final_features,
            "iv_results": iv_results,
            "removed_by_iv": removed_by_iv,
            "removed_by_correlation": [r["feature"] for r in removed_by_corr],
            "psi_scores": psi_scores,
        }

    # ────────────────────────────────────────────
    # 私有辅助方法
    # ────────────────────────────────────────────

    @staticmethod
    def _get_feature_columns(df: pd.DataFrame, target: str) -> list[str]:
        """获取所有可用作特征的列（排除目标列）。"""
        exclude = {target}
        return [c for c in df.columns if c not in exclude]

    @staticmethod
    def _auto_numeric_bins(
        data: pd.Series, max_bins: int
    ) -> list[dict[str, Any]]:
        """对数值列自动等频分箱。

        Returns:
            [{"type": "numeric", "left": float, "right": float}, ...]
        """
        data = data.dropna()
        if data.nunique() <= 1:
            # 常量列 —— 只有一个箱
            return [{"type": "numeric", "left": -np.inf, "right": np.inf}]

        # 尝试等频分箱
        actual_bins = min(max_bins, data.nunique())
        try:
            binned = pd.qcut(data, q=actual_bins, duplicates="drop")
        except ValueError:
            # 回退: 等宽分箱
            try:
                binned = pd.cut(data, bins=actual_bins, duplicates="drop")
            except ValueError:
                return [{"type": "numeric", "left": -np.inf, "right": np.inf}]

        # 提取唯一区间 —— qcut/cut 可能返回 Series（非 Categorical）
        if hasattr(binned, "cat") and hasattr(binned.cat, "categories"):
            categories = binned.cat.categories
        elif hasattr(binned, "dtype") and hasattr(binned.dtype, "categories"):
            categories = binned.dtype.categories
        else:
            # 回退: 通过 np.histogram 自行分箱
            hist_bins = np.histogram_bin_edges(data.dropna(), bins=actual_bins)
            bins: list[dict[str, Any]] = []
            for i in range(len(hist_bins) - 1):
                bins.append({"type": "numeric", "left": float(hist_bins[i]), "right": float(hist_bins[i + 1])})
            if bins:
                bins[0]["left"] = -np.inf
                bins[-1]["right"] = np.inf
            return bins if bins else [{"type": "numeric", "left": -np.inf, "right": np.inf}]

        seen: set[tuple[float, float]] = set()
        bins = []
        for interval in categories:
            left = float(interval.left)
            right = float(interval.right)
            key = (left, right)
            if key not in seen:
                seen.add(key)
                bins.append({"type": "numeric", "left": left, "right": right})

        if not bins:
            return [{"type": "numeric", "left": -np.inf, "right": np.inf}]

        # 确保首尾覆盖
        bins[0]["left"] = -np.inf
        bins[-1]["right"] = np.inf

        return bins

    @staticmethod
    def _auto_category_bins(
        data: pd.Series, max_bins: int
    ) -> list[dict[str, Any]]:
        """对类别列自动分箱。

        高基数时按频率取 top-N，其余归入 "OTHER"。

        Returns:
            [{"type": "category", "values": [str, ...]}, ...]
        """
        value_counts = data.value_counts()

        if len(value_counts) <= max_bins:
            return [
                {"type": "category", "values": [str(val)]}
                for val in value_counts.index
            ]

        # 保留 top-(max_bins - 1)，其余合并
        top_values = value_counts.head(max_bins - 1).index.tolist()
        other_values = value_counts.iloc[max_bins - 1:].index.tolist()

        bins = [
            {"type": "category", "values": [str(v)]} for v in top_values
        ]
        bins.append({"type": "category", "values": [str(v) for v in other_values]})
        return bins

    @staticmethod
    def _compute_woe_bins(
        df: pd.DataFrame,
        feature: str,
        target: str,
        bins: list[dict[str, Any]],
        n_good: int,
        n_bad: int,
    ) -> list[dict[str, Any]]:
        """根据已划分的 bins 计算每个 bin 的 WOE 和 IV。

        Args:
            df:      原始数据
            feature: 特征列
            target:  目标列
            bins:    分箱定义
            n_good:  好样本总数
            n_bad:   坏样本总数
        """
        eps = 0.5  # 平滑因子，避免除零
        col_data = df[feature]
        target_data = df[target]
        woe_bins: list[dict[str, Any]] = []

        for bin_def in bins:
            if bin_def["type"] == "numeric":
                mask = (col_data > bin_def["left"]) & (col_data <= bin_def["right"])
                bin_label = f"({bin_def['left']:.4f}, {bin_def['right']:.4f}]"
            else:
                mask = col_data.astype(str).isin(bin_def["values"])
                bin_label = ", ".join(bin_def["values"]) if len(bin_def["values"]) <= 3 else f"{bin_def['values'][0]} 等 {len(bin_def['values'])} 项"

            bin_good = int((mask & (target_data == 0)).sum())
            bin_bad = int((mask & (target_data == 1)).sum())
            bin_total = int(mask.sum())

            if bin_total == 0:
                woe_bins.append({
                    "bin": bin_label,
                    "count": 0,
                    "good_count": 0,
                    "bad_count": 0,
                    "bad_rate": 0.0,
                    "woe": 0.0,
                    "iv": 0.0,
                })
                continue

            pct_good = (bin_good + eps) / (n_good + 2 * eps)
            pct_bad = (bin_bad + eps) / (n_bad + 2 * eps)

            woe = float(np.log(pct_good / pct_bad))
            iv = float((pct_good - pct_bad) * woe)

            woe_bins.append({
                "bin": bin_label,
                "count": bin_total,
                "good_count": bin_good,
                "bad_count": bin_bad,
                "bad_rate": round(bin_bad / bin_total, 6),
                "woe": round(woe, 6),
                "iv": round(iv, 6),
            })

        return woe_bins


# 全局单例
feature_engineer = FeatureEngineer()
