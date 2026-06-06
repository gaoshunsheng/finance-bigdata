"""推理服务 API"""
from fastapi import APIRouter, HTTPException

from app.schemas import (
    ApiResponse,
    InferenceRequest,
    InferenceResponse,
    BatchInferenceRequest,
    BatchInferenceResponse,
)
from app.core.inference.inference_service import inference_service

router = APIRouter()


@router.post("/predict", summary="单笔推理", response_model=ApiResponse)
async def predict(request: InferenceRequest):
    """单笔模型推理"""
    try:
        result = inference_service.predict(
            model_id=request.model_id,
            features_dict=request.features,
        )

        if request.return_explanation:
            explanation = inference_service.explain(
                model_id=request.model_id,
                features_dict=request.features,
            )
            result["explanation"] = explanation

        return ApiResponse(data=result)
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/batch-predict", summary="批量推理", response_model=ApiResponse)
async def batch_predict(request: BatchInferenceRequest):
    """批量模型推理"""
    try:
        import time
        start = time.time()

        batch_result = inference_service.batch_predict(
            model_id=request.model_id,
            records=request.records,
        )

        predictions = batch_result["predictions"]
        latency_ms = (time.time() - start) * 1000

        results = predictions
        if request.return_explanation:
            results = []
            for idx, pred in enumerate(predictions):
                # Use the original record features for explanation
                record_features = request.records[idx] if idx < len(request.records) else {}
                explanation = inference_service.explain(
                    model_id=request.model_id,
                    features_dict=record_features,
                )
                pred_copy = {k: v for k, v in pred.items() if k != "_features"}
                pred_copy["explanation"] = explanation
                results.append(pred_copy)
        else:
            results = [{k: v for k, v in p.items() if k != "_features"} for p in predictions]

        return ApiResponse(data={
            "model_id": request.model_id,
            "predictions": results,
            "total_count": len(results),
            "latency_ms": round(latency_ms, 2),
        })
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
