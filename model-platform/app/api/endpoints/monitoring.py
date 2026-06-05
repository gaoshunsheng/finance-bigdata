"""模型监控 API"""
from fastapi import APIRouter, HTTPException

from app.schemas import ApiResponse
from app.core.monitoring.monitor import model_monitor

router = APIRouter()


@router.get("/dashboard/{model_id}", summary="监控看板")
async def get_dashboard(model_id: str, model_name: str = None):
    """获取模型监控看板数据"""
    dashboard = model_monitor.get_dashboard(model_id, model_name)
    return ApiResponse(data=dashboard)


@router.post("/psi/check", summary="PSI检测")
async def check_psi(model_id: str, reference_dist: list[float], current_dist: list[float]):
    """执行PSI检测"""
    result = model_monitor.calculate_daily_psi(
        model_id=model_id,
        reference_dist=reference_dist,
        current_dist=current_dist,
    )
    return ApiResponse(data=result)


@router.post("/drift/check", summary="特征漂移检测")
async def check_feature_drift(
    model_id: str,
    reference_features: dict[str, list[float]],
    current_features: dict[str, list[float]],
):
    """检测特征分布漂移"""
    result = model_monitor.calculate_feature_drift(
        model_id=model_id,
        reference_features=reference_features,
        current_features=current_features,
    )
    return ApiResponse(data=result)


@router.post("/ks/track", summary="KS趋势记录")
async def track_ks(model_id: str, ks_value: float):
    """记录KS指标"""
    result = model_monitor.track_ks_trend(
        model_id=model_id,
        ks_value=ks_value,
    )
    return ApiResponse(data=result)


@router.get("/alerts", summary="告警列表")
async def list_alerts(model_id: str = None):
    """获取活跃告警"""
    alerts = model_monitor.list_alerts(model_id)
    return ApiResponse(data=alerts)


@router.post("/alerts/{alert_id}/acknowledge", summary="确认告警")
async def acknowledge_alert(alert_id: str):
    """确认告警 (标记为已处理)"""
    result = model_monitor.acknowledge_alert(alert_id)
    if not result:
        raise HTTPException(status_code=404, detail="告警不存在")
    return ApiResponse(data={"alert_id": alert_id, "status": "acknowledged"})
