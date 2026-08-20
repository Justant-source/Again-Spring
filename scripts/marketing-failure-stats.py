#!/usr/bin/env python3
"""Aggregate marketing job failure stats from the dev database.

Queries marketing_job table for the last 30 days, groups by failure_stage + failure_code,
and reports terminal status distribution (FAILED vs PARTIAL).

Lists top 10 codes by frequency with most-recent example job ID and truncated error_summary.

Usage:
  python3 scripts/marketing-failure-stats.py

Database connection:
  Uses .env.dev defaults (MARIADB_HOST, MARIADB_USER, MARIADB_PASSWORD, MARIADB_DATABASE)
  Accesses dev database via docker exec againspring-mariadb-dev.
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Optional


def load_env(path: Path) -> dict[str, str]:
    """Load .env file."""
    env = {}
    if not path.exists():
        return env
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        env[k.strip()] = v.strip().strip('"').strip("'")
    return env


def docker_mariadb_query(sql: str) -> str:
    """Execute SQL via docker exec against dev database.

    Returns raw output (tab-separated for JSON output).
    """
    env = load_env(Path("env/.env.dev"))
    db_name = os.environ.get("MARIADB_DATABASE", env.get("MARIADB_DATABASE", "againspring_dev"))

    try:
        result = subprocess.run(
            [
                "docker", "exec", "againspring-mariadb-dev", "sh", "-c",
                f'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" "{db_name}" -N -e "{sql}"'
            ],
            capture_output=True,
            text=True,
            timeout=30,
        )
        if result.returncode != 0:
            raise RuntimeError(f"mariadb error: {result.stderr}")
        return result.stdout
    except subprocess.TimeoutExpired:
        raise RuntimeError("mariadb query timeout")
    except Exception as e:
        raise RuntimeError(f"docker exec failed: {e}")


def query_failure_stats() -> dict[tuple[Optional[str], Optional[str]], dict]:
    """Query and aggregate failure stats for the last 30 days.

    Returns:
      {(failure_stage, failure_code): {
        'failed_count': int,
        'partial_count': int,
        'total_count': int,
        'most_recent_job_id': int,
        'most_recent_error_summary': str,
      }, ...}
    """
    thirty_days_ago = (datetime.now(timezone.utc) - timedelta(days=30)).isoformat()

    query = f"""
    SELECT
        COALESCE(failure_stage, 'NULL') as failure_stage,
        COALESCE(failure_code, 'NULL') as failure_code,
        status,
        id,
        COALESCE(error_summary, '') as error_summary,
        created_at
    FROM marketing_job
    WHERE created_at >= '{thirty_days_ago}'
      AND status IN ('FAILED', 'PARTIAL')
    ORDER BY created_at DESC
    """

    stats: dict[tuple[Optional[str], Optional[str]], dict] = {}

    try:
        output = docker_mariadb_query(query)

        for line in output.strip().split("\n"):
            if not line.strip():
                continue

            parts = line.split("\t")
            if len(parts) < 6:
                continue

            stage = parts[0] if parts[0] != "NULL" else None
            code = parts[1] if parts[1] != "NULL" else None
            status = parts[2]
            job_id = int(parts[3])
            error_summary = parts[4] if len(parts) > 4 else ""

            key = (stage, code)

            if key not in stats:
                stats[key] = {
                    "failed_count": 0,
                    "partial_count": 0,
                    "total_count": 0,
                    "most_recent_job_id": job_id,
                    "most_recent_error_summary": (error_summary or "")[:120],
                }

            stats[key]["total_count"] += 1
            if status == "FAILED":
                stats[key]["failed_count"] += 1
            elif status == "PARTIAL":
                stats[key]["partial_count"] += 1

    except Exception as e:
        print(f"ERROR: Query failed: {e}", file=sys.stderr)
        raise

    return stats


def format_table(stats: dict[tuple[Optional[str], Optional[str]], dict]) -> str:
    """Format stats as a Korean table.

    Shows top 10 codes by frequency.
    """
    if not stats:
        return "최근 30일 마케팅 실패: 없음"

    # Sort by total_count descending, take top 10
    sorted_stats = sorted(stats.items(), key=lambda x: -x[1]["total_count"])[:10]

    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    lines = [
        f"📊 마케팅 실패 통계 (최근 30일, {now})",
        "",
        "┌─ 실패 단계(stage) ─────────┬─ 실패 코드(code) ─────────────┬─ 건수 ─┬─ 상태 ─┬─ 예시 Job ID ─┬─ 오류 요약 ──────────────────────────────────────────────────┐",
        "│                           │                              │       │ F / P │              │                                                           │",
    ]

    for idx, ((stage, code), data) in enumerate(sorted_stats, 1):
        stage_str = (stage or "NULL")[:26].ljust(26)
        code_str = (code or "NULL")[:30].ljust(30)
        count_str = str(data["total_count"]).rjust(5)
        status_str = f"{data['failed_count']}/{data['partial_count']}".ljust(5)
        job_id_str = str(data["most_recent_job_id"]).rjust(13)
        summary_str = (data["most_recent_error_summary"] or "(없음)")[:57].ljust(57)

        lines.append(
            f"│ {stage_str} │ {code_str} │{count_str} │ {status_str} │ {job_id_str} │ {summary_str} │"
        )

    lines.append(
        "└─────────────────────────┴──────────────────────────────┴───────┴───────┴──────────────┴───────────────────────────────────────────────────────────┘"
    )

    lines.extend(
        [
            "",
            f"총 실패: {sum(d['total_count'] for d in stats.values())}건",
            f"  - FAILED: {sum(d['failed_count'] for d in stats.values())}건",
            f"  - PARTIAL: {sum(d['partial_count'] for d in stats.values())}건",
            "",
            "설명: F = FAILED 건수, P = PARTIAL 건수",
        ]
    )

    return "\n".join(lines)


def main() -> int:
    try:
        stats = query_failure_stats()
        output = format_table(stats)
        print(output)
        return 0
    except Exception as e:
        print(f"ERROR: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
