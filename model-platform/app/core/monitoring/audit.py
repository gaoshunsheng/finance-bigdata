"""操作审计日志模块 — 记录模型平台所有操作。

审计日志保留 5 年，记录内容包括:
- 操作时间、操作人、操作类型
- 操作对象 (模型/数据集/导出等)
- 操作前后快照 (before/after)
- 客户端IP、请求ID
"""
import hashlib
import json
import threading
from collections import OrderedDict
from datetime import datetime, timezone, timedelta
from typing import Any, Optional

from loguru import logger

# 北京时间
_BEIJING_TZ = timezone(timedelta(hours=8))

# 审计日志保留 5 年
_RETENTION_YEARS = 5

# 内存存储 (后续替换为 Elasticsearch/MySQL)
_audit_store: list[dict] = []
_store_lock = threading.Lock()

# 最大内存条数 (超出后循环写入)
_MAX_IN_MEMORY = 10000


class AuditLogger:
    """操作审计记录器"""

    def __init__(self):
        self._store = _audit_store
        self._lock = _store_lock

    def log(
        self,
        action: str,
        target_type: str,
        target_id: str,
        operator: str = "system",
        before_snapshot: Optional[dict] = None,
        after_snapshot: Optional[dict] = None,
        details: Optional[str] = None,
        client_ip: Optional[str] = None,
        request_id: Optional[str] = None,
    ) -> dict:
        """记录操作审计日志。

        Args:
            action: 操作类型 (CREATE/UPDATE/DELETE/TRAIN/EVALUATE/EXPORT/PREDICT/ACKNOWLEDGE)
            target_type: 操作对象类型 (MODEL/DATASET/EXPORT/ALERT)
            target_id: 操作对象ID
            operator: 操作人
            before_snapshot: 操作前快照
            after_snapshot: 操作后快照
            details: 详细说明
            client_ip: 客户端IP
            request_id: 请求追踪ID

        Returns:
            审计日志记录
        """
        entry = {
            "id": _generate_audit_id(),
            "timestamp": datetime.now(_BEIJING_TZ).isoformat(),
            "action": action,
            "target_type": target_type,
            "target_id": target_id,
            "operator": operator,
            "before_hash": _hash_snapshot(before_snapshot) if before_snapshot else None,
            "after_hash": _hash_snapshot(after_snapshot) if after_snapshot else None,
            "details": details,
            "client_ip": client_ip,
            "request_id": request_id,
            "retention_years": _RETENTION_YEARS,
        }

        with self._lock:
            if len(self._store) >= _MAX_IN_MEMORY:
                # 保留后半部分
                self._store[:] = self._store[_MAX_IN_MEMORY // 2:]
            self._store.append(entry)

        logger.info("AUDIT: {}", json.dumps(entry, ensure_ascii=False, default=str))
        return entry

    def log_training(
        self, model_id: str, algorithm: str, operator: str = "system", **kwargs
    ) -> dict:
        """记录模型训练审计"""
        return self.log(
            action="TRAIN",
            target_type="MODEL",
            target_id=model_id,
            operator=operator,
            after_snapshot={"algorithm": algorithm, **kwargs},
            details=f"模型训练: {algorithm}",
        )

    def log_prediction(
        self, model_id: str, features_count: int, operator: str = "system", **kwargs
    ) -> dict:
        """记录推理调用审计"""
        return self.log(
            action="PREDICT",
            target_type="MODEL",
            target_id=model_id,
            operator=operator,
            details=f"模型推理: 特征数={features_count}",
        )

    def log_export(
        self, model_id: str, export_format: str, operator: str = "system", **kwargs
    ) -> dict:
        """记录模型导出审计"""
        return self.log(
            action="EXPORT",
            target_type="MODEL",
            target_id=model_id,
            operator=operator,
            after_snapshot={"format": export_format, **kwargs},
            details=f"模型导出: {export_format}",
        )

    def log_alert_action(
        self, alert_id: str, action: str, operator: str = "system", **kwargs
    ) -> dict:
        """记录告警操作审计"""
        return self.log(
            action=action,
            target_type="ALERT",
            target_id=alert_id,
            operator=operator,
            details=f"告警操作: {action}",
        )

    def query(
        self,
        action: Optional[str] = None,
        target_type: Optional[str] = None,
        target_id: Optional[str] = None,
        operator: Optional[str] = None,
        limit: int = 100,
        offset: int = 0,
    ) -> list[dict]:
        """查询审计日志"""
        with self._lock:
            results = self._store.copy()

        if action:
            results = [r for r in results if r["action"] == action]
        if target_type:
            results = [r for r in results if r["target_type"] == target_type]
        if target_id:
            results = [r for r in results if r["target_id"] == target_id]
        if operator:
            results = [r for r in results if r["operator"] == operator]

        # 按时间倒序
        results.sort(key=lambda x: x["timestamp"], reverse=True)
        return results[offset : offset + limit]

    def count(self) -> int:
        """返回审计日志总数"""
        with self._lock:
            return len(self._store)


def _generate_audit_id() -> str:
    """生成审计日志ID"""
    import uuid

    return f"audit_{uuid.uuid4().hex[:12]}"


def _hash_snapshot(snapshot: dict) -> str:
    """计算快照哈希 (不存储原始敏感数据)"""
    content = json.dumps(snapshot, sort_keys=True, default=str, ensure_ascii=False)
    return hashlib.sha256(content.encode()).hexdigest()[:16]


# 全局单例
audit_logger = AuditLogger()
