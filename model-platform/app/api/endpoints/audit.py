"""审计日志 API 端点"""
from fastapi import APIRouter, Query

from app.core.monitoring.audit import audit_logger
from app.schemas import ApiResponse

router = APIRouter(prefix="/audit", tags=["审计日志"])


@router.get("/logs", response_model=ApiResponse)
async def list_audit_logs(
    action: str | None = None,
    target_type: str | None = None,
    target_id: str | None = None,
    operator: str | None = None,
    limit: int = Query(default=100, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
):
    """查询审计日志"""
    logs = audit_logger.query(
        action=action,
        target_type=target_type,
        target_id=target_id,
        operator=operator,
        limit=limit,
        offset=offset,
    )
    return ApiResponse(
        success=True,
        data={"logs": logs, "total": audit_logger.count()},
        message="查询成功",
    )


@router.get("/stats", response_model=ApiResponse)
async def audit_stats():
    """审计日志统计"""
    all_logs = audit_logger.query(limit=10000)
    from collections import Counter

    action_counts = Counter(r["action"] for r in all_logs)
    target_counts = Counter(r["target_type"] for r in all_logs)

    return ApiResponse(
        success=True,
        data={
            "total": len(all_logs),
            "by_action": dict(action_counts),
            "by_target_type": dict(target_counts),
            "retention_years": 5,
        },
        message="统计成功",
    )
