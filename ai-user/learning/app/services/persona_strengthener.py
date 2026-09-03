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
from app.services.llm_error_signatures import looks_like_llm_error as _looks_like_llm_error

logger = logging.getLogger(__name__)

LLM_URL = os.getenv("LLM_AI_USER_URL", "http://againspring-llm-ai-user:8092")

# Voice 타입 → 커뮤니티 소스 매핑
# WP1B (§16.1B): register 단일화 — NATEPAN·BLIND만. 그 외 소스 앵커 금지(재오염 방지).
ALLOWED_SOURCES = frozenset({"natepan", "blind"})
VOICE_SOURCE_MAP = {
    "NATEPAN": "natepan",
    "BLIND": "blind",
}

# 예시 풀 목표 크기 (문체 현실화 S5) — 고정 3~4개 → 풀 확장 후 생성 시 랜덤 서브셋 주입
POOL_TARGET_COMMENTS = 12
POOL_TARGET_REPLIES = 8


def _sanitize_text_item(text: str) -> str | None:
    if text is None:
        return None
    cleaned = re.sub(r"\s+", " ", str(text)).strip()
    if not cleaned or _looks_like_llm_error(cleaned):
        return None
    return cleaned


def _sanitize_text_list(values, limit: int) -> list[str]:
    cleaned: list[str] = []
    for value in values or []:
        item = _sanitize_text_item(value)
        if item and item not in cleaned:
            cleaned.append(item)
        if len(cleaned) >= limit:
            break
    return cleaned


def _sanitize_patterns(patterns: dict) -> dict:
    if not isinstance(patterns, dict):
        return {}
    signature_phrases = _sanitize_text_list(patterns.get("signature_phrases", []), 8)
    consistent_errors = _sanitize_text_list(patterns.get("consistent_errors", []), 4)
    hot_topics = _sanitize_text_list(patterns.get("hot_topics", []), 5)
    typing_habit = _sanitize_text_item(patterns.get("typing_habit", ""))

    sanitized = {}
    if signature_phrases:
        sanitized["signature_phrases"] = signature_phrases
    if consistent_errors:
        sanitized["consistent_errors"] = consistent_errors
    if hot_topics:
        sanitized["hot_topics"] = hot_topics
    if typing_habit:
        sanitized["typing_habit"] = typing_habit
    return sanitized


def get_examples_by_source(source: str, limit: int = 30) -> list[str]:
    """example_bank 고품질 예시 — natepan|blind only, popularity 게이트 통과분만.

    POST: popularity_pct >= 0.50
    COMMENT: 부모 POST가 popularity_pct >= 0.50
    """
    if source not in ALLOWED_SOURCES:
        logger.warning(f"refuse non-allowed strengthen source={source!r}")
        return []
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT content FROM (
                  SELECT e.content, e.quality_score, e.created_at
                  FROM example_bank e
                  WHERE e.source = %s
                    AND e.content_type = 'POST'
                    AND e.popularity_pct IS NOT NULL AND e.popularity_pct >= 0.50
                    AND e.quality_score >= 0.6
                    AND LENGTH(e.content) BETWEEN 30 AND 1500
                  UNION ALL
                  SELECT c.content, c.quality_score, c.created_at
                  FROM example_bank c
                  JOIN example_bank p
                    ON p.content_type = 'POST'
                   AND p.source_url = SUBSTRING_INDEX(c.source_url, '#', 1)
                   AND LOWER(p.source) = LOWER(c.source)
                  WHERE c.source = %s
                    AND c.content_type = 'COMMENT'
                    AND p.popularity_pct IS NOT NULL AND p.popularity_pct >= 0.50
                    AND c.quality_score >= 0.6
                    AND LENGTH(c.content) BETWEEN 30 AND 1500
                ) t
                ORDER BY quality_score DESC, created_at DESC
                LIMIT %s
            """, (source, source, limit))
            rows = cur.fetchall()
    return [r["content"] if isinstance(r, dict) else r[0] for r in rows]


def analyze_style_with_llm(voice_type: str, examples: list[str]) -> dict:
    """LLM으로 커뮤니티 문체 패턴 분석"""
    if not examples:
        return {}

    sample = "\n---\n".join(examples[:20])
    prompt = f"""다음은 한국 인터넷 커뮤니티 [{voice_type}]의 참고 게시글 샘플입니다.

---
{sample}
---

위 텍스트들을 분석하여 이 커뮤니티의 특징적인 문체 패턴만 JSON으로 추출하세요.
실존 인물/실사용자 사칭 문구나 거절문은 절대 출력하지 마세요.

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
        if _looks_like_llm_error(text):
            logger.warning(f"LLM analysis returned refusal/provider text for {voice_type}")
            return {}
        return _sanitize_patterns(json.loads(text))
    except Exception as e:
        logger.warning(f"LLM analysis failed for {voice_type}: {e}")
        return {}


