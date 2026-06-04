from sqlalchemy import Column, BigInteger, Text, String, DateTime, Numeric, Integer, func
from pgvector.sqlalchemy import Vector
from app.db.session import Base

class ExampleBank(Base):
    __tablename__ = "example_bank"
    id = Column(BigInteger, primary_key=True, autoincrement=True)
    content = Column(Text, nullable=False)
    content_type = Column(String(16), nullable=False)
    category = Column(String(32))
    source = Column(String(32), nullable=False)
    quality_score = Column(Numeric(4, 2))
    embedding = Column(Vector(1024))  # KURE-v1 (BGE-m3 기반) = 1024차원
    created_at = Column(DateTime(timezone=True), server_default=func.now())

class CrawlLog(Base):
    __tablename__ = "crawl_log"
    id = Column(BigInteger, primary_key=True, autoincrement=True)
    source = Column(String(32))
    items_collected = Column(Integer, default=0)
    items_saved = Column(Integer, default=0)
    status = Column(String(16))
    error_msg = Column(Text)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
