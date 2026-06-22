from fastapi import APIRouter, BackgroundTasks
from app.db.session import get_db
from app.services.quality_filter import QualityFilter
from app.services.register_classifier import classify as classify_register
import logging, asyncio

logger = logging.getLogger(__name__)
router = APIRouter()
quality = QualityFilter()

async def _do_crawl(source, daily_limit, embed_service):
    try:
        if source == "naver":
            from app.crawlers.naver_comments import crawl
        elif source == "daum":
            from app.crawlers.daum_comments import crawl
        elif source == "dcinside":
            from app.crawlers.dcinside import crawl
        elif source == "natepan":
            from app.crawlers.natepan import crawl
        elif source == "bobaedream":
            from app.crawlers.bobaedream import crawl
        elif source == "blind":
            from app.crawlers.blind import crawl
        elif source == "fmkorea":
            from app.crawlers.fmkorea import crawl
        elif source == "theqoo":
            from app.crawlers.theqoo import crawl
        elif source == "clien":
            from app.crawlers.clien import crawl
        elif source == "ppomppu":
            from app.crawlers.ppomppu import crawl
        elif source == "ruliweb":
            from app.crawlers.ruliweb import crawl
        elif source == "mlbpark":
            from app.crawlers.mlbpark import crawl
        else:
            logger.warning(f"Unknown source: {source}")
            return

        items = await crawl(daily_limit=daily_limit)
        saved = 0
        skipped_dupes = 0

        with get_db() as conn:
            with conn.cursor() as cur:
                # Load existing source_urls for this source (one query, O(1) lookup)
                cur.execute(
                    "SELECT source_url FROM example_bank WHERE source=%s AND source_url IS NOT NULL",
                    (source.upper(),)
                )
                existing_urls = {row["source_url"] for row in cur.fetchall()}  # DictCursor

                for item in items:
                    if not quality.passes(item["content"]):
                        continue

                    # Dedup: skip if source_url already exists in DB
                    source_url = item.get("source_url")
                    if source_url is not None and source_url in existing_urls:
                        skipped_dupes += 1
                        continue

                    vec = embed_service.embed(item["content"][:512])
                    vec_str = "[" + ",".join(f"{v:.8f}" for v in vec) + "]"
                    register = classify_register(item["content"])

                    sql = """INSERT INTO example_bank
                             (content, content_type, category, source, quality_score, register,
                              title, source_url, author_id, posted_at, embedding, created_at)
                             VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, VEC_FromText(%s), NOW(3))"""
                    cur.execute(sql, (
                        item["content"],
                        item.get("content_type", "COMMENT"),
                        item.get("category", "OTHER"),
                        item.get("source", source.upper()),
                        quality.score(item["content"]),
                        register,
                        item.get("title"),
                        source_url,
                        item.get("author_id"),
                        item.get("posted_at"),
                        vec_str
                    ))
                    saved += 1

                    # Track newly-inserted URL to dedup within this run
                    if source_url is not None:
                        existing_urls.add(source_url)

                # Log crawl operation
                log_sql = """INSERT INTO crawl_log
                             (source, items_collected, items_saved, status, created_at)
                             VALUES (%s, %s, %s, %s, NOW(3))"""
                cur.execute(log_sql, (source, len(items), saved, "SUCCESS"))

        logger.info(f"Crawl {source}: collected={len(items)} saved={saved} skipped_dupes={skipped_dupes}")
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
    return {"status": "started", "source": source, "limit": limit}


@router.get("/log")
def get_crawl_log():
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute("""SELECT source, status, items_saved, created_at
                          FROM crawl_log
                          ORDER BY created_at DESC
                          LIMIT 50""")
            rows = cur.fetchall()
    return [{"source": r["source"], "status": r["status"], "saved": r["items_saved"], "at": str(r["created_at"])} for r in rows]
