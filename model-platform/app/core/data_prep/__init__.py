"""数据准备模块 —— 样本管理与特征工程"""

from app.core.data_prep.feature_engineering import FeatureEngineer
from app.core.data_prep.sample_manager import SampleManager

__all__ = ["SampleManager", "FeatureEngineer"]

# 全局单例
sample_manager = SampleManager()
feature_engineer = FeatureEngineer()
