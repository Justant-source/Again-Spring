"""Popularity-based source claim for reconstruction (blind / natepan POST).

Picks unused high-popularity crawl POSTs from example_bank, soft-reserves them,
and supports commit / release / expire. Does not change findSimilar/search.
"""
from __future__ import annotations

import logging
from datetime import datetime, timezone
from decimal import Decimal
from typing import Any, Mapping, Optional, Sequence

from pymysql.err import IntegrityError

from app.db.session import get_db

logger = logging.getLogger(__name__)

ALLOWED_SOURCES = frozenset({"blind", "natepan"})
DEFAULT_WINDOW_DAYS = 14
DEFAULT_EXPAND_DAYS = 30
STATUS_SOFT = "SOFT"
STATUS_COMMITTED = "COMMITTED"
# Max candidates to try per window when soft-reserve races
_CLAIM_ATTEMPTS_PER_WINDOW = 8


def normalize_source(source: str) -> str:
    return (source or "").strip().lower()


def is_allowed_source(source: str) -> bool:
    return normalize_source(source) in ALLOWED_SOURCES


def window_attempts(
    window_days: int = DEFAULT_WINDOW_DAYS,
    expand_days: int = DEFAULT_EXPAND_DAYS,
) -> list[int]:
    """Return day windows to try: primary, then expand once if larger."""
    w = max(1, int(window_days))
    e = max(1, int(expand_days))
    if e > w:
        return [w, e]
    return [w]


def is_reservation_blocking(
    status: Optional[str],
    reserve_until: Optional[datetime],
    now: datetime,
) -> bool:
    """True if a reservation row permanently or currently blocks reclaim."""
    if not status:
        return False
    if status == STATUS_COMMITTED:
        return True
    if status == STATUS_SOFT and reserve_until is not None and reserve_until > now:
        return True
    return False


def filter_claim_candidates(
    rows: Sequence[Mapping[str, Any]],
    *,
    source: str,
    used_example_ids: set[int],
    reservations: Mapping[int, Mapping[str, Any]],
    window_start: datetime,
    now: datetime,
) -> list[Mapping[str, Any]]:
    """Pure filter mirroring claim SQL (for unit tests). Rank preserved."""
    src = normalize_source(source)
    out: list[Mapping[str, Any]] = []
    for row in rows:
        if normalize_source(str(row.get("source") or "")) != src:
            continue
        if row.get("content_type") != "POST":
            continue
        if not row.get("source_url"):
            continue
        if row.get("popularity_pct") is None:
            continue
        created = row.get("created_at")
        if created is not None and created < window_start:
            continue
        eid = int(row["id"])
        if eid in used_example_ids:
            continue
        res = reservations.get(eid)
        if res and is_reservation_blocking(res.get("status"), res.get("reserve_until"), now):
            continue
        out.append(row)
    return sorted(
        out,
        key=lambda r: (
            r.get("popularity_pct") is None,
            -(float(r["popularity_pct"]) if r.get("popularity_pct") is not None else 0.0),
        ),
    )


def parse_reserve_until(value: str | datetime) -> datetime:
    """Parse ISO-8601 reserveUntil into a naive UTC-comparable datetime for MariaDB."""
    if isinstance(value, datetime):
        dt = value
    else:
        raw = (value or "").strip()
        if raw.endswith("Z"):
            raw = raw[:-1] + "+00:00"
        dt = datetime.fromisoformat(raw)
    if dt.tzinfo is not None:
        dt = dt.astimezone(timezone.utc).replace(tzinfo=None)
    return dt


def _score_from_pct(pct: Any) -> Optional[float]:
    if pct is None:
        return None
    if isinstance(pct, Decimal):
        return float(pct)
    return float(pct)


def row_to_claimed_item(row: Mapping[str, Any]) -> dict[str, Any]:
    return {
        "id": int(row["id"]),
        "content": row["content"],
        "source": row["source"],
        "title": row.get("title"),
        "sourceUrl": row.get("source_url"),
        "score": _score_from_pct(row.get("popularity_pct")),
    }


_SELECT_CANDIDATE_SQL = """
SELECT eb.id, eb.content, eb.source, eb.title, eb.source_url, eb.popularity_pct
FROM example_bank eb
WHERE eb.content_type = 'POST'
  AND eb.source = %s
  AND eb.source_url IS NOT NULL
  AND eb.popularity_pct IS NOT NULL
  AND eb.created_at >= DATE_SUB(NOW(3), INTERVAL %s DAY)
  AND NOT EXISTS (
      SELECT 1 FROM posts p WHERE p.source_example_id = eb.id
  )
  AND NOT EXISTS (
      SELECT 1 FROM example_source_reservations r
      WHERE r.example_id = eb.id
        AND (
            r.status = 'COMMITTED'
            OR (r.status = 'SOFT' AND r.reserve_until > NOW(3))
        )
  )
  {exclude_clause}
ORDER BY eb.popularity_pct DESC
LIMIT 1
FOR UPDATE SKIP LOCKED
"""


