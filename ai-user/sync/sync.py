"""
Prod DB -> dev DB 동기화 서비스.

- 5분 콘텐츠 증분 (T1+U1): posts/comments/votes/likes + 참조 users(비식별)·personas
- 24h full (SYNC_CRON, 기본 KST 05:30): 전체 SYNC_TABLES
- 실사용자 계정은 dev에서 로그인 불가하도록 비식별화
- D1: prod 우선 upsert (dev-only 행 삭제 안 함)
"""

from __future__ import annotations

import logging
import os
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Callable, Iterable
from zoneinfo import ZoneInfo

import pymysql
import pymysql.cursors
from apscheduler.schedulers.blocking import BlockingScheduler
from apscheduler.triggers.cron import CronTrigger

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [sync] %(levelname)s %(message)s",
    datefmt="%Y-%m-%dT%H:%M:%S",
)
log = logging.getLogger(__name__)


def _env(name: str, default: str) -> str:
    value = os.getenv(name)
    return value if value is not None else default


PROD = dict(
    host=_env("PROD_DB_HOST", "againspring-mariadb-prod"),
    port=int(_env("PROD_DB_PORT", "3306")),
    user=_env("PROD_DB_USER", "againspring"),
    password=_env("PROD_DB_PASSWORD", ""),
    database=_env("PROD_DB_NAME", "againspring"),
    charset="utf8mb4",
    cursorclass=pymysql.cursors.DictCursor,
    connect_timeout=10,
    autocommit=False,
)
DEV = dict(
    host=_env("DEV_DB_HOST", "againspring-mariadb-dev"),
    port=int(_env("DEV_DB_PORT", "3306")),
    user=_env("DEV_DB_USER", "againspring"),
    password=_env("DEV_DB_PASSWORD", ""),
    database=_env("DEV_DB_NAME", "againspring_dev"),
    charset="utf8mb4",
    cursorclass=pymysql.cursors.DictCursor,
    connect_timeout=10,
    autocommit=False,
)

# 24h full
SYNC_CRON = _env("SYNC_CRON", "30 5 * * *")
# 5분 콘텐츠 증분
SYNC_CONTENT_CRON = _env("SYNC_CONTENT_CRON", "*/5 * * * *")
SYNC_TIMEZONE = ZoneInfo(_env("SYNC_TIMEZONE", "Asia/Seoul"))
BACKFILL_DAYS = int(_env("SYNC_BACKFILL_DAYS", "7"))
CONTENT_LOOKBACK_MINUTES = int(_env("SYNC_CONTENT_LOOKBACK_MINUTES", "15"))


@dataclass(frozen=True)
class SyncContext:
    """한 sync cycle 동안 고정되는 문맥. synthetic_ids = prod users.synthetic=1 의 id 집합."""

    synthetic_ids: frozenset[str]


@dataclass(frozen=True)
class TableSpec:
    name: str
    primary_keys: tuple[str, ...]
    mode: str = "incremental"  # incremental | full
    time_columns: tuple[str, ...] = ()
    transform: Callable[[dict, datetime, "SyncContext"], dict] | None = None
    where: str | None = None  # 추가 SELECT 조건 (컬럼 존재 시에만 적용; build_select_sql 참고)


REAL_POST_BODY = "[prod 본문은 dev로 복사하지 않습니다]"


def _is_truthy_db(value) -> bool:
    if isinstance(value, (bytes, bytearray)):
        return any(value)
    return bool(value)


def _is_real_author(row: dict, ctx: "SyncContext") -> bool:
    author = row.get("author_id")
    return author is None or str(author) not in ctx.synthetic_ids


def _mask_real_post(row: dict, now: datetime, ctx: "SyncContext") -> dict:
    masked = dict(row)
    if "invite_token" in masked:
        masked["invite_token"] = None  # 비밀값은 작성자 불문 제거
    if not _is_real_author(masked, ctx):
        return masked
    pid = str(masked.get("id", ""))
    if "title" in masked:
        masked["title"] = f"[비식별] 사연 {pid[-6:]}"
    for col in ("body_published", "body"):
        if col in masked and masked[col] is not None:
            masked[col] = REAL_POST_BODY
    if "partner_body_published" in masked and masked["partner_body_published"]:
        masked["partner_body_published"] = REAL_POST_BODY
    for col in ("body_raw", "partner_body_raw", "source_original_body", "promo_title"):
        if col in masked:
            masked[col] = None
    return masked


def _mask_real_comment(row: dict, now: datetime, ctx: "SyncContext") -> dict:
    if not _is_real_author(row, ctx):
        return row
    masked = dict(row)
    if "body" in masked:
        masked["body"] = f"[비식별 댓글 {masked.get('id')}]"
    return masked


