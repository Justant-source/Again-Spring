import logging
import os
from fastapi import APIRouter, Query, Request
from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel
from typing import Optional, List
from app.db.session import get_db
from app.services.embedding import EmbeddingService

logger = logging.getLogger(__name__)
router = APIRouter()
embedding_service = EmbeddingService()

# Quality threshold for RAG examples (0.0-1.0 scale)
# Default 0.5: filters out obvious noise while allowing imperfect conflict narratives
MIN_QUALITY_SCORE = float(os.getenv("RAG_MIN_QUALITY", "0.5"))


class CamelCompatModel(BaseModel):
    """orchestrator(Java) 클라이언트는 camelCase(JSON)로 직렬화 — snake/camel 모두 수용.

    주의: 기존엔 alias가 없어 contentType/topK 등이 조용히 무시되고
    save는 content_type 필수 누락으로 422가 났음 (2026-06-11 수정).
    """
    model_config = ConfigDict(populate_by_name=True, alias_generator=to_camel)


class SaveRequest(CamelCompatModel):
    content: str
    content_type: str
    category: Optional[str] = None
    source: str = "SELF_GENERATED"
    quality_score: Optional[float] = None


class SearchRequest(CamelCompatModel):
    query: str
    content_type: Optional[str] = None
    category: Optional[str] = None
    register: Optional[str] = None  # 'casual' | 'polite' | 'mixed' | None
    exclude_self_generated: bool = True  # SELF_GENERATED 제외 여부
    top_k: int = 3


class StyleSampleRequest(CamelCompatModel):
    """문체 앵커용 랜덤 샘플 요청 — 주제 무관, 말투(소스·레지스터)만 일치."""
    content_type: str = "COMMENT"
    source: Optional[str] = None     # 크롤 소스 (natepan 등). None=전체 크롤 소스
    register: Optional[str] = None   # 'casual' | 'polite' (mixed 포함 매칭)
    top_k: int = 3
    max_len: int = 300               # 본문 최대 길이 (자)


class ExampleItem(BaseModel):
    id: int
    content: str
    source: str
    score: Optional[float] = None
    title: Optional[str] = None
    source_url: Optional[str] = None


class ClaimPopularSourceRequest(CamelCompatModel):
    source: str  # blind | natepan
    reservation_key: str
    reserve_until: str  # ISO-8601
    window_days: Optional[int] = 14
    expand_days: Optional[int] = 30
    # Plaza enum (COUPLE/MARRIED/FRIEND/FAMILY/WORK/OTHER). Filters example_bank
    # to matching board categories so reconstruct content stays in the right plaza.
    category: Optional[str] = None
    # Already-tried example_bank ids (LLM/safety failed). Claim the next popular row.
    exclude_example_ids: Optional[list[int]] = None


class SourceReservationKeyRequest(CamelCompatModel):
    example_id: int
    reservation_key: str


@router.post("/save")
def save_example(req: SaveRequest, request: Request):
    from app.services.register_classifier import classify as classify_register
    embed_service = request.app.state.embed_service
    try:
        vec = embed_service.embed(req.content[:512])
        vec_str = "[" + ",".join(f"{v:.8f}" for v in vec) + "]"
        register = classify_register(req.content)
        sql = """INSERT INTO example_bank
                 (content, content_type, category, source, quality_score, register, embedding, created_at)
                 VALUES (%s, %s, %s, %s, %s, %s, VEC_FromText(%s), NOW(3))"""
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(sql, (
                    req.content, req.content_type, req.category,
                    req.source, req.quality_score, register, vec_str
                ))
                new_id = cur.lastrowid
        return {"id": new_id, "status": "saved"}
    except Exception as e:
        logger.error(f"save_example error: {e}")
        return {"status": "error", "detail": str(e)}


