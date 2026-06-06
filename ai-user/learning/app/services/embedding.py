from sentence_transformers import SentenceTransformer
from typing import List
import logging

logger = logging.getLogger(__name__)

class EmbeddingService:
    MODEL_NAME = "nlpai-lab/KURE-v1"

    def __init__(self):
        self.model = None

    def load(self):
        logger.info(f"Loading {self.MODEL_NAME}...")
        self.model = SentenceTransformer(self.MODEL_NAME)
        # KURE-v1 (BGE-M3 기반) = 1024차원. example_bank.embedding VECTOR(1024)과 일치해야 함.
        dim = self.model.get_sentence_embedding_dimension()
        if dim != 1024:
            raise RuntimeError(
                f"KURE-v1 embedding dimension mismatch: expected 1024, got {dim}. "
                f"example_bank.embedding is VECTOR(1024) — 모델과 컬럼 차원을 일치시키세요."
            )
        logger.info(f"Embedding model loaded ({dim} dim)")

    def embed(self, text):
        if not self.model:
            raise RuntimeError("Model not loaded")
        vec = self.model.encode(text, normalize_embeddings=True)
        return vec.tolist()

    def embed_batch(self, texts):
        if not self.model:
            raise RuntimeError("Model not loaded")
        vecs = self.model.encode(texts, normalize_embeddings=True, batch_size=32, show_progress_bar=False)
        return vecs.tolist()
