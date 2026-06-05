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

# Server
HOST = os.getenv("MODEL_PLATFORM_HOST", "0.0.0.0")
PORT = int(os.getenv("MODEL_PLATFORM_PORT", "8082"))
DEBUG = os.getenv("MODEL_PLATFORM_DEBUG", "false").lower() == "true"

# Database
DATABASE_URL = os.getenv(
    "MODEL_PLATFORM_DB_URL",
    "mysql+pymysql://root:password@localhost:3306/model_platform"
)

# Redis
REDIS_URL = os.getenv("MODEL_PLATFORM_REDIS_URL", "redis://localhost:6379/2")

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
