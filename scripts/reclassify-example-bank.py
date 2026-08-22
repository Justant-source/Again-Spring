#!/usr/bin/env python3
"""
Recovery script for 13,699 dead-inventory crawl examples in prod example_bank.

Context:
  - 13,699 rows have lowercase categories (romance, marriage, workplace)
    created 2026-06-06 to 2026-08-19, only 232 scored (1.7%).
  - After commit 51d175d8, crawlers began storing UPPERCASE plaza enums.
  - source_claim.py only filters for UPPERCASE categories, making lowercase rows invisible.
  - plaza_classifier.py (pure Python keyword-based) can reclassify them deterministically.

Procedure:
  1. Identify rows with lowercase categories (romance, marriage, workplace, OTHER).
  2. Read content + title, reclassify using classify_plaza().
  3. Group by new plaza and score within per-plaza cohorts.
  4. Show projected inventory change.
  5. On --apply: store old categories in backup_old_categories, update category/popularity_pct.
  6. On rollback: restore from backup_old_categories.

Dry-run mode (default):
  - Read-only, prints projection, exits.
  - Pass --apply to actually write to DB.

Cost:
  - Classifier: O(n) Python keyword matching, ~1µs per row, no LLM calls.
  - Scoring: Percentile within per-plaza cohort, requires loading all rows per plaza.
  - Total: <2 minutes for 13.7k rows on a single CPU.
  - DB: 2 UPDATEs per row within a transaction.
"""

import argparse
import logging
import os
import sys
from collections import defaultdict
from datetime import datetime
from decimal import Decimal
from typing import Any, Dict, List, Optional, Tuple

import pymysql
from pymysql.cursors import DictCursor

# Import the classifier from the learning service
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../ai-user/learning"))
from app.services.plaza_classifier import classify_plaza

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

# Category mappings
OLD_CATEGORIES = {"romance", "marriage", "workplace", "other"}
PLAZA_NAMES = {"COUPLE", "MARRIED", "FRIEND", "FAMILY", "WORK", "OTHER"}

# Popularity gate config (from popularity_gate.py)
MIN_POPULARITY_PCT = 0.3  # 30th percentile minimum
DEFAULT_MIN_PCTS_BY_PLAZA = {
    "blind": 0.25,
    "natepan": 0.30,
    "dcinside": 0.20,
    "theqoo": 0.25,
    "clien": 0.25,
}


def get_db_connection(config: Dict[str, Any]) -> pymysql.Connection:
    """Create DB connection from config or env vars."""
    return pymysql.connect(
        host=config.get("host", os.getenv("DB_HOST", "localhost")),
        port=config.get("port", int(os.getenv("DB_PORT", 3306))),
        user=config.get("user", os.getenv("DB_USER", "againspring")),
        password=config.get("password", os.getenv("DB_PASSWORD", "")),
        database=config.get("database", os.getenv("DB_NAME", "againspring_prod")),
        cursorclass=DictCursor,
        charset="utf8mb4",
    )


def score_posts_within_plaza(
    rows: List[Dict[str, Any]], source: str
) -> Dict[int, Optional[float]]:
    """
    Score rows within a plaza cohort using percentile ranking.

    Scores by engagement (comments + likes), then percentile within the batch.
    Returns {temp_id → popularity_pct (0.0-1.0)}.
    """
    if not rows:
        return {}

    # Simple scoring: prefer by comment count + like count
    # (simplified from popularity_gate.score_posts which has more metrics)
    scores: Dict[int, float] = {}
    for row in rows:
        comments = row.get("comments") or 0
        likes = row.get("likes") or 0
        # Basic heuristic: comments are more valuable than likes
        score = float(comments) * 1.5 + float(likes) * 0.5
        scores[row["id"]] = score

    # Convert to percentile (0.0-1.0)
    if not scores:
        return {row["id"]: None for row in rows}

    sorted_scores = sorted(scores.values())
    pct_by_score: Dict[float, float] = {}
    for i, score_val in enumerate(sorted_scores):
        if score_val not in pct_by_score:
            pct_by_score[score_val] = min(1.0, (i + 1) / len(sorted_scores))

    result = {}
    min_pct = DEFAULT_MIN_PCTS_BY_PLAZA.get(source.lower(), MIN_POPULARITY_PCT)
    for row in rows:
        row_score = scores[row["id"]]
        pct = pct_by_score[row_score]
        # Apply floor: only rows above min_pct are scored
        result[row["id"]] = pct if pct >= min_pct else None

    return result


def select_lowercase_rows(
    cur: DictCursor, limit: Optional[int] = None
) -> List[Dict[str, Any]]:
    """Fetch all rows with lowercase categories from example_bank."""
    # BINARY 비교 필수 — MariaDB 기본 콜레이션은 대소문자를 구분하지 않아서
    # 그냥 IN ('other', ...)로 쓰면 이미 정상 분류된 대문자 'OTHER'(prod 4,036건)까지
    # 걸려 들어와 멀쩡한 행을 재분류해버린다. 소문자 레거시 값만 골라야 한다.
    sql = """
    SELECT id, content, title, category, source, popularity_pct
    FROM example_bank
    WHERE content_type = 'POST'
      AND BINARY category IN (%s, %s, %s, %s)
    ORDER BY created_at ASC
    """
    args = list(OLD_CATEGORIES)
    if limit:
        sql += " LIMIT %s"
        args.append(limit)
    cur.execute(sql, args)
    return cur.fetchall() or []