def _mask_real_user(row: dict, now: datetime, ctx: "SyncContext | None" = None) -> dict:
    if _is_truthy_db(row.get("synthetic")):
        return row

    user_id = str(row.get("id", "user"))
    suffix = user_id[-8:]
    masked = dict(row)

    masked["email"] = f"masked-{suffix}@dev.invalid"
    masked["nickname"] = f"익명사용자-{suffix[-4:]}"

    for column in (
        "password_hash",
        "provider",
        "provider_id",
        "communication_style",
        "mbti_type",
        "onboarding_answers",
        "mbti_profile",
        "mediator_default_x",
        "mediator_default_y",
        "terms_agreed_at",
        "privacy_agreed_at",
        "disclaimer_agreed_at",
        "marketing_agreed_at",
        "suspended_until",
    ):
        if column in masked:
            masked[column] = None

    if "status" in masked:
        masked["status"] = "SUSPENDED"
    if "suspended_reason" in masked:
        masked["suspended_reason"] = "mirrored-from-prod"
    if "tokens_invalidated_at" in masked:
        masked["tokens_invalidated_at"] = now
    if "deleted_at" in masked:
        masked["deleted_at"] = now
    if "must_change_password" in masked:
        masked["must_change_password"] = False
    if "is_guest" in masked:
        masked["is_guest"] = False

    return masked


USERS_SPEC = TableSpec(
    "users", ("id",), time_columns=("updated_at", "created_at"), transform=_mask_real_user
)
PERSONAS_SPEC = TableSpec("personas", ("id",), mode="full")

# 24h full 대상
SYNC_TABLES: tuple[TableSpec, ...] = (
    USERS_SPEC,
    PERSONAS_SPEC,
    TableSpec("persona_relationships", ("id",), mode="full"),
    TableSpec("persona_seen_posts", ("persona_id", "post_id"), time_columns=("seen_at",)),
    TableSpec("persona_action_log", ("id",), time_columns=("created_at",)),
    TableSpec("persona_history_entries", ("id",), time_columns=("created_at",)),
    TableSpec("persona_life_state", ("persona_id",), time_columns=("updated_at",)),
    TableSpec("persona_daily_quota", ("persona_id", "day_bucket"), mode="full"),
    TableSpec("ai_user_runtime", ("id",), mode="full"),
    TableSpec("ai_user_generation_config", ("id",), mode="full"),
    TableSpec("ai_content_corrections", ("id",), time_columns=("created_at",)),
    TableSpec("ai_global_rules", ("id",), time_columns=("created_at",)),
    TableSpec("ai_prompt_template", ("key",), time_columns=("updated_at",)),
    TableSpec("system_setting", ("setting_key",), time_columns=("updated_at",)),
    TableSpec("posts", ("id",), time_columns=("updated_at", "created_at")),
    TableSpec("vote_options", ("id",), mode="full"),
    TableSpec("post_comments", ("id",), time_columns=("updated_at", "created_at")),
    TableSpec("votes", ("id",), time_columns=("created_at",)),
    TableSpec("post_likes", ("id",), time_columns=("created_at",)),
)

# 5분 콘텐츠 (T1) — users/personas는 U1 동반으로만
CONTENT_TABLES: tuple[TableSpec, ...] = (
    TableSpec("posts", ("id",), time_columns=("updated_at", "created_at")),
    TableSpec("vote_options", ("id",), mode="full"),
    TableSpec("post_comments", ("id",), time_columns=("updated_at", "created_at")),
    TableSpec("votes", ("id",), time_columns=("created_at",)),
    TableSpec("post_likes", ("id",), time_columns=("created_at",)),
)

USER_ID_COLUMNS = (
    "author_id",
    "partner_user_id",
    "user_id",
    "voter_user_id",
)


def conn(cfg: dict) -> pymysql.Connection:
    return pymysql.connect(**cfg)


def get_columns(cursor, table: str) -> list[str]:
    cursor.execute(f"SHOW COLUMNS FROM `{table}`")
    rows = cursor.fetchall()
    return [row["Field"] for row in rows]


def table_exists(cursor, table: str) -> bool:
    cursor.execute(
        "SELECT 1 FROM information_schema.tables "
        "WHERE table_schema = DATABASE() AND table_name = %s LIMIT 1",
        (table,),
    )
    return cursor.fetchone() is not None


