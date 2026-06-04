from fastapi import APIRouter, Request
from pydantic import BaseModel
from typing import List

router = APIRouter()

class EmbedRequest(BaseModel):
    text: str

class EmbedResponse(BaseModel):
    embedding: List[float]

@router.post("", response_model=EmbedResponse)
def embed_text(req: EmbedRequest, request: Request):
    embed_service = request.app.state.embed_service
    vec = embed_service.embed(req.text[:512])
    return EmbedResponse(embedding=vec)