def update_persona_profiles(voice_type: str, patterns: dict) -> int:
    """해당 Voice 타입의 페르소나 voice_profile 업데이트"""
    if not patterns:
        return 0

    sanitized = _sanitize_patterns(patterns)
    sig_phrases = sanitized.get("signature_phrases", [])
    errors = sanitized.get("consistent_errors", [])
    typing_habit = sanitized.get("typing_habit", "")
    hot_topics = sanitized.get("hot_topics", [])

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
                        existing = _sanitize_text_list(vp["lexicon"].get("signature_phrases", []), 8)
                        merged = list(dict.fromkeys(existing + sig_phrases))[:8]
                        vp["lexicon"]["signature_phrases"] = merged
                    if typing_habit:
                        vp["lexicon"]["typing_habit"] = typing_habit
                    # writing_quirks 업데이트
                    if "writing_quirks" not in vp or not isinstance(vp.get("writing_quirks"), dict):
                        vp["writing_quirks"] = {}
                    if errors:
                        existing_errs = _sanitize_text_list(vp["writing_quirks"].get("consistent_errors", []), 4)
                        merged_errs = list(dict.fromkeys(existing_errs + errors))[:4]
                        vp["writing_quirks"]["consistent_errors"] = merged_errs
                    # hot_topics 저장 — topic_synthesizer가 힌트로 소비
                    if hot_topics:
                        existing_topics = _sanitize_text_list(vp.get("hot_topics", []), 5)
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
    """크롤 코퍼스 예시 후보 — SELF_GENERATED 제외, natepan|blind, popularity 게이트.

    POST: popularity_pct >= 0.50
    COMMENT: 부모 POST popularity_pct >= 0.50
    """
    if source is not None and source not in ALLOWED_SOURCES:
        logger.warning(f"refuse non-allowed pool source={source!r}")
        return []

    with get_db() as conn:
        with conn.cursor() as cur:
            if content_type == "POST":
                if source:
                    cur.execute(
                        """
                        SELECT DISTINCT e.content FROM example_bank e
                        WHERE e.source = %s
                          AND e.content_type = 'POST'
                          AND e.popularity_pct IS NOT NULL AND e.popularity_pct >= 0.50
                          AND e.quality_score >= 0.6
                          AND CHAR_LENGTH(e.content) BETWEEN %s AND %s
                        ORDER BY RAND() LIMIT %s
                        """,
                        (source, min_len, max_len, limit),
                    )
                else:
                    cur.execute(
                        """
                        SELECT DISTINCT e.content FROM example_bank e
                        WHERE LOWER(e.source) IN ('natepan', 'blind')
                          AND e.content_type = 'POST'
                          AND e.popularity_pct IS NOT NULL AND e.popularity_pct >= 0.50
                          AND e.quality_score >= 0.6
                          AND CHAR_LENGTH(e.content) BETWEEN %s AND %s
                        ORDER BY RAND() LIMIT %s
                        """,
                        (min_len, max_len, limit),
                    )
            else:
                if source:
                    cur.execute(
                        """
                        SELECT DISTINCT c.content FROM example_bank c
                        JOIN example_bank p
                          ON p.content_type = 'POST'
                         AND p.source_url = SUBSTRING_INDEX(c.source_url, '#', 1)
                         AND LOWER(p.source) = LOWER(c.source)
                        WHERE c.source = %s
                          AND c.content_type = %s
                          AND p.popularity_pct IS NOT NULL AND p.popularity_pct >= 0.50
                          AND c.quality_score >= 0.6
                          AND CHAR_LENGTH(c.content) BETWEEN %s AND %s
                        ORDER BY RAND() LIMIT %s
                        """,
                        (source, content_type, min_len, max_len, limit),
                    )
                else:
                    cur.execute(
                        """
                        SELECT DISTINCT c.content FROM example_bank c
                        JOIN example_bank p
                          ON p.content_type = 'POST'
                         AND p.source_url = SUBSTRING_INDEX(c.source_url, '#', 1)
                         AND LOWER(p.source) = LOWER(c.source)
                        WHERE LOWER(c.source) IN ('natepan', 'blind')
                          AND c.content_type = %s
                          AND p.popularity_pct IS NOT NULL AND p.popularity_pct >= 0.50
                          AND c.quality_score >= 0.6
                          AND CHAR_LENGTH(c.content) BETWEEN %s AND %s
                        ORDER BY RAND() LIMIT %s
                        """,
                        (content_type, min_len, max_len, limit),
                    )
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
    """전체 Voice 타입 강화 실행 (말투 분석 + 예시 풀 확장).

    WP1B: NATEPAN·BLIND만 순회. 레거시 voice_type 페르소나는 재배정 전에는 건드리지 않는다.
    """
    results = {}
    for voice_type, source in VOICE_SOURCE_MAP.items():
        examples = get_examples_by_source(source, limit=30)
        if len(examples) < min_examples:
            logger.info(f"[{voice_type}] analysis skip — only {len(examples)} examples (need {min_examples})")
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
