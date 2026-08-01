from fastapi import APIRouter, BackgroundTasks
from app.db.session import get_db
from app.services.quality_filter import QualityFilter
from app.services.register_classifier import classify as classify_register
from datetime import datetime
import logging, asyncio

logger = logging.getLogger(__name__)
router = APIRouter()
quality = QualityFilter()

# Wave1-D: 활성 크롤러는 natepan · blind 둘뿐 (그 외 모듈 삭제됨)
_CRAWLERS = {
    "natepan": "app.crawlers.natepan",
    "blind": "app.crawlers.blind",
}


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

        # 기존 URL 조회는 짧은 커넥션으로 분리 — SELECT~INSERT를 한 트랜잭션에 묶으면
        # 임베딩(건당 100~500ms) 동안 lock을 점유해 동시 크롤 시 1205가 난다 (2026-06 인시던트)
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT source_url FROM example_bank WHERE source=%s AND source_url IS NOT NULL",
                    (source.upper(),)
                )
                existing_urls = {row["source_url"] for row in cur.fetchall()}  # DictCursor

        # 임베딩·문체 분류는 DB 커넥션 밖에서 선계산
        rows = []
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

            # engagement_span_hours 계산: comment_timestamps 리스트 기반
            # 크롤러마다 datetime 객체 또는 "YYYY-MM-DD HH:MM:SS" 문자열을 섞어 반환할 수 있어 여기서 정규화
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

            rows.append((
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
                item.get("view_count"),
                item.get("like_count"),
                item.get("comment_count"),
                engagement_span_hours,
                vec_str
            ))

            # Track newly-inserted URL to dedup within this run
            if source_url is not None:
                existing_urls.add(source_url)

        sql = """INSERT INTO example_bank
                 (content, content_type, category, source, quality_score, register,
                  title, source_url, author_id, posted_at, view_count, like_count,
                  comment_count, engagement_span_hours, embedding, created_at)
                 VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                         VEC_FromText(%s), NOW(3))"""
        BATCH_COMMIT = 50
        with get_db() as conn:
            with conn.cursor() as cur:
                for params in rows:
                    cur.execute(sql, params)
                    saved += 1
                    if saved % BATCH_COMMIT == 0:
                        conn.commit()

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
    # DB는 UTC 저장(naive datetime) — str()은 "YYYY-MM-DD HH:MM:SS.ffffff" 형식이라
    # 표준 ISO-8601이 아니고, 소비 측(backend AdminCrawlStatusService)의 Instant.parse()가 못 읽는다.
    # isoformat() + "Z"로 명시적 UTC ISO-8601을 내려준다.
    return [{"source": r["source"], "status": r["status"], "saved": r["items_saved"], "at": r["created_at"].isoformat() + "Z"} for r in rows]
