from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger
import asyncio
import logging
import os
import random
from zoneinfo import ZoneInfo

logger = logging.getLogger(__name__)
KST = ZoneInfo("Asia/Seoul")

# init_scheduler()가 2회 호출되면 daily_crawl이 동시 실행되어 lock timeout(1205)을 유발한다
# (2026-06-21~24 natepan FAILED 원인). 싱글턴 가드로 차단.
_scheduler = None

# WO-CRAWL-01: NATEPAN(1500) + BLIND(500) 활성 — 블라인드 최우선 강화 (240→500)
# 403 차단율을 admin 배지로 관찰하며 단계 증액 예정
# limit=0 → _do_crawl 내부에서 skip (if not limit: return)
SOURCES = [
    ("natepan",  1500),  # Phase 1: 전체 예산 NATEPAN 집중
    ("naver",       0),  # 비활성
    ("daum",        0),  # 비활성
    ("dcinside",    0),  # 비활성
    ("bobaedream",  0),  # 비활성
    ("blind",     500),  # WO-CRAWL-01: 결혼생활·썸·연애·회사생활 채널 (채널당 ~166개)
    ("fmkorea",     0),  # 비활성
    ("theqoo",      0),  # 비활성
    ("clien",       0),  # 비활성
    ("ppomppu",     0),  # 비활성
    ("ruliweb",     0),  # 비활성
    ("mlbpark",     0),  # 비활성
]

async def run_daily_crawl():
    """매일 KST 03:00 — NATEPAN 전용 크롤 → 완료 후 말투 강화 + 토픽 합성"""
    from app.main import embed_service
    from app.api.crawl import _do_crawl
    logger.info("Daily crawl started (NATEPAN-only, Phase 1 v2)")
    for source, limit in SOURCES:
        if not limit:
            continue
        try:
            await _do_crawl(source, limit, embed_service)
            await asyncio.sleep(60 * (3 + 7 * random.random()))
        except Exception as e:
            logger.error(f"Daily crawl {source} error: {e}")
    logger.info("Daily crawl completed — triggering persona strengthen + topic synthesis")
    await run_strengthen()
    await run_topic_synthesis()

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


async def run_topic_synthesis():
    """크롤+강화 완료 후 실행 — 오늘의 갈등 주제 시드 합성 → daily_topic 저장"""
    try:
        from app.main import embed_service
        from app.services.topic_synthesizer import synthesize_daily_topics
        import concurrent.futures, functools
        loop = asyncio.get_event_loop()
        result = await loop.run_in_executor(
            None, functools.partial(synthesize_daily_topics, embed_service)
        )
        logger.info(f"Topic synthesis completed: {result}")
    except Exception as e:
        logger.error(f"Topic synthesis error: {e}")


async def run_recompute_popularity_scores():
    """크롤 완료 후 이용 지표 바탕으로 popularity_pct 재계산 — KST 04:00에 매일 실행"""
    try:
        from app.services.popularity_scorer import recompute_popularity_scores
        import concurrent.futures, functools
        loop = asyncio.get_event_loop()
        result = await loop.run_in_executor(None, functools.partial(recompute_popularity_scores))
        logger.info(f"Popularity score recomputation completed: {result}")
    except Exception as e:
        logger.error(f"Popularity score recomputation error: {e}")

def init_scheduler():
    global _scheduler
    if _scheduler is not None:
        logger.warning("init_scheduler() called twice — reusing existing scheduler (duplicate crawl prevented)")
        return _scheduler

    scheduler_enabled = os.getenv("AI_LEARNING_ENABLED", "true").strip().lower() in {"1", "true", "yes", "on"}
    crawl_enabled = os.getenv("AI_LEARNING_CRAWL_ENABLED", "false").strip().lower() in {"1", "true", "yes", "on"}

    if not scheduler_enabled:
        logger.info("Scheduler disabled via AI_LEARNING_ENABLED=false")
        return None
    if not crawl_enabled:
        logger.info("Scheduler jobs disabled via AI_LEARNING_CRAWL_ENABLED=false")
        return None

    scheduler = AsyncIOScheduler(timezone=KST)
    # 크롤 + 강화: 매일 KST 03:00
    scheduler.add_job(run_daily_crawl, CronTrigger(hour=3, minute=0, timezone=KST),
                      id="daily_crawl", name="Daily Crawl + Strengthen")
    # 인기도 점수 재계산: 매일 KST 04:00 (크롤 완료 후, 강화 전)
    scheduler.add_job(run_recompute_popularity_scores, CronTrigger(hour=4, minute=0, timezone=KST),
                      id="daily_popularity", name="Daily Popularity Score Recomputation")
    # 독립 강화+토픽보강: 매일 KST 05:00
    async def _standalone_strengthen_and_synthesize():
        await run_strengthen()
        await run_topic_synthesis()

    scheduler.add_job(_standalone_strengthen_and_synthesize, CronTrigger(hour=5, minute=0, timezone=KST),
                      id="daily_strengthen", name="Daily Strengthen + Topic Synthesis (standalone)")
    scheduler.start()
    _scheduler = scheduler
    logger.info("Scheduler initialized — crawl 03:00 KST, popularity 04:00 KST, strengthen 03:00+05:00 KST daily")
    return scheduler
