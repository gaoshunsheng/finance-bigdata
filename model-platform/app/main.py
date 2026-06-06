"""FastAPI application entry point"""
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from loguru import logger

from app.api import router as api_router
from app.config import DEBUG, CORS_ORIGINS

from app.core.inference.inference_service import inference_service


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理 — 替代弃用的 on_event 装饰器"""
    logger.info("Model Platform starting up...")
    inference_service.clear_cache()
    yield
    # 关闭时清理资源
    inference_service.clear_cache()
    logger.info("Model Platform shutting down...")


app = FastAPI(
    title="风控模型平台 API",
    description="Model training, evaluation, deployment and monitoring service",
    version="0.1.0",
    debug=DEBUG,
    lifespan=lifespan,
)

# 安全修复: CORS 不再使用 allow_origins=["*"] + allow_credentials=True
# 改为从配置读取允许的源列表
app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_router, prefix="/api/v1")


@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {"status": "UP", "service": "model-platform"}
