"""
样本管理模块 —— 负责数据集创建、筛选与样本拆分。

支持三种拆分策略:
- RANDOM:       随机打乱后按比例拆分
- STRATIFIED:   按标签分层抽样拆分，保证各子集正负样本比例一致
- TIME_BASED:   按时间列排序后按时间顺序拆分（训练集最早、测试集最新）

数据集目前以内存字典存储，后续可替换为持久化方案。
"""

from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any, Optional

import numpy as np
import pandas as pd

from app.schemas import SampleFilter, SampleSplitStrategy


class SampleManager:
    """样本管理器，提供数据集的创建、筛选、拆分及查询能力。"""

    def __init__(self) -> None:
        # dataset_id -> dataset dict
        self._datasets: dict[str, dict[str, Any]] = {}

    # ────────────────────────────────────────────
    # 公共方法
    # ────────────────────────────────────────────

    def create_dataset(
        self,
        df: pd.DataFrame,
        name: str,
        filters: Optional[SampleFilter] = None,
    ) -> dict[str, Any]:
        """从 DataFrame 创建数据集，可选择性应用样本筛选条件。

        Args:
            df:      原始数据
            name:    数据集名称
            filters: 筛选条件，包含时间窗口、产品、渠道及标签配置

        Returns:
            数据集字典，包含 id / name / train_df / val_df / test_df / metadata 等字段。
            初始创建时 train/val/test 均为 None，需调用 split_dataset 进行拆分。
        """
        if df is None or df.empty:
            raise ValueError("输入 DataFrame 不能为空")

        working_df = df.copy()

        # 应用筛选条件
        metadata: dict[str, Any] = {
            "name": name,
            "created_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "row_count_raw": len(working_df),
            "column_count": len(working_df.columns),
            "filters": None,
        }

        if filters is not None:
            working_df = self._apply_filters(working_df, filters)
            metadata["filters"] = filters.model_dump()

        metadata["row_count_filtered"] = len(working_df)

        # 统计标签分布（如果标签列存在）
        if filters is not None and filters.label_column in working_df.columns:
            label_col = filters.label_column
            metadata["positive_count"] = int(
                (working_df[label_col] == filters.positive_label).sum()
            )
            metadata["negative_count"] = int(
                (working_df[label_col] == filters.negative_label).sum()
            )

        dataset_id = self._generate_id()
        dataset: dict[str, Any] = {
            "id": dataset_id,
            "name": name,
            "df": working_df,
            "train_df": None,
            "val_df": None,
            "test_df": None,
            "metadata": metadata,
        }

        self._datasets[dataset_id] = dataset
        return dataset

    def split_dataset(
        self,
        dataset_id: str,
        strategy: SampleSplitStrategy = SampleSplitStrategy.STRATIFIED,
        train_ratio: float = 0.6,
        val_ratio: float = 0.2,
        test_ratio: float = 0.2,
        time_column: Optional[str] = None,
        random_seed: int = 42,
    ) -> dict[str, Any]:
        """对已有数据集进行训练集 / 验证集 / 测试集拆分。

        Args:
            dataset_id:   数据集 ID
            strategy:     拆分策略 (RANDOM / STRATIFIED / TIME_BASED)
            train_ratio:  训练集比例
            val_ratio:    验证集比例
            test_ratio:   测试集比例
            time_column:  时间列名（TIME_BASED 策略必须提供）
            random_seed:  随机种子

        Returns:
            更新后的数据集字典。

        Raises:
            ValueError: 数据集不存在 / 比例之和不等于 1 / 缺少必要列
        """
        self._validate_ratios(train_ratio, val_ratio, test_ratio)

        dataset = self._get_dataset_or_raise(dataset_id)
        df = dataset["df"]

        if strategy == SampleSplitStrategy.RANDOM:
            train_df, val_df, test_df = self._split_random(
                df, train_ratio, val_ratio, test_ratio, random_seed
            )
        elif strategy == SampleSplitStrategy.STRATIFIED:
            train_df, val_df, test_df = self._split_stratified(
                df, train_ratio, val_ratio, test_ratio, random_seed
            )
        elif strategy == SampleSplitStrategy.TIME_BASED:
            if time_column is None:
                raise ValueError("TIME_BASED 策略必须指定 time_column")
            train_df, val_df, test_df = self._split_time_based(
                df, train_ratio, val_ratio, test_ratio, time_column
            )
        else:
            raise ValueError(f"不支持的拆分策略: {strategy}")

        # 更新数据集
        dataset["train_df"] = train_df
        dataset["val_df"] = val_df
        dataset["test_df"] = test_df
        dataset["split_strategy"] = strategy.value if hasattr(strategy, 'value') else strategy
        dataset["train_count"] = len(train_df)
        dataset["val_count"] = len(val_df)
        dataset["test_count"] = len(test_df)
        dataset["metadata"]["split_strategy"] = dataset["split_strategy"]
        dataset["metadata"]["split_ratios"] = {
            "train": train_ratio,
            "val": val_ratio,
            "test": test_ratio,
        }
        dataset["metadata"]["train_count"] = len(train_df)
        dataset["metadata"]["val_count"] = len(val_df)
        dataset["metadata"]["test_count"] = len(test_df)
        dataset["metadata"]["random_seed"] = random_seed
        if time_column is not None:
            dataset["metadata"]["time_column"] = time_column

        return dataset

    def get_dataset(self, dataset_id: str) -> Optional[dict[str, Any]]:
        """根据 ID 获取数据集信息。

        返回数据集字典（不包含原始 df 字段以节省序列化开销），
        若 ID 不存在则返回 None。
        """
        ds = self._datasets.get(dataset_id)
        if ds is None:
            return None
        return self._sanitize_dataset(ds)

    def list_datasets(self) -> list[dict[str, Any]]:
        """列出所有数据集的摘要信息。"""
        return [self._sanitize_dataset(ds) for ds in self._datasets.values()]

    # ────────────────────────────────────────────
    # 私有方法 - 筛选
    # ────────────────────────────────────────────

    @staticmethod
    def _apply_filters(df: pd.DataFrame, filters: SampleFilter) -> pd.DataFrame:
        """依次应用时间窗口、产品、渠道筛选条件。"""
        result = df.copy()

        # 时间窗口筛选
        if filters.start_date is not None or filters.end_date is not None:
            # 自动检测时间列
            time_col = SampleManager._detect_time_column(result)
            if time_col is None:
                raise ValueError("未能检测到时间列，请在数据中包含日期/时间列")
            result[time_col] = pd.to_datetime(result[time_col], errors="coerce")
            if filters.start_date is not None:
                start = pd.Timestamp(filters.start_date)
                result = result[result[time_col] >= start]
            if filters.end_date is not None:
                end = pd.Timestamp(filters.end_date)
                result = result[result[time_col] <= end]

        # 产品筛选
        if filters.products:
            product_col = SampleManager._detect_column_by_keywords(
                result, ["product", "产品", "product_type", "product_type_cd"]
            )
            if product_col is None:
                raise ValueError("未能检测到产品列")
            result = result[result[product_col].isin(filters.products)]

        # 渠道筛选
        if filters.channels:
            channel_col = SampleManager._detect_column_by_keywords(
                result, ["channel", "渠道", "channel_cd"]
            )
            if channel_col is None:
                raise ValueError("未能检测到渠道列")
            result = result[result[channel_col].isin(filters.channels)]

        # 标签筛选 —— 只保留正/负样本
        label_col = filters.label_column
        if label_col not in result.columns:
            raise ValueError(f"标签列 '{label_col}' 不存在")
        allowed_labels = [filters.positive_label, filters.negative_label]
        result = result[result[label_col].isin(allowed_labels)]

        if result.empty:
            raise ValueError("筛选后数据集为空，请调整筛选条件")

        return result.reset_index(drop=True)

    @staticmethod
    def _detect_time_column(df: pd.DataFrame) -> Optional[str]:
        """自动检测 DataFrame 中的时间列。"""
        # 优先匹配常见命名
        candidates = [
            "date", "time", "datetime", "apply_date", "apply_time",
            "申请日期", "申请时间", "created_at", "order_date",
        ]
        for c in candidates:
            if c in df.columns:
                return c
        # 回退: 检查 datetime 类型列
        for col in df.columns:
            if pd.api.types.is_datetime64_any_dtype(df[col]):
                return col
        # 回退: 检查列名包含 date / time
        for col in df.columns:
            lower = col.lower()
            if "date" in lower or "time" in lower:
                return col
        return None

    @staticmethod
    def _detect_column_by_keywords(
        df: pd.DataFrame, keywords: list[str]
    ) -> Optional[str]:
        """根据关键字列表匹配列名。"""
        for kw in keywords:
            for col in df.columns:
                if col.lower() == kw.lower():
                    return col
        # 宽松匹配
        for kw in keywords:
            for col in df.columns:
                if kw.lower() in col.lower():
                    return col
        return None

    # ────────────────────────────────────────────
    # 私有方法 - 拆分
    # ────────────────────────────────────────────

    @staticmethod
    def _split_random(
        df: pd.DataFrame,
        train_ratio: float,
        val_ratio: float,
        test_ratio: float,
        random_seed: int,
    ) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
        """随机拆分。"""
        rng = np.random.RandomState(random_seed)
        indices = rng.permutation(len(df))

        n = len(df)
        train_end = int(n * train_ratio)
        val_end = train_end + int(n * val_ratio)

        train_idx = indices[:train_end]
        val_idx = indices[train_end:val_end]
        test_idx = indices[val_end:]

        return (
            df.iloc[train_idx].reset_index(drop=True),
            df.iloc[val_idx].reset_index(drop=True),
            df.iloc[test_idx].reset_index(drop=True),
        )

    @staticmethod
    def _split_stratified(
        df: pd.DataFrame,
        train_ratio: float,
        val_ratio: float,
        test_ratio: float,
        random_seed: int,
    ) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
        """分层抽样拆分 —— 保持各子集的正负样本比例一致。

        自动检测标签列（优先使用名为 'label' 的列，否则取最后一列）。
        """
        label_col = SampleManager._detect_label_column(df)

        train_parts: list[pd.DataFrame] = []
        val_parts: list[pd.DataFrame] = []
        test_parts: list[pd.DataFrame] = []

        rng = np.random.RandomState(random_seed)

        for label_value, group in df.groupby(label_col):
            group = group.sample(frac=1, random_state=rng).reset_index(drop=True)
            n = len(group)
            train_end = int(n * train_ratio)
            val_end = train_end + int(n * val_ratio)

            train_parts.append(group.iloc[:train_end])
            val_parts.append(group.iloc[train_end:val_end])
            test_parts.append(group.iloc[val_end:])

        train_df = pd.concat(train_parts, ignore_index=True)
        val_df = pd.concat(val_parts, ignore_index=True)
        test_df = pd.concat(test_parts, ignore_index=True)

        # 打乱各子集内部顺序
        train_df = train_df.sample(frac=1, random_state=rng).reset_index(drop=True)
        val_df = val_df.sample(frac=1, random_state=rng).reset_index(drop=True)
        test_df = test_df.sample(frac=1, random_state=rng).reset_index(drop=True)

        return train_df, val_df, test_df

    @staticmethod
    def _split_time_based(
        df: pd.DataFrame,
        train_ratio: float,
        val_ratio: float,
        test_ratio: float,
        time_column: str,
    ) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
        """按时间顺序拆分 —— 训练集最早、测试集最新。"""
        if time_column not in df.columns:
            raise ValueError(f"时间列 '{time_column}' 不存在")

        sorted_df = df.copy()
        sorted_df[time_column] = pd.to_datetime(sorted_df[time_column], errors="coerce")
        sorted_df = sorted_df.dropna(subset=[time_column])
        sorted_df = sorted_df.sort_values(time_column).reset_index(drop=True)

        n = len(sorted_df)
        train_end = int(n * train_ratio)
        val_end = train_end + int(n * val_ratio)

        return (
            sorted_df.iloc[:train_end].reset_index(drop=True),
            sorted_df.iloc[train_end:val_end].reset_index(drop=True),
            sorted_df.iloc[val_end:].reset_index(drop=True),
        )

    @staticmethod
    def _detect_label_column(df: pd.DataFrame) -> str:
        """自动检测标签列。"""
        candidates = ["label", "target", "y", "is_default", "is_overdue", "bad"]
        for c in candidates:
            if c in df.columns:
                return c
        # 回退: 取最后一列
        return df.columns[-1]

    # ────────────────────────────────────────────
    # 私有方法 - 工具
    # ────────────────────────────────────────────

    @staticmethod
    def _generate_id() -> str:
        """生成数据集唯一标识。"""
        return f"ds_{uuid.uuid4().hex[:12]}"

    def _get_dataset_or_raise(self, dataset_id: str) -> dict[str, Any]:
        """获取数据集，不存在则抛异常。"""
        if dataset_id not in self._datasets:
            raise ValueError(f"数据集 '{dataset_id}' 不存在")
        return self._datasets[dataset_id]

    @staticmethod
    def _validate_ratios(
        train_ratio: float, val_ratio: float, test_ratio: float
    ) -> None:
        """校验拆分比例之和是否等于 1。"""
        total = round(train_ratio + val_ratio + test_ratio, 6)
        if not np.isclose(total, 1.0, atol=1e-6):
            raise ValueError(
                f"拆分比例之和必须等于 1.0，当前为 {total} "
                f"(train={train_ratio}, val={val_ratio}, test={test_ratio})"
            )

    @staticmethod
    def _sanitize_dataset(ds: dict[str, Any]) -> dict[str, Any]:
        """移除大体积字段，返回可安全序列化的摘要。"""
        return {
            "id": ds["id"],
            "name": ds["name"],
            "train_df": ds.get("train_df"),
            "val_df": ds.get("val_df"),
            "test_df": ds.get("test_df"),
            "metadata": ds["metadata"],
        }


# 全局单例
sample_manager = SampleManager()
