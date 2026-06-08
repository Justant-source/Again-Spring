"""
크롤링 데이터 → LLM 분석 → 페르소나 voice_profile 강화
"""
import json
import logging
import os
import requests
from app.db.session import get_db

logger = logging.getLogger(__name__)

LLM_URL = os.getenv("LLM_AI_USER_URL", "http://againspring-llm-ai-user-dev:8092")

# Voice 타입 → 커뮤니티 소스 매핑
VOICE_SOURCE_MAP = {
    "NATEPAN":  "natepan",
    "DCINSIDE": "dcinside",
    "BLIND":    "blind",
    "FMKOREA":  "fmkorea",
    "THEQOO":   "theqoo",
    "CLIEN":    "clien",
    "PPOMPPU":  "ppomppu",
    "RULIWEB":  "ruliweb",
    "MLBPARK":  "mlbpark",
    "GENERAL":  None,  # 여러 소스 혼합
}


def get_examples_by_source(source: str, limit: int = 30) -> list[str]:
    """example_bank에서 source별 고품질 예시 조회"""
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT content FROM example_bank
                WHERE source = %s AND quality_score >= 0.6
                  AND LENGTH(content) BETWEEN 30 AND 1500
                ORDER BY quality_score DESC, created_at DESC
                LIMIT %s
            """, (source, limit))
            rows = cur.fetchall()
    return [r["content"] if isinstance(r, dict) else r[0] for r in rows]


def analyze_style_with_llm(voice_type: str, examples: list[str]) -> dict:
    """LLM으로 커뮤니티 문체 패턴 분석"""
    if not examples:
        return {}

    sample = "\n---\n".join(examples[:20])
    prompt = f"""다음은 한국 인터넷 커뮤니티 [{voice_type}]에서 수집한 실제 게시글 샘플입니다.

---
{sample}
---

위 텍스트들을 분석하여 이 커뮤니티의 특징적인 문체 패턴을 JSON으로 추출하세요.

반드시 아래 형식의 JSON만 반환하세요 (설명 없이):
{{
  "signature_phrases": ["자주 쓰는 표현1", "자주 쓰는 표현2", "자주 쓰는 표현3", "자주 쓰는 표현4", "자주 쓰는 표현5"],
  "consistent_errors": ["문체 특징1 (예: 온점 미사용)", "특징2"],
  "hot_topics": ["주로 다루는 주제1", "주제2", "주제3"],
  "typing_habit": "이 커뮤니티의 타이핑 습관 한 줄 설명"
}}"""

    try:
        resp = requests.post(
            f"{LLM_URL}/generate/persona",
            json={"prompt": prompt, "correlationId": f"strengthen-{voice_type}"},
            timeout=60
        )
        resp.raise_for_status()
        text = resp.json().get("text", "")
        # JSON 파싱 (마크다운 코드블록 제거)
        text = text.strip().removeprefix("```json").removeprefix("```").removesuffix("```").strip()
        return json.loads(text)
    except Exception as e:
        logger.warning(f"LLM analysis failed for {voice_type}: {e}")
        return {}


def update_persona_profiles(voice_type: str, patterns: dict) -> int:
    """해당 Voice 타입의 페르소나 voice_profile 업데이트"""
    if not patterns:
        return 0

    sig_phrases = patterns.get("signature_phrases", [])
    errors = patterns.get("consistent_errors", [])
    typing_habit = patterns.get("typing_habit", "")
    hot_topics = patterns.get("hot_topics", [])

    if not sig_phrases and not errors and not hot_topics:
        return 0

    with get_db() as conn:
        with conn.cursor() as cur:
            # voice_profile JSON의 lexicon, writing_quirks 업데이트
            cur.execute("""
                SELECT id, voice_profile FROM personas
                WHERE JSON_UNQUOTE(JSON_EXTRACT(voice_profile, '$.voice_type')) = %s
                LIMIT 50
            """, (voice_type,))
            rows = cur.fetchall()

            updated = 0
            for row in rows:
                persona_id = row["id"] if isinstance(row, dict) else row[0]
                vp_raw = row["voice_profile"] if isinstance(row, dict) else row[1]
                try:
                    vp = json.loads(vp_raw) if isinstance(vp_raw, str) else vp_raw
                    # lexicon 업데이트
                    if "lexicon" not in vp or not isinstance(vp.get("lexicon"), dict):
                        vp["lexicon"] = {}
                    if sig_phrases:
                        existing = vp["lexicon"].get("signature_phrases", [])
                        merged = list(dict.fromkeys(existing + sig_phrases))[:8]
                        vp["lexicon"]["signature_phrases"] = merged
                    if typing_habit:
                        vp["lexicon"]["typing_habit"] = typing_habit
                    # writing_quirks 업데이트
                    if "writing_quirks" not in vp or not isinstance(vp.get("writing_quirks"), dict):
                        vp["writing_quirks"] = {}
                    if errors:
                        existing_errs = vp["writing_quirks"].get("consistent_errors", [])
                        merged_errs = list(dict.fromkeys(existing_errs + errors))[:4]
                        vp["writing_quirks"]["consistent_errors"] = merged_errs
                    # hot_topics 저장 — topic_synthesizer가 힌트로 소비
                    if hot_topics:
                        existing_topics = vp.get("hot_topics", [])
                        merged_topics = list(dict.fromkeys(existing_topics + hot_topics))[:5]
                        vp["hot_topics"] = merged_topics

                    cur.execute(
                        "UPDATE personas SET voice_profile = %s WHERE id = %s",
                        (json.dumps(vp, ensure_ascii=False), persona_id)
                    )
                    updated += 1
                except Exception as e:
                    logger.debug(f"Failed to update persona {persona_id}: {e}")
                    continue

    return updated


def strengthen_all(min_examples: int = 10) -> dict:
    """전체 Voice 타입 강화 실행"""
    results = {}
    for voice_type, source in VOICE_SOURCE_MAP.items():
        if source is None:
            continue
        examples = get_examples_by_source(source, limit=30)
        if len(examples) < min_examples:
            logger.info(f"[{voice_type}] skip — only {len(examples)} examples (need {min_examples})")
            results[voice_type] = {"status": "skip", "examples": len(examples)}
            continue
        patterns = analyze_style_with_llm(voice_type, examples)
        updated = update_persona_profiles(voice_type, patterns)
        logger.info(f"[{voice_type}] strengthened {updated} personas")
        results[voice_type] = {"status": "ok", "updated": updated, "patterns": list(patterns.keys())}
    return results
