import logging
import os
from fastapi import APIRouter, Request
from pydantic import BaseModel
from typing import Optional, List
from app.db.session import get_db
from app.services.embedding import EmbeddingService

logger = logging.getLogger(__name__)
router = APIRouter()
embedding_service = EmbeddingService()

# Quality threshold for RAG examples (0.0-1.0 scale)
# Default 0.5: filters out obvious noise while allowing imperfect conflict narratives
MIN_QUALITY_SCORE = float(os.getenv("RAG_MIN_QUALITY", "0.5"))


class SaveRequest(BaseModel):
    content: str
    content_type: str
    category: Optional[str] = None
    source: str = "SELF_GENERATED"
    quality_score: Optional[float] = None


class SearchRequest(BaseModel):
    query: str
    content_type: Optional[str] = None
    category: Optional[str] = None
    top_k: int = 3


class ExampleItem(BaseModel):
    id: int
    content: str
    source: str
    score: Optional[float] = None


@router.post("/save")
def save_example(req: SaveRequest, request: Request):
    embed_service = request.app.state.embed_service
    try:
        vec = embed_service.embed(req.content[:512])
        vec_str = "[" + ",".join(f"{v:.8f}" for v in vec) + "]"
        sql = """INSERT INTO example_bank
                 (content, content_type, category, source, quality_score, embedding, created_at)
                 VALUES (%s, %s, %s, %s, %s, VEC_FromText(%s), NOW(3))"""
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(sql, (
                    req.content, req.content_type, req.category,
                    req.source, req.quality_score, vec_str
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

        # ── 공통 SELECT 템플릿 ──────────────────────────────────────────────
        SELECT_TMPL = """
            SELECT id, content, source,
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

        # ── Stage 1: content_type + category + quality ─────────────────────
        quality_condition = f"quality_score >= {MIN_QUALITY_SCORE}"
        rows = run_query(
            base_conditions + cat_conditions + [quality_condition],
            base_params + cat_params
        )

        # ── Stage 2: content_type + category (quality 제거) ─────────────────
        if not rows and cat_conditions:
            logger.warning(
                "search_examples stage2: no quality-filtered results for category=%s, "
                "retrying without quality gate", req.category
            )
            rows = run_query(
                base_conditions + cat_conditions,
                base_params + cat_params
            )

        # ── Stage 3: content_type만 (category 완화) ──────────────────────────
        # 크롤링 데이터(category='talk','hot','freeboard' 등)에 도달하는 경로
        if not rows:
            logger.warning(
                "search_examples stage3: relaxing category filter for category=%s — "
                "returning cross-category similarity matches (crawled data may appear)",
                req.category
            )
            rows = run_query(base_conditions, base_params)

        return [
            ExampleItem(id=r["id"], content=r["content"],
                        source=r["source"], score=float(r["similarity"]))
            for r in rows
        ]
    except Exception as e:
        logger.error(f"search_examples error: {e}")
        return []


@router.get("/count")
def count_examples():
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT source, COUNT(*) AS cnt FROM example_bank GROUP BY source")
            rows = cur.fetchall()
    return {r["source"]: r["cnt"] for r in rows}
