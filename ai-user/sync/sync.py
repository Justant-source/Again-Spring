"""
AI 콘텐츠 동기화 서비스: prod DB → dev DB

동기화 대상 (AI 봇이 생성한 레코드만):
  1. users          (synthetic=1, varchar id)  → 충돌 없음
  2. personas       (varchar id)               → 충돌 없음
  3. posts          (AI 작성, varchar id)       → 충돌 없음
  4. vote_options   (AI 글의 선택지)            → INSERT IGNORE
  5. post_comments  (AI 작성)                  → INSERT IGNORE
  6. votes          (AI 투표)                  → INSERT IGNORE
  7. post_likes     (AI 좋아요)                → INSERT IGNORE

실행 주기: SYNC_INTERVAL_SECONDS (기본 300초)
"""

import os
import time
import logging
from datetime import datetime, timezone, timedelta
import pymysql
import pymysql.cursors

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [sync] %(levelname)s %(message)s",
    datefmt="%Y-%m-%dT%H:%M:%S"
)
log = logging.getLogger(__name__)

# ── 설정 ─────────────────────────────────────────────────────────────────────
PROD = dict(
    host=os.getenv("PROD_DB_HOST", "againspring-mariadb-prod"),
    port=int(os.getenv("PROD_DB_PORT", "3306")),
    user=os.getenv("PROD_DB_USER", "againspring"),
    password=os.getenv("PROD_DB_PASSWORD", ""),
    database=os.getenv("PROD_DB_NAME", "againspring"),
    charset="utf8mb4",
    cursorclass=pymysql.cursors.DictCursor,
    connect_timeout=10,
)
DEV = dict(
    host=os.getenv("DEV_DB_HOST", "againspring-mariadb-dev"),
    port=int(os.getenv("DEV_DB_PORT", "3306")),
    user=os.getenv("DEV_DB_USER", "againspring"),
    password=os.getenv("DEV_DB_PASSWORD", ""),
    database=os.getenv("DEV_DB_NAME", "againspring_dev"),
    charset="utf8mb4",
    cursorclass=pymysql.cursors.DictCursor,
    connect_timeout=10,
)
INTERVAL = int(os.getenv("SYNC_INTERVAL_SECONDS", "300"))
# 최초 실행 시 이전 X일치 데이터까지 소급 복사 (기본 3일)
BACKFILL_DAYS = int(os.getenv("SYNC_BACKFILL_DAYS", "3"))


# ── 유틸 ─────────────────────────────────────────────────────────────────────

def conn(cfg: dict) -> pymysql.Connection:
    return pymysql.connect(**cfg)


def rows_to_insert(cursor, table: str, rows: list[dict]) -> int:
    """INSERT IGNORE — 중복(id 충돌)은 건너뜀."""
    if not rows:
        return 0
    cols = list(rows[0].keys())
    placeholders = ", ".join(["%s"] * len(cols))
    col_names    = ", ".join([f"`{c}`" for c in cols])
    sql = f"INSERT IGNORE INTO `{table}` ({col_names}) VALUES ({placeholders})"
    count = 0
    for row in rows:
        try:
            cursor.execute(sql, [row[c] for c in cols])
            if cursor.rowcount > 0:
                count += 1
        except Exception as e:
            log.debug("INSERT IGNORE skip [%s]: %s", table, e)
    return count


# ── 동기화 단계 ───────────────────────────────────────────────────────────────

def sync_users(prod_cur, dev_cur) -> int:
    """AI 봇 계정 (synthetic=1) 동기화."""
    prod_cur.execute("SELECT * FROM users WHERE synthetic = 1")
    rows = prod_cur.fetchall()
    return rows_to_insert(dev_cur, "users", rows)


def sync_personas(prod_cur, dev_cur) -> int:
    """AI 페르소나 데이터 동기화."""
    prod_cur.execute("SELECT * FROM personas")
    rows = prod_cur.fetchall()
    return rows_to_insert(dev_cur, "personas", rows)


def sync_posts(prod_cur, dev_cur, since: datetime) -> list[str]:
    """AI 작성 글 동기화. 삽입된 post_id 목록 반환."""
    prod_cur.execute(
        """SELECT p.* FROM posts p
           JOIN users u ON u.id = p.author_id
           WHERE u.synthetic = 1 AND p.created_at > %s
           ORDER BY p.created_at""",
        (since,)
    )
    rows = prod_cur.fetchall()
    inserted_ids = []
    for row in rows:
        try:
            cols = list(row.keys())
            placeholders = ", ".join(["%s"] * len(cols))
            col_names = ", ".join([f"`{c}`" for c in cols])
            sql = f"INSERT IGNORE INTO posts ({col_names}) VALUES ({placeholders})"
            dev_cur.execute(sql, [row[c] for c in cols])
            if dev_cur.rowcount > 0:
                inserted_ids.append(row["id"])
        except Exception as e:
            log.debug("INSERT IGNORE skip [posts]: %s", e)
    return inserted_ids


