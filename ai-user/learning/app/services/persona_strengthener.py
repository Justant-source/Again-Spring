"""
크롤링 데이터 → LLM 분석 → 페르소나 voice_profile 강화
"""
import json
import logging
import os
import random
import re
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
    "GENERAL":  None,   # 여러 소스 혼합
    "ARCALIVE": None,   # 전용 크롤러 없음 — 혼합 소스 풀만
    "INVEN":    None,   # 전용 크롤러 없음 — 혼합 소스 풀만
}

# 예시 풀 목표 크기 (문체 현실화 S5) — 고정 3~4개 → 풀 확장 후 생성 시 랜덤 서브셋 주입
POOL_TARGET_COMMENTS = 12
POOL_TARGET_REPLIES = 8


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


def _clean_example(text: str) -> str:
    """풀 예시 정규화 — 문장 끝 온점·쌍따옴표 제거 (플랫폼 문체 규칙과 일치)."""
    t = re.sub(r'(?<![.?!])\.\s*$', '', text.strip())
    return t.replace('"', '').strip()


def get_example_pool(source: str | None, content_type: str, limit: int,
                     min_len: int, max_len: int) -> list[str]:
    """크롤 코퍼스에서 예시 후보 랜덤 추출. SELF_GENERATED 제외 — AI투 증폭 루프 방지."""
    conds = ["source != 'SELF_GENERATED'", "content_type = %s",
             "quality_score >= 0.6", "CHAR_LENGTH(content) BETWEEN %s AND %s"]
    params: list = [content_type, min_len, max_len]
    if source:
        conds.append("source = %s")
        params.append(source)
    sql = f"""SELECT DISTINCT content FROM example_bank
              WHERE {' AND '.join(conds)}
              ORDER BY RAND() LIMIT %s"""
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, params + [limit])
            rows = cur.fetchall()
    return [_clean_example(r["content"] if isinstance(r, dict) else r[0]) for r in rows]


def expand_persona_example_pools(voice_type: str, source: str | None) -> int:
    """voice_profile의 example_comments/example_replies 풀 확장 (문체 현실화 S5).

    - 수제(큐레이션) 예시는 보존 — pool_meta.curated_*로 첫 실행 시 개수를 기록,
      이후 실행에선 크롤 추가분만 새 랜덤 샘플로 교체 (매일 새벽 자연 회전)
    - 페르소나마다 후보군에서 서로 다른 랜덤 서브셋 배정 → 동일 voice 페르소나 간 획일화 방지
    - 오케스트레이터 appendExamples는 풀에서 shuffle 후 2~3개만 주입하므로 호출마다 예시가 달라짐
    """
    comment_cands = get_example_pool(source, "COMMENT", limit=80, min_len=15, max_len=200)
    if len(comment_cands) < 8:
        # 댓글 코퍼스 부족 → 짧은 글로 보충 (캐던스 앵커 대용)
        comment_cands += get_example_pool(source, "POST", limit=40, min_len=20, max_len=200)
    if len(comment_cands) < 8 and source:
        # 소스 전용 코퍼스 자체가 부족 (전용 크롤러 없는 voice) → 혼합 소스 폴백
        comment_cands += get_example_pool(None, "COMMENT", limit=60, min_len=15, max_len=200)
    reply_cands = [c for c in comment_cands if len(c) <= 80] or comment_cands
    if not comment_cands:
        logger.info(f"[{voice_type}] pool skip — no candidates (source={source})")
        return 0

    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT id, voice_profile FROM personas
                WHERE JSON_UNQUOTE(JSON_EXTRACT(voice_profile, '$.voice_type')) = %s
                LIMIT 100
            """, (voice_type,))
            rows = cur.fetchall()

            updated = 0
            for row in rows:
                persona_id = row["id"] if isinstance(row, dict) else row[0]
                vp_raw = row["voice_profile"] if isinstance(row, dict) else row[1]
                try:
                    vp = json.loads(vp_raw) if isinstance(vp_raw, str) else vp_raw
                    meta = vp.get("pool_meta") or {}

                    for key, cands, target, meta_key in (
                        ("example_comments", comment_cands, POOL_TARGET_COMMENTS, "curated_comments"),
                        ("example_replies", reply_cands, POOL_TARGET_REPLIES, "curated_replies"),
                    ):
                        current = vp.get(key) or []
                        if not isinstance(current, list):
                            current = []
                        if meta_key not in meta:
                            meta[meta_key] = len(current)  # 첫 실행: 현재 예시 전부를 수제로 기록
                        curated = current[:meta[meta_key]]
                        need = max(0, target - len(curated))
                        picked = random.sample(cands, min(need, len(cands))) if need else []
                        merged = list(dict.fromkeys(curated + picked))  # 중복 제거, 순서 보존
                        vp[key] = merged[:target] if len(merged) > target else merged

                    vp["pool_meta"] = meta
                    cur.execute(
                        "UPDATE personas SET voice_profile = %s WHERE id = %s",
                        (json.dumps(vp, ensure_ascii=False), persona_id)
                    )
                    updated += 1
                except Exception as e:
                    logger.debug(f"Pool expand failed for persona {persona_id}: {e}")
                    continue
    return updated


def strengthen_all(min_examples: int = 10) -> dict:
    """전체 Voice 타입 강화 실행 (말투 분석 + 예시 풀 확장)"""
    results = {}
    for voice_type, source in VOICE_SOURCE_MAP.items():
        if source is None:
            # 전용 크롤러 없는 voice — 분석은 스킵, 예시 풀만 혼합 소스로 확장
            pool_n = expand_persona_example_pools(voice_type, None)
            results[voice_type] = {"status": "pool-only", "pool_updated": pool_n}
            continue
        examples = get_examples_by_source(source, limit=30)
        if len(examples) < min_examples:
            logger.info(f"[{voice_type}] analysis skip — only {len(examples)} examples (need {min_examples})")
            # 분석은 스킵해도 예시 풀은 확장 (혼합 소스 폴백 포함)
            pool_n = expand_persona_example_pools(voice_type, source)
            results[voice_type] = {"status": "skip", "examples": len(examples), "pool_updated": pool_n}
            continue
        patterns = analyze_style_with_llm(voice_type, examples)
        updated = update_persona_profiles(voice_type, patterns)
        pool_n = expand_persona_example_pools(voice_type, source)
        logger.info(f"[{voice_type}] strengthened {updated} personas, pool expanded {pool_n}")
        results[voice_type] = {"status": "ok", "updated": updated, "pool_updated": pool_n,
                               "patterns": list(patterns.keys())}
    return results