def ensure_dev_table(prod_cur, dev_cur, table: str) -> bool:
    """dev에 테이블이 없으면 prod DDL로 생성. prod에도 없으면 False."""
    if table_exists(dev_cur, table):
        return True
    if not table_exists(prod_cur, table):
        log.warning("Skipping %s: missing on prod", table)
        return False
    prod_cur.execute(f"SHOW CREATE TABLE `{table}`")
    row = prod_cur.fetchone()
    create_sql = None
    if row:
        create_sql = row.get("Create Table")
        if not create_sql:
            for key, value in row.items():
                if key.lower() == "create table":
                    create_sql = value
                    break
    if not create_sql:
        log.error("Could not read CREATE TABLE for %s", table)
        return False
    log.info("Creating missing dev table from prod DDL: %s", table)
    dev_cur.execute(create_sql)
    return True


def build_select_sql(table: str, columns: list[str], spec: TableSpec, since: datetime) -> tuple[str, list]:
    quoted_cols = ", ".join(f"`{col}`" for col in columns)
    if spec.mode == "full":
        return f"SELECT {quoted_cols} FROM `{table}`", []

    filters = [col for col in spec.time_columns if col in columns]
    if not filters:
        return f"SELECT {quoted_cols} FROM `{table}`", []

    clauses = [f"`{col}` >= %s" for col in filters]
    params = [since] * len(filters)
    return (
        f"SELECT {quoted_cols} FROM `{table}` WHERE " + " OR ".join(clauses),
        params,
    )


def build_upsert_sql(table: str, columns: list[str], primary_keys: tuple[str, ...]) -> str:
    quoted_cols = ", ".join(f"`{col}`" for col in columns)
    placeholders = ", ".join(["%s"] * len(columns))
    update_cols = [col for col in columns if col not in primary_keys]
    if update_cols:
        updates = ", ".join(f"`{col}` = VALUES(`{col}`)" for col in update_cols)
    else:
        pk = primary_keys[0]
        updates = f"`{pk}` = VALUES(`{pk}`)"
    return (
        f"INSERT INTO `{table}` ({quoted_cols}) VALUES ({placeholders}) "
        f"ON DUPLICATE KEY UPDATE {updates}"
    )


def upsert_rows(
    prod_cur,
    dev_cur,
    spec: TableSpec,
    rows: list[dict],
    now: datetime,
    ctx: SyncContext,
) -> int:
    if not rows:
        return 0
    if not ensure_dev_table(prod_cur, dev_cur, spec.name):
        return 0

    prod_columns = get_columns(prod_cur, spec.name)
    dev_columns = set(get_columns(dev_cur, spec.name))
    common_columns = [col for col in prod_columns if col in dev_columns]
    if not common_columns:
        log.warning("Skipping %s: no common columns", spec.name)
        return 0

    upsert_sql = build_upsert_sql(spec.name, common_columns, spec.primary_keys)
    synced = 0
    for row in rows:
        working = dict(row)
        if spec.transform is not None:
            working = spec.transform(working, now, ctx)
        values = [working.get(col) for col in common_columns]
        dev_cur.execute(upsert_sql, values)
        synced += 1
    return synced


def sync_table(
    prod_cur, dev_cur, spec: TableSpec, since: datetime, now: datetime, ctx: SyncContext
) -> tuple[int, list[dict]]:
    if not ensure_dev_table(prod_cur, dev_cur, spec.name):
        return 0, []

    prod_columns = get_columns(prod_cur, spec.name)
    dev_columns = set(get_columns(dev_cur, spec.name))
    common_columns = [col for col in prod_columns if col in dev_columns]
    if not common_columns:
        log.warning("Skipping %s: no common columns", spec.name)
        return 0, []

    select_sql, params = build_select_sql(spec.name, common_columns, spec, since)
    prod_cur.execute(select_sql, params)
    rows = prod_cur.fetchall()
    if not rows:
        return 0, []

    count = upsert_rows(prod_cur, dev_cur, spec, rows, now, ctx)
    return count, [dict(r) for r in rows]


def _collect_ids(rows: Iterable[dict], columns: Iterable[str]) -> set[str]:
    ids: set[str] = set()
    for row in rows:
        for col in columns:
            value = row.get(col)
            if value is not None and str(value).strip():
                ids.add(str(value))
    return ids


def sync_rows_by_ids(
    prod_cur,
    dev_cur,
    spec: TableSpec,
    ids: set[str],
    now: datetime,
    ctx: SyncContext,
) -> int:
    if not ids:
        return 0
    if not ensure_dev_table(prod_cur, dev_cur, spec.name):
        return 0

    prod_columns = get_columns(prod_cur, spec.name)
    dev_columns = set(get_columns(dev_cur, spec.name))
    common_columns = [col for col in prod_columns if col in dev_columns]
    if not common_columns:
        return 0

    quoted_cols = ", ".join(f"`{col}`" for col in common_columns)
    id_list = sorted(ids)
    # chunk IN lists
    synced = 0
    chunk_size = 200
    for i in range(0, len(id_list), chunk_size):
        chunk = id_list[i : i + chunk_size]
        placeholders = ", ".join(["%s"] * len(chunk))
        prod_cur.execute(
            f"SELECT {quoted_cols} FROM `{spec.name}` WHERE `id` IN ({placeholders})",
            chunk,
        )
        rows = prod_cur.fetchall()
        synced += upsert_rows(prod_cur, dev_cur, spec, rows, now, ctx)
    return synced


