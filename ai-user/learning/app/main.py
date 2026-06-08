from fastapi import FastAPI
from contextlib import asynccontextmanager
from app.db.models import create_tables
from app.services.embedding import EmbeddingService
from app.api import embed, examples, crawl, health, strengthen, topics
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

embed_service = EmbeddingService()

@asynccontextmanager
async def lifespan(app):
    logger.info("Loading KURE-v1 embedding model...")
    embed_service.load()
    create_tables()
    from app.scheduler import init_scheduler
    _scheduler = init_scheduler()
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
