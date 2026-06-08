"""
일일 토픽 시드 API.
GET  /topics/today?category=&limit=   — 오늘 합성된 시드 (least-used 우선)
POST /topics/{id}/use                 — 사용 카운트 +1 (오케스트레이터 로테이션용)
GET  /topics/stats                    — 토픽뱅크 상태
POST /topics/synthesize               — 수동 합성 트리거 (관리자용)
"""
import logging
from fastapi import APIRouter, Request, HTTPException
from typing import Optional
from app.db.session import get_db

logger = logging.getLogger(__name__)
router = APIRouter()


@router.get("/today")
def get_daily_topics(request: Request, category: Optional[str] = None, limit: int = 5):
    """
    오늘(KST) 합성된 주제 시드 반환. used_count 낮은 순 → RAND() 로테이션.
    learning 비활성 또는 데이터 없으면 [] 반환.
    """
    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                if category:
                    cur.execute("""
                        SELECT id, category, seed_text, used_count, quality_score
                        FROM daily_topic
                        WHERE day = CURDATE() AND category = %s
                        ORDER BY used_count ASC, RAND()
                        LIMIT %s
                    """, (category.upper(), min(limit, 20)))
                else:
                    cur.execute("""
                        SELECT id, category, seed_text, used_count, quality_score
                        FROM daily_topic
                        WHERE day = CURDATE()
                        ORDER BY used_count ASC, RAND()
                        LIMIT %s
                    """, (min(limit, 20),))
                rows = cur.fetchall()
        return [
            {
                "id": r["id"] if isinstance(r, dict) else r[0],
                "category": r["category"] if isinstance(r, dict) else r[1],
                "text": r["seed_text"] if isinstance(r, dict) else r[2],
                "usedCount": r["used_count"] if isinstance(r, dict) else r[3],
                "qualityScore": float(r["quality_score"]) if isinstance(r, dict) else float(r[4]),
            }
            for r in rows
        ]
    except Exception as e:
        logger.warning(f"Failed to fetch daily topics: {e}")
        return []


@router.post("/{topic_id}/use")
def mark_topic_used(topic_id: int):
    """사용 카운트 +1. 오케스트레이터 fire-and-forget 호출용."""
    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "UPDATE daily_topic SET used_count = used_count + 1 WHERE id = %s",
                    (topic_id,)
                )
            conn.commit()
        return {"ok": True}
    except Exception as e:
        logger.warning(f"mark_topic_used failed for {topic_id}: {e}")
        raise HTTPException(status_code=500, detail="update failed")


@router.get("/stats")
def get_topic_stats():
    """일일 토픽뱅크 상태: 날짜별·카테고리별 개수, 평균 used_count."""
    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute("""
                    SELECT day, category,
                           COUNT(*) AS cnt,
                           AVG(used_count) AS avg_used,
                           AVG(quality_score) AS avg_quality
                    FROM daily_topic
                    WHERE day >= CURDATE() - INTERVAL 7 DAY
                    GROUP BY day, category
                    ORDER BY day DESC, category
                """)
                rows = cur.fetchall()
        return [
            {
                "day": str(r["day"] if isinstance(r, dict) else r[0]),
                "category": r["category"] if isinstance(r, dict) else r[1],
                "count": r["cnt"] if isinstance(r, dict) else r[2],
                "avgUsed": round(float(r["avg_used"] if isinstance(r, dict) else r[3]), 2),
                "avgQuality": round(float(r["avg_quality"] if isinstance(r, dict) else r[4]), 2),
            }
            for r in rows
        ]
    except Exception as e:
        logger.warning(f"topic stats failed: {e}")
        return []


@router.post("/synthesize")
async def manual_synthesize(request: Request):
    """관리자 수동 합성 트리거. 스케줄 배치와 동일 로직."""
    from app.main import embed_service
    from app.services.topic_synthesizer import synthesize_daily_topics
    import asyncio, concurrent.futures, functools
    loop = asyncio.get_event_loop()
    try:
        result = await loop.run_in_executor(
            None, functools.partial(synthesize_daily_topics, embed_service)
        )
        return result
    except Exception as e:
        logger.error(f"Manual topic synthesis failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))