def project_inventory(
    rows: List[Dict[str, Any]], apply_dry_run: bool = True
) -> Tuple[Dict[str, int], Dict[str, int], List[str]]:
    """
    Project category and scoring changes without writing to DB.

    Returns (current_by_category, projected_by_category, issues).
    """
    current_by_category: Dict[str, int] = defaultdict(int)
    projected_by_category: Dict[str, int] = defaultdict(int)
    issues = []

    # Track current state
    for row in rows:
        current_by_category[row["category"]] += 1

    # Project reclassification
    by_plaza: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
    for i, row in enumerate(rows):
        row_id = row["id"]
        content = row.get("content") or ""
        title = row.get("title") or ""
        try:
            new_plaza = classify_plaza(content, title)
            if new_plaza not in PLAZA_NAMES:
                new_plaza = "OTHER"
        except Exception as e:
            issues.append(f"Row {row_id}: classify_plaza failed: {e}")
            new_plaza = "OTHER"

        # Temp ID for scoring
        row["temp_id"] = i
        by_plaza[new_plaza].append(row)

    # Score within per-plaza cohorts
    scored_count = 0
    for plaza, plaza_rows in by_plaza.items():
        pct_by_id = score_posts_within_plaza(plaza_rows, rows[0].get("source", "blind"))
        for row in plaza_rows:
            pct = pct_by_id.get(row["temp_id"])
            if pct is not None:
                scored_count += 1
            projected_by_category[plaza] += 1

    logger.info(
        "Projection complete: %d rows reclassified, %d scored (%.1f%%)",
        len(rows),
        scored_count,
        100.0 * scored_count / len(rows) if rows else 0,
    )

    return dict(current_by_category), dict(projected_by_category), issues


def reclassify_and_score(
    rows: List[Dict[str, Any]],
) -> List[Tuple[int, str, Optional[float]]]:
    """
    Reclassify and score rows, returning (id, new_category, new_popularity_pct).
    """
    result = []
    by_plaza: Dict[str, List[Dict[str, Any]]] = defaultdict(list)

    for i, row in enumerate(rows):
        content = row.get("content") or ""
        title = row.get("title") or ""
        try:
            new_plaza = classify_plaza(content, title)
            if new_plaza not in PLAZA_NAMES:
                new_plaza = "OTHER"
        except Exception as e:
            logger.warning(f"Row {row['id']}: classify_plaza failed: {e}")
            new_plaza = "OTHER"

        row["temp_id"] = i
        row["new_plaza"] = new_plaza
        by_plaza[new_plaza].append(row)

    # Score within per-plaza cohorts
    for plaza, plaza_rows in by_plaza.items():
        source = plaza_rows[0].get("source", "blind")
        pct_by_id = score_posts_within_plaza(plaza_rows, source)
        for row in plaza_rows:
            pct = pct_by_id.get(row["temp_id"])
            # Convert float to Decimal for DB storage
            pct_decimal = Decimal(str(pct)) if pct is not None else None
            result.append((row["id"], row["new_plaza"], pct_decimal))

    return result


def backup_old_categories(
    cur: DictCursor, rows: List[Dict[str, Any]]
) -> None:
    """Create backup_old_categories table if needed, store old values."""
    # Create backup table (idempotent)
    create_backup_sql = """
    CREATE TABLE IF NOT EXISTS backup_old_categories (
        id BIGINT NOT NULL,
        old_category VARCHAR(32) NOT NULL,
        old_popularity_pct DECIMAL(4,3),
        backed_up_at TIMESTAMP(3) NOT NULL DEFAULT NOW(3),
        PRIMARY KEY (id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    COMMENT='Backup of example_bank category before reclassification'
    """
    cur.execute(create_backup_sql)

    # Insert backup rows
    insert_sql = """
    INSERT INTO backup_old_categories (id, old_category, old_popularity_pct)
    VALUES (%s, %s, %s)
    ON DUPLICATE KEY UPDATE backed_up_at = NOW(3)
    """
    for row in rows:
        cur.execute(
            insert_sql,
            (row["id"], row["category"], row.get("popularity_pct")),
        )
    logger.info(f"Backed up {len(rows)} old category values")


def apply_reclassification(
    cur: DictCursor,
    changes: List[Tuple[int, str, Optional[float]]],
) -> None:
    """Apply reclassification and scoring updates to example_bank."""
    update_sql = """
    UPDATE example_bank
    SET category = %s, popularity_pct = %s, updated_at = NOW(3)
    WHERE id = %s
    """
    for row_id, new_plaza, new_pct in changes:
        cur.execute(update_sql, (new_plaza, new_pct, row_id))
    logger.info(f"Applied {len(changes)} updates to example_bank")


