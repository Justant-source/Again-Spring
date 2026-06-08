"""
크롤 데이터 → LLM 추상화 → 일일 갈등 주제 시드(daily_topic) 합성.
매일 03:00 KST 크롤 완료 직후 실행. 원문 직접 인용·PII 없는 1~2문장 premise 생성.
"""
import json
import logging
import os
import requests
from app.db.session import get_db
from app.services.quality_filter import QualityFilter

logger = logging.getLogger(__name__)

LLM_URL = os.getenv("LLM_AI_USER_URL", "http://againspring-llm-ai-user-dev:8092")
MIN_CRAWL_QUALITY = 0.6   # example_bank 조회 품질 하한
MIN_CRAWL_POSTS = 5        # 카테고리 합성에 필요한 최소 샘플 수
TOPICS_PER_RUN = 5         # 회당 합성 시드 수 (카테고리 무관, LLM이 카테고리 태깅)

# 앱 카테고리 (archetypes.yml 기준)
APP_CATEGORIES = ["COUPLE", "MARRIED", "FRIEND", "FAMILY", "WORK", "OTHER"]
APP_CATEGORY_KR = {
    "COUPLE":  "연인·연애 갈등",
    "MARRIED": "부부·기혼 갈등",
    "FRIEND":  "친구·지인 갈등",
    "FAMILY":  "가족·부모 갈등",
    "WORK":    "직장·직업 갈등",
    "OTHER":   "기타 갈등",
}


def _get_todays_crawled_posts(limit: int = 60) -> list[dict]:
    """오늘 크롤된 고품질 POST 샘플 (크롤 board category 무관하게 전체 풀)."""
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT content, quality_score
                FROM example_bank
                WHERE DATE(created_at) = CURDATE()
                  AND content_type = 'POST'
                  AND quality_score >= %s
                ORDER BY quality_score DESC, RAND()
                LIMIT %s
            """, (MIN_CRAWL_QUALITY, limit))
            rows = cur.fetchall()
    return [{"content": r["content"] if isinstance(r, dict) else r[0],
             "quality_score": float(r["quality_score"] if isinstance(r, dict) else r[1])}
            for r in rows]


def _get_hot_topics_hint() -> list[str]:
    """personas.voice_profile.hot_topics 에서 오늘 강화된 트렌드 힌트 수집."""
    topics: list[str] = []
    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute("""
                    SELECT JSON_EXTRACT(voice_profile, '$.hot_topics') AS ht
                    FROM personas
                    WHERE voice_profile IS NOT NULL
                    LIMIT 100
                """)
                rows = cur.fetchall()
        for row in rows:
            raw = row["ht"] if isinstance(row, dict) else row[0]
            if not raw:
                continue
            try:
                lst = json.loads(raw) if isinstance(raw, str) else raw
                if isinstance(lst, list):
                    topics.extend(str(t) for t in lst if t)
            except Exception:
                pass
    except Exception as e:
        logger.warning(f"hot_topics hint fetch failed: {e}")
    # 중복 제거 후 상위 10개
    seen: set[str] = set()
    deduped: list[str] = []
    for t in topics:
        if t not in seen:
            seen.add(t)
            deduped.append(t)
    return deduped[:10]


def _synthesize_with_llm(posts_sample: list[str], hot_topics_hint: list[str]) -> list[dict]:
    """
    LLM 호출: 크롤 샘플 + hot_topics 힌트 → 앱 카테고리별 추상화 주제 시드 목록.
    반환: [{"category": "COUPLE", "text": "...", "confidence": 0.9, "source_topics": [...]}, ...]
    원문 직접 인용 금지, PII 금지, 일반화된 상황 구조, 댓글 유발형.
    """
    sample_text = "\n---\n".join(posts_sample[:20])
    hint_block = "\n".join(f"- {t}" for t in hot_topics_hint) if hot_topics_hint else "(없음)"
    cats_desc = "\n".join(f"  - {c}: {APP_CATEGORY_KR[c]}" for c in APP_CATEGORIES)
    n = TOPICS_PER_RUN

    prompt = f"""당신은 한국 인터넷 커뮤니티 갈등 소재 분석 전문가입니다.

아래는 오늘 수집된 커뮤니티 갈등 게시글 샘플입니다:
---
{sample_text}
---

최근 페르소나 분석에서 추출한 트렌드 힌트:
{hint_block}

