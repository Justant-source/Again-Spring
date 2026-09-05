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
    """특정 Voice 타입만 예시 풀 확장.

    persona-diversity-v4(2026-09) 이후 `update_persona_profiles`는 no-op다 — voice_type 단위로
    lexicon·general_style·*_style을 일괄 덮어쓰던 동작이 150명 페르소나 말투를 3종으로
    수렴시킨 원인이었고, 이제 문체는 페르소나별 프로필 재생성이 소유한다.

    그런데 이 엔드포인트만 `analyze_style_with_llm` + `update_persona_profiles`를 계속 불러
    **LLM을 실제로 태우고 결과를 버렸다**. 02:00 크론 경로(`strengthen_all`)는 이미
    `expand_persona_example_pools`만 호출한다 — 그쪽과 동작을 맞춘다(2026-09-05 리뷰).
    """
    from app.services.persona_strengthener import (
        VOICE_SOURCE_MAP, expand_persona_example_pools
    )
    source = VOICE_SOURCE_MAP.get(voice_type.upper())
    if not source:
        return {"status": "error", "message": f"Unknown voice type: {voice_type}"}

    def _run():
        expanded = expand_persona_example_pools(voice_type.upper(), source)
        logger.info(f"[{voice_type}] example pool expanded for {expanded} personas")

    background_tasks.add_task(_run)
    return {
        "status": "started",
        "voice_type": voice_type,
        "source": source,
        "scope": "example_pools_only",
        "note": "문체(lexicon/general_style)는 페르소나별 프로필 재생성이 소유한다 — 여기서 안 건드린다",
    }


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
