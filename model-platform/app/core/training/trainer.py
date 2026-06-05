"""
模型训练管线模块

提供逻辑回归、XGBoost、LightGBM 三种算法的训练能力,
支持网格搜索超参数调优与交叉验证评估。
"""

import time
from datetime import datetime
from typing import Any, Optional

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import GridSearchCV, cross_val_score
from xgboost import XGBClassifier
from lightgbm import LGBMClassifier

from app.core.data_prep.sample_manager import SampleManager
from app.schemas import ModelAlgorithm, ModelStatus


class ModelTrainer:
    """模型训练器,支持 LR / XGBoost / LightGBM 的网格搜索训练"""

    def __init__(self, sample_manager: Optional[SampleManager] = None) -> None:
        """
        初始化模型训练器

        Args:
            sample_manager: 样本管理器实例, 为 None 时自动创建新实例
        """
        # 内存模型存储: model_id -> 训练结果字典
        self._models: dict[str, dict[str, Any]] = {}
        self._id_counter: int = 0
        self._sample_manager: SampleManager = sample_manager or SampleManager()

    # ------------------------------------------------------------------
    # ID 生成
    # ------------------------------------------------------------------

    def _next_id(self) -> str:
        """生成唯一模型 ID, 格式 model_001"""
        self._id_counter += 1
        return f"model_{self._id_counter:03d}"

    # ------------------------------------------------------------------
    # 逻辑回归
    # ------------------------------------------------------------------

    def train_lr(
        self,
        X_train: pd.DataFrame,
        y_train: pd.Series,
        X_val: pd.DataFrame,
        y_val: pd.Series,
        hyperparams: Optional[dict[str, Any]] = None,
        cv_folds: int = 5,
        random_seed: int = 42,
    ) -> dict[str, Any]:
        """
        训练逻辑回归模型

        Args:
            X_train: 训练集特征
            y_train: 训练集标签
            X_val: 验证集特征
            y_val: 验证集标签
            hyperparams: 自定义超参数网格, 为 None 时使用默认网格
            cv_folds: 交叉验证折数
            random_seed: 随机种子

        Returns:
            包含 best_estimator / best_params / cv_results / feature_importance 的字典
        """
        # 默认超参数网格
        default_grid: dict[str, Any] = {
            "C": [0.01, 0.1, 1, 10],
            "penalty": ["l1", "l2"],
            "solver": ["saga"],
        }
        param_grid = hyperparams if hyperparams else default_grid

        base_estimator = LogisticRegression(random_state=random_seed, max_iter=1000)

        grid_search = GridSearchCV(
            estimator=base_estimator,
            param_grid=param_grid,
            cv=cv_folds,
            scoring="roc_auc",
            n_jobs=-1,
            refit=True,
        )
        grid_search.fit(X_train, y_train)

        # 交叉验证分数
        cv_scores = cross_val_score(
            grid_search.best_estimator_, X_train, y_train,
            cv=cv_folds, scoring="roc_auc", n_jobs=-1,
        )

        # 特征重要性 = 回归系数绝对值
        coefficients = grid_search.best_estimator_.coef_[0]
        feature_names = list(X_train.columns)
        feature_importance = dict(zip(
            feature_names,
            np.abs(coefficients).tolist(),
        ))

        # 构造 cv_results 摘要
        cv_results_df = pd.DataFrame(grid_search.cv_results_)
        cv_results = {
            "mean_test_score": cv_results_df["mean_test_score"].tolist(),
            "std_test_score": cv_results_df["std_test_score"].tolist(),
            "params": cv_results_df["params"].tolist(),
        }

        return {
            "best_estimator": grid_search.best_estimator_,
            "best_params": grid_search.best_params_,
            "cv_results": cv_results,
            "cv_scores": cv_scores.tolist(),
            "cv_mean": float(cv_scores.mean()),
            "cv_std": float(cv_scores.std()),
            "feature_importance": feature_importance,
        }

    # ------------------------------------------------------------------
    # XGBoost
    # ------------------------------------------------------------------

    def train_xgboost(
        self,
        X_train: pd.DataFrame,
        y_train: pd.Series,
        X_val: pd.DataFrame,
        y_val: pd.Series,
        hyperparams: Optional[dict[str, Any]] = None,
        cv_folds: int = 5,
        random_seed: int = 42,
    ) -> dict[str, Any]:
        """
        训练 XGBoost 模型

        Args:
            X_train: 训练集特征
            y_train: 训练集标签
            X_val: 验证集特征
            y_val: 验证集标签
            hyperparams: 自定义超参数网格, 为 None 时使用默认网格
            cv_folds: 交叉验证折数
            random_seed: 随机种子

        Returns:
            包含 best_estimator / best_params / cv_results / feature_importance 的字典
        """
        default_grid: dict[str, Any] = {
            "n_estimators": [100, 200],
            "max_depth": [3, 5, 7],
            "learning_rate": [0.01, 0.1],
            "subsample": [0.8, 1.0],
            "colsample_bytree": [0.8, 1.0],
        }
        param_grid = hyperparams if hyperparams else default_grid

        base_estimator = XGBClassifier(
            random_state=random_seed,
            use_label_encoder=False,
            eval_metric="logloss",
            verbosity=0,
        )

        grid_search = GridSearchCV(
            estimator=base_estimator,
            param_grid=param_grid,
            cv=cv_folds,
            scoring="roc_auc",
            n_jobs=-1,
            refit=True,
        )
        grid_search.fit(X_train, y_train)

        cv_scores = cross_val_score(
            grid_search.best_estimator_, X_train, y_train,
            cv=cv_folds, scoring="roc_auc", n_jobs=-1,
        )

        # 特征重要性
        booster = grid_search.best_estimator_
        raw_importance = booster.feature_importances_
        feature_names = list(X_train.columns)
        feature_importance = dict(zip(
            feature_names,
            raw_importance.tolist(),
        ))

        cv_results_df = pd.DataFrame(grid_search.cv_results_)
        cv_results = {
            "mean_test_score": cv_results_df["mean_test_score"].tolist(),
            "std_test_score": cv_results_df["std_test_score"].tolist(),
            "params": cv_results_df["params"].tolist(),
        }

        return {
            "best_estimator": grid_search.best_estimator_,
            "best_params": grid_search.best_params_,
            "cv_results": cv_results,
            "cv_scores": cv_scores.tolist(),
            "cv_mean": float(cv_scores.mean()),
            "cv_std": float(cv_scores.std()),
            "feature_importance": feature_importance,
        }

    # ------------------------------------------------------------------
    # LightGBM
    # ------------------------------------------------------------------

    def train_lightgbm(
        self,
        X_train: pd.DataFrame,
        y_train: pd.Series,
        X_val: pd.DataFrame,
        y_val: pd.Series,
        hyperparams: Optional[dict[str, Any]] = None,
        cv_folds: int = 5,
        random_seed: int = 42,
    ) -> dict[str, Any]:
        """
        训练 LightGBM 模型

        Args:
            X_train: 训练集特征
            y_train: 训练集标签
            X_val: 验证集特征
            y_val: 验证集标签
            hyperparams: 自定义超参数网格, 为 None 时使用默认网格
            cv_folds: 交叉验证折数
            random_seed: 随机种子

        Returns:
            包含 best_estimator / best_params / cv_results / feature_importance 的字典
        """
        default_grid: dict[str, Any] = {
            "n_estimators": [100, 200],
            "max_depth": [3, 5, 7],
            "learning_rate": [0.01, 0.1],
            "num_leaves": [15, 31, 63],
            "subsample": [0.8, 1.0],
        }
        param_grid = hyperparams if hyperparams else default_grid

        base_estimator = LGBMClassifier(
            random_state=random_seed,
            verbose=-1,
        )

        grid_search = GridSearchCV(
            estimator=base_estimator,
            param_grid=param_grid,
            cv=cv_folds,
            scoring="roc_auc",
            n_jobs=-1,
            refit=True,
        )
        grid_search.fit(X_train, y_train)

        cv_scores = cross_val_score(
            grid_search.best_estimator_, X_train, y_train,
            cv=cv_folds, scoring="roc_auc", n_jobs=-1,
        )

        raw_importance = grid_search.best_estimator_.feature_importances_
        feature_names = list(X_train.columns)
        feature_importance = dict(zip(
            feature_names,
            raw_importance.tolist(),
        ))

        cv_results_df = pd.DataFrame(grid_search.cv_results_)
        cv_results = {
            "mean_test_score": cv_results_df["mean_test_score"].tolist(),
            "std_test_score": cv_results_df["std_test_score"].tolist(),
            "params": cv_results_df["params"].tolist(),
        }

        return {
            "best_estimator": grid_search.best_estimator_,
            "best_params": grid_search.best_params_,
            "cv_results": cv_results,
            "cv_scores": cv_scores.tolist(),
            "cv_mean": float(cv_scores.mean()),
            "cv_std": float(cv_scores.std()),
            "feature_importance": feature_importance,
        }

    # ------------------------------------------------------------------
    # 主训练入口
    # ------------------------------------------------------------------

    def train(self, request_dict: dict[str, Any]) -> dict[str, Any]:
        """
        模型训练主入口

        Args:
            request_dict: 训练请求字典, 包含:
                - name: 模型名称
                - algorithm: 算法类型 (LR / XGBOOST / LIGHTGBM)
                - dataset_id: 数据集 ID
                - target_column: 目标列名
                - features: 特征列列表 (None 表示使用全部列)
                - hyperparams: 超参数网格 (None 使用默认)
                - cv_folds: 交叉验证折数
                - random_seed: 随机种子
                - description: 模型描述

        Returns:
            训练结果字典, 包含 model_id / name / algorithm / status /
            best_params / cv_scores / cv_mean / cv_std /
            training_time_seconds / feature_importance / created_at

        Raises:
            ValueError: 数据集不存在、目标列缺失、数据集为空等
        """
        name: str = request_dict["name"]
        algorithm: str = request_dict["algorithm"]
        dataset_id: str = request_dict["dataset_id"]
        target_column: str = request_dict.get("target_column", "label")
        features: Optional[list[str]] = request_dict.get("features")
        hyperparams: Optional[dict[str, Any]] = request_dict.get("hyperparams")
        cv_folds: int = request_dict.get("cv_folds", 5)
        random_seed: int = request_dict.get("random_seed", 42)
        description: Optional[str] = request_dict.get("description")

        # ── 获取数据集 ──
        dataset = self._sample_manager.get_dataset(dataset_id)
        if dataset is None:
            raise ValueError(f"数据集不存在: {dataset_id}")

        # SampleManager.get_dataset 返回字典, 包含 train_df / val_df
        train_df: Optional[pd.DataFrame] = dataset.get("train_df")
        val_df: Optional[pd.DataFrame] = dataset.get("val_df")

        if train_df is None or (isinstance(train_df, pd.DataFrame) and train_df.empty):
            raise ValueError("数据集训练集为空, 请先调用 split_dataset 进行数据拆分")

        # 如果验证集不存在, 从训练集中按 80/20 切分
        if val_df is None or (isinstance(val_df, pd.DataFrame) and val_df.empty):
            shuffled = train_df.sample(frac=1, random_state=random_seed).reset_index(drop=True)
            split_idx = int(len(shuffled) * 0.8)
            train_df = shuffled.iloc[:split_idx]
            val_df = shuffled.iloc[split_idx:]

        # 确保是 DataFrame 类型
        if not isinstance(train_df, pd.DataFrame):
            raise ValueError(f"训练集格式不支持: {type(train_df)}")
        if not isinstance(val_df, pd.DataFrame):
            raise ValueError(f"验证集格式不支持: {type(val_df)}")

        # ── 校验目标列 ──
        if target_column not in train_df.columns:
            raise ValueError(
                f"目标列不存在: {target_column}, 可用列: {list(train_df.columns)}"
            )

        # ── 确定特征列 ──
        if features is None:
            feature_cols = [c for c in train_df.columns if c != target_column]
        else:
            missing = set(features) - set(train_df.columns)
            if missing:
                raise ValueError(f"以下特征列不存在: {sorted(missing)}")
            feature_cols = list(features)

        if not feature_cols:
            raise ValueError("无可用特征列, 至少需要一个特征")

        X_train = train_df[feature_cols]
        y_train = train_df[target_column]
        X_val = val_df[feature_cols]
        y_val = val_df[target_column]

        # ── 选择算法并训练 ──
        start_time = time.time()

        algorithm_upper = algorithm.upper() if isinstance(algorithm, str) else algorithm.value
        if algorithm_upper == ModelAlgorithm.LR.value:
            result = self.train_lr(
                X_train, y_train, X_val, y_val,
                hyperparams=hyperparams,
                cv_folds=cv_folds,
                random_seed=random_seed,
            )
        elif algorithm_upper == ModelAlgorithm.XGBOOST.value:
            result = self.train_xgboost(
                X_train, y_train, X_val, y_val,
                hyperparams=hyperparams,
                cv_folds=cv_folds,
                random_seed=random_seed,
            )
        elif algorithm_upper == ModelAlgorithm.LIGHTGBM.value:
            result = self.train_lightgbm(
                X_train, y_train, X_val, y_val,
                hyperparams=hyperparams,
                cv_folds=cv_folds,
                random_seed=random_seed,
            )
        else:
            raise ValueError(f"不支持的算法类型: {algorithm}")

        elapsed = time.time() - start_time

        # ── 组装输出 ──
        model_id = self._next_id()
        now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        model_record: dict[str, Any] = {
            "model_id": model_id,
            "name": name,
            "algorithm": algorithm_upper,
            "status": ModelStatus.EVALUATED.value,
            "best_params": result["best_params"],
            "cv_scores": result["cv_scores"],
            "cv_mean": result["cv_mean"],
            "cv_std": result["cv_std"],
            "training_time_seconds": round(elapsed, 3),
            "feature_importance": result["feature_importance"],
            "created_at": now,
            "description": description,
            # 内部字段 (不返回给调用方, 仅用于后续评估/推理)
            "_best_estimator": result["best_estimator"],
            "_cv_results_detail": result["cv_results"],
        }

        self._models[model_id] = model_record

        # 返回时去掉内部字段
        return {
            "model_id": model_id,
            "name": name,
            "algorithm": algorithm_upper,
            "status": ModelStatus.EVALUATED.value,
            "best_params": result["best_params"],
            "cv_scores": result["cv_scores"],
            "cv_mean": result["cv_mean"],
            "cv_std": result["cv_std"],
            "training_time_seconds": round(elapsed, 3),
            "feature_importance": result["feature_importance"],
            "created_at": now,
        }

    # ------------------------------------------------------------------
    # 查询接口
    # ------------------------------------------------------------------

    def get_model(self, model_id: str) -> Optional[dict[str, Any]]:
        """
        根据模型 ID 获取模型信息

        Args:
            model_id: 模型唯一标识

        Returns:
            模型信息字典, 不存在时返回 None
        """
        return self._models.get(model_id)

    def list_models(self) -> list[dict[str, Any]]:
        """
        列出所有已训练模型

        Returns:
            模型信息列表 (不含内部字段)
        """
        external_keys = {
            "model_id", "name", "algorithm", "status",
            "best_params", "cv_scores", "cv_mean", "cv_std",
            "training_time_seconds", "feature_importance",
            "created_at", "description",
        }
        result: list[dict[str, Any]] = []
        for record in self._models.values():
            result.append({k: v for k, v in record.items() if k in external_keys})
        return result


# 全局单例
model_trainer = ModelTrainer()
