"""数据准备 API - 样本管理与特征工程"""
from fastapi import APIRouter, HTTPException

from app.schemas import (
    ApiResponse,
    SampleFilter,
    SampleSplitRequest,
    FeatureEngineeringRequest,
    FeatureEngineeringResult,
    IVResult,
)
from app.core.data_prep.sample_manager import sample_manager
from app.core.data_prep.feature_engineering import feature_engineer

router = APIRouter()


@router.post("/datasets", summary="创建数据集")
async def create_dataset(filter_params: SampleFilter):
    """根据筛选条件创建样本数据集"""
    try:
        # 实际场景中从数据库/文件加载，这里返回示例数据集
        import pandas as pd
        import numpy as np

        # 生成示例数据
        np.random.seed(42)
        n = 1000
        df = pd.DataFrame({
            "customer_id": range(1, n + 1),
            "age": np.random.randint(20, 65, n),
            "income": np.random.lognormal(10, 1, n),
            "loan_amount": np.random.lognormal(9, 1.5, n),
            "credit_score": np.random.randint(300, 850, n),
            "overdue_count_6m": np.random.poisson(0.5, n),
            "credit_query_count_3m": np.random.poisson(2, n),
            "debt_ratio": np.random.beta(2, 5, n),
            "employment_years": np.random.exponential(5, n),
            "product_type": np.random.choice(["消费贷", "经营贷", "房贷"], n),
            "channel": np.random.choice(["线上", "线下", "API"], n),
            filter_params.label_column: np.random.choice(
                [filter_params.positive_label, filter_params.negative_label],
                n,
                p=[0.15, 0.85],
            ),
        })

        dataset = sample_manager.create_dataset(
            df=df,
            name=f"dataset_{filter_params.start_date or 'all'}",
            filters=filter_params,
        )

        return ApiResponse(
            data={
                "dataset_id": dataset["id"],
                "name": dataset["name"],
                "total_rows": len(df),
                "positive_count": int(df[filter_params.label_column].sum()),
                "negative_count": int((df[filter_params.label_column] == filter_params.negative_label).sum()),
                "features": [c for c in df.columns if c not in ["customer_id", filter_params.label_column]],
            }
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/datasets/{dataset_id}/split", summary="数据集切分")
async def split_dataset(dataset_id: str, request: SampleSplitRequest):
    """将数据集切分为训练集/验证集/测试集"""
    dataset = sample_manager.get_dataset(dataset_id)
    if not dataset:
        raise HTTPException(status_code=404, detail="数据集不存在")

    result = sample_manager.split_dataset(
        dataset_id=dataset_id,
        strategy=request.strategy.value,
        train_ratio=request.train_ratio,
        val_ratio=request.val_ratio,
        test_ratio=request.test_ratio,
        time_column=request.time_column,
        random_seed=request.random_seed,
    )

    return ApiResponse(data={
        "dataset_id": dataset_id,
        "strategy": request.strategy.value,
        "train_count": result["train_count"],
        "val_count": result["val_count"],
        "test_count": result["test_count"],
        "split_ratio": f"{request.train_ratio:.0%}/{request.val_ratio:.0%}/{request.test_ratio:.0%}",
    })


@router.get("/datasets", summary="数据集列表")
async def list_datasets():
    """获取所有数据集"""
    datasets = sample_manager.list_datasets()
    return ApiResponse(data=datasets)


@router.get("/datasets/{dataset_id}", summary="数据集详情")
async def get_dataset(dataset_id: str):
    """获取数据集详情"""
    dataset = sample_manager.get_dataset(dataset_id)
    if not dataset:
        raise HTTPException(status_code=404, detail="数据集不存在")
    return ApiResponse(data=dataset)


@router.post("/feature-engineering", summary="特征工程")
async def run_feature_engineering(request: FeatureEngineeringRequest):
    """执行特征工程: IV筛选 → 相关性过滤 → WOE分箱"""
    dataset = sample_manager.get_dataset(request.dataset_id)
    if not dataset:
        raise HTTPException(status_code=404, detail="数据集不存在")

    df = dataset.get("full_df") or dataset.get("train_df")
    if df is None:
        raise HTTPException(status_code=400, detail="数据集无数据")

    result = feature_engineer.run_feature_engineering(
        df=df,
        target=request.target_column,
        iv_threshold=request.iv_threshold,
        corr_threshold=request.correlation_threshold,
        max_bins=request.max_bins,
    )

    return ApiResponse(data=result)