@router.post("/search", response_model=List[ExampleItem])
def search_examples(req: SearchRequest, request: Request) -> List[ExampleItem]:
    """
    3단계 폴백 검색:
      Stage1: content_type + category + quality_score 조건
      Stage2: content_type + category (quality 제거)
      Stage3: content_type만 (category 완화) — 크롤링 데이터(talk/hot/freeboard 등) 도달 가능
    """
    embed_service = request.app.state.embed_service
    try:
        vec = embed_service.embed(req.query[:512])
        vec_str = "[" + ",".join(f"{v:.8f}" for v in vec) + "]"

        # base_conditions: content_type만 — Stage3에서도 유지
        base_conditions: list = []
        base_params: list = []
        if req.content_type:
            base_conditions.append("content_type = %s")
            base_params.append(req.content_type)

        # cat_conditions: category 필터 — Stage3에서 DROP됨
        cat_conditions: list = []
        cat_params: list = []
        if req.category:
            cat_conditions.append("(category = %s OR category IS NULL)")
            cat_params.append(req.category)

        # source_conditions: SELF_GENERATED 제외
        source_conditions: list = []
        source_params: list = []
        if req.exclude_self_generated:
            source_conditions.append("source != %s")
            source_params.append("SELF_GENERATED")

        # register_conditions: 문체 필터 — Stage3에서도 유지 (quality와 다르게)
        register_conditions: list = []
        register_params: list = []
        if req.register:
            register_conditions.append("(register = %s OR register = %s)")
            register_params.extend([req.register, 'mixed'])

        # ── 공통 SELECT 템플릿 ──────────────────────────────────────────────
        SELECT_TMPL = """
            SELECT id, content, source, title, source_url,
                   1 - VEC_DISTANCE_COSINE(embedding, VEC_FromText(%s)) AS similarity
            FROM example_bank
            {where}
            ORDER BY 1 - VEC_DISTANCE_COSINE(embedding, VEC_FromText(%s)) DESC,
                     quality_score DESC
            LIMIT %s
        """

        def run_query(where_conditions: list, where_params: list) -> list:
            where = ("WHERE " + " AND ".join(where_conditions)) if where_conditions else ""
            sql = SELECT_TMPL.format(where=where)
            all_params = [vec_str] + where_params + [vec_str, req.top_k]
            with get_db() as conn:
                with conn.cursor() as cur:
                    cur.execute(sql, all_params)
                    return cur.fetchall()

        # ── Stage 1: content_type + category + quality + source + register ─────────────────────
        quality_condition = f"quality_score >= {MIN_QUALITY_SCORE}"
        rows = run_query(
            base_conditions + cat_conditions + source_conditions + register_conditions + [quality_condition],
            base_params + cat_params + source_params + register_params
        )

        # ── Stage 2: content_type + category + source + register (quality 제거) ─────────────────
        if not rows and cat_conditions:
            logger.warning(
                "search_examples stage2: no quality-filtered results for category=%s, "
                "retrying without quality gate", req.category
            )
            rows = run_query(
                base_conditions + cat_conditions + source_conditions + register_conditions,
                base_params + cat_params + source_params + register_params
            )

        # ── Stage 3: content_type + source + register (category + quality 완화) ──────────────────────────
        # 크롤링 데이터(category='talk','hot','freeboard' 등)에 도달하는 경로, source + register 유지
        if not rows:
            logger.warning(
                "search_examples stage3: relaxing category filter for category=%s — "
                "returning cross-category similarity matches (crawled data may appear)",
                req.category
            )
            rows = run_query(base_conditions + source_conditions + register_conditions,
                           base_params + source_params + register_params)

        return [
            ExampleItem(id=r["id"], content=r["content"], source=r["source"],
                        score=float(r["similarity"]),
                        title=r.get("title"), source_url=r.get("source_url"))
            for r in rows
        ]
    except Exception as e:
        logger.error(f"search_examples error: {e}")
        return []


