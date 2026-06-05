"""模型导出 API"""
from fastapi import APIRouter, HTTPException

from app.schemas import ApiResponse, ExportRequest, ExportFormat
from app.core.export.exporter import model_exporter
from app.core.training.trainer import model_trainer

router = APIRouter()


@router.post("/export", summary="导出模型")
async def export_model(request: ExportRequest):
    """导出模型为 PMML 或 ONNX 格式"""
    model = model_trainer.get_model(request.model_id)
    if not model:
        raise HTTPException(status_code=404, detail="模型不存在")

    try:
        if request.format == ExportFormat.PMML:
            result = model_exporter.export_pmml(model, version=request.version)
        elif request.format == ExportFormat.ONNX:
            features = model.get("features", [])
            result = model_exporter.export_onnx(model, feature_names=features, version=request.version)
        else:
            raise HTTPException(status_code=400, detail=f"不支持的导出格式: {request.format}")

        return ApiResponse(data=result)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/exports", summary="导出记录列表")
async def list_exports():
    """获取所有模型导出记录"""
    exports = model_exporter.list_exports()
    return ApiResponse(data=exports)


@router.get("/exports/{export_id}", summary="导出记录详情")
async def get_export(export_id: str):
    """获取导出记录详情"""
    export = model_exporter.get_export(export_id)
    if not export:
        raise HTTPException(status_code=404, detail="导出记录不存在")
    return ApiResponse(data=export)
