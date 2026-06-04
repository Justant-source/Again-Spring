from fastapi import APIRouter, BackgroundTasks, Depends
from sqlalchemy.orm import Session
from app.db.session import get_db, SessionLocal
from app.db.models import ExampleBank, CrawlLog
from app.services.quality_filter import QualityFilter
import logging, asyncio

logger = logging.getLogger(__name__)
router = APIRouter()
quality = QualityFilter()

async def _do_crawl(source, daily_limit, embed_service):
    db = SessionLocal()
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
        else:
            logger.warning(f"Unknown source: {source}")
            return

        items = await crawl(daily_limit=daily_limit)
        saved = 0
        for item in items:
            if not quality.passes(item["content"]):
                continue
            vec = embed_service.embed(item["content"][:512])
            ex = ExampleBank(
                content=item["content"],
                content_type=item.get("content_type", "COMMENT"),
                category=item.get("category", "OTHER"),
                source=item.get("source", source.upper()),
                quality_score=quality.score(item["content"]),
                embedding=vec,
            )
            db.add(ex)
            saved += 1
        db.commit()
        log_entry = CrawlLog(source=source, items_collected=len(items), items_saved=saved, status="SUCCESS")
        db.add(log_entry)
        db.commit()
        logger.info(f"Crawl {source}: collected={len(items)} saved={saved}")
    except Exception as e:
        db.rollback()
        log_entry = CrawlLog(source=source, items_collected=0, items_saved=0, status="FAILED", error_msg=str(e)[:500])
        db.add(log_entry)
        db.commit()
        logger.error(f"Crawl {source} failed: {e}")
    finally:
        db.close()

@router.post("/{source}")
async def trigger_crawl(source: str, background_tasks: BackgroundTasks, limit: int = 100):
    from app.main import embed_service
    background_tasks.add_task(_do_crawl, source, limit, embed_service)
    return {"status": "started", "source": source, "limit": limit}

@router.get("/log")
def get_crawl_log(db: Session = Depends(get_db)):
    from sqlalchemy import text
    rows = db.execute(text("SELECT source, status, items_saved, created_at FROM crawl_log ORDER BY created_at DESC LIMIT 50")).fetchall()
    return [{"source": r.source, "status": r.status, "saved": r.items_saved, "at": str(r.created_at)} for r in rows]
