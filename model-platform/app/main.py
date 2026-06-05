"""FastAPI application entry point"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from loguru import logger

from app.api import router as api_router
from app.config import DEBUG

app = FastAPI(
    title="风控模型平台 API",
    description="Model training, evaluation, deployment and monitoring service",
    version="0.1.0",
    debug=DEBUG,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_router, prefix="/api/v1")


@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {"status": "UP", "service": "model-platform"}


@app.on_event("startup")
async def startup():
    logger.info("Model Platform starting up...")


@app.on_event("shutdown")
async def shutdown():
    logger.info("Model Platform shutting down...")
