"""
数据库会话管理

提供 SQLAlchemy 引擎、会话工厂和表初始化功能。
使用同步 SQLAlchemy 以保持与现有代码风格一致。
"""

from __future__ import annotations

from typing import Generator

from loguru import logger
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, Session

from app.db.models import Base

# ──────────────────────────────────────────────
# 引擎 & 会话工厂（懒初始化）
# ──────────────────────────────────────────────

_engine = None
_session_factory: sessionmaker | None = None


def _get_database_url() -> str:
    """获取数据库 URL，支持环境变量和配置文件两种方式。"""
    try:
        from app.config import DATABASE_URL
        return DATABASE_URL
    except RuntimeError:
        # DATABASE_URL 未配置时返回 None，表示不使用数据库
        return ""


def _ensure_engine():
    """确保引擎已创建（懒初始化）。"""
    global _engine, _session_factory
    if _engine is not None:
        return True

    db_url = _get_database_url()
    if not db_url:
        logger.warning("DATABASE_URL 未配置，数据库持久化已禁用")
        return False

    try:
        # MySQL 特有连接参数
        connect_args: dict = {}
        if db_url.startswith("mysql"):
            connect_args["charset"] = "utf8mb4"

        _engine = create_engine(
            db_url,
            echo=False,
            pool_pre_ping=True,       # 自动检测断开的连接
            pool_recycle=3600,        # 每小时回收连接
            pool_size=5,
            max_overflow=10,
            connect_args=connect_args,
        )
        _session_factory = sessionmaker(
            bind=_engine,
            autocommit=False,
            autoflush=False,
        )
        logger.info(f"数据库引擎已创建: {db_url.split('@')[-1] if '@' in db_url else db_url}")
        return True
    except Exception as e:
        logger.error(f"数据库引擎创建失败: {e}")
        _engine = None
        _session_factory = None
        return False


# 对外暴露的属性访问
@property
def engine(_):
    """兼容旧代码中直接访问 engine 的场景"""
    _ensure_engine()
    return _engine


# ──────────────────────────────────────────────
# 模块级引用（在 _ensure_engine 后有效）
# ──────────────────────────────────────────────

# 为外部模块提供直接访问（会在首次使用时初始化）
class _EngineAccessor:
    """延迟初始化引擎代理"""
    def __getattr__(self, name):
        _ensure_engine()
        if _engine is None:
            return None
        return getattr(_engine, name)


class _SessionFactoryAccessor:
    """延迟初始化会话工厂代理"""
    def __call__(self, **kwargs) -> Session | None:
        _ensure_engine()
        if _session_factory is None:
            return None
        return _session_factory(**kwargs)


engine_proxy = _EngineAccessor()
SessionLocal = _SessionFactoryAccessor()


def get_session() -> Session | None:
    """获取数据库会话，DB 不可用时返回 None。"""
    _ensure_engine()
    if _session_factory is None:
        return None
    return _session_factory()


def get_db() -> Generator[Session, None, None]:
    """
    FastAPI 依赖注入: 获取数据库会话。

    用法:
        @router.get("/models")
        def list_models(db: Session = Depends(get_db)):
            ...

    注意: DB 不可用时 db 为 None，调用方需自行处理。
    """
    session = get_session()
    if session is None:
        yield None
        return
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


# ──────────────────────────────────────────────
# 表初始化
# ──────────────────────────────────────────────

def init_db() -> bool:
    """
    初始化数据库: 创建所有表（如不存在）。

    Returns:
        True 表示初始化成功, False 表示 DB 不可用
    """
    if not _ensure_engine():
        logger.warning("数据库不可用，跳过表初始化")
        return False

    try:
        Base.metadata.create_all(bind=_engine)
        logger.info("数据库表初始化完成")
        return True
    except Exception as e:
        logger.error(f"数据库表初始化失败: {e}")
        return False
