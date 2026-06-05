"""模型训练 API"""
from fastapi import APIRouter, HTTPException

from app.schemas import ApiResponse, TrainingRequest, TrainingResult
from app.core.training.trainer import model_trainer

router = APIRouter()


@router.post("/train", summary="发起模型训练")
async def train_model(request: TrainingRequest):
    """发起模型训练任务"""
    try:
        result = model_trainer.train(request.model_dump())
        return ApiResponse(data=result)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/models", summary="模型列表")
async def list_models():
    """获取所有训练完成的模型"""
    models = model_trainer.list_models()
    return ApiResponse(data=models)


@router.get("/models/{model_id}", summary="模型详情")
async def get_model(model_id: str):
    """获取模型详情"""
    model = model_trainer.get_model(model_id)
    if not model:
        raise HTTPException(status_code=404, detail="模型不存在")
    return ApiResponse(data=model)
