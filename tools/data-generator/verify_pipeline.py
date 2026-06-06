#!/usr/bin/env python3
"""
全链路验证脚本 — 6 个检查点验证完整数据管道。

CP1: MySQL 源数据    — 行数、外键、时序、值域
CP2: DataX 同步      — HDFS/ODS 文件和分区
CP3: Spark ETL       — DWD/DWS/ADS 聚合正确性
CP4: Canal CDC       — Kafka topics、消息格式
CP5: Flink 特征      — Redis key + HBase 行
CP6: 决策引擎        — API 健康检查 + ES 日志

用法:
    python verify_pipeline.py
    python verify_pipeline.py --checkpoint CP1,CP5
    python verify_pipeline.py --expected-counts output/expected_counts.json
"""

import argparse
import json
import sys
import time
from datetime import datetime
from pathlib import Path

from loguru import logger

from config import get_config, BEIJING_TZ, beijing_now
from verifiers.base import CheckResult, CheckStatus
from verifiers.mysql_check import MySQLCheck
from verifiers.hdfs_check import HdfsCheck
from verifiers.hive_check import HiveCheck
from verifiers.canal_check import CanalCheck
from verifiers.flink_check import FlinkCheck
from verifiers.decision_check import DecisionCheck


# ---------------------------------------------------------------------------
# 报告格式化
# ---------------------------------------------------------------------------

STATUS_ICONS = {
    "PASS": "✅",
    "WARN": "⚠️",
    "FAIL": "❌",
    "SKIP": "⏭️",
}


def format_report(results: list[CheckResult]) -> str:
    """生成文本格式验证报告。"""
    now = beijing_now()
    lines = [
        "═" * 60,
        "  全链路数据验证报告",
        f"  时间: {now}",
        "═" * 60,
        "",
    ]

    pass_count = 0
    warn_count = 0
    fail_count = 0
    skip_count = 0
    total_ms = 0

    for r in results:
        icon = STATUS_ICONS.get(r.status.value, "?")
        lines.append(f"  {icon} {r.checkpoint}: {r.name:<30s} {r.status.value}")
        lines.append(f"     耗时: {r.duration_ms}ms")

        for d in r.details:
            d_icon = STATUS_ICONS.get(d.status.value, "?")
            msg = d.message or d.actual or ""
            if d.expected and d.actual:
                msg = f"期望: {d.expected}, 实际: {d.actual}"
            lines.append(f"    {d_icon} {d.name}: {msg}")

        lines.append("")

        if r.status == CheckStatus.PASS:
            pass_count += 1
        elif r.status == CheckStatus.WARN:
            warn_count += 1
        elif r.status == CheckStatus.FAIL:
            fail_count += 1
        else:
            skip_count += 1
        total_ms += r.duration_ms

    lines.append("═" * 60)
    summary = f"  总结: {pass_count} PASS, {warn_count} WARN, {fail_count} FAIL, {skip_count} SKIP"
    lines.append(summary)
    lines.append(f"  总耗时: {total_ms / 1000:.1f}s")
    lines.append("═" * 60)

    return "\n".join(lines)


def save_report(report_text: str, results: list[CheckResult], output_dir: str = "output"):
    """保存报告到文件。"""
    output_path = Path(__file__).parent / output_dir
    output_path.mkdir(parents=True, exist_ok=True)

    timestamp = datetime.now(BEIJING_TZ).strftime("%Y%m%d_%H%M%S")

    # 文本报告
    txt_path = output_path / f"verify_report_{timestamp}.txt"
    with open(txt_path, "w", encoding="utf-8") as f:
        f.write(report_text)
    logger.info(f"文本报告已保存: {txt_path}")

    # JSON 报告
    json_path = output_path / f"verify_result_{timestamp}.json"
    json_data = {
        "generated_at": beijing_now(),
        "summary": {
            "pass": sum(1 for r in results if r.status == CheckStatus.PASS),
            "warn": sum(1 for r in results if r.status == CheckStatus.WARN),
            "fail": sum(1 for r in results if r.status == CheckStatus.FAIL),
            "skip": sum(1 for r in results if r.status == CheckStatus.SKIP),
        },
        "checkpoints": [
            {
                "checkpoint": r.checkpoint,
                "name": r.name,
                "status": r.status.value,
                "duration_ms": r.duration_ms,
                "details": [
                    {
                        "name": d.name,
                        "status": d.status.value,
                        "expected": d.expected,
                        "actual": d.actual,
                        "message": d.message,
                    }
                    for d in r.details
                ],
            }
            for r in results
        ],
    }
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(json_data, f, ensure_ascii=False, indent=2)
    logger.info(f"JSON 报告已保存: {json_path}")


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------

ALL_CHECKPOINTS = {
    "CP1": ("MySQL 源数据", MySQLCheck),
    "CP2": ("DataX 同步", HdfsCheck),
    "CP3": ("Spark ETL", HiveCheck),
    "CP4": ("Canal CDC", CanalCheck),
    "CP5": ("Flink 特征", FlinkCheck),
    "CP6": ("决策引擎", DecisionCheck),
}


def run_verification(checkpoints: list[str], expected_path: str):
    """执行指定的验证检查点。"""
    cfg = get_config()

    logger.info("=" * 60)
    logger.info("  全链路数据验证")
    logger.info(f"  检查点: {', '.join(checkpoints)}")
    logger.info("=" * 60)

    results: list[CheckResult] = []

    for cp in checkpoints:
        if cp not in ALL_CHECKPOINTS:
            logger.warning(f"未知的检查点: {cp}")
            continue

        name, verifier_cls = ALL_CHECKPOINTS[cp]
        logger.info(f"\n{'─' * 40}")
        logger.info(f"  运行 {cp}: {name}...")
        logger.info(f"{'─' * 40}")

        if cp == "CP1":
            verifier = verifier_cls(cfg, expected_path)
        else:
            verifier = verifier_cls(cfg)

        result = verifier.run()
        results.append(result)

        # 打印结果
        icon = STATUS_ICONS.get(result.status.value, "?")
        logger.info(f"  → {icon} {result.status.value} ({result.duration_ms}ms)")
        for d in result.details:
            d_icon = STATUS_ICONS.get(d.status.value, "?")
            msg = d.message or d.actual or ""
            logger.info(f"    {d_icon} {d.name}: {msg}")

    # 生成报告
    report = format_report(results)
    print("\n" + report)

    # 保存报告
    save_report(report, results)

    return results


def main():
    parser = argparse.ArgumentParser(description="全链路数据验证脚本")
    parser.add_argument(
        "--checkpoint",
        type=str,
        default="all",
        help="检查点 (CP1,CP2,... 或 all)，默认 all"
    )
    parser.add_argument(
        "--expected-counts",
        type=str,
        default="output/expected_counts.json",
        help="expected_counts.json 路径"
    )
    args = parser.parse_args()

    # 解析检查点
    if args.checkpoint.lower() == "all":
        checkpoints = list(ALL_CHECKPOINTS.keys())
    else:
        checkpoints = [cp.strip().upper() for cp in args.checkpoint.split(",")]

    results = run_verification(checkpoints, args.expected_counts)

    # 返回码: 有 FAIL 返回 1
    if any(r.status == CheckStatus.FAIL for r in results):
        sys.exit(1)


if __name__ == "__main__":
    main()
