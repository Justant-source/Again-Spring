from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger
import logging, asyncio, random

logger = logging.getLogger(__name__)

SOURCES = [
    ("naver", 500), ("daum", 500),
    ("dcinside", 100), ("natepan", 50), ("bobaedream", 100), ("blind", 50)
]

async def run_daily_crawl():
    from app.main import embed_service
    from app.api.crawl import _do_crawl
    logger.info("Daily crawl started")
    for source, limit in SOURCES:
        try:
            await _do_crawl(source, limit, embed_service)
            wait = 60 * (3 + 7 * random.random())
            await asyncio.sleep(wait)
        except Exception as e:
            logger.error(f"Daily crawl {source} error: {e}")
    logger.info("Daily crawl completed")

def init_scheduler():
    scheduler = AsyncIOScheduler()
    scheduler.add_job(run_daily_crawl, CronTrigger(hour=18, minute=0))
    scheduler.start()
    logger.info("Crawler scheduler initialized (daily 03:00 KST)")
    return scheduler