def _run_tables(
    label: str,
    specs: tuple[TableSpec, ...],
    since: datetime,
    *,
    companion_authors: bool,
) -> None:
    now = datetime.now(timezone.utc)
    log.info(
        "%s sync start | since=%s | companion_authors=%s",
        label,
        since.isoformat(),
        companion_authors,
    )

    try:
        prod = conn(PROD)
        dev = conn(DEV)
    except Exception as exc:
        log.error("DB 연결 실패: %s", exc)
        return

    counts: dict[str, int] = {}
    collected_rows: list[dict] = []
    try:
        with prod.cursor() as prod_cur, dev.cursor() as dev_cur:
            dev_cur.execute("SET FOREIGN_KEY_CHECKS = 0")
            for spec in specs:
                try:
                    count, rows = sync_table(prod_cur, dev_cur, spec, since, now)
                    counts[spec.name] = count
                    if companion_authors:
                        collected_rows.extend(rows)
                except Exception as exc:
                    log.error("Table sync failed [%s]: %s", spec.name, exc, exc_info=True)
                    raise

            if companion_authors:
                user_ids = _collect_ids(collected_rows, USER_ID_COLUMNS)
                # personas.id == users.id for AI personas
                persona_ids = set(user_ids)
                counts["users(companion)"] = sync_rows_by_ids(
                    prod_cur, dev_cur, USERS_SPEC, user_ids, now
                )
                counts["personas(companion)"] = sync_rows_by_ids(
                    prod_cur, dev_cur, PERSONAS_SPEC, persona_ids, now
                )

            dev_cur.execute("SET FOREIGN_KEY_CHECKS = 1")
        dev.commit()
        log.info(
            "%s sync complete | %s",
            label,
            ", ".join(f"{table}={count}" for table, count in counts.items()),
        )
    except Exception:
        dev.rollback()
        raise
    finally:
        prod.close()
        dev.close()


def run_full_sync_cycle() -> None:
    now = datetime.now(timezone.utc)
    since = now - timedelta(days=BACKFILL_DAYS)
    log.info(
        "Daily full sync | cron='%s' tz=%s | backfill=%sd",
        SYNC_CRON,
        SYNC_TIMEZONE.key,
        BACKFILL_DAYS,
    )
    _run_tables("full", SYNC_TABLES, since, companion_authors=False)


def run_content_sync_cycle() -> None:
    now = datetime.now(timezone.utc)
    since = now - timedelta(minutes=CONTENT_LOOKBACK_MINUTES)
    log.info(
        "Content sync | cron='%s' tz=%s | lookback=%sm",
        SYNC_CONTENT_CRON,
        SYNC_TIMEZONE.key,
        CONTENT_LOOKBACK_MINUTES,
    )
    _run_tables("content", CONTENT_TABLES, since, companion_authors=True)


def main() -> None:
    log.info(
        "Prod->dev sync starting | full_cron='%s' content_cron='%s' tz=%s | backfill=%sd | content_lookback=%sm",
        SYNC_CRON,
        SYNC_CONTENT_CRON,
        SYNC_TIMEZONE.key,
        BACKFILL_DAYS,
        CONTENT_LOOKBACK_MINUTES,
    )
    # 기동 시 full 1회 → content도 이어서 (재기동 직후 정합)
    try:
        run_full_sync_cycle()
    except Exception as exc:
        log.error("Startup full sync failed (scheduler continues): %s", exc, exc_info=True)
    try:
        run_content_sync_cycle()
    except Exception as exc:
        log.error("Startup content sync failed (scheduler continues): %s", exc, exc_info=True)

    scheduler = BlockingScheduler(timezone=SYNC_TIMEZONE)
    scheduler.add_job(
        run_full_sync_cycle,
        CronTrigger.from_crontab(SYNC_CRON, timezone=SYNC_TIMEZONE),
        id="prod-dev-sync-full",
        replace_existing=True,
        max_instances=1,
    )
    scheduler.add_job(
        run_content_sync_cycle,
        CronTrigger.from_crontab(SYNC_CONTENT_CRON, timezone=SYNC_TIMEZONE),
        id="prod-dev-sync-content",
        replace_existing=True,
        max_instances=1,
    )
    scheduler.start()


if __name__ == "__main__":
    main()
