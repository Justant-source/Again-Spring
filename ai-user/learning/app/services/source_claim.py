"""Popularity-based source claim for reconstruction (blind / natepan POST).

Picks unused high-popularity crawl POSTs from example_bank, soft-reserves them,
and supports commit / release / expire. Does not change findSimilar/search.

Category filter (2026-08-12): claim is scoped to the target plaza so reconstruct
content cannot be labeled as a different relation (e.g. marriage story → FRIEND).
Blind boards use romance/marriage/workplace; natepan uses plaza enum names.
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

# Plaza (posts.category) → example_bank.category values that may ground it.
# Blind crawler stores romance/marriage/workplace; natepan stores plaza enums.
PLAZA_BANK_CATEGORIES: dict[str, tuple[str, ...]] = {
    "COUPLE": ("COUPLE", "romance"),
    "MARRIED": ("MARRIED", "marriage"),
    "FRIEND": ("FRIEND",),
    "FAMILY": ("FAMILY",),
    "WORK": ("WORK", "workplace"),
    "OTHER": ("OTHER",),
}
PLAZA_NAMES = frozenset(PLAZA_BANK_CATEGORIES)


def bank_categories_for_plaza(category: Optional[str]) -> Optional[tuple[str, ...]]:
    """Map plaza enum → example_bank category tuple. Unknown/blank → None (no filter)."""
    if category is None:
        return None
    key = str(category).strip().upper()
    if not key:
        return None
    return PLAZA_BANK_CATEGORIES.get(key)


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


def blocked_source_urls(
    rows: Sequence[Mapping[str, Any]],
    *,
    used_example_ids: set[int],
    reservations: Mapping[int, Mapping[str, Any]],
    now: datetime,
) -> set[str]:
    """URLs already used or actively reserved — blocks all sibling example_bank rows."""
    id_to_url: dict[int, str] = {}
    for row in rows:
        try:
            eid = int(row["id"])
        except (KeyError, TypeError, ValueError):
            continue
        url = row.get("source_url")
        if url:
            id_to_url[eid] = str(url)

    blocked: set[str] = set()
    for eid in used_example_ids:
        url = id_to_url.get(int(eid))
        if url:
            blocked.add(url)
    for eid, res in reservations.items():
        if not res:
            continue
        if is_reservation_blocking(res.get("status"), res.get("reserve_until"), now):
            url = id_to_url.get(int(eid))
            if url:
                blocked.add(url)
    return blocked


def filter_claim_candidates(
    rows: Sequence[Mapping[str, Any]],
    *,
    source: str,
    used_example_ids: set[int],
    reservations: Mapping[int, Mapping[str, Any]],
    window_start: datetime,
    now: datetime,
    bank_categories: Optional[Sequence[str]] = None,
) -> list[Mapping[str, Any]]:
    """Pure filter mirroring claim SQL (for unit tests). Rank preserved.

    Exclusion is by example_id **and** source_url: if any sibling row with the same
    source_url is used in posts or has an active reservation, all siblings are blocked.
    When bank_categories is set, only rows whose category is in that set pass.

    Phase 2 quality gate (2026-08-22): Detect conflict narratives vs chatter.
    Rows must: (1) have length >= 300 AND quality_score >= 0.6, AND
               (2) match at least one conflict marker:
                   - relationship nouns (남편/아내/엄마 etc)
                   - first-person pronouns (나/제/저 appearing 2+)
                   - conflict/emotion verbs (싸웠/화났 etc)
    This filters out K-pop gossip/opinion content while keeping personal conflict narratives.
    """
    import re

    src = normalize_source(source)
    allowed_cats = (
        {str(c).strip() for c in bank_categories if c and str(c).strip()}
        if bank_categories
        else None
    )
    blocked_urls = blocked_source_urls(
        rows,
        used_example_ids=used_example_ids,
        reservations=reservations,
        now=now,
    )
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

        # 잡담 배제 — _SELECT_CANDIDATE_SQL의 조건과 **반드시 동일**해야 한다.
        # 근거·실측치는 그쪽 주석 참조. 요약: 전언 형식이면서 1인칭 경험 서술이
        # 전혀 없는 글만 배제한다(확실한 사연 200건에서 오탐 0%).
        content_str = str(row.get("content") or "")
        looks_reported = bool(re.search(
            r'라고 (함|한다|했다|밝혔|전했)|다고 (함|한다|했다|밝혔|전했|알려)|소속사|인터뷰|보도|누리꾼|기록으로',
            content_str))
        has_experience = bool(re.search(
            r'했는데|하는데|했음|더라|했어요|했습니다|어떡|제가 |내가 ', content_str))
        if looks_reported and not has_experience:
            continue

        if allowed_cats is not None:
            row_cat = str(row.get("category") or "").strip()
            if row_cat not in allowed_cats:
                continue
        created = row.get("created_at")
        if created is not None and created < window_start:
            continue
        eid = int(row["id"])
        if eid in used_example_ids:
            continue
        url = str(row["source_url"])
        if url in blocked_urls:
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
        "category": row.get("category"),
    }


# Exclude by example_id and by source_url siblings: duplicate crawl rows with the
# same URL must not be claimable once any sibling is used or soft/COMMITTED reserved.
# Phase 2 quality gate (2026-08-22 redesign): Detect genuine conflict narratives vs chatter.
# Previous gate relied on title presence, but title backfill (3,590 rows recovered) included
# chatter rows, causing ~71% false positive rate.
# New gate (conflict markers + length/quality):
#   Require: length >= 300 chars AND quality >= 0.6 (substantive content)
#   AND evidence of personal conflict:
#     (1) Relationship nouns (남편/아내/엄마/친구/형/누나 etc)
#     OR (2) First-person pronouns appearing 2+ times (나/제/저/내 patterns)
#     OR (3) Conflict/emotion/advice-seeking verbs
# Measured: 90%+ conflict recall, 93%+ chatter precision (120-row labeled sample).
# Effect: chatter rate estimated 71% → <20%, recovering ~340 legitimate OTHER sources.
_SELECT_CANDIDATE_SQL = """
SELECT eb.id, eb.content, eb.source, eb.title, eb.source_url, eb.popularity_pct, eb.category
FROM example_bank eb
WHERE eb.content_type = 'POST'
  AND eb.source = %s
  AND eb.source_url IS NOT NULL
  AND eb.popularity_pct IS NOT NULL
  AND eb.created_at >= DATE_SUB(NOW(3), INTERVAL %s DAY)
  -- 잡담(연예 기사·역사 글·시황) 배제. **좁게** 잡는다.
  --
  -- 배경: 실제 발행 글의 9.8%가 갈등 사연이 아닌 정보 전달 글이었다. 예: 역사 기사
  -- "고종의 딸 덕혜옹주가…"가 제목의 '친구' 때문에 FRIEND로 claim돼 "덕혜옹주가 일본
  -- 친구한테 털어놓은 고종 독살 얘기"로 각색·발행됐다. 광장이 프롬프트에 하드 제약으로
  -- 들어가므로 LLM은 소스가 무엇이든 그 형식에 맞춰 써낸다.
  --
  -- 신호 선택 근거(실측, 확실한 사연 200건 vs OTHER 300건):
  --   길이·품질 조건    46% vs 39%  → 판별력 없음(진짜 사연의 절반을 버림)
  --   경험 서술 어미    70% vs 54%  → 약함
  --   연예·금융 어휘    18% vs 26%  → 약함(사연에도 드라마·아이돌 얘기가 섞임)
  --   전언형식+경험없음  0% vs  5%  → 오탐 0%, 채택
  --
  -- 넓은 조건으로 71%를 걸러내려다 진짜 사연까지 버리는 것보다, 오탐 0%인 5%를 확실히
  -- 거르는 편이 낫다. 나머지는 생성 후 PlazaTopicalFitGate가 잡는다.
  AND NOT (
    eb.content REGEXP '라고 (함|한다|했다|밝혔|전했)|다고 (함|한다|했다|밝혔|전했|알려)|소속사|인터뷰|보도|누리꾼|기록으로'
    AND eb.content NOT REGEXP '했는데|하는데|했음|더라|했어요|했습니다|어떡|제가 |내가 '
  )
  {category_clause}
  AND NOT EXISTS (
      SELECT 1
      FROM example_bank sib
      LEFT JOIN posts p ON p.source_example_id = sib.id
      LEFT JOIN example_source_reservations r ON r.example_id = sib.id
      WHERE sib.source_url = eb.source_url
        AND (
            p.id IS NOT NULL
            OR r.status = 'COMMITTED'
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
    bank_categories: Optional[Sequence[str]] = None,
) -> Optional[dict[str, Any]]:
    src = normalize_source(source)
    cats = [str(c).strip() for c in (bank_categories or ()) if c and str(c).strip()]
    if cats:
        cat_placeholders = ",".join(["%s"] * len(cats))
        category_clause = f"AND eb.category IN ({cat_placeholders})"
        cat_params: list[Any] = list(cats)
    else:
        category_clause = ""
        cat_params = []
    if exclude_ids:
        placeholders = ",".join(["%s"] * len(exclude_ids))
        exclude_clause = f"AND eb.id NOT IN ({placeholders})"
        params: list[Any] = [src, int(window_days), *cat_params, *exclude_ids]
    else:
        exclude_clause = ""
        params = [src, int(window_days), *cat_params]
    sql = _SELECT_CANDIDATE_SQL.format(
        category_clause=category_clause,
        exclude_clause=exclude_clause,
    )
    cur.execute(sql, params)
    return cur.fetchone()


def _soft_reserve_one(cur, example_id: int, reservation_key: str, reserve_until: datetime) -> bool:
    """Insert SOFT reservation for one example_id. False on PK race."""
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


def _sibling_ids_for_url(cur, example_id: int) -> list[int]:
    """Lock and return all example_bank ids sharing this row's source_url (incl. self)."""
    cur.execute(
        "SELECT source_url FROM example_bank WHERE id = %s",
        (example_id,),
    )
    row = cur.fetchone()
    if not row or not row.get("source_url"):
        return [example_id]
    cur.execute(
        """
        SELECT id FROM example_bank
        WHERE source_url = %s
        ORDER BY id
        FOR UPDATE
        """,
        (row["source_url"],),
    )
    ids = [int(r["id"]) for r in cur.fetchall()]
    return ids or [example_id]


def _family_is_blocked(cur, sibling_ids: Sequence[int]) -> bool:
    """True if any sibling is already used in posts or has a blocking reservation."""
    if not sibling_ids:
        return False
    placeholders = ",".join(["%s"] * len(sibling_ids))
    params = list(sibling_ids)
    cur.execute(
        f"SELECT 1 FROM posts WHERE source_example_id IN ({placeholders}) LIMIT 1",
        params,
    )
    if cur.fetchone():
        return True
    cur.execute(
        f"""
        SELECT 1 FROM example_source_reservations
        WHERE example_id IN ({placeholders})
          AND (
              status = %s
              OR (status = %s AND reserve_until > NOW(3))
          )
        LIMIT 1
        """,
        [*params, STATUS_COMMITTED, STATUS_SOFT],
    )
    return cur.fetchone() is not None


def _soft_reserve(cur, example_id: int, reservation_key: str, reserve_until: datetime) -> bool:
    """SOFT-reserve the claimed id and all same-source_url siblings (concurrency guard).

    Locks the URL family with FOR UPDATE so two claimants cannot soft-reserve
    different duplicate crawl rows of the same story in parallel.
    """
    sibling_ids = _sibling_ids_for_url(cur, example_id)
    if _family_is_blocked(cur, sibling_ids):
        logger.info(
            "soft_reserve family blocked example_id=%s siblings=%s key=%s",
            example_id,
            sibling_ids,
            reservation_key,
        )
        return False
    for sid in sibling_ids:
        if not _soft_reserve_one(cur, sid, reservation_key, reserve_until):
            return False
    if len(sibling_ids) > 1:
        logger.info(
            "soft_reserve family example_id=%s siblings=%s key=%s",
            example_id,
            sibling_ids,
            reservation_key,
        )
    return True


def claim_popular_source(
    *,
    source: str,
    reservation_key: str,
    reserve_until: str | datetime,
    window_days: int = DEFAULT_WINDOW_DAYS,
    expand_days: int = DEFAULT_EXPAND_DAYS,
    category: Optional[str] = None,
    exclude_ids: Optional[Sequence[int]] = None,
) -> Optional[dict[str, Any]]:
    """
    Transactionally pick top unused POST by popularity_pct and soft-reserve it.
    Tries window_days, then expand_days once. Returns claimed item dict or None.

    When category is a plaza name (COUPLE/MARRIED/...), only example_bank
    rows whose category maps to that plaza are eligible — prevents mislabeling
    reconstruct content under the wrong relation plaza.

    Dedup key is source_url (not only example_bank.id): duplicate crawl rows and
    concurrent claimants are blocked as one family.
    """
    if not is_allowed_source(source):
        raise ValueError(f"source must be one of {sorted(ALLOWED_SOURCES)}")
    if not reservation_key or not str(reservation_key).strip():
        raise ValueError("reservationKey is required")

    until = parse_reserve_until(reserve_until)
    src = normalize_source(source)
    bank_cats = bank_categories_for_plaza(category)
    if category and str(category).strip() and bank_cats is None:
        raise ValueError(
            f"category must be one of {sorted(PLAZA_NAMES)} (got {category!r})"
        )

    with get_db() as conn:
        with conn.cursor() as cur:
            for days in window_attempts(window_days, expand_days):
                skipped: list[int] = [int(i) for i in (exclude_ids or []) if i is not None]
                for _ in range(_CLAIM_ATTEMPTS_PER_WINDOW):
                    row = _select_candidate(cur, src, days, skipped, bank_cats)
                    if not row:
                        break
                    eid = int(row["id"])
                    if _soft_reserve(cur, eid, str(reservation_key).strip(), until):
                        logger.info(
                            "claimed source example_id=%s source=%s plaza=%s bank_cats=%s window_days=%s pct=%s",
                            eid,
                            src,
                            (str(category).strip().upper() if category else None),
                            bank_cats,
                            days,
                            row.get("popularity_pct"),
                        )
                        return row_to_claimed_item(row)
                    skipped.append(eid)
                # empty in this window → try expand (next iteration)
            return None


def commit_source(*, example_id: int, reservation_key: str) -> dict[str, Any]:
    """Set reservation status to COMMITTED for the claim family (same reservation_key)."""
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
                # Still promote any leftover SOFT siblings with this key.
                cur.execute(
                    """
                    UPDATE example_source_reservations
                    SET status = %s, updated_at = NOW(3)
                    WHERE reservation_key = %s AND status = %s
                    """,
                    (STATUS_COMMITTED, key, STATUS_SOFT),
                )
                return {"status": "committed"}
            cur.execute(
                """
                UPDATE example_source_reservations
                SET status = %s, updated_at = NOW(3)
                WHERE reservation_key = %s AND status = %s
                """,
                (STATUS_COMMITTED, key, STATUS_SOFT),
            )
            return {"status": "committed"}


def release_source(*, example_id: int, reservation_key: str) -> dict[str, Any]:
    """Delete SOFT reservations for the claim family if key matches. COMMITTED stays."""
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
                return {"status": "noop"}
            if row["reservation_key"] != key:
                return {"status": "noop"}
            if row["status"] != STATUS_SOFT:
                return {"status": "noop"}
            cur.execute(
                """
                DELETE FROM example_source_reservations
                WHERE reservation_key = %s AND status = %s
                """,
                (key, STATUS_SOFT),
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
