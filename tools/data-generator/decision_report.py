#!/usr/bin/env python3
"""
决策日志报表生成器。

从 ES 决策日志索引或本地 JSONL 文件读取决策日志，生成：
  - 决策总览统计 (通过率/拒绝率/评分分布/延迟)
  - 规则命中率排名
  - 评分分布直方图
  - 时间维度趋势 (按日/按小时)
  - Markdown 报告 + 4 个 CSV 数据文件

用法:
  python decision_report.py                          # 自动检测数据源
  python decision_report.py --source es              # 从 ES 读取
  python decision_report.py --source local --input output/decision_logs_*.jsonl
  python decision_report.py --source local --input output/
"""

import argparse
import csv
import json
import os
import sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
from typing import Optional

import httpx
from loguru import logger

from config import get_config, beijing_now

# 输出目录
OUTPUT_DIR = Path(__file__).parent / "output" / "reports"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)


# ---------------------------------------------------------------------------
# 数据源
# ---------------------------------------------------------------------------

def load_from_es(cfg: dict) -> list[dict]:
    """从 Elasticsearch 加载决策日志。"""
    es_cfg = cfg.get("elasticsearch", {})
    host = es_cfg.get("host", "localhost")
    port = es_cfg.get("port", 9200)
    index_prefix = es_cfg.get("index_prefix", "decision-log-")
    now = datetime.now()
    index_name = f"{index_prefix}{now.strftime('%Y.%m')}"

    results = []
    try:
        with httpx.Client(timeout=30.0) as client:
            # 获取总数
            resp = client.get(f"http://{host}:{port}/{index_name}/_count")
            total = resp.json().get("count", 0) if resp.status_code == 200 else 0
            print(f"ES 索引 {index_name}: {total} 条日志")

            if total == 0:
                return []

            # 滚动查询
            scroll = "2m"
            resp = client.post(
                f"http://{host}:{port}/{index_name}/_search?scroll={scroll}",
                json={"size": 1000, "sort": [{"timestamp": "asc"}]},
                headers={"Content-Type": "application/json"},
            )

            while True:
                data = resp.json()
                hits = data.get("hits", {}).get("hits", [])
                for hit in hits:
                    results.append(hit["_source"])

                scroll_id = data.get("_scroll_id")
                if not hits or not scroll_id:
                    break

                resp = client.post(
                    f"http://{host}:{port}/_search/scroll",
                    json={"scroll": scroll, "scroll_id": scroll_id},
                    headers={"Content-Type": "application/json"},
                )

            # 清理 scroll
            if scroll_id:
                client.delete(f"http://{host}:{port}/_search/scroll",
                              json={"scroll_id": scroll_id})

    except Exception as e:
        logger.warning(f"ES 查询失败: {e}")
        return []

    print(f"✓ 从 ES 加载 {len(results)} 条日志")
    return results


def load_from_local(input_path: str) -> list[dict]:
    """从本地 JSONL 文件加载决策日志。"""
    path = Path(input_path)
    results = []

    if path.is_dir():
        # 加载目录下所有 .jsonl 文件
        for f in sorted(path.glob("decision_logs_*.jsonl")):
            results.extend(_read_jsonl(f))
    elif path.is_file():
        results.extend(_read_jsonl(path))
    else:
        # glob 匹配
        import glob as _glob
        for f in sorted(_glob.glob(input_path)):
            results.extend(_read_jsonl(Path(f)))

    print(f"✓ 从本地文件加载 {len(results)} 条日志")
    return results


def _read_jsonl(filepath: Path) -> list[dict]:
    """读取单个 JSONL 文件。"""
    results = []
    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                try:
                    results.append(json.loads(line))
                except json.JSONDecodeError:
                    pass
    return results


# ---------------------------------------------------------------------------
# 统计计算 (Tasks 10.1 ~ 10.4)
# ---------------------------------------------------------------------------