def rollback_reclassification(cur: DictCursor) -> None:
    """Restore original categories from backup_old_categories."""
    restore_sql = """
    UPDATE example_bank eb
    JOIN backup_old_categories boc ON eb.id = boc.id
    SET eb.category = boc.old_category,
        eb.popularity_pct = boc.old_popularity_pct,
        eb.updated_at = NOW(3)
    WHERE boc.backed_up_at > DATE_SUB(NOW(3), INTERVAL 24 HOUR)
    """
    cur.execute(restore_sql)
    logger.info(f"Rolled back {cur.rowcount} rows")


def main():
    parser = argparse.ArgumentParser(
        description="Recovery script for dead-inventory crawl examples (lowercase categories)",
        epilog="Example: python3 reclassify-example-bank.py --limit 100 (dry-run, 100 rows)\n"
        "         python3 reclassify-example-bank.py --apply --limit 1000 (apply to 1000 rows)\n"
        "         python3 reclassify-example-bank.py --rollback (restore from backup)",
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
        "--rollback",
        action="store_true",
        help="Restore original categories from backup_old_categories",
    )
    parser.add_argument(
        "--host",
        default=os.getenv("DB_HOST", "localhost"),
        help="DB host (env: DB_HOST)",
    )
    parser.add_argument(
        "--port", type=int, default=int(os.getenv("DB_PORT", 3306)), help="DB port"
    )
    parser.add_argument(
        "--user",
        default=os.getenv("DB_USER", "againspring"),
        help="DB user (env: DB_USER)",
    )
    parser.add_argument(
        "--password", default=os.getenv("DB_PASSWORD", ""), help="DB password"
    )
    parser.add_argument(
        "--database",
        default=os.getenv("DB_NAME", "againspring_prod"),
        help="DB name (env: DB_NAME)",
    )

    args = parser.parse_args()

    db_config = {
        "host": args.host,
        "port": args.port,
        "user": args.user,
        "password": args.password,
        "database": args.database,
    }

    # Connect to DB
    try:
        conn = get_db_connection(db_config)
        logger.info(
            f"Connected to {db_config['database']} @ {db_config['host']}:{db_config['port']}"
        )
    except Exception as e:
        logger.error(f"Failed to connect to DB: {e}")
        sys.exit(1)

    try:
        with conn.cursor() as cur:
            # Rollback mode
            if args.rollback:
                logger.info("Rolling back reclassification from backup...")
                rollback_reclassification(cur)
                conn.commit()
                logger.info("Rollback complete")
                return

            # Normal mode: dry-run or apply
            logger.info(
                f"Fetching lowercase-category rows (limit={args.limit})..."
            )
            rows = select_lowercase_rows(cur, limit=args.limit)
            if not rows:
                logger.info("No lowercase-category rows found")
                return

            logger.info(f"Fetched {len(rows)} rows")
            logger.info("Projecting reclassification...")

            current_inv, projected_inv, issues = project_inventory(rows)

            # Print report
            print("\n" + "=" * 80)
            print("RECLASSIFICATION PROJECTION REPORT")
            print("=" * 80)
            print()
            print(f"Sample: {len(rows)} rows (limit={args.limit})")
            print(f"Database: {db_config['database']}")
            print(f"Mode: {'APPLY' if args.apply else 'DRY-RUN'}")
            print()

            print("Current Inventory by Category:")
            for cat in sorted(current_inv.keys()):
                print(f"  {cat:12s}: {current_inv[cat]:6d}")
            print(f"  {'TOTAL':12s}: {sum(current_inv.values()):6d}")
            print()

            print("Projected Inventory by Plaza (after reclassification):")
            for plaza in sorted(PLAZA_NAMES):
                count = projected_inv.get(plaza, 0)
                delta = count - current_inv.get(
                    plaza.lower(), 0
                )  # old plaza names were lowercase
                marker = f"(+{delta})" if delta > 0 else f"({delta})" if delta < 0 else ""
                print(f"  {plaza:12s}: {count:6d} {marker}")
            print(f"  {'TOTAL':12s}: {sum(projected_inv.values()):6d}")
            print()

            if issues:
                print("Issues encountered:")
                for issue in issues[:10]:  # Show first 10 issues
                    print(f"  - {issue}")
                if len(issues) > 10:
                    print(f"  ... and {len(issues) - 10} more issues")
                print()

            print("=" * 80)

            if args.apply:
                logger.info("Applying reclassification...")

                # Backup old categories
                backup_old_categories(cur, rows)

                # Reclassify and score
                changes = reclassify_and_score(rows)

                # Apply updates
                apply_reclassification(cur, changes)

                # Commit transaction
                conn.commit()
                logger.info(f"Applied reclassification to {len(changes)} rows")
                print("\n✓ Changes applied and committed")
            else:
                logger.info(
                    "Dry-run mode: no changes written. Pass --apply to commit."
                )
                print("\n✓ Dry-run complete. Pass --apply to commit changes.")

    except Exception as e:
        logger.error(f"Error: {e}", exc_info=True)
        conn.rollback()
        sys.exit(1)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
