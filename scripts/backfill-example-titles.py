#!/usr/bin/env python3
"""
Retroactive title backfill script for prod example_bank.

Context:
  - Phase 1 crawl fix added _extract_title_from_detail() to natepan.py (2026-08-XX).
  - Fix uses tiered fallback: og:title → bare h1 → legacy selectors.
  - ~1,400 existing rows still have NULL/empty titles; they matter because:
    * plaza_classifier weights title hits ×3
    * quality gate keys off title presence

Procedure:
  1. Query example_bank for rows where title IS NULL/empty, source_url NOT NULL, content_type='POST'
  2. Report true count per source in dry-run
  3. For each row: fetch source_url, extract title via _extract_title_from_detail(), skip on 404/empty
  4. Apply rate limiting (≥1.5s between requests), User-Agent rotation
  5. Abort on 10 consecutive fetch failures (layout change / block detection)
  6. Batch commits (50 rows), log every id → title, skip rows with existing non-empty titles
  7. In dry-run: project how many would succeed, sample 10 titles, estimate wall-clock
  8. On --apply: actually write to DB

Dry-run mode (default):
  - Read-only, prints projection/samples, exits.
  - Pass --apply to actually write to DB.

Cost:
  - HTTP fetches: ~1.5s per row (rate limiting), 1,400 rows ≈ 35 minutes full run
  - Parsing: BeautifulSoup, ~1ms per row
  - DB: 1 UPDATE per successful row within a transaction

Note: Uses docker exec to access prod database. Requires .env.prod to be loaded.
"""

import argparse
import json
import logging
import os
import random
import re
import subprocess
import sys
import time
from collections import defaultdict
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple

import requests
from bs4 import BeautifulSoup

# Import the title extractor and classifier from the learning service
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../ai-user/learning"))
from app.crawlers.natepan import _extract_title_from_detail

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

# User-Agent rotation (same as natepan.py)
USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:124.0) Gecko/20100101 Firefox/124.0",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
]

RATE_LIMIT_SECS = 1.5
CONSECUTIVE_FAIL_LIMIT = 10
BATCH_SIZE = 50
DOCKER_CONTAINER = "againspring-mariadb-prod"


def load_env_prod() -> Dict[str, str]:
    """Load .env.prod file and return as dict."""
    env_path = os.path.join(os.path.dirname(__file__), "../env/.env.prod")
    env_vars = {}
    try:
        with open(env_path, "r") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#"):
                    if "=" in line:
                        key, val = line.split("=", 1)
                        env_vars[key] = val
    except FileNotFoundError:
        logger.warning(f"Could not find {env_path}")
    return env_vars


def run_docker_sql(sql: str, db_name: str = "againspring_prod") -> Optional[str]:
    """Run SQL command inside docker container and return output."""
    env_vars = load_env_prod()
    root_pwd = env_vars.get("MARIADB_ROOT_PASSWORD", "")

    cmd = [
        "docker", "exec", DOCKER_CONTAINER,
        "sh", "-c",
        f'mariadb -uroot -p"{root_pwd}" {db_name} -e "{sql}"'
    ]

    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        if result.returncode == 0:
            return result.stdout
        else:
            logger.error(f"Docker SQL error: {result.stderr}")
            return None
    except Exception as e:
        logger.error(f"Failed to run docker sql: {e}")
        return None


def fetch_with_retry(url: str, timeout: int = 10) -> Optional[str]:
    """Fetch URL with User-Agent rotation. Returns None on 404/403/410 or exception."""
    try:
        resp = requests.get(
            url,
            headers={
                "User-Agent": random.choice(USER_AGENTS),
                "Accept-Language": "ko-KR,ko;q=0.9",
                "Referer": "https://pann.nate.com/",
            },
            timeout=timeout,
        )
        if resp.status_code in (404, 403, 410):
            logger.debug(f"Fetch {url}: HTTP {resp.status_code}")
            return None
        resp.raise_for_status()
        return resp.text
    except Exception as e:
        logger.debug(f"Fetch {url}: {type(e).__name__}: {e}")
        return None