class StatsCalculator:
    """各类统计计算。"""

    @staticmethod
    def overview(results: list[dict]) -> dict:
        """决策总览统计。"""
        total = len(results)
        if total == 0:
            return {"total": 0}

        passed = sum(1 for r in results if r.get("decisionResult") == "PASS")
        rejected = sum(1 for r in results if r.get("decisionResult") == "REJECT")
        reviewed = sum(1 for r in results if r.get("decisionResult") == "REVIEW")
        manual = sum(1 for r in results if r.get("decisionResult") == "MANUAL")
        unknown = total - passed - rejected - reviewed - manual

        scores = [r.get("score") for r in results if r.get("score") is not None]
        durations = [r.get("durationMs", 0) for r in results if r.get("durationMs")]

        def percentile(sorted_vals, pct):
            if not sorted_vals:
                return 0
            idx = int(len(sorted_vals) * pct / 100)
            return sorted_vals[min(idx, len(sorted_vals) - 1)]

        scores_sorted = sorted(scores) if scores else []
        dur_sorted = sorted(durations) if durations else []

        return {
            "total": total,
            "passed": passed, "pass_rate": round(passed / total * 100, 1) if total else 0,
            "rejected": rejected, "reject_rate": round(rejected / total * 100, 1) if total else 0,
            "reviewed": reviewed, "review_rate": round(reviewed / total * 100, 1) if total else 0,
            "manual": manual, "manual_rate": round(manual / total * 100, 1) if total else 0,
            "unknown": unknown,
            "avg_score": round(sum(scores) / len(scores), 0) if scores else None,
            "median_score": percentile(scores_sorted, 50) if scores_sorted else None,
            "score_p50": percentile(scores_sorted, 50),
            "score_p95": percentile(scores_sorted, 95),
            "score_p99": percentile(scores_sorted, 99),
            "avg_latency_ms": round(sum(durations) / len(durations), 1) if durations else None,
            "latency_p50_ms": percentile(dur_sorted, 50),
            "latency_p95_ms": percentile(dur_sorted, 95),
            "latency_p99_ms": percentile(dur_sorted, 99),
        }

    @staticmethod
    def rule_hit_stats(results: list[dict]) -> list[dict]:
        """规则命中率统计。"""
        hit_counter = Counter()
        total = len(results)

        for r in results:
            hit_rules = r.get("hitRules", [])
            if isinstance(hit_rules, list):
                for rule in hit_rules:
                    hit_counter[str(rule)] += 1
            elif isinstance(hit_rules, str):
                try:
                    parsed = json.loads(hit_rules)
                    if isinstance(parsed, list):
                        for rule in parsed:
                            hit_counter[str(rule)] += 1
                except json.JSONDecodeError:
                    pass

        return sorted(
            [
                {
                    "rule": rule,
                    "hit_count": count,
                    "hit_rate": round(count / total * 100, 2) if total else 0,
                }
                for rule, count in hit_counter.items()
            ],
            key=lambda x: x["hit_count"],
            reverse=True,
        )

    @staticmethod
    def score_distribution(results: list[dict]) -> list[dict]:
        """评分分布统计（带决策结果交叉）。"""
        buckets = [
            ("< 500", 0, 500),
            ("500-549", 500, 550),
            ("550-649", 550, 650),
            ("650-749", 650, 750),
            ("≥ 750", 750, 9999),
        ]

        dist = []
        for label, lo, hi in buckets:
            in_range = [r for r in results
                        if r.get("score") is not None and lo <= r["score"] < hi]
            count = len(in_range)
            total = len(results)
            pct = round(count / total * 100, 1) if total else 0

            decision_breakdown = {"PASS": 0, "REJECT": 0, "REVIEW": 0, "MANUAL": 0, "UNKNOWN": 0}
            for r in in_range:
                dr = r.get("decisionResult", "UNKNOWN")
                decision_breakdown[dr] = decision_breakdown.get(dr, 0) + 1

            dist.append({
                "score_range": label,
                "count": count,
                "percentage": pct,
                **{f"{k}_count": v for k, v in decision_breakdown.items()},
            })

        return dist

    @staticmethod
    def time_trends(results: list[dict]) -> dict:
        """时间维度趋势统计。"""
        daily = defaultdict(lambda: {"total": 0, "passed": 0, "rejected": 0, "scores": [], "durations": []})
        hourly = defaultdict(lambda: {"total": 0, "passed": 0, "rejected": 0, "scores": [], "durations": []})

        for r in results:
            ts = r.get("timestamp", "")
            if not ts:
                continue

            try:
                # 尝试多种时间格式
                for fmt in ["%Y-%m-%d %H:%M:%S", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%dT%H:%M:%SZ"]:
                    try:
                        dt = datetime.strptime(ts[:19], "%Y-%m-%d %H:%M:%S" if " " in ts else "%Y-%m-%dT%H:%M:%S")
                        break
                    except ValueError:
                        continue
                else:
                    continue

                day_key = dt.strftime("%Y-%m-%d")
                hour_key = dt.strftime("%H:00")

                daily[day_key]["total"] += 1
                hourly[hour_key]["total"] += 1

                if r.get("decisionResult") == "PASS":
                    daily[day_key]["passed"] += 1
                    hourly[hour_key]["passed"] += 1
                elif r.get("decisionResult") == "REJECT":
                    daily[day_key]["rejected"] += 1
                    hourly[hour_key]["rejected"] += 1

                if r.get("score") is not None:
                    daily[day_key]["scores"].append(r["score"])
                    hourly[hour_key]["scores"].append(r["score"])
                if r.get("durationMs"):
                    daily[day_key]["durations"].append(r["durationMs"])
                    hourly[hour_key]["durations"].append(r["durationMs"])
            except (ValueError, IndexError):
                pass

        def format_trend(data: dict) -> list[dict]:
            result = []
            for key in sorted(data.keys()):
                d = data[key]
                scores = d["scores"]
                durs = d["durations"]
                total = d["total"]
                result.append({
                    "period": key,
                    "total": total,
                    "pass_rate": round(d["passed"] / total * 100, 1) if total else 0,
                    "reject_rate": round(d["rejected"] / total * 100, 1) if total else 0,
                    "avg_score": round(sum(scores) / len(scores), 0) if scores else None,
                    "avg_latency_ms": round(sum(durs) / len(durs), 1) if durs else None,
                })
            return result

        return {
            "daily": format_trend(daily),
            "hourly": format_trend(hourly),
        }


# ---------------------------------------------------------------------------
# 报表生成 (Tasks 10.5 ~ 10.8)
# ---------------------------------------------------------------------------

class ReportGenerator:
    """报表生成器。"""

    def __init__(self, cfg: dict):
        self.cfg = cfg
        self.calc = StatsCalculator()
        self.timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")

    def generate(self, results: list[dict], source_label: str = "local") -> str:
        """
        生成完整报表，返回 Markdown 报告文件路径。
        """
        if not results:
            print("无数据，跳过报表生成")
            return ""

        print(f"\n生成报表 (数据源: {source_label}, {len(results)} 条)...")

        # 计算各项统计
        overview = self.calc.overview(results)
        rule_hits = self.calc.rule_hit_stats(results)
        score_dist = self.calc.score_distribution(results)
        trends = self.calc.time_trends(results)

        # 生成文件
        md_path = self._write_markdown(overview, rule_hits, score_dist, trends, source_label)
        self._write_csv(overview, rule_hits, score_dist, trends)

        return md_path

    def _write_markdown(self, overview: dict, rule_hits: list[dict],
                        score_dist: list[dict], trends: dict,
                        source_label: str) -> str:
        """生成 Markdown 报告。"""
        path = OUTPUT_DIR / f"decision_report_{self.timestamp}.md"

        lines = [
            f"# 决策引擎运行报告",
            f"",
            f"**生成时间**: {beijing_now()}",
            f"**数据源**: {source_label}",
            f"**总记录数**: {overview.get('total', 0)}",
            f"",
            "---",
            "",
            "## 1. 决策总览",
            "",
            f"| 指标 | 数值 |",
            f"|------|------|",
            f"| 总请求数 | {overview.get('total', 0)} |",
            f"| 通过 (PASS) | {overview.get('passed', 0)} ({overview.get('pass_rate', 0)}%) |",
            f"| 拒绝 (REJECT) | {overview.get('rejected', 0)} ({overview.get('reject_rate', 0)}%) |",
            f"| 人工复核 (REVIEW) | {overview.get('reviewed', 0)} ({overview.get('review_rate', 0)}%) |",
            f"| 人工介入 (MANUAL) | {overview.get('manual', 0)} ({overview.get('manual_rate', 0)}%) |",
            f"| 平均评分 | {overview.get('avg_score', 'N/A')} |",
            f"| 评分中位数 (P50) | {overview.get('score_p50', 'N/A')} |",
            f"| 评分 P95 | {overview.get('score_p95', 'N/A')} |",
            f"| 评分 P99 | {overview.get('score_p99', 'N/A')} |",
            f"| 平均延迟 (ms) | {overview.get('avg_latency_ms', 'N/A')} |",
            f"| 延迟 P50 (ms) | {overview.get('latency_p50_ms', 'N/A')} |",
            f"| 延迟 P95 (ms) | {overview.get('latency_p95_ms', 'N/A')} |",
            f"| 延迟 P99 (ms) | {overview.get('latency_p99_ms', 'N/A')} |",
            f"",
            "---",
            "",
            "## 2. 规则命中率 TOP 15",
            "",
            "| 排名 | 规则 | 命中次数 | 命中率 |",
            "|------|------|----------|--------|",
        ]

        for i, rh in enumerate(rule_hits[:15], 1):
            lines.append(f"| {i} | {rh['rule']} | {rh['hit_count']} | {rh['hit_rate']}% |")

        if not rule_hits:
            lines.append("| - | 无规则命中数据 | - | - |")

        lines.extend([
            "",
            "---",
            "",
            "## 3. 评分分布",
            "",
            "| 评分区间 | 数量 | 占比 | PASS | REJECT | REVIEW | MANUAL |",
            "|----------|------|------|------|--------|--------|--------|",
        ])

        for sd in score_dist:
            lines.append(
                f"| {sd['score_range']} | {sd['count']} | {sd['percentage']}% "
                f"| {sd.get('PASS_count', 0)} | {sd.get('REJECT_count', 0)} "
                f"| {sd.get('REVIEW_count', 0)} | {sd.get('MANUAL_count', 0)} |"
            )

        lines.extend([
            "",
            "---",
            "",
            "## 4. 按日趋势",
            "",
            "| 日期 | 请求数 | 通过率 | 拒绝率 | 平均评分 | 平均延迟(ms) |",
            "|------|--------|--------|--------|----------|-------------|",
        ])

        for t in trends.get("daily", []):
            lines.append(
                f"| {t['period']} | {t['total']} | {t['pass_rate']}% | {t['reject_rate']}% "
                f"| {t.get('avg_score', 'N/A')} | {t.get('avg_latency_ms', 'N/A')} |"
            )

        if not trends.get("daily"):
            lines.append("| - | - | - | - | - | - |")

        lines.extend([
            "",
            "## 5. 按小时趋势",
            "",
            "| 时段 | 请求数 | 通过率 | 拒绝率 | 平均评分 |",
            "|------|--------|--------|--------|----------|",
        ])

        for t in trends.get("hourly", []):
            lines.append(
                f"| {t['period']} | {t['total']} | {t['pass_rate']}% "
                f"| {t['reject_rate']}% | {t.get('avg_score', 'N/A')} |"
            )

        if not trends.get("hourly"):
            lines.append("| - | - | - | - | - |")

        lines.extend([
            "",
            "---",
            f"*报告由 decision_report.py 自动生成*",
        ])

        with open(path, "w", encoding="utf-8") as f:
            f.write("\n".join(lines))

        print(f"✓ Markdown 报告: {path}")
        return str(path)

    def _write_csv(self, overview: dict, rule_hits: list[dict],
                   score_dist: list[dict], trends: dict):
        """生成 CSV 数据文件。"""

        # 总览 CSV
        path = OUTPUT_DIR / f"decision_stats_{self.timestamp}.csv"
        with open(path, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=overview.keys())
            writer.writeheader()
            writer.writerow(overview)
        print(f"✓ 总览 CSV: {path}")

        # 规则命中 CSV
        path = OUTPUT_DIR / f"rule_hit_stats_{self.timestamp}.csv"
        with open(path, "w", newline="", encoding="utf-8") as f:
            if rule_hits:
                writer = csv.DictWriter(f, fieldnames=rule_hits[0].keys())
                writer.writeheader()
                writer.writerows(rule_hits)
        print(f"✓ 规则命中 CSV: {path}")

        # 评分分布 CSV
        path = OUTPUT_DIR / f"score_distribution_{self.timestamp}.csv"
        with open(path, "w", newline="", encoding="utf-8") as f:
            if score_dist:
                writer = csv.DictWriter(f, fieldnames=score_dist[0].keys())
                writer.writeheader()
                writer.writerows(score_dist)
        print(f"✓ 评分分布 CSV: {path}")

        # 每日趋势 CSV
        path = OUTPUT_DIR / f"daily_trend_{self.timestamp}.csv"
        daily = trends.get("daily", [])
        with open(path, "w", newline="", encoding="utf-8") as f:
            if daily:
                writer = csv.DictWriter(f, fieldnames=daily[0].keys())
                writer.writeheader()
                writer.writerows(daily)
        print(f"✓ 每日趋势 CSV: {path}")


# ---------------------------------------------------------------------------
# 主入口
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="决策日志报表生成器")
    parser.add_argument("--source", choices=["es", "local", "auto"], default="auto",
                        help="数据源: es / local / auto (默认: auto，优先ES)")
    parser.add_argument("--input", type=str, default="output/",
                        help="本地数据源路径或 JSONL 文件 (默认: output/)")
    args = parser.parse_args()

    cfg = get_config()
    results: list[dict] = []
    source_label = "unknown"

    if args.source in ("auto", "es"):
        # 优先 ES
        results = load_from_es(cfg)
        if results:
            source_label = "ES"
        elif args.source == "es":
            print("ES 数据源为空，请检查索引是否存在")
            sys.exit(1)

    if not results and args.source in ("auto", "local"):
        results = load_from_local(args.input)
        if results:
            source_label = f"local ({args.input})"
        else:
            print(f"未找到数据源 (ES 和 local({args.input}) 均为空)")
            sys.exit(1)

    if not results:
        print("无决策日志数据")
        sys.exit(0)

    reporter = ReportGenerator(cfg)
    md_path = reporter.generate(results, source_label)

    if md_path:
        print(f"\n✓ 报表生成完成: {md_path}")


if __name__ == "__main__":
    main()