위 샘플들의 갈등 패턴을 분석하여, **{n}개의 원본 갈등 시드 premise**를 만들어주세요.

각 시드는:
1. 1~2문장의 자연스러운 갈등 상황 (샘플 원문 직접 인용 절대 금지)
2. PII 없음 (실명·전화번호·주민번호·특정 조직명 등 불가)
3. 구체적이되 일반화된 상황 (누구나 공감 가능)
4. 댓글/공감 유발하는 자극적 표현 허용
5. 아래 카테고리 중 하나를 반드시 지정:
{cats_desc}

반드시 아래 JSON 배열만 반환하세요 (설명·마크다운 없이):
[
  {{
    "category": "카테고리코드",
    "text": "갈등 시드 1~2문장",
    "confidence": 0.0~1.0,
    "source_topics": ["관련힌트1", "관련힌트2"]
  }}
]"""

    try:
        resp = requests.post(
            f"{LLM_URL}/generate/persona",
            json={"prompt": prompt, "correlationId": "topic-synthesis-daily"},
            timeout=120,
        )
        resp.raise_for_status()
        raw = resp.json().get("text", "")
        raw = raw.strip().removeprefix("```json").removeprefix("```").removesuffix("```").strip()
        parsed = json.loads(raw)
        if not isinstance(parsed, list):
            logger.warning("LLM returned non-list for topic synthesis")
            return []
        results = []
        for item in parsed:
            if not isinstance(item, dict) or "text" not in item or "category" not in item:
                continue
            cat = item["category"].upper().strip()
            if cat not in APP_CATEGORIES:
                cat = "OTHER"
            results.append({
                "category": cat,
                "text": item["text"],
                "confidence": float(item.get("confidence", 0.8)),
                "source_topics": item.get("source_topics", []),
            })
        return results
    except Exception as e:
        logger.warning(f"LLM topic synthesis failed: {e}")
        return []


def _save_seeds(seeds: list[dict], embed_service) -> int:
    """합성된 시드를 daily_topic 테이블에 저장. 오늘치 기존 행은 먼저 삭제(멱등)."""
    qf = QualityFilter()
    saved = 0
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM daily_topic WHERE day = CURDATE()")
            for seed in seeds:
                text = seed["text"]
                if not qf.passes(text):
                    logger.warning(f"Synthesized seed failed quality filter: {text[:60]}")
                    continue
                try:
                    vec = embed_service.embed(text[:512])
                    vec_str = "[" + ",".join(f"{v:.8f}" for v in vec) + "]"
                    source_signal = ",".join(seed.get("source_topics", []))[:255]
                    cur.execute("""
                        INSERT INTO daily_topic
                            (day, category, seed_text, source_signal, quality_score, embedding, created_at)
                        VALUES (CURDATE(), %s, %s, %s, %s, VEC_FromText(%s), NOW(3))
                    """, (
                        seed["category"],
                        text,
                        source_signal,
                        round(seed["confidence"], 2),
                        vec_str,
                    ))
                    saved += 1
                except Exception as e:
                    logger.warning(f"Failed to save seed '{text[:40]}': {e}")
        conn.commit()
    return saved


def synthesize_daily_topics(embed_service) -> dict:
    """
    메인 진입점: 크롤 데이터 → LLM 합성 → daily_topic 저장.
    embed_service: app.main에서 주입.
    """
    posts = _get_todays_crawled_posts(limit=60)
    if len(posts) < MIN_CRAWL_POSTS:
        logger.info(f"Topic synthesis skipped — only {len(posts)} crawled posts today (need {MIN_CRAWL_POSTS})")
        return {"status": "skip", "posts": len(posts)}

    hot_topics = _get_hot_topics_hint()
    posts_text = [p["content"] for p in posts]

    seeds = _synthesize_with_llm(posts_text, hot_topics)
    if not seeds:
        logger.warning("Topic synthesis: LLM returned no seeds")
        return {"status": "llm_empty", "posts": len(posts)}

    saved = _save_seeds(seeds, embed_service)
    logger.info(f"Daily topic synthesis: {saved}/{len(seeds)} seeds saved (from {len(posts)} posts, {len(hot_topics)} hot_topics)")
    return {"status": "ok", "synthesized": saved, "llm_candidates": len(seeds), "hot_topics": len(hot_topics)}
