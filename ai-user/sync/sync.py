"""
Prod DB -> dev DB 일일 동기화 서비스.

- 실행 주기: SYNC_CRON (기본 KST 05:30, 하루 1회)
- 방식: prod를 기준으로 최근 N일 창을 읽어 dev에 upsert
- 실사용자 계정은 dev에서 로그인 불가하도록 비식별화
"""

from __future__ import annotations

import logging
import os
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Callable
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

SYNC_CRON = _env("SYNC_CRON", "30 5 * * *")
SYNC_TIMEZONE = ZoneInfo(_env("SYNC_TIMEZONE", "Asia/Seoul"))
BACKFILL_DAYS = int(_env("SYNC_BACKFILL_DAYS", "7"))


@dataclass(frozen=True)
class TableSpec:
    name: str
    primary_keys: tuple[str, ...]
    mode: str = "incremental"  # incremental | full
    time_columns: tuple[str, ...] = ()
    transform: Callable[[dict, datetime], dict] | None = None


def _is_truthy_db(value) -> bool:
    if isinstance(value, (bytes, bytearray)):
        return any(value)
    return bool(value)


def _mask_real_user(row: dict, now: datetime) -> dict:
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


SYNC_TABLES: tuple[TableSpec, ...] = (
    TableSpec("users", ("id",), time_columns=("updated_at", "created_at"), transform=_mask_real_user),
    TableSpec("personas", ("id",), mode="full"),
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


def conn(cfg: dict) -> pymysql.Connection:
    return pymysql.connect(**cfg)


def get_columns(cursor, table: str) -> list[str]:
    cursor.execute(f"SHOW COLUMNS FROM `{table}`")
    rows = cursor.fetchall()
    return [row["Field"] for row in rows]


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


def sync_table(prod_cur, dev_cur, spec: TableSpec, since: datetime, now: datetime) -> int:
    prod_columns = get_columns(prod_cur, spec.name)
    dev_columns = set(get_columns(dev_cur, spec.name))
    common_columns = [col for col in prod_columns if col in dev_columns]
    if not common_columns:
        log.warning("Skipping %s: no common columns", spec.name)
        return 0

    select_sql, params = build_select_sql(spec.name, common_columns, spec, since)
    prod_cur.execute(select_sql, params)
    rows = prod_cur.fetchall()
    if not rows:
        return 0

    upsert_sql = build_upsert_sql(spec.name, common_columns, spec.primary_keys)
    synced = 0
    for row in rows:
        working = dict(row)
        if spec.transform is not None:
            working = spec.transform(working, now)
        values = [working.get(col) for col in common_columns]
        dev_cur.execute(upsert_sql, values)
        synced += 1
    return synced


def run_sync_cycle() -> None:
    now = datetime.now(timezone.utc)
    since = now - timedelta(days=BACKFILL_DAYS)
    log.info(
        "Daily sync start | cron='%s' tz=%s | backfill=%sd | window_since=%s",
        SYNC_CRON,
        SYNC_TIMEZONE.key,
        BACKFILL_DAYS,
        since.isoformat(),
    )

    try:
        prod = conn(PROD)
        dev = conn(DEV)
    except Exception as exc:
        log.error("DB 연결 실패: %s", exc)
        return

    counts: dict[str, int] = {}
    try:
        with prod.cursor() as prod_cur, dev.cursor() as dev_cur:
            dev_cur.execute("SET FOREIGN_KEY_CHECKS = 0")
            for spec in SYNC_TABLES:
                try:
                    counts[spec.name] = sync_table(prod_cur, dev_cur, spec, since, now)
                except Exception as exc:
                    log.error("Table sync failed [%s]: %s", spec.name, exc, exc_info=True)
                    raise
            dev_cur.execute("SET FOREIGN_KEY_CHECKS = 1")
        dev.commit()
        log.info(
            "Daily sync complete | %s",
            ", ".join(f"{table}={count}" for table, count in counts.items()),
        )
    except Exception:
        dev.rollback()
        raise
    finally:
        prod.close()
        dev.close()


def main() -> None:
    log.info(
        "Prod->dev sync scheduler starting | cron='%s' tz=%s | backfill=%sd",
        SYNC_CRON,
        SYNC_TIMEZONE.key,
        BACKFILL_DAYS,
    )
    scheduler = BlockingScheduler(timezone=SYNC_TIMEZONE)
    trigger = CronTrigger.from_crontab(SYNC_CRON, timezone=SYNC_TIMEZONE)
    scheduler.add_job(run_sync_cycle, trigger, id="prod-dev-sync", replace_existing=True, max_instances=1)
    scheduler.start()


if __name__ == "__main__":
    main()