def select_titleless_rows(
    limit: Optional[int] = None, source: Optional[str] = None
) -> List[Dict[str, Any]]:
    """Fetch rows with NULL/empty titles from example_bank that have proper post URLs."""
    sql = """
    SELECT id, content, title, source, source_url, created_at
    FROM example_bank
    WHERE content_type = 'POST'
      AND (title IS NULL OR title = '')
      AND source_url IS NOT NULL
      AND source_url NOT IN (
        'https://pann.nate.com/',
        'https://pann.nate.com'
      )
    """

    if source:
        sql += f" AND source = '{source}'"

    sql += " ORDER BY id ASC"

    if limit:
        sql += f" LIMIT {limit}"

    output = run_docker_sql(sql)
    if not output:
        return []

    rows = []
    # Parse mariadb output format (tab-separated)
    lines = output.strip().split("\n")
    if len(lines) < 2:
        return []

    headers = lines[0].split("\t")
    for line in lines[1:]:
        values = line.split("\t")
        if len(values) == len(headers):
            row = {}
            for header, value in zip(headers, values):
                row[header] = value
            rows.append(row)

    return rows


def count_titleless_by_source() -> Tuple[Dict[str, int], Dict[str, int]]:
    """Count titleless rows per source in example_bank.

    Returns (with_valid_urls, with_base_url_only).
    """
    # Count with valid post URLs
    sql = """
    SELECT source, COUNT(*) as count
    FROM example_bank
    WHERE content_type = 'POST'
      AND (title IS NULL OR title = '')
      AND source_url IS NOT NULL
      AND source_url NOT IN (
        'https://pann.nate.com/',
        'https://pann.nate.com'
      )
    GROUP BY source
    ORDER BY count DESC
    """
    output = run_docker_sql(sql)
    result_valid = {}
    if output:
        lines = output.strip().split("\n")
        for line in lines[1:]:
            parts = line.split("\t")
            if len(parts) >= 2:
                result_valid[parts[0]] = int(parts[1])

    # Count with base URL only
    sql2 = """
    SELECT source, COUNT(*) as count
    FROM example_bank
    WHERE content_type = 'POST'
      AND (title IS NULL OR title = '')
      AND source_url IN (
        'https://pann.nate.com/',
        'https://pann.nate.com'
      )
    GROUP BY source
    ORDER BY count DESC
    """
    output2 = run_docker_sql(sql2)
    result_base = {}
    if output2:
        lines = output2.strip().split("\n")
        for line in lines[1:]:
            parts = line.split("\t")
            if len(parts) >= 2:
                result_base[parts[0]] = int(parts[1])

    return result_valid, result_base


def extract_title_from_url(url: str) -> Optional[str]:
    """Fetch URL and extract title using _extract_title_from_detail."""
    html = fetch_with_retry(url)
    if not html:
        return None

    try:
        soup = BeautifulSoup(html, "html.parser")
        title = _extract_title_from_detail(soup)
        return title
    except Exception as e:
        logger.debug(f"Parse error for {url}: {e}")
        return None


def backfill_titles_dry_run(
    rows: List[Dict[str, Any]]
) -> Tuple[int, int, List[Tuple[str, str]], float]:
    """
    Simulate backfill: fetch and extract titles for dry-run.

    Returns (success_count, fail_count, sample_titles, estimated_wall_clock_minutes).
    """
    if not rows:
        return 0, 0, [], 0.0

    success_count = 0
    fail_count = 0
    sample_titles: List[Tuple[str, str]] = []
    consecutive_fails = 0

    start_time = time.time()

    for i, row in enumerate(rows):
        row_id = row["id"]
        url = row["source_url"]

        # Rate limiting
        if i > 0:
            time.sleep(RATE_LIMIT_SECS)

        logger.info(f"[{i+1}/{len(rows)}] Fetching {row_id} from {url[:60]}...")

        title = extract_title_from_url(url)

        if title:
            success_count += 1
            consecutive_fails = 0
            if len(sample_titles) < 10:
                sample_titles.append((str(row_id), title[:100]))
            logger.info(f"  ✓ Extracted title: {title[:80]}")
        else:
            fail_count += 1
            consecutive_fails += 1
            logger.warning(f"  ✗ Failed to extract title")

            if consecutive_fails >= CONSECUTIVE_FAIL_LIMIT:
                logger.error(
                    f"Consecutive fail limit ({CONSECUTIVE_FAIL_LIMIT}) reached. "
                    f"Stopping to avoid hammering the site."
                )
                break

    elapsed_secs = time.time() - start_time
    elapsed_mins = elapsed_secs / 60.0
    estimated_full_mins = (len(rows) / (i + 1)) * elapsed_mins if i + 1 > 0 else 0.0

    return success_count, fail_count, sample_titles, estimated_full_mins