@router.post("/style-sample", response_model=List[ExampleItem])
def style_sample(req: StyleSampleRequest) -> List[ExampleItem]:
    """문체 앵커용 랜덤 샘플 — 주제 무관, 소스·레지스터·타입만 일치 (문체 현실화 S2).

    임베딩 불사용(ORDER BY RAND()) — 가볍고 호출마다 다른 예시 반환.
    SELF_GENERATED 제외 필수: 자기 출력 재학습 → AI투 증폭 루프 방지.

    폴백 순서:
      Stage1: source + content_type + register + quality
      Stage2: source 완화 (전체 크롤 소스, 타입·레지스터 유지)
      Stage3: COMMENT 요청인데 코퍼스 부족 시 → 같은 소스의 짧은 POST (캐던스 앵커 대용)
    """
    def run(conditions: list, params: list) -> list:
        where = "WHERE " + " AND ".join(conditions)
        sql = f"""SELECT id, content, source, quality_score AS score
                  FROM example_bank {where}
                  ORDER BY RAND() LIMIT %s"""
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(sql, params + [req.top_k])
                return cur.fetchall()

    try:
        base_conds = ["source != %s", "CHAR_LENGTH(content) BETWEEN 15 AND %s"]
        base_params: list = ["SELF_GENERATED", req.max_len]
        if req.register:
            base_conds.append("(register = %s OR register = %s)")
            base_params.extend([req.register, "mixed"])

        # Stage 1: 소스 일치 + 타입 일치 + 품질 (문체 앵커는 검색 RAG보다 높은 0.6 하한)
        style_min_quality = max(0.6, MIN_QUALITY_SCORE)
        conds1 = list(base_conds) + ["content_type = %s", f"quality_score >= {style_min_quality}"]
        params1 = list(base_params) + [req.content_type]
        if req.source:
            conds1.append("source = %s")
            params1.append(req.source)
        rows = run(conds1, params1)

        # Stage 2: 소스 완화 (전체 크롤 소스)
        if not rows and req.source:
            rows = run(list(base_conds) + ["content_type = %s"],
                       list(base_params) + [req.content_type])

        # Stage 3: COMMENT 코퍼스 부족 → 같은 소스의 짧은 POST를 캐던스 앵커로
        if not rows and req.content_type == "COMMENT":
            short_cap = min(200, req.max_len)
            conds3 = ["source != %s", "CHAR_LENGTH(content) BETWEEN 15 AND %s", "content_type = %s"]
            params3: list = ["SELF_GENERATED", short_cap, "POST"]
            if req.register:
                conds3.append("(register = %s OR register = %s)")
                params3.extend([req.register, "mixed"])
            if req.source:
                conds3.append("source = %s")
                params3.append(req.source)
            rows = run(conds3, params3)
            if not rows and req.source:  # 소스도 완화
                rows = run(conds3[:-1], params3[:-1])

        return [
            ExampleItem(id=r["id"], content=r["content"], source=r["source"],
                        score=float(r["score"]) if r.get("score") is not None else None,
                        title=r.get("title"), source_url=r.get("source_url"))
            for r in rows
        ]
    except Exception as e:
        logger.error(f"style_sample error: {e}")
        return []


@router.get("/export")
def export_examples(
    content_type: Optional[str] = Query(default=None, alias="contentType"),
    source_class: str = Query(default="human", alias="sourceClass"),
    since: Optional[str] = None,
    limit: int = 1000,
    offset: int = 0,
):
    """코퍼스 export — ASAU ML 서비스가 학습 데이터 pull 시 사용.

    source_class:
      'human' (default) → source != 'SELF_GENERATED' (크롤 데이터)
      'ai'              → source  = 'SELF_GENERATED' (봇 생성)
      'all'             → 전체

    since: ISO datetime (예: '2026-01-01 00:00:00') — 커서 기반 페이지네이션
    limit: 최대 1000, offset: 페이지네이션
    """
    conditions: list = []
    params: list = []

    if content_type:
        conditions.append("content_type = %s")
        params.append(content_type)

    if source_class == "human":
        conditions.append("source != %s")
        params.append("SELF_GENERATED")
    elif source_class == "ai":
        conditions.append("source = %s")
        params.append("SELF_GENERATED")
    # source_class == "all" → no filter

    if since:
        conditions.append("created_at > %s")
        params.append(since)

    where = ("WHERE " + " AND ".join(conditions)) if conditions else ""
    sql = f"""
        SELECT id, content, content_type, source, created_at
        FROM example_bank
        {where}
        ORDER BY created_at ASC
        LIMIT %s OFFSET %s
    """
    params.extend([min(int(limit), 5000), int(offset)])

    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(sql, params)
                rows = cur.fetchall()
        return {
            "items": [
                {
                    "id": r["id"],
                    "content": r["content"],
                    "contentType": r["content_type"],
                    "source": r["source"],
                    "createdAt": str(r["created_at"]) if r.get("created_at") else None,
                }
                for r in rows
            ],
            "total": len(rows),
            "offset": int(offset),
            "limit": int(limit),
        }
    except Exception as e:
        logger.error(f"export_examples error: {e}")
        return {"items": [], "total": 0, "offset": int(offset), "limit": int(limit), "error": str(e)}


