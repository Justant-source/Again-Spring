from fastapi import APIRouter, BackgroundTasks
from app.db.session import get_db
from app.services.quality_filter import QualityFilter
from app.services.register_classifier import classify as classify_register
from app.services.popularity_gate import (
    MIN_POPULARITY_PCT,
    filter_comments_for_parents,
    load_ranked_parent_urls,
    select_popular_posts,
)
from datetime import datetime
import logging

logger = logging.getLogger(__name__)
router = APIRouter()
quality = QualityFilter()

# Wave1-D: 활성 크롤러는 natepan · blind 둘뿐 (그 외 모듈 삭제됨)
_CRAWLERS = {
    "natepan": "app.crawlers.natepan",
    "blind": "app.crawlers.blind",
}

# Named lock so concurrent crawl runs for the same source cannot both INSERT
# a URL that was absent when each run snapshot existing_urls (pre-embed).
_CRAWL_INGEST_LOCK_TIMEOUT_SEC = 120


def _crawl_ingest_lock_name(source: str) -> str:
    return f"ai_learning_crawl_ingest:{source.lower()}"


def _gate_items(source: str, items: list, existing_ranked_parents: set[str]) -> tuple[list, int]:
    """Apply popularity gate. Returns (accepted_items, skipped_unpopular_count)."""
    posts = [i for i in items if (i.get("content_type") or "COMMENT") == "POST"]
    comments = [i for i in items if (i.get("content_type") or "COMMENT") != "POST"]

    accepted_posts, _url_pct = select_popular_posts(posts, source=source)
    accepted_urls = {p.get("source_url") for p in accepted_posts if p.get("source_url")}
    # Comments may attach to newly accepted posts OR already-ranked parents in DB
    allow_parents = accepted_urls | existing_ranked_parents
    accepted_comments = filter_comments_for_parents(comments, allow_parents)

    skipped = (len(posts) - len(accepted_posts)) + (len(comments) - len(accepted_comments))
    return list(accepted_posts) + list(accepted_comments), skipped