def sync_vote_options(prod_cur, dev_cur, post_ids: list[str]) -> int:
    """AI 글의 투표 선택지 동기화."""
    if not post_ids:
        return 0
    fmt = ",".join(["%s"] * len(post_ids))
    prod_cur.execute(f"SELECT * FROM vote_options WHERE post_id IN ({fmt})", post_ids)
    rows = prod_cur.fetchall()
    return rows_to_insert(dev_cur, "vote_options", rows)


def sync_comments(prod_cur, dev_cur, since: datetime) -> int:
    """AI 작성 댓글/대댓글 동기화 (FK 체크 해제 후)."""
    prod_cur.execute(
        """SELECT pc.* FROM post_comments pc
           JOIN users u ON u.id = pc.author_id
           WHERE u.synthetic = 1 AND pc.created_at > %s
           ORDER BY pc.created_at""",
        (since,)
    )
    rows = prod_cur.fetchall()
    return rows_to_insert(dev_cur, "post_comments", rows)


def sync_votes(prod_cur, dev_cur, since: datetime) -> int:
    """AI 투표 동기화."""
    prod_cur.execute(
        """SELECT v.* FROM votes v
           JOIN users u ON u.id = v.voter_user_id
           WHERE u.synthetic = 1 AND v.created_at > %s
           ORDER BY v.created_at""",
        (since,)
    )
    rows = prod_cur.fetchall()
    return rows_to_insert(dev_cur, "votes", rows)


def sync_likes(prod_cur, dev_cur, since: datetime) -> int:
    """AI 좋아요 동기화."""
    prod_cur.execute(
        """SELECT pl.* FROM post_likes pl
           JOIN users u ON u.id = pl.user_id
           WHERE u.synthetic = 1 AND pl.created_at > %s
           ORDER BY pl.created_at""",
        (since,)
    )
    rows = prod_cur.fetchall()
    return rows_to_insert(dev_cur, "post_likes", rows)


# ── 메인 동기화 루프 ──────────────────────────────────────────────────────────

def run_sync(since: datetime) -> datetime:
    """한 번의 동기화 사이클 실행. 다음 실행의 since 시각 반환."""
    try:
        prod = conn(PROD)
        dev  = conn(DEV)
    except Exception as e:
        log.error("DB 연결 실패: %s", e)
        return since

    try:
        with prod.cursor() as p_cur, dev.cursor() as d_cur:
            # FK 체크 해제 (auto_increment bigint id 충돌 방지)
            d_cur.execute("SET FOREIGN_KEY_CHECKS = 0")

            u = sync_users(p_cur, d_cur)
            r = sync_personas(p_cur, d_cur)
            post_ids = sync_posts(p_cur, d_cur, since)
            vo = sync_vote_options(p_cur, d_cur, post_ids)
            c = sync_comments(p_cur, d_cur, since)
            v = sync_votes(p_cur, d_cur, since)
            l = sync_likes(p_cur, d_cur, since)

            d_cur.execute("SET FOREIGN_KEY_CHECKS = 1")
            dev.commit()

        now = datetime.now(timezone.utc)
        if u + r + len(post_ids) + vo + c + v + l > 0:
            log.info(
                "동기화 완료 | users=%d personas=%d posts=%d vote_opts=%d "
                "comments=%d votes=%d likes=%d | since=%s",
                u, r, len(post_ids), vo, c, v, l,
                since.strftime("%Y-%m-%dT%H:%M:%S")
            )
        else:
            log.debug("신규 AI 콘텐츠 없음 (since=%s)", since.strftime("%Y-%m-%dT%H:%M:%S"))
        return now

    except Exception as e:
        log.error("동기화 오류: %s", e, exc_info=True)
        return since
    finally:
        prod.close()
        dev.close()


def main():
    log.info("AI 콘텐츠 동기화 시작 (prod→dev) | 주기=%ds | 소급=%d일",
             INTERVAL, BACKFILL_DAYS)

    # 최초 실행: 소급 기간까지 복사
    since = datetime.now(timezone.utc) - timedelta(days=BACKFILL_DAYS)
    log.info("초기 소급 동기화: since=%s", since.strftime("%Y-%m-%dT%H:%M:%S"))

    while True:
        since = run_sync(since)
        time.sleep(INTERVAL)


if __name__ == "__main__":
    main()
