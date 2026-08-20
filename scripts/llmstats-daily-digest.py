#!/usr/bin/env python3
"""Aggregate LLM token/retry stats from docker logs over the last 24h.

Parses [LLMSTATS] lines from containers:
  - againspring-ai-user-orchestrator-dev
  - againspring-ai-user-orchestrator
  - againspring-llm-ai-user

Outputs: compact Korean digest to stdout or Telegram.

Usage:
  python3 scripts/llmstats-daily-digest.py [--dry-run] [--send <config_path>]

Environment variables (optional):
  TELEGRAM_BOT_TOKEN  — bot token for sendMessage
  TELEGRAM_CHAT_ID    — chat/channel ID

If --send with config_path: read JSON {telegram_bot_token, telegram_chat_id} from file.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Optional
from urllib import error, request
from zoneinfo import ZoneInfo

KST = ZoneInfo("Asia/Seoul")

# Containers to collect logs from (tolerate missing)
CONTAINERS = [
    "againspring-ai-user-orchestrator-dev",
    "againspring-ai-user-orchestrator",
    "againspring-llm-ai-user",
]


@dataclass
class LLMStat:
    ts: str
    sys: str  # AS or ASM
    type_: str  # POST, COMMENT, REPLY, etc.
    model: str
    attempt: int
    retry_reason: str
    in_tokens: int
    out_tokens: int
    cache_read: int
    cache_write: int
    cache_hit_pct: int
    result: str  # OK, RETRY, FAIL
    duration_ms: int
    corr_id: str


@dataclass
class AggregateStats:
    """Aggregated stats per sys+type."""

    sys: str
    type_: str
    total_calls: int = 0
    total_in_tokens: int = 0
    total_out_tokens: int = 0
    total_cache_read: int = 0
    total_cache_write: int = 0
    total_cache_hit: int = 0
    cache_hit_count: int = 0  # for averaging
    retry_count: int = 0  # calls with attempt > 1 or result == RETRY
    retry_reasons: dict[str, int] = field(default_factory=lambda: defaultdict(int))
    fail_count: int = 0  # result == FAIL
    total_duration_ms: int = 0
    calls: list[LLMStat] = field(default_factory=list)


def parse_llmstats_line(line: str) -> Optional[LLMStat]:
    """Parse a single [LLMSTATS] line.

    Expected format:
    [LLMSTATS] ts=... sys=AS|ASM type=... model=... attempt=N retryReason=... \
      in=N out=N cache_read=N cache_write=N cache_hit=N% result=OK|RETRY|FAIL \
      duration_ms=N corrId=...
    """
    if "[LLMSTATS]" not in line:
        return None

    try:
        # Extract key=value pairs after [LLMSTATS]
        stats_part = line.split("[LLMSTATS]", 1)[1].strip()
        pairs = {}
        for match in re.finditer(r"(\w+)=([^\s]+)", stats_part):
            key, val = match.groups()
            pairs[key] = val.strip('"')

        # Parse cache_hit with % stripped
        cache_hit_str = pairs.get("cache_hit", "0").rstrip("%")
        cache_hit_pct = int(cache_hit_str) if cache_hit_str else 0

        return LLMStat(
            ts=pairs.get("ts", ""),
            sys=pairs.get("sys", "AS"),
            type_=pairs.get("type", "UNKNOWN"),
            model=pairs.get("model", "haiku"),
            attempt=int(pairs.get("attempt", "1")),
            retry_reason=pairs.get("retryReason", "NONE"),
            in_tokens=int(pairs.get("in", "0")),
            out_tokens=int(pairs.get("out", "0")),
            cache_read=int(pairs.get("cache_read", "0")),
            cache_write=int(pairs.get("cache_write", "0")),
            cache_hit_pct=cache_hit_pct,
            result=pairs.get("result", "OK"),
            duration_ms=int(pairs.get("duration_ms", "0")),
            corr_id=pairs.get("corrId", ""),
        )
    except (ValueError, IndexError) as e:
        print(f"[WARN] Failed to parse LLMSTATS line: {e}", file=sys.stderr)
        return None


def fetch_docker_logs(container: str, hours: int = 24) -> str:
    """Fetch logs from a container over the last N hours. Tolerate missing container."""
    try:
        result = subprocess.run(
            ["docker", "logs", "--since", f"{hours}h", container],
            capture_output=True,
            text=True,
            timeout=30,
        )
        return result.stdout + result.stderr
    except subprocess.TimeoutExpired:
        print(f"[WARN] docker logs timeout for {container}", file=sys.stderr)
        return ""
    except Exception as e:
        print(f"[WARN] docker logs error for {container}: {e}", file=sys.stderr)
        return ""


def aggregate_stats(stats_list: list[LLMStat]) -> dict[tuple[str, str], AggregateStats]:
    """Group stats by (sys, type) and compute aggregates."""
    groups: dict[tuple[str, str], AggregateStats] = defaultdict(
        lambda: AggregateStats(sys="", type_="")
    )

    for stat in stats_list:
        key = (stat.sys, stat.type_)
        agg = groups[key]
        if not agg.sys:
            agg.sys = stat.sys
            agg.type_ = stat.type_

        agg.total_calls += 1
        agg.total_in_tokens += stat.in_tokens
        agg.total_out_tokens += stat.out_tokens
        agg.total_cache_read += stat.cache_read
        agg.total_cache_write += stat.cache_write
        agg.total_cache_hit += stat.cache_hit_pct
        agg.cache_hit_count += 1
        agg.total_duration_ms += stat.duration_ms
        agg.calls.append(stat)

        # Count as retry if attempt > 1 or result == RETRY
        if stat.attempt > 1 or stat.result == "RETRY":
            agg.retry_count += 1
            agg.retry_reasons[stat.retry_reason] += 1

        # Count failures
        if stat.result == "FAIL":
            agg.fail_count += 1

    return groups


def format_digest(groups: dict[tuple[str, str], AggregateStats]) -> str:
    """Format aggregated stats as a Korean digest."""
    if not groups:
        return "최근 24시간 LLM 통계: 로그 없음"

    now = datetime.now(KST)
    digest_lines = [
        f"📊 LLM 통계 다이제스트 ({now.strftime('%Y-%m-%d %H:%M')} KST)",
        "",
    ]

    # Sort by sys, then type
    sorted_groups = sorted(groups.items(), key=lambda x: (x[0][0], x[0][1]))

    total_calls = 0
    total_tokens = 0
    total_retries = 0

    for (sys, type_), agg in sorted_groups:
        total_calls += agg.total_calls
        total_tokens += agg.total_in_tokens + agg.total_out_tokens
        total_retries += agg.retry_count

        retry_rate = (agg.retry_count / agg.total_calls * 100) if agg.total_calls > 0 else 0
        avg_cache_hit = (agg.total_cache_hit / agg.cache_hit_count) if agg.cache_hit_count > 0 else 0
        avg_duration = agg.total_duration_ms / agg.total_calls if agg.total_calls > 0 else 0

        # Format retry reasons breakdown
        retry_reasons_str = ""
        if agg.retry_reasons:
            top_reasons = sorted(agg.retry_reasons.items(), key=lambda x: -x[1])[:3]
            retry_reasons_str = " | " + ", ".join(f"{k}:{v}" for k, v in top_reasons)

        digest_lines.append(
            f"  {sys}:{type_:12} | 호출: {agg.total_calls:4} | "
            f"토큰: {agg.total_in_tokens + agg.total_out_tokens:7} | "
            f"재시도: {agg.retry_count:3}회 ({retry_rate:5.1f}%){retry_reasons_str}"
        )

        if agg.fail_count > 0:
            digest_lines.append(f"    ⚠️ 실패: {agg.fail_count}회 | 캐시히트: {avg_cache_hit:.0f}% | 평균시간: {avg_duration:.0f}ms")

    digest_lines.extend(
        [
            "",
            f"📈 합계: 호출 {total_calls} | 토큰 {total_tokens:,} | 재시도 {total_retries}회 ({total_retries/max(1,total_calls)*100:.1f}%)",
        ]
    )

    return "\n".join(digest_lines)


def send_telegram(digest: str, token: str, chat_id: str, dry_run: bool = False) -> bool:
    """Send digest to Telegram. Return True on success."""
    if dry_run:
        print("[DRY-RUN] Would send to Telegram:")
        print(f"  Chat ID: {chat_id}")
        print(f"  Message: {digest[:100]}...")
        return True

    url = f"https://api.telegram.org/bot{token}/sendMessage"
    payload = json.dumps({"chat_id": chat_id, "text": digest}).encode()
    try:
        req = request.Request(url, data=payload, headers={"Content-Type": "application/json"})
        with request.urlopen(req, timeout=10) as resp:
            result = json.loads(resp.read().decode())
            if result.get("ok"):
                print("[INFO] Telegram message sent successfully")
                return True
            else:
                print(f"[ERROR] Telegram API error: {result.get('description')}", file=sys.stderr)
                return False
    except Exception as e:
        print(f"[ERROR] Failed to send Telegram message: {e}", file=sys.stderr)
        return False


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Aggregate LLM stats from docker logs over last 24h"
    )
    parser.add_argument(
        "--dry-run", action="store_true", help="Print digest instead of sending"
    )
    parser.add_argument(
        "--send", type=str, metavar="CONFIG", help="Config JSON file with telegram credentials"
    )
    args = parser.parse_args()

    # Collect logs from all containers
    all_logs = ""
    for container in CONTAINERS:
        logs = fetch_docker_logs(container, hours=24)
        all_logs += logs + "\n"

    # Parse all LLMSTATS lines
    stats_list = []
    for line in all_logs.split("\n"):
        stat = parse_llmstats_line(line)
        if stat:
            stats_list.append(stat)

    # Aggregate
    groups = aggregate_stats(stats_list)

    # Format digest
    digest = format_digest(groups)
    print(digest)

    # Send to Telegram if requested
    if args.send:
        try:
            config = json.loads(open(args.send).read())
            token = config.get("telegram_bot_token") or os.environ.get("TELEGRAM_BOT_TOKEN")
            chat_id = config.get("telegram_chat_id") or os.environ.get("TELEGRAM_CHAT_ID")
        except Exception as e:
            print(f"[ERROR] Failed to load config: {e}", file=sys.stderr)
            return 1
    else:
        token = os.environ.get("TELEGRAM_BOT_TOKEN")
        chat_id = os.environ.get("TELEGRAM_CHAT_ID")

    if token and chat_id:
        if not send_telegram(digest, token, chat_id, dry_run=args.dry_run):
            return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
