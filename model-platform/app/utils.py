"""Utility functions"""
import hashlib
import json
import uuid
from datetime import datetime
from typing import Any


def generate_id(prefix: str = "id") -> str:
    """生成唯一ID"""
    return f"{prefix}_{uuid.uuid4().hex[:12]}"


def now_beijing() -> datetime:
    """获取当前北京时间"""
    from datetime import timezone, timedelta
    tz = timezone(timedelta(hours=8))
    return datetime.now(tz)


def now_str() -> str:
    """当前时间字符串 (北京时间)"""
    return now_beijing().strftime("%Y-%m-%d %H:%M:%S")


def dict_hash(d: dict[str, Any]) -> str:
    """计算字典的MD5哈希"""
    content = json.dumps(d, sort_keys=True, default=str)
    return hashlib.md5(content.encode()).hexdigest()[:12]