async def _do_crawl(source, daily_limit, embed_service):
    try:
        module_path = _CRAWLERS.get(source)
        if module_path is None:
            logger.warning(f"Unknown source: {source}")
            return

        import importlib
        crawl = importlib.import_module(module_path).crawl

        items = await crawl(daily_limit=daily_limit)
        saved = 0
        skipped_dupes = 0
        skipped_unpopular = 0
        source_key = source.lower()

        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT source_url FROM example_bank WHERE LOWER(source)=%s AND source_url IS NOT NULL",
                    (source_key,)
                )
                existing_urls = {row["source_url"] for row in cur.fetchall()}
                ranked_parents = load_ranked_parent_urls(cur, source_key, MIN_POPULARITY_PCT)

        # Popularity gate BEFORE embed — do not waste embeddings on low-engagement posts.
        gated_items, skipped_unpopular = _gate_items(source_key, items, ranked_parents)
        logger.info(
            "Crawl %s popularity gate: collected=%s gated=%s skipped_unpopular=%s min_pct=%.2f",
            source_key, len(items), len(gated_items), skipped_unpopular, MIN_POPULARITY_PCT,
        )

        rows = []
        for item in gated_items:
            if not quality.passes(item["content"]):
                continue

            source_url = item.get("source_url")
            if source_url is not None and source_url in existing_urls:
                skipped_dupes += 1
                continue

            vec = embed_service.embed(item["content"][:512])
            vec_str = "[" + ",".join(f"{v:.8f}" for v in vec) + "]"
            register = classify_register(item["content"])

            comment_timestamps = item.get("comment_timestamps")
            engagement_span_hours = None
            if comment_timestamps:
                parsed_ts = []
                for ts in comment_timestamps:
                    if isinstance(ts, datetime):
                        parsed_ts.append(ts)
                    elif isinstance(ts, str):
                        try:
                            parsed_ts.append(datetime.strptime(ts, "%Y-%m-%d %H:%M:%S"))
                        except ValueError:
                            continue
                if len(parsed_ts) >= 2:
                    span_seconds = (max(parsed_ts) - min(parsed_ts)).total_seconds()
                    engagement_span_hours = span_seconds / 3600

            popularity_pct = item.get("popularity_pct")  # set for accepted POSTs in-batch
            if (item.get("content_type") or "COMMENT") != "POST":
                popularity_pct = None  # COMMENT: style anchor only

            rows.append((
                item["content"],
                item.get("content_type", "COMMENT"),
                item.get("category", "OTHER"),
                item.get("source", source_key).lower() if item.get("source") else source_key,
                quality.score(item["content"]),
                register,
                item.get("title"),
                source_url,
                item.get("author_id"),
                item.get("posted_at"),
                item.get("view_count"),
                item.get("like_count"),
                item.get("comment_count"),
                engagement_span_hours,
                popularity_pct,
                vec_str
            ))

            if source_url is not None:
                existing_urls.add(source_url)

        sql = """INSERT INTO example_bank
                 (content, content_type, category, source, quality_score, register,
                  title, source_url, author_id, posted_at, view_count, like_count,
                  comment_count, engagement_span_hours, popularity_pct, embedding, created_at)
                 VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                         VEC_FromText(%s), NOW(3))"""
        BATCH_COMMIT = 50
        # source_url is column index 7 in the insert tuple (0-based).
        _URL_IDX = 7
        lock_name = _crawl_ingest_lock_name(source_key)
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT GET_LOCK(%s, %s) AS acquired",
                    (lock_name, _CRAWL_INGEST_LOCK_TIMEOUT_SEC),
                )
                lock_row = cur.fetchone() or {}
                acquired = lock_row.get("acquired")
                if acquired != 1:
                    raise RuntimeError(
                        f"crawl ingest lock not acquired source={source_key} "
                        f"lock={lock_name} acquired={acquired}"
                    )
                try:
                    # Re-load under lock — closes the race between pre-embed snapshot
                    # and insert when two crawl runs overlap.
                    cur.execute(
                        "SELECT source_url FROM example_bank "
                        "WHERE LOWER(source)=%s AND source_url IS NOT NULL",
                        (source_key,),
                    )
                    locked_urls = {row["source_url"] for row in cur.fetchall()}

                    for params in rows:
                        source_url = params[_URL_IDX]
                        if source_url is not None and source_url in locked_urls:
                            skipped_dupes += 1
                            continue
                        cur.execute(sql, params)
                        saved += 1
                        if source_url is not None:
                            locked_urls.add(source_url)
                        if saved % BATCH_COMMIT == 0:
                            conn.commit()

                    log_sql = """INSERT INTO crawl_log
                                 (source, items_collected, items_saved, status, created_at)
                                 VALUES (%s, %s, %s, %s, NOW(3))"""
                    cur.execute(log_sql, (source, len(items), saved, "SUCCESS"))
                finally:
                    cur.execute("SELECT RELEASE_LOCK(%s)", (lock_name,))

        logger.info(
            f"Crawl {source}: collected={len(items)} saved={saved} "
            f"skipped_dupes={skipped_dupes} skipped_unpopular={skipped_unpopular}"
        )
    except Exception as e:
        with get_db() as conn:
            with conn.cursor() as cur:
                log_sql = """INSERT INTO crawl_log
                             (source, items_collected, items_saved, status, error_msg, created_at)
                             VALUES (%s, %s, %s, %s, %s, NOW(3))"""
                cur.execute(log_sql, (source, 0, 0, "FAILED", str(e)[:500]))
        logger.error(f"Crawl {source} failed: {e}")


@router.post("/{source}")
async def trigger_crawl(source: str, background_tasks: BackgroundTasks, limit: int = 100):
    from app.main import embed_service
    background_tasks.add_task(_do_crawl, source, limit, embed_service)
    return {"status": "started", "source": source, "limit": limit,
            "min_popularity_pct": MIN_POPULARITY_PCT}


@router.get("/log")
def get_crawl_log():
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute("""SELECT source, status, items_saved, created_at
                          FROM crawl_log
                          ORDER BY created_at DESC
                          LIMIT 50""")
            rows = cur.fetchall()
    return [{"source": r["source"], "status": r["status"], "saved": r["items_saved"], "at": r["created_at"].isoformat() + "Z"} for r in rows]
