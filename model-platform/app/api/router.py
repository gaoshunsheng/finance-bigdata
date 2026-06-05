"""API route definitions for the Model Platform"""
from fastapi import APIRouter

router = APIRouter()

# Sub-routers will be included from individual endpoint modules
from app.api.endpoints import (
    data_prep,
    training,
    evaluation,
    export,
    inference,
    monitoring,
)

router.include_router(data_prep.router, prefix="/data", tags=["数据准备"])
router.include_router(training.router, prefix="/training", tags=["模型训练"])
router.include_router(evaluation.router, prefix="/evaluation", tags=["模型评估"])
router.include_router(export.router, prefix="/export", tags=["模型导出"])
router.include_router(inference.router, prefix="/inference", tags=["推理服务"])
router.include_router(monitoring.router, prefix="/monitoring", tags=["模型监控"])
