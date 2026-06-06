"""
验证器基类 — 统一报告格式。
"""

import time
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Optional

from loguru import logger


class CheckStatus(str, Enum):
    PASS = "PASS"
    WARN = "WARN"
    FAIL = "FAIL"
    SKIP = "SKIP"


@dataclass
class Detail:
    """单项检查结果"""
    name: str
    status: CheckStatus
    expected: str = ""
    actual: str = ""
    message: str = ""

    @property
    def icon(self) -> str:
        return {"PASS": "✅", "WARN": "⚠️", "FAIL": "❌", "SKIP": "⏭️"}.get(self.status.value, "?")


@dataclass
class CheckResult:
    """检查点结果"""
    checkpoint: str
    name: str
    status: CheckStatus = CheckStatus.SKIP
    details: list[Detail] = field(default_factory=list)
    duration_ms: int = 0

    @property
    def icon(self) -> str:
        return {"PASS": "✅", "WARN": "⚠️", "FAIL": "❌", "SKIP": "⏭️"}.get(self.status.value, "?")

    def add_detail(self, name: str, status: CheckStatus,
                   expected: str = "", actual: str = "", message: str = ""):
        self.details.append(Detail(name=name, status=status, expected=expected,
                                    actual=actual, message=message))

    def compute_status(self):
        """根据子检查结果计算整体状态。"""
        statuses = [d.status for d in self.details]
        if any(s == CheckStatus.FAIL for s in statuses):
            self.status = CheckStatus.FAIL
        elif any(s == CheckStatus.WARN for s in statuses):
            self.status = CheckStatus.WARN
        elif all(s == CheckStatus.SKIP for s in statuses):
            self.status = CheckStatus.SKIP
        else:
            self.status = CheckStatus.PASS


class BaseVerifier:
    """验证器基类"""

    checkpoint: str = ""
    name: str = ""

    def __init__(self, cfg: dict):
        self.cfg = cfg
        self.result = CheckResult(checkpoint=self.checkpoint, name=self.name)

    def run(self) -> CheckResult:
        """执行验证，返回结果。"""
        start = time.time()
        try:
            self._check()
        except Exception as e:
            logger.error(f"[{self.checkpoint}] 验证异常: {e}")
            self.result.status = CheckStatus.FAIL
            self.result.add_detail("异常", CheckStatus.FAIL, message=str(e))
        finally:
            self.result.duration_ms = int((time.time() - start) * 1000)
            if self.result.status == CheckStatus.SKIP and self.result.details:
                self.result.compute_status()
        return self.result

    def _check(self):
        """子类实现具体检查逻辑。"""
        raise NotImplementedError

    def _print_result(self, result: CheckResult):
        """打印检查结果。"""
        logger.info(f"\n  {result.icon} {result.checkpoint}: {result.name}  {result.status.value}")
        for d in result.details:
            logger.info(f"    {d.icon} {d.name}: {d.message or d.actual}")