@router.post("/claim-popular-source")
def claim_popular_source(req: ClaimPopularSourceRequest):
    """Pick unused high-popularity crawl POST and soft-reserve it for reconstruction.

    Window: created_at last windowDays (default 14), expand once to expandDays (30).
    Sources: blind | natepan only. Empty → {"status":"empty"} (caller skips slot).
    """
    from fastapi import HTTPException
    from app.services import source_claim

    try:
        item = source_claim.claim_popular_source(
            source=req.source,
            reservation_key=req.reservation_key,
            reserve_until=req.reserve_until,
            window_days=req.window_days if req.window_days is not None else 14,
            expand_days=req.expand_days if req.expand_days is not None else 30,
            category=req.category,
            exclude_ids=req.exclude_example_ids,
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        logger.error(f"claim_popular_source error: {e}")
        raise HTTPException(status_code=500, detail=str(e))
    if item is None:
        return {"status": "empty"}
    return item


@router.post("/commit-source")
def commit_source(req: SourceReservationKeyRequest):
    """Permanently mark a soft reservation as COMMITTED (key must match)."""
    from fastapi import HTTPException
    from app.services import source_claim

    try:
        result = source_claim.commit_source(
            example_id=req.example_id,
            reservation_key=req.reservation_key,
        )
    except Exception as e:
        logger.error(f"commit_source error: {e}")
        raise HTTPException(status_code=500, detail=str(e))
    if result.get("status") == "key_mismatch":
        raise HTTPException(status_code=403, detail="reservation key mismatch")
    if result.get("status") == "missing":
        raise HTTPException(status_code=404, detail="reservation not found")
    return result


@router.post("/release-source")
def release_source(req: SourceReservationKeyRequest):
    """Release SOFT reservation if key matches. No-op if COMMITTED or missing."""
    from fastapi import HTTPException
    from app.services import source_claim

    try:
        return source_claim.release_source(
            example_id=req.example_id,
            reservation_key=req.reservation_key,
        )
    except Exception as e:
        logger.error(f"release_source error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/expire-source-reservations")
def expire_source_reservations():
    """Delete SOFT reservations past reserve_until."""
    from fastapi import HTTPException
    from app.services import source_claim

    try:
        return source_claim.expire_source_reservations()
    except Exception as e:
        logger.error(f"expire_source_reservations error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/{example_id}", response_model=ExampleItem)
def get_example(example_id: int) -> ExampleItem:
    """단일 원본 조회 — 원본 비교 화면에서 정확한 1건을 id로 가져오는 경로."""
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT id, content, source, title, source_url FROM example_bank WHERE id = %s",
                (example_id,)
            )
            row = cur.fetchone()
    if not row:
        from fastapi import HTTPException
        raise HTTPException(status_code=404, detail="example not found")
    return ExampleItem(
        id=row["id"], content=row["content"], source=row["source"],
        title=row.get("title"), source_url=row.get("source_url")
    )


@router.get("/count")
def count_examples():
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT source, COUNT(*) AS cnt FROM example_bank GROUP BY source")
            rows = cur.fetchall()
    return {r["source"]: r["cnt"] for r in rows}