def _select_candidate(
    cur,
    source: str,
    window_days: int,
    exclude_ids: Sequence[int],
) -> Optional[dict[str, Any]]:
    src = normalize_source(source)
    if exclude_ids:
        placeholders = ",".join(["%s"] * len(exclude_ids))
        exclude_clause = f"AND eb.id NOT IN ({placeholders})"
        params: list[Any] = [src, int(window_days), *exclude_ids]
    else:
        exclude_clause = ""
        params = [src, int(window_days)]
    sql = _SELECT_CANDIDATE_SQL.format(exclude_clause=exclude_clause)
    cur.execute(sql, params)
    return cur.fetchone()


def _soft_reserve(cur, example_id: int, reservation_key: str, reserve_until: datetime) -> bool:
    """Insert SOFT reservation. Reclaim expired SOFT via delete-then-insert. False on race."""
    cur.execute(
        """
        DELETE FROM example_source_reservations
        WHERE example_id = %s AND status = %s AND reserve_until <= NOW(3)
        """,
        (example_id, STATUS_SOFT),
    )
    try:
        cur.execute(
            """
            INSERT INTO example_source_reservations
                (example_id, reservation_key, status, reserve_until, created_at, updated_at)
            VALUES (%s, %s, %s, %s, NOW(3), NOW(3))
            """,
            (example_id, reservation_key, STATUS_SOFT, reserve_until),
        )
        return True
    except IntegrityError:
        logger.info(
            "soft_reserve race/conflict example_id=%s key=%s",
            example_id,
            reservation_key,
        )
        return False


def claim_popular_source(
    *,
    source: str,
    reservation_key: str,
    reserve_until: str | datetime,
    window_days: int = DEFAULT_WINDOW_DAYS,
    expand_days: int = DEFAULT_EXPAND_DAYS,
) -> Optional[dict[str, Any]]:
    """
    Transactionally pick top unused POST by popularity_pct and soft-reserve it.
    Tries window_days, then expand_days once. Returns claimed item dict or None.
    """
    if not is_allowed_source(source):
        raise ValueError(f"source must be one of {sorted(ALLOWED_SOURCES)}")
    if not reservation_key or not str(reservation_key).strip():
        raise ValueError("reservationKey is required")

    until = parse_reserve_until(reserve_until)
    src = normalize_source(source)

    with get_db() as conn:
        with conn.cursor() as cur:
            for days in window_attempts(window_days, expand_days):
                skipped: list[int] = []
                for _ in range(_CLAIM_ATTEMPTS_PER_WINDOW):
                    row = _select_candidate(cur, src, days, skipped)
                    if not row:
                        break
                    eid = int(row["id"])
                    if _soft_reserve(cur, eid, str(reservation_key).strip(), until):
                        logger.info(
                            "claimed source example_id=%s source=%s window_days=%s pct=%s",
                            eid,
                            src,
                            days,
                            row.get("popularity_pct"),
                        )
                        return row_to_claimed_item(row)
                    skipped.append(eid)
                # empty in this window → try expand (next iteration)
            return None


def commit_source(*, example_id: int, reservation_key: str) -> dict[str, Any]:
    """Set reservation status to COMMITTED (permanent). Verifies key."""
    key = str(reservation_key).strip()
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT example_id, reservation_key, status
                FROM example_source_reservations
                WHERE example_id = %s
                FOR UPDATE
                """,
                (int(example_id),),
            )
            row = cur.fetchone()
            if not row:
                return {"status": "missing"}
            if row["reservation_key"] != key:
                return {"status": "key_mismatch"}
            if row["status"] == STATUS_COMMITTED:
                return {"status": "committed"}
            cur.execute(
                """
                UPDATE example_source_reservations
                SET status = %s, updated_at = NOW(3)
                WHERE example_id = %s AND reservation_key = %s
                """,
                (STATUS_COMMITTED, int(example_id), key),
            )
            return {"status": "committed"}


def release_source(*, example_id: int, reservation_key: str) -> dict[str, Any]:
    """Delete SOFT reservation if key matches. No-op if COMMITTED or missing."""
    key = str(reservation_key).strip()
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                DELETE FROM example_source_reservations
                WHERE example_id = %s AND reservation_key = %s AND status = %s
                """,
                (int(example_id), key, STATUS_SOFT),
            )
            if cur.rowcount > 0:
                return {"status": "released"}
            return {"status": "noop"}


def expire_source_reservations() -> dict[str, Any]:
    """Delete expired SOFT reservations (reserve_until < NOW)."""
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                DELETE FROM example_source_reservations
                WHERE status = %s AND reserve_until < NOW(3)
                """,
                (STATUS_SOFT,),
            )
            return {"status": "ok", "deleted": int(cur.rowcount)}
