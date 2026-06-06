"""Application configuration"""
import os
from pathlib import Path

# Base directories
BASE_DIR = Path(__file__).resolve().parent
MODEL_STORAGE_DIR = BASE_DIR / "storage" / "models"
DATA_STORAGE_DIR = BASE_DIR / "storage" / "data"
REPORT_STORAGE_DIR = BASE_DIR / "storage" / "reports"

# Ensure directories exist
for d in [MODEL_STORAGE_DIR, DATA_STORAGE_DIR, REPORT_STORAGE_DIR]:
    d.mkdir(parents=True, exist_ok=True)

# Server — support both MODEL_PLATFORM_* and generic APP_* / HOST env vars
HOST = os.getenv("MODEL_PLATFORM_HOST", os.getenv("APP_HOST", "0.0.0.0"))
PORT = int(os.getenv("MODEL_PLATFORM_PORT", os.getenv("APP_PORT", "8082")))
DEBUG = os.getenv("MODEL_PLATFORM_DEBUG", "false").lower() == "true"

# Database — support both MODEL_PLATFORM_DB_URL and DATABASE_URL
# SECURITY: No hardcoded password. Must be set via environment variable.
_db_url = os.getenv("MODEL_PLATFORM_DB_URL", os.getenv("DATABASE_URL"))
if not _db_url:
    raise RuntimeError(
        "Database URL must be configured via MODEL_PLATFORM_DB_URL or DATABASE_URL environment variable. "
        "Example: mysql+pymysql://user:password@localhost:3306/model_platform"
    )
DATABASE_URL = _db_url

# CORS — 从环境变量读取允许的源，多个源用逗号分隔
# 安全修复: 不再硬编码数据库密码为默认值
CORS_ORIGINS = [
    origin.strip()
    for origin in os.getenv("CORS_ORIGINS", "http://localhost:3000,http://localhost:8080").split(",")
    if origin.strip()
]

# Redis — support both MODEL_PLATFORM_REDIS_URL and REDIS_URL
REDIS_URL = os.getenv("MODEL_PLATFORM_REDIS_URL", os.getenv("REDIS_URL", "redis://localhost:6379/2"))

# gRPC
GRPC_PORT = int(os.getenv("MODEL_PLATFORM_GRPC_PORT", "50051"))

# Training defaults
DEFAULT_TRAIN_TEST_SPLIT = 0.2
DEFAULT_RANDOM_SEED = 42
DEFAULT_CV_FOLDS = 5

# PSI thresholds
PSI_WARNING_THRESHOLD = 0.1
PSI_CRITICAL_THRESHOLD = 0.25

# Model monitoring
PSI_CHECK_INTERVAL_HOURS = 24
KS_TREND_WINDOW_DAYS = 30
