import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api import crawl, embed, examples, health, strengthen, topics
from app.db.models import create_tables
from app.services.embedding import EmbeddingService

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

embed_service = EmbeddingService()


def _env_flag(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}

@asynccontextmanager
async def lifespan(app):
    logger.info("Loading KURE-v1 embedding model...")
    embed_service.load()
    create_tables()
    if _env_flag("AI_LEARNING_ENABLED", True):
        from app.scheduler import init_scheduler
        init_scheduler()
    else:
        logger.info("AI Learning scheduler disabled via AI_LEARNING_ENABLED=false")
    logger.info("AI Learning service ready")
    yield

app = FastAPI(title="AI Learning Service", lifespan=lifespan)
app.state.embed_service = embed_service

app.include_router(health.router)
app.include_router(embed.router, prefix="/embed")
app.include_router(examples.router, prefix="/examples")
app.include_router(crawl.router, prefix="/crawl")
app.include_router(strengthen.router, prefix="/strengthen")
app.include_router(topics.router, prefix="/topics")