def backfill_titles_apply(
    rows: List[Dict[str, Any]]
) -> Tuple[int, int, List[str]]:
    """
    Actually backfill titles: fetch, extract, and write to DB.

    Returns (success_count, fail_count, issues).
    """
    if not rows:
        return 0, 0, []

    success_count = 0
    fail_count = 0
    issues: List[str] = []
    batch: List[Tuple[str, str]] = []
    consecutive_fails = 0

    for i, row in enumerate(rows):
        row_id = row["id"]
        url = row["source_url"]

        # Rate limiting
        if i > 0:
            time.sleep(RATE_LIMIT_SECS)

        logger.info(f"[{i+1}/{len(rows)}] Processing {row_id}...")

        title = extract_title_from_url(url)

        if title:
            success_count += 1
            consecutive_fails = 0
            batch.append((row_id, title))
            logger.info(f"  ✓ Title: {title[:80]}")

            # Batch commit
            if len(batch) >= BATCH_SIZE:
                _commit_batch(batch)
                batch = []
        else:
            fail_count += 1
            consecutive_fails += 1
            issues.append(f"Row {row_id}: failed to extract title from {url}")
            logger.warning(f"  ✗ Failed")

            if consecutive_fails >= CONSECUTIVE_FAIL_LIMIT:
                logger.error(
                    f"Consecutive fail limit ({CONSECUTIVE_FAIL_LIMIT}) reached. Stopping."
                )
                break

    # Final commit
    if batch:
        _commit_batch(batch)
        batch = []

    logger.info(f"Applied {success_count} title updates")
    return success_count, fail_count, issues


def _commit_batch(batch: List[Tuple[str, str]]) -> None:
    """Commit a batch of (row_id, title) updates using bound parameters.

    제목은 외부 사이트에서 긁어온 값이다. 이전 구현은 SQL을 f-string으로 만들고
    작은따옴표만 치환한 뒤 `docker exec sh -c 'mariadb -e "..."'`로 넘겼는데,
    그러면 제목이 SQL 파서와 셸 파서를 모두 통과한다. 한국어 게시글 제목에 흔한
    큰따옴표(`"진짜?"라고`), 역슬래시(MariaDB 기본값에서 이스케이프 문자), `$`,
    백틱이 명령을 깨뜨리거나 잘린 제목을 저장할 수 있다. 파라미터 바인딩으로
    두 계층을 모두 없앤다.

    또한 title이 이미 채워진 행은 절대 덮어쓰지 않는다(동시 크롤 반영분 보호).
    """
    import pymysql

    env_vars = load_env_prod()
    conn = pymysql.connect(
        host=_db_host(),
        user="root",
        password=env_vars.get("MARIADB_ROOT_PASSWORD", ""),
        database="againspring_prod",
        charset="utf8mb4",
        autocommit=False,
    )
    try:
        with conn.cursor() as cur:
            for row_id, title in batch:
                # example_bank에는 updated_at 컬럼이 없다 (created_at만 존재).
                cur.execute(
                    "UPDATE example_bank SET title = %s "
                    "WHERE id = %s AND content_type = 'POST' "
                    "AND (title IS NULL OR title = '')",
                    (title, row_id),
                )
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()
    logger.info(f"Committed batch of {len(batch)} rows")


def _db_host() -> str:
    """prod MariaDB 컨테이너 IP — 재빌드 때마다 바뀌므로 매번 조회한다."""
    out = subprocess.run(
        ["docker", "inspect", DOCKER_CONTAINER, "--format",
         "{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}"],
        capture_output=True, text=True, timeout=20,
    )
    return (out.stdout or "").split()[0]


