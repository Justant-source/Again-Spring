from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger
import logging, asyncio, random

logger = logging.getLogger(__name__)

# 전체 12개 소스 (기존 6 + 신규 6)
SOURCES = [
    ("naver",    500), ("daum",    500),
    ("dcinside", 100), ("natepan",  50), ("bobaedream", 100), ("blind",   50),
    ("fmkorea",  150), ("theqoo",  120), ("clien",      100), ("ppomppu", 100),
    ("ruliweb",  120), ("mlbpark",  80),
]

async def run_daily_crawl():
    """매일 KST 03:00 — 12개 소스 크롤 → 완료 후 말투 강화 자동 실행"""
    from app.main import embed_service
    from app.api.crawl import _do_crawl
    logger.info("Daily crawl started (12 sources)")
    for source, limit in SOURCES:
        try:
            await _do_crawl(source, limit, embed_service)
            await asyncio.sleep(60 * (3 + 7 * random.random()))
        except Exception as e:
            logger.error(f"Daily crawl {source} error: {e}")
    logger.info("Daily crawl completed — triggering persona strengthen")
    await run_strengthen()

async def run_strengthen():
    """크롤 완료 후 또는 독립 스케줄로 실행 — 누적 데이터로 페르소나 말투 강화"""
    try:
        from app.services.persona_strengthener import strengthen_all
        import concurrent.futures, functools
        loop = asyncio.get_event_loop()
        results = await loop.run_in_executor(None, functools.partial(strengthen_all, 10))
        total = sum(v.get("updated", 0) for v in results.values() if isinstance(v, dict))
        logger.info(f"Persona strengthen completed: {total} personas updated — {results}")
    except Exception as e:
        logger.error(f"Persona strengthen error: {e}")

def init_scheduler():
    scheduler = AsyncIOScheduler()
    # 크롤 + 강화: 매일 UTC 18:00 (KST 03:00)
    scheduler.add_job(run_daily_crawl, CronTrigger(hour=18, minute=0),
                      id="daily_crawl", name="Daily Crawl + Strengthen")
    # 독립 강화: 매일 UTC 20:00 (KST 05:00) — 크롤이 늦게 끝날 경우 보완
    scheduler.add_job(run_strengthen, CronTrigger(hour=20, minute=0),
                      id="daily_strengthen", name="Daily Strengthen (standalone)")
    scheduler.start()
    logger.info("Scheduler initialized — crawl 03:00 KST, strengthen 03:00+05:00 KST daily")
    return scheduler
