from fastapi import APIRouter, BackgroundTasks
import logging

router = APIRouter()
logger = logging.getLogger(__name__)


@router.post("/batch")
async def strengthen_batch(background_tasks: BackgroundTasks, min_examples: int = 10):
    """전체 Voice 타입 말투 강화 (백그라운드)"""
    from app.services.persona_strengthener import strengthen_all
    background_tasks.add_task(strengthen_all, min_examples)
    return {"status": "started", "message": "강화 작업이 백그라운드에서 시작됩니다"}


@router.post("/{voice_type}")
async def strengthen_voice(voice_type: str, background_tasks: BackgroundTasks):
    """특정 Voice 타입만 강화"""
    from app.services.persona_strengthener import (
        VOICE_SOURCE_MAP, get_examples_by_source,
        analyze_style_with_llm, update_persona_profiles
    )
    source = VOICE_SOURCE_MAP.get(voice_type.upper())
    if not source:
        return {"status": "error", "message": f"Unknown voice type: {voice_type}"}

    def _run():
        examples = get_examples_by_source(source, limit=30)
        patterns = analyze_style_with_llm(voice_type.upper(), examples)
        updated = update_persona_profiles(voice_type.upper(), patterns)
        logger.info(f"[{voice_type}] strengthened {updated} personas")

    background_tasks.add_task(_run)
    return {"status": "started", "voice_type": voice_type, "source": source}


@router.get("/status")
def strengthen_status():
    """소스별 example_bank 현황"""
    from app.db.session import get_db
    from app.services.persona_strengthener import VOICE_SOURCE_MAP
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT source, COUNT(*) cnt, ROUND(AVG(quality_score),2) avg_score
                FROM example_bank GROUP BY source ORDER BY cnt DESC
            """)
            rows = cur.fetchall()
    return {
        "example_bank": [{"source": r["source"] if isinstance(r, dict) else r[0],
                         "count": r["cnt"] if isinstance(r, dict) else r[1],
                         "avg_quality": r["avg_score"] if isinstance(r, dict) else r[2]} for r in rows],
        "voice_source_map": VOICE_SOURCE_MAP
    }
