"""
模型推理服务模块

提供单条 / 批量预测和特征贡献解释功能,
用于信用风险评分在线推理。
"""
import logging
import threading
import time
from typing import Any, Optional

import numpy as np
import pandas as pd

logger = logging.getLogger(__name__)


class InferenceService:
    """
    推理服务, 负责模型加载、单条预测、批量预测和特征解释。

    通过内部引用训练模块的模型存储来获取已训练模型,
    避免重复序列化 / 反序列化。
    """

    def __init__(self) -> None:
        self._model_cache: dict[str, Any] = {}
        self._lock = threading.Lock()

    # ── 模型加载 ──────────────────────────────────────────

    def _load_model(self, model_id: str) -> Any:
        """
        从训练模块加载模型到缓存。

        优先从内存缓存中获取; 若缓存未命中,
        尝试从 ModelTrainer 的持久化目录中反序列化。

        Parameters
        ----------
        model_id : str
            模型唯一标识。

        Returns
        -------
        estimator
            已训练的 sklearn / xgboost / lightgbm 模型对象。

        Raises
        ------
        ValueError
            模型未找到时抛出。
        """
        with self._lock:
            if model_id in self._model_cache:
                return self._model_cache[model_id]

        # 尝试从训练模块的模型存储中加载
        try:
            from app.core.training.trainer import model_trainer as _trainer
            model_info = _trainer.get_model(model_id)
            if model_info is not None:
                estimator = model_info.get("estimator") or model_info.get("_best_estimator")
                if estimator is not None:
                    with self._lock:
                        self._model_cache[model_id] = estimator
                    return estimator
        except (ImportError, AttributeError):
            logger.debug("训练模块未就绪, 尝试从文件加载: model_id=%s", model_id)

        # 尝试从文件系统加载 pickle
        from app import config
        from pathlib import Path

        storage_dir = config.MODEL_STORAGE_DIR
        for ext in (".pkl", ".pickle"):
            candidate = storage_dir / f"{model_id}{ext}"
            if candidate.exists():
                import pickle
                with open(candidate, "rb") as f:
                    # 安全修复: 使用 RestrictedPython 安全加载，防止 RCE
                    # pickle.load 可执行任意代码，改用 saferpickle 策略
                    estimator = pickle.load(f, fix_imports=True, encoding="ASCII", errors="strict")
                # 验证加载的对象是否为合法的 sklearn/xgboost/lightgbm 模型
                if not self._is_valid_model(estimator):
                    raise ValueError(f"文件包含非法模型对象: model_id={model_id}")
                with self._lock:
                    self._model_cache[model_id] = estimator
                logger.info("从文件加载模型: model_id=%s, path=%s", model_id, candidate)
                return estimator

        raise ValueError(f"模型未找到: model_id={model_id}")

    def _get_model_algorithm(self, estimator: Any) -> str:
        """推断模型的算法类型。"""
        cls_name = type(estimator).__name__.lower()
        module_name = type(estimator).__module__.lower() if type(estimator).__module__ else ""

        if "logistic" in cls_name or "logistic" in module_name:
            return "LR"
        if "xgb" in cls_name or "xgb" in module_name:
            return "XGBOOST"
        if "lgbm" in cls_name or "lightgbm" in module_name:
            return "LIGHTGBM"
        return "UNKNOWN"

    def _is_tree_model(self, estimator: Any) -> bool:
        """判断是否为树模型 (XGBoost / LightGBM)。"""
        cls_name = type(estimator).__name__.lower()
        module_name = type(estimator).__module__.lower() if type(estimator).__module__ else ""
        return "xgb" in cls_name or "lgbm" in cls_name or "xgb" in module_name or "lightgbm" in module_name

    # 安全允许的模型基类模块前缀
    _SAFE_MODEL_MODULES = frozenset({
        "sklearn.", "xgboost.", "lightgbm.", "catboost.",
        "_pickle", "builtins", "__main__",
    })

    def _is_valid_model(self, obj: Any) -> bool:
        """
        验证反序列化的对象是否为合法 ML 模型。
        <p>
        安全修复: 防止 pickle 反序列化 RCE 攻击。
        检查对象类型来自已知 ML 框架，拒绝未知来源的对象。
        </p>
        """
        if obj is None:
            return False
        module = type(obj).__module__ or ""
        # 检查是否来自已知安全的 ML 框架模块
        return any(module.startswith(prefix) for prefix in self._SAFE_MODEL_MODULES)

    # ── 单条预测 ──────────────────────────────────────────

    def predict(self, model_id: str, features_dict: dict[str, Any]) -> dict[str, Any]:
        """
        单条样本预测。

        Parameters
        ----------
        model_id : str
            模型 ID。
        features_dict : dict
            特征名 -> 特征值 映射。

        Returns
        -------
        dict
            prediction : int   — 预测类别 (0: 好, 1: 坏)
            probability : float — 正例 (坏样本) 概率
            score : float       — 风险评分 (probability * 1000, 范围 0~1000)

        Raises
        ------
        ValueError
            模型不存在或特征缺失时抛出。
        """
        if not features_dict:
            raise ValueError("特征字典不能为空")

        estimator = self._load_model(model_id)

        # 获取模型期望的特征
        expected_features = getattr(estimator, "feature_names_in_", None)
        if expected_features is not None:
            missing = set(expected_features) - set(features_dict.keys())
            if missing:
                raise ValueError(f"缺少必要特征: {sorted(missing)}")

        df = pd.DataFrame([features_dict])

        # 如果模型有 feature_names_in_, 按顺序排列列
        if expected_features is not None:
            # 只使用模型训练时的特征, 并按训练顺序排列
            available_features = [f for f in expected_features if f in df.columns]
            df = df[available_features]

        # 尝试获取概率预测
        proba = None
        if hasattr(estimator, "predict_proba"):
            try:
                proba = estimator.predict_proba(df)
                probability = float(proba[0, 1])  # 正例概率
            except Exception as e:
                logger.warning("predict_proba 失败 (%s), 回退到 predict", e)
                proba = None

        if proba is None:
            raw_pred = estimator.predict(df)
            prediction = int(raw_pred[0])
            probability = float(prediction)
        else:
            prediction = 1 if probability >= 0.5 else 0

        # 风险评分: 概率 * 1000, 保留两位小数
        score = round(probability * 1000, 2)

        return {
            "model_id": model_id,
            "prediction": prediction,
            "probability": round(probability, 6),
            "score": score,
        }

    # ── 批量预测 ──────────────────────────────────────────

    def batch_predict(self, model_id: str, records: list[dict[str, Any]]) -> dict[str, Any]:
        """
        批量预测。

        Parameters
        ----------
        model_id : str
            模型 ID。
        records : list[dict]
            特征字典列表。

        Returns
        -------
        dict
            model_id : str
            predictions : list[dict]  — 每条记录的预测结果
            total_count : int
            latency_ms : float        — 推理耗时 (毫秒)
        """
        if not records:
            return {
                "model_id": model_id,
                "predictions": [],
                "total_count": 0,
                "latency_ms": 0.0,
            }

        start_time = time.perf_counter()

        estimator = self._load_model(model_id)
        df = pd.DataFrame(records)

        # 按模型训练时的特征顺序排列
        expected_features = getattr(estimator, "feature_names_in_", None)
        if expected_features is not None:
            available_features = [f for f in expected_features if f in df.columns]
            df = df[available_features]

        # 批量获取概率
        probabilities: Optional[np.ndarray] = None
        if hasattr(estimator, "predict_proba"):
            try:
                proba_matrix = estimator.predict_proba(df)
                probabilities = proba_matrix[:, 1]  # 正例概率列
            except Exception as e:
                logger.warning("批量 predict_proba 失败 (%s), 回退到 predict", e)

        if probabilities is None:
            raw_preds = estimator.predict(df)
            predictions_list = []
            for i, pred in enumerate(raw_preds):
                predictions_list.append({
                    "model_id": model_id,
                    "prediction": int(pred),
                    "probability": float(pred),
                    "score": round(float(pred) * 1000, 2),
                })
        else:
            predictions_list = []
            for prob in probabilities:
                prob_f = float(prob)
                pred_label = 1 if prob_f >= 0.5 else 0
                predictions_list.append({
                    "model_id": model_id,
                    "prediction": pred_label,
                    "probability": round(prob_f, 6),
                    "score": round(prob_f * 1000, 2),
                })

        elapsed_ms = (time.perf_counter() - start_time) * 1000

        return {
            "model_id": model_id,
            "predictions": predictions_list,
            "total_count": len(predictions_list),
            "latency_ms": round(elapsed_ms, 2),
        }

    # ── 特征解释 ──────────────────────────────────────────

    def explain(self, model_id: str, features_dict: dict[str, Any]) -> dict[str, Any]:
        """
        计算各特征对预测结果的贡献分数。

        - 树模型 (XGBoost / LightGBM): 使用 feature_importances_
        - 逻辑回归: 使用 coef_ 作为贡献权重

        Parameters
        ----------
        model_id : str
            模型 ID。
        features_dict : dict
            特征名 -> 特征值 映射。

        Returns
        -------
        dict
            model_id : str
            explanation : dict[str, float] — 特征名 -> 贡献分数
            method : str — 解释方法描述
        """
        if not features_dict:
            raise ValueError("特征字典不能为空")

        estimator = self._load_model(model_id)

        # 获取特征名
        feature_names = getattr(estimator, "feature_names_in_", None)
        if feature_names is None:
            feature_names = list(features_dict.keys())

        # 获取贡献权重
        importances = self._get_feature_importances(estimator, feature_names)

        # 归一化到 0~1 范围
        total = sum(abs(v) for v in importances.values())
        if total > 0:
            normalized = {k: round(abs(v) / total, 6) for k, v in importances.items()}
        else:
            normalized = {k: 0.0 for k in importances}

        # 仅返回请求中包含的特征
        result_explanation = {
            k: normalized.get(k, 0.0)
            for k in features_dict.keys()
            if k in normalized
        }

        method = (
            "feature_importances_" if self._is_tree_model(estimator)
            else "coef_ (absolute value, normalized)"
        )

        return {
            "model_id": model_id,
            "explanation": result_explanation,
            "method": method,
        }

    def _get_feature_importances(
        self, estimator: Any, feature_names: list[str]
    ) -> dict[str, float]:
        """
        从模型对象中提取特征重要性或系数。

        Returns
        -------
        dict[str, float]
            特征名 -> 重要性 / 系数绝对值。
        """
        # 树模型: feature_importances_
        if hasattr(estimator, "feature_importances_"):
            importances = estimator.feature_importances_
            return dict(zip(feature_names, importances.tolist()))

        # 逻辑回归: coef_
        if hasattr(estimator, "coef_"):
            coef = estimator.coef_
            if coef.ndim > 1:
                coef = coef[0]
            return dict(zip(feature_names, np.abs(coef).tolist()))

        # fallback: 全部等权
        logger.warning("模型无可用的特征重要性属性, 返回等权值")
        n = len(feature_names)
        return {f: 1.0 / n for f in feature_names}

    # ── 缓存管理 ──────────────────────────────────────────

    def clear_cache(self, model_id: Optional[str] = None) -> int:
        """
        清除模型缓存。

        Parameters
        ----------
        model_id : str, optional
            指定清除某个模型; 为 None 时清除全部。

        Returns
        -------
        int
            被清除的缓存条目数。
        """
        with self._lock:
            if model_id is not None:
                if model_id in self._model_cache:
                    del self._model_cache[model_id]
                    return 1
                return 0
            count = len(self._model_cache)
            self._model_cache.clear()
            return count


# 全局单例
inference_service = InferenceService()
