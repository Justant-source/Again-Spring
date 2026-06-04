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
        logger.info("Embedding model loaded (768 dim)")

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
