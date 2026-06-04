import logging
from fastapi import APIRouter, Request
from pydantic import BaseModel
from typing import Optional, List
from app.db.session import get_db
from app.services.embedding import EmbeddingService

logger = logging.getLogger(__name__)
router = APIRouter()
embedding_service = EmbeddingService()


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
    embed_service = request.app.state.embed_service
    try:
        vec = embed_service.embed(req.query[:512])
        vec_str = "[" + ",".join(f"{v:.8f}" for v in vec) + "]"

        conditions = []
        params: list = []

        if req.content_type:
            conditions.append("content_type = %s")
            params.append(req.content_type)
        if req.category:
            conditions.append("(category = %s OR category IS NULL)")
            params.append(req.category)

        where = ("WHERE " + " AND ".join(conditions)) if conditions else ""
        sql = f"""
            SELECT id, content, source,
                   1 - VEC_DISTANCE_COSINE(embedding, VEC_FromText(%s)) AS similarity
            FROM example_bank
            {where}
            ORDER BY VEC_DISTANCE_COSINE(embedding, VEC_FromText(%s)) ASC
            LIMIT %s
        """
        # vec_str appears twice (once for SELECT, once for ORDER BY)
        all_params = [vec_str] + params + [vec_str, req.top_k]

        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(sql, all_params)
                rows = cur.fetchall()

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
