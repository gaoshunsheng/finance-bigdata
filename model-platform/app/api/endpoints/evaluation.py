"""模型评估 API"""
from fastapi import APIRouter, HTTPException

from app.schemas import ApiResponse, EvaluationRequest, EvaluationReport
from app.core.evaluation.evaluator import model_evaluator
from app.core.training.trainer import model_trainer

router = APIRouter()


@router.post("/evaluate", summary="评估模型")
async def evaluate_model(request: EvaluationRequest):
    """对模型进行评估, 生成评估报告"""
    model = model_trainer.get_model(request.model_id)
    if not model:
        raise HTTPException(status_code=404, detail="模型不存在")

    try:
        dataset = model.get("_dataset")
        if not dataset:
            raise HTTPException(status_code=400, detail="模型无关联数据集")

        test_df = dataset.get("test_df")
        if test_df is None:
            raise HTTPException(status_code=400, detail="数据集未切分, 缺少测试集")

        target_col = model.get("target_column", "label")
        features = model.get("features")

        if features:
            X_test = test_df[features]
        else:
            numeric_cols = test_df.select_dtypes(include=["number"]).columns.tolist()
            X_test = test_df[[c for c in numeric_cols if c != target_col]]

        y_test = test_df[target_col]

        report = model_evaluator.evaluate(
            model_result=model,
            X_test=X_test,
            y_test=y_test,
            threshold=request.threshold,
        )

        return ApiResponse(data=report)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/reports", summary="评估报告列表")
async def list_reports():
    """获取所有评估报告"""
    reports = model_evaluator.list_reports()
    return ApiResponse(data=reports)


@router.get("/reports/{model_id}", summary="评估报告详情")
async def get_report(model_id: str):
    """获取模型评估报告"""
    report = model_evaluator.get_report(model_id)
    if not report:
        raise HTTPException(status_code=404, detail="评估报告不存在")
    return ApiResponse(data=report)