def main():
    parser = argparse.ArgumentParser(
        description="Retroactive title backfill for prod example_bank",
        epilog="Example: python3 backfill-example-titles.py --limit 30 (dry-run, 30 rows)\n"
        "         python3 backfill-example-titles.py --apply --limit 100 (apply to 100 rows)\n"
        "         python3 backfill-example-titles.py --source natepan (filter by source)",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Actually write changes to DB; omit for dry-run",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="Limit to N rows (useful for testing)",
    )
    parser.add_argument(
        "--source",
        default=None,
        help="Filter by source (e.g., 'natepan', 'blind')",
    )

    args = parser.parse_args()

    logger.info("Connecting to prod database via docker...")

    # Count titleless rows per source
    logger.info("Counting titleless rows by source...")
    counts_valid, counts_base = count_titleless_by_source()

    # Fetch rows
    logger.info(
        f"Fetching titleless rows (limit={args.limit}, source={args.source})..."
    )
    rows = select_titleless_rows(limit=args.limit, source=args.source)

    if not rows:
        logger.info("No titleless rows with valid post URLs found")
        return

    logger.info(f"Fetched {len(rows)} rows")

    # Print report
    print("\n" + "=" * 80)
    print("TITLE BACKFILL PROJECTION REPORT")
    print("=" * 80)
    print()
    print(f"Database: againspring_prod (via docker)")
    print(f"Mode: {'APPLY' if args.apply else 'DRY-RUN'}")
    print()

    print("Titleless Rows by Source (with valid post URLs):")
    for source in sorted(counts_valid.keys()):
        count = counts_valid[source]
        print(f"  {source:12s}: {count:6d}")
    total_valid = sum(counts_valid.values())
    print(f"  {'TOTAL':12s}: {total_valid:6d}")
    print()

    print("Titleless Rows (with base URL only — not re-fetchable):")
    for source in sorted(counts_base.keys()):
        count = counts_base[source]
        print(f"  {source:12s}: {count:6d}")
    total_base = sum(counts_base.values())
    print(f"  {'TOTAL':12s}: {total_base:6d}")
    print()

    total_titleless = total_valid + total_base
    print(f"Grand Total (all titleless): {total_titleless:d}")
    print()

    print(f"Sample: {len(rows)} rows (limit={args.limit}, source={args.source})")
    print()

    # Run dry-run or apply
    if args.apply:
        logger.info("Applying title backfill...")
        success, fail, issues = backfill_titles_apply(rows)

        print("=" * 80)
        print("APPLY RESULT")
        print("=" * 80)
        print()
        print(f"Success:        {success} rows")
        print(f"Failed:         {fail} rows")
        print(f"Total:          {len(rows)} rows")
        print()

        if issues:
            print("Issues:")
            for issue in issues[:10]:
                print(f"  - {issue}")
            if len(issues) > 10:
                print(f"  ... and {len(issues) - 10} more issues")
            print()

        print("=" * 80)

    else:
        logger.info("Running dry-run...")
        success, fail, samples, est_wall_clock = backfill_titles_dry_run(rows)

        print("DRY-RUN RESULTS")
        print("=" * 80)
        print()
        print(f"Success rate:   {success}/{len(rows)} ({100.0*success/len(rows):.1f}%)")
        print(f"Failed:         {fail}/{len(rows)}")
        print()

        if samples:
            print("Sample Extracted Titles:")
            for row_id, title in samples:
                print(f"  [{row_id}] {title}")
            print()

        print(f"Estimated wall-clock for full {len(rows)} rows: {est_wall_clock:.1f} minutes")
        print()

        # Estimate for full run
        if total_valid > 0 and success > 0:
            success_rate = success / len(rows)
            estimated_total_success = int(total_valid * success_rate)
            estimated_total_time = (total_valid / len(rows)) * est_wall_clock
            print(f"Full run estimate (all {total_valid} titleless rows with valid URLs):")
            print(f"  Expected success: {estimated_total_success} rows")
            print(f"  Estimated time:   {estimated_total_time:.1f} minutes")
            print()

        print("=" * 80)
        print("\n✓ Dry-run complete. Pass --apply to commit changes.")


if __name__ == "__main__":
    main()
