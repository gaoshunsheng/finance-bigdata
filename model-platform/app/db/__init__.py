"""
数据库持久化层

提供 ORM 模型、数据库会话管理和仓库层 CRUD 操作。
支持 MySQL 持久化，同时保证 DB 不可用时应用仍可正常运行（优雅降级）。
"""

from app.db.session import init_db, get_db, engine, SessionLocal

__all__ = ["init_db", "get_db", "engine", "SessionLocal"]
