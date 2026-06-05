"""
模型评估模块

提供信用风险模型的全面评估功能，包括：
- AUC / KS / Gini 等核心指标计算
- 混淆矩阵、Lift、PSI、VIF 等辅助指标
- KS 曲线、ROC 曲线、评分分布等可视化数据生成
- 评估报告缓存与查询
"""

from datetime import datetime
from typing import Any, Optional

import numpy as np
import pandas as pd
from sklearn.metrics import (
    accuracy_score,
    auc,
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
    roc_curve,
)


class ModelEvaluator:
    """
    模型评估器

    对训练好的信用风险模型进行全面的评估分析，生成包含
    各类指标和可视化数据的评估报告。报告按 model_id 缓存
    在内存中，支持后续查询。
    """

    def __init__(self) -> None:
        # 内存中的报告缓存：model_id -> evaluation report dict
        self._reports: dict[str, dict[str, Any]] = {}

    # ──────────────────────────────────────────────
    # 核心指标计算
    # ──────────────────────────────────────────────

    @staticmethod
    def calculate_ks(y_true: np.ndarray | list, y_prob: np.ndarray | list) -> tuple[float, float]:
        """
        计算 KS (Kolmogorov-Smirnov) 统计量

        通过对正负样本的累积分布做差，找到最大差值点作为 KS 值，
        同时返回该最大差值对应的概率阈值（即 KS 切分点）。

        Args:
            y_true: 真实标签数组 (0/1)
            y_prob: 预测为正类的概率数组

        Returns:
            (ks_value, ks_threshold):
                ks_value   - KS 统计量值
                ks_threshold - KS 最大差值处的概率阈值
        """
        y_true = np.asarray(y_true, dtype=np.float64)
        y_prob = np.asarray(y_prob, dtype=np.float64)

        if len(y_true) == 0 or len(y_prob) == 0:
            return 0.0, 0.5

        # 检查是否只有单一类别
        unique_labels = np.unique(y_true)
        if len(unique_labels) < 2:
            return 0.0, 0.5

        positive_mask = y_true == 1
        negative_mask = y_true == 0

        pos_probs = y_prob[positive_mask]
        neg_probs = y_prob[negative_mask]

        if len(pos_probs) == 0 or len(neg_probs) == 0:
            return 0.0, 0.5

        # 合并所有概率值作为阈值候选
        thresholds = np.sort(np.unique(y_prob))
        n_pos = len(pos_probs)
        n_neg = len(neg_probs)

        ks_value = 0.0
        ks_threshold = 0.5

        for thr in thresholds:
            cdf_pos = np.sum(pos_probs <= thr) / n_pos
            cdf_neg = np.sum(neg_probs <= thr) / n_neg
            diff = abs(cdf_pos - cdf_neg)
            if diff > ks_value:
                ks_value = diff
                ks_threshold = float(thr)

        return float(ks_value), float(ks_threshold)

    @staticmethod
    def calculate_auc(y_true: np.ndarray | list, y_prob: np.ndarray | list) -> float:
        """
        计算 AUC (Area Under the ROC Curve)

        使用 sklearn.metrics.roc_auc_score 计算 ROC 曲线下面积。

        Args:
            y_true: 真实标签数组 (0/1)
            y_prob: 预测为正类的概率数组

        Returns:
            auc_value: AUC 值，范围 [0, 1]
        """
        y_true = np.asarray(y_true, dtype=np.float64)
        y_prob = np.asarray(y_prob, dtype=np.float64)

        if len(y_true) == 0 or len(y_prob) == 0:
            return 0.0

        unique_labels = np.unique(y_true)
        if len(unique_labels) < 2:
            return 0.0

        try:
            return float(roc_auc_score(y_true, y_prob))
        except ValueError:
            return 0.0

    @staticmethod
    def calculate_gini(auc_value: float) -> float:
        """
        计算 Gini 系数

        Gini = 2 * AUC - 1

        Args:
            auc_value: AUC 值

        Returns:
            gini_value: Gini 系数
        """
        return float(2.0 * auc_value - 1.0)

    @staticmethod
    def calculate_confusion_matrix(
        y_true: np.ndarray | list,
        y_pred: np.ndarray | list,
    ) -> list[list[int]]:
        """
        计算混淆矩阵 (二分类)

        Args:
            y_true: 真实标签数组 (0/1)
            y_pred: 预测标签数组 (0/1)

        Returns:
            2x2 混淆矩阵，格式为 [[TN, FP], [FN, TP]]
        """
        y_true = np.asarray(y_true, dtype=np.int64)
        y_pred = np.asarray(y_pred, dtype=np.int64)

        if len(y_true) == 0 or len(y_pred) == 0:
            return [[0, 0], [0, 0]]

        cm = confusion_matrix(y_true, y_pred, labels=[0, 1])
        return cm.tolist()

    @staticmethod
    def calculate_lift(
        y_true: np.ndarray | list,
        y_prob: np.ndarray | list,
        percentile: float = 10,
    ) -> float:
        """
        计算 Lift 值

        按预测概率降序排列，取前 percentile% 的样本，
        计算其正样本率与总体正样本率的比值。

        Args:
            y_true: 真实标签数组 (0/1)
            y_prob: 预测为正类的概率数组
            percentile: 百分位阈值 (默认10，即前10%)

        Returns:
            lift_value: Lift 值
        """
        y_true = np.asarray(y_true, dtype=np.float64)
        y_prob = np.asarray(y_prob, dtype=np.float64)

        if len(y_true) == 0 or len(y_prob) == 0:
            return 0.0

        total_pos_rate = np.mean(y_true)
        if total_pos_rate == 0:
            return 0.0

        n_samples = len(y_true)
        n_top = max(1, int(n_samples * percentile / 100.0))

        # 按概率降序排列的索引
        sorted_indices = np.argsort(-y_prob)
        top_indices = sorted_indices[:n_top]

        top_pos_rate = np.mean(y_true[top_indices])
        lift = top_pos_rate / total_pos_rate

        return float(lift)

    @staticmethod
    def calculate_psi(
        y_train_prob: np.ndarray | list,
        y_test_prob: np.ndarray | list,
        bins: int = 10,
    ) -> float:
        """
        计算 PSI (Population Stability Index)

        对比训练集和测试集的预测概率分布稳定性。
        将概率值分成等频桶，比较两个分布中各桶的占比差异。

        PSI < 0.1   : 分布稳定
        0.1 <= PSI < 0.25 : 需要关注
        PSI >= 0.25 : 分布显著漂移

        Args:
            y_train_prob: 训练集预测概率数组
            y_test_prob: 测试集预测概率数组
            bins: 分桶数量 (默认10)

        Returns:
            psi_value: PSI 值
        """
        y_train_prob = np.asarray(y_train_prob, dtype=np.float64)
        y_test_prob = np.asarray(y_test_prob, dtype=np.float64)

        if len(y_train_prob) == 0 or len(y_test_prob) == 0:
            return 0.0

        # 使用训练集的分位数作为切分点
        epsilon = 1e-8
        quantiles = np.linspace(0, 100, bins + 1)
        edges = np.percentile(y_train_prob, quantiles)
        # 确保首尾覆盖全部范围
        edges[0] = -np.inf
        edges[-1] = np.inf

        train_counts = np.histogram(y_train_prob, bins=edges)[0]
        test_counts = np.histogram(y_test_prob, bins=edges)[0]

        train_props = train_counts / len(y_train_prob)
        test_props = test_counts / len(y_test_prob)

        # 对零桶添加极小值以避免 log(0) 和除零
        train_props = np.where(train_props == 0, epsilon, train_props)
        test_props = np.where(test_props == 0, epsilon, test_props)

        psi = np.sum((test_props - train_props) * np.log(test_props / train_props))

        return float(psi)

    @staticmethod
    def calculate_vif(
        X: np.ndarray | pd.DataFrame,
        feature_names: list[str],
    ) -> dict[str, float]:
        """
        计算方差膨胀因子 (Variance Inflation Factor)

        用于检测特征间的多重共线性。
        VIF > 10 通常表示存在严重的共线性问题。

        Args:
            X: 特征矩阵 (n_samples, n_features)
            feature_names: 特征名称列表

        Returns:
            vif_scores: {特征名称: VIF 值} 的字典
        """
        from statsmodels.stats.outliers_influence import variance_inflation_factor

        if isinstance(X, pd.DataFrame):
            X_array = X.values.astype(np.float64)
        else:
            X_array = np.asarray(X, dtype=np.float64)

        if X_array.shape[0] == 0 or X_array.shape[1] == 0:
            return {name: 0.0 for name in feature_names}

        n_features = X_array.shape[1]
        if len(feature_names) != n_features:
            # 名称数量不匹配时使用默认名称
            feature_names = [f"feature_{i}" for i in range(n_features)]

        vif_scores: dict[str, float] = {}
        for i in range(n_features):
            try:
                vif_val = variance_inflation_factor(X_array, i)
                # 极大值（完全共线性）设为 inf
                if np.isnan(vif_val) or np.isinf(vif_val):
                    vif_scores[feature_names[i]] = float("inf")
                else:
                    vif_scores[feature_names[i]] = float(vif_val)
            except Exception:
                vif_scores[feature_names[i]] = float("inf")

        return vif_scores

    # ──────────────────────────────────────────────
    # 可视化数据生成
    # ──────────────────────────────────────────────

    def _generate_ks_curve_data(
        self,
        y_true: np.ndarray,
        y_prob: np.ndarray,
    ) -> dict[str, list[float]]:
        """
        生成 KS 曲线绘图数据

        返回每个阈值下的正样本累积率和负样本累积率，
        可直接用于绘制 KS 曲线图。

        Args:
            y_true: 真实标签
            y_prob: 预测概率

        Returns:
            {"thresholds": [...], "cumulative_positive_rate": [...], "cumulative_negative_rate": [...]}
        """
        positive_mask = y_true == 1
        negative_mask = y_true == 0

        pos_probs = y_prob[positive_mask]
        neg_probs = y_prob[negative_mask]

        thresholds = np.sort(np.unique(y_prob)).tolist()
        n_pos = len(pos_probs)
        n_neg = len(neg_probs)

        if n_pos == 0 or n_neg == 0:
            return {
                "thresholds": thresholds,
                "cumulative_positive_rate": [0.0] * len(thresholds),
                "cumulative_negative_rate": [0.0] * len(thresholds),
            }

        cum_pos_rates = []
        cum_neg_rates = []
        for thr in thresholds:
            cum_pos_rates.append(float(np.sum(pos_probs <= thr) / n_pos))
            cum_neg_rates.append(float(np.sum(neg_probs <= thr) / n_neg))

        return {
            "thresholds": thresholds,
            "cumulative_positive_rate": cum_pos_rates,
            "cumulative_negative_rate": cum_neg_rates,
        }

    def _generate_roc_curve_data(
        self,
        y_true: np.ndarray,
        y_prob: np.ndarray,
    ) -> dict[str, list[float]]:
        """
        生成 ROC 曲线绘图数据

        Args:
            y_true: 真实标签
            y_prob: 预测概率

        Returns:
            {"fpr": [...], "tpr": [...]} — 假阳性率和真阳性率列表
        """
        unique_labels = np.unique(y_true)
        if len(unique_labels) < 2:
            return {"fpr": [0.0, 1.0], "tpr": [0.0, 1.0]}

        try:
            fpr, tpr, _ = roc_curve(y_true, y_prob)
            return {
                "fpr": fpr.tolist(),
                "tpr": tpr.tolist(),
            }
        except ValueError:
            return {"fpr": [0.0, 1.0], "tpr": [0.0, 1.0]}

    def _generate_score_distribution(
        self,
        y_true: np.ndarray,
        y_prob: np.ndarray,
        n_bins: int = 20,
    ) -> dict[str, Any]:
        """
        生成评分分布直方图数据

        分别统计正样本和负样本在各分箱段的数量，
        用于绘制评分分布对比图。

        Args:
            y_true: 真实标签
            y_prob: 预测概率
            n_bins: 分箱数量

        Returns:
            {
                "bins": [分箱边界列表],
                "positive_counts": [正样本各分箱计数],
                "negative_counts": [负样本各分箱计数],
                "bin_labels": [分箱标签]
            }
        """
        bin_edges = np.linspace(0, 1, n_bins + 1)

        pos_probs = y_prob[y_true == 1]
        neg_probs = y_prob[y_true == 0]

        pos_counts, _ = np.histogram(pos_probs, bins=bin_edges)
        neg_counts, _ = np.histogram(neg_probs, bins=bin_edges)

        bin_labels = [f"{bin_edges[i]:.2f}-{bin_edges[i + 1]:.2f}" for i in range(n_bins)]

        return {
            "bins": bin_edges.tolist(),
            "positive_counts": pos_counts.tolist(),
            "negative_counts": neg_counts.tolist(),
            "bin_labels": bin_labels,
        }

    # ──────────────────────────────────────────────
    # 完整评估流程
    # ──────────────────────────────────────────────

    def evaluate(
        self,
        model_result: dict[str, Any],
        X_test: np.ndarray | pd.DataFrame,
        y_test: np.ndarray | pd.Series | list,
        threshold: float = 0.5,
        y_train_prob: Optional[np.ndarray | list] = None,
    ) -> dict[str, Any]:
        """
        执行完整模型评估流程

        根据训练结果和测试数据，计算全部评估指标，生成可视化数据，
        并将报告缓存到内存中。

        Args:
            model_result: 训练结果字典，需包含以下键：
                - estimator: 训练好的模型对象 (sklearn/xgb/lgbm)
                - model_id: 模型唯一标识
                - algorithm: 算法类型 (LR/XGBOOST/LIGHTGBM)
            X_test: 测试集特征矩阵
            y_test: 测试集真实标签
            threshold: 分类阈值 (默认0.5)
            y_train_prob: 训练集预测概率（用于 PSI 计算，可选）

        Returns:
            完整评估报告字典，字段与 EvaluationReport schema 对齐：
            {
                "model_id": str,
                "algorithm": str,
                "metrics": {
                    "auc", "ks", "gini", "accuracy", "precision",
                    "recall", "f1_score", "confusion_matrix",
                    "psi", "lift_at_10", "lift_at_20", "vif_scores"
                },
                "ks_curve": {...},
                "roc_curve": {...},
                "score_distribution": {...},
                "feature_importance": {...},
                "evaluated_at": "yyyy-MM-dd HH:mm:ss"
            }
        """
        # 提取模型信息
        estimator = model_result.get("estimator") or model_result.get("_best_estimator")
        if estimator is None:
            raise ValueError("模型结果中缺少 estimator 或 _best_estimator")
        model_id = model_result["model_id"]
        algorithm = model_result["algorithm"]

        # 统一转为 numpy 数组
        if isinstance(X_test, pd.DataFrame):
            X_array = X_test.values
            feature_names = X_test.columns.tolist()
        else:
            X_array = np.asarray(X_test)
            feature_names = [f"feature_{i}" for i in range(X_array.shape[1])]

        y_true = np.asarray(y_test, dtype=np.float64).ravel()

        # 预测概率和标签
        y_prob: np.ndarray = np.asarray(estimator.predict_proba(X_array)[:, 1], dtype=np.float64)
        y_pred: np.ndarray = (y_prob >= threshold).astype(int)

        # ── 核心指标 ──
        auc_value = self.calculate_auc(y_true, y_prob)
        ks_value, _ = self.calculate_ks(y_true, y_prob)
        gini_value = self.calculate_gini(auc_value)

        accuracy = float(accuracy_score(y_true, y_pred))

        # 处理只有单一类别的情况
        unique_labels = np.unique(y_true)
        if len(unique_labels) >= 2:
            prec = float(precision_score(y_true, y_pred, zero_division=0))
            rec = float(recall_score(y_true, y_pred, zero_division=0))
            f1 = float(f1_score(y_true, y_pred, zero_division=0))
        else:
            prec = 0.0
            rec = 0.0
            f1 = 0.0

        cm = self.calculate_confusion_matrix(y_true, y_pred)

        # PSI
        psi_value: Optional[float] = None
        if y_train_prob is not None:
            psi_value = self.calculate_psi(y_train_prob, y_prob)

        # Lift
        lift_10 = self.calculate_lift(y_true, y_prob, percentile=10)
        lift_20 = self.calculate_lift(y_true, y_prob, percentile=20)

        # VIF
        vif_scores: Optional[dict[str, float]] = None
        try:
            vif_scores = self.calculate_vif(X_array, feature_names)
        except Exception:
            vif_scores = None

        # ── 可视化数据 ──
        ks_curve = self._generate_ks_curve_data(y_true, y_prob)
        roc_curve_data = self._generate_roc_curve_data(y_true, y_prob)
        score_dist = self._generate_score_distribution(y_true, y_prob)

        # ── 特征重要性 ──
        feature_importance: dict[str, float] = {}
        if hasattr(estimator, "feature_importances_"):
            importances = estimator.feature_importances_
            for name, imp in zip(feature_names, importances):
                feature_importance[name] = float(imp)
        elif hasattr(estimator, "coef_"):
            coefs = estimator.coef_.ravel()
            for name, coef in zip(feature_names, coefs):
                feature_importance[name] = float(abs(coef))

        # ── 组装报告 ──
        report: dict[str, Any] = {
            "model_id": model_id,
            "algorithm": algorithm,
            "metrics": {
                "auc": auc_value,
                "ks": ks_value,
                "gini": gini_value,
                "accuracy": accuracy,
                "precision": prec,
                "recall": rec,
                "f1_score": f1,
                "confusion_matrix": cm,
                "psi": psi_value,
                "lift_at_10": lift_10,
                "lift_at_20": lift_20,
                "vif_scores": vif_scores,
            },
            "ks_curve": ks_curve,
            "roc_curve": roc_curve_data,
            "score_distribution": score_dist,
            "feature_importance": feature_importance,
            "evaluated_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        }

        # 缓存报告
        self._reports[model_id] = report

        return report

    # ──────────────────────────────────────────────
    # 报告查询
    # ──────────────────────────────────────────────

    def get_report(self, model_id: str) -> Optional[dict[str, Any]]:
        """
        获取缓存的评估报告

        Args:
            model_id: 模型唯一标识

        Returns:
            评估报告字典，若不存在则返回 None
        """
        return self._reports.get(model_id)

    def list_reports(self) -> list[dict[str, Any]]:
        """
        列出所有已缓存的评估报告

        Returns:
            所有评估报告的列表
        """
        return list(self._reports.values())


# 全局单例
model_evaluator = ModelEvaluator()
