from fastapi import APIRouter, Depends, Request
from pydantic import BaseModel
from typing import List, Optional
from sqlalchemy.orm import Session
from sqlalchemy import text
from app.db.session import get_db
from app.db.models import ExampleBank
import logging

logger = logging.getLogger(__name__)
router = APIRouter()

class SaveRequest(BaseModel):
    content: str
    content_type: str
    category: Optional[str] = None
    source: str = "SELF_GENERATED"
    quality_score: Optional[float] = None

class SearchRequest(BaseModel):
    query: str
    content_type: str
    category: Optional[str] = None
    top_k: int = 3

class ExampleItem(BaseModel):
    id: int
    content: str
    source: str
    score: Optional[float] = None

@router.post("/save")
def save_example(req: SaveRequest, request: Request, db: Session = Depends(get_db)):
    embed_service = request.app.state.embed_service
    try:
        vec = embed_service.embed(req.content[:512])
        ex = ExampleBank(
            content=req.content,
            content_type=req.content_type,
            category=req.category,
            source=req.source,
            quality_score=req.quality_score,
            embedding=vec,
        )
        db.add(ex)
        db.commit()
        return {"id": ex.id, "status": "saved"}
    except Exception as e:
        db.rollback()
        logger.error(f"Save failed: {e}")
        return {"status": "error", "message": str(e)}

@router.post("/search", response_model=List[ExampleItem])
def search_examples(req: SearchRequest, request: Request, db: Session = Depends(get_db)):
    embed_service = request.app.state.embed_service
    try:
        vec = embed_service.embed(req.query[:512])
        query = text("""
            SELECT id, content, source,
                   1 - (embedding <=> CAST(:vec AS vector)) AS similarity
            FROM example_bank
            WHERE content_type = :ctype
              AND (:cat IS NULL OR category = :cat OR category IS NULL)
            ORDER BY embedding <=> CAST(:vec AS vector)
            LIMIT :k
        """)
        rows = db.execute(query, {"vec": str(vec), "ctype": req.content_type, "cat": req.category, "k": req.top_k}).fetchall()
        return [ExampleItem(id=r.id, content=r.content, source=r.source, score=float(r.similarity)) for r in rows]
    except Exception as e:
        logger.error(f"Search failed: {e}")
        return []

@router.get("/count")
def count_examples(db: Session = Depends(get_db)):
    rows = db.execute(text("SELECT source, COUNT(*) as cnt FROM example_bank GROUP BY source")).fetchall()
    return {r.source: r.cnt for r in rows}
