"""模型训练 API"""
import asyncio
from concurrent.futures import ProcessPoolExecutor

from fastapi import APIRouter, HTTPException

from app.schemas import ApiResponse, TrainingRequest, TrainingResult
from app.core.training.trainer import model_trainer

router = APIRouter()

# Process pool for CPU-bound training tasks to avoid blocking the async event loop
_process_pool = ProcessPoolExecutor(max_workers=2)


@router.post("/train", summary="发起模型训练")
async def train_model(request: TrainingRequest):
    """发起模型训练任务"""
    loop = asyncio.get_event_loop()
    try:
        result = await loop.run_in_executor(
            _process_pool, model_trainer.train, request.model_dump()
        )
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
    # Strip internal fields that cannot be JSON-serialized
    public_keys = {
        "model_id", "name", "algorithm", "status",
        "best_params", "cv_scores", "cv_mean", "cv_std",
        "training_time_seconds", "feature_importance",
        "created_at", "description",
    }
    return ApiResponse(data={k: v for k, v in model.items() if k in public_keys})
