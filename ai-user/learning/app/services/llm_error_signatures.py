"""LLM 오류·거절·누출 시그니처 SSOT 로더 (docs/shared/policies/llm-error-signatures.json)."""
from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

ENV_PATH = "LLM_ERROR_SIGNATURES_PATH"
_CANDIDATES = (
    Path("/app/shared/docs/policies/llm-error-signatures.json"),
    Path(__file__).resolve().parents[4] / "docs" / "shared" / "policies" / "llm-error-signatures.json",
)


@dataclass(frozen=True)
class Signatures:
    signatures: tuple[str, ...]
    prompt_leak_patterns: tuple[re.Pattern, ...]
    korean_ratio_min: float
    korean_check_min_chars: int

    def contains_signature(self, lower: str) -> bool:
        return any(sig in lower for sig in self.signatures)

    def has_insufficient_korean(self, text: str) -> bool:
        significant = sum(1 for ch in text if ord(ch) > 32)
        if significant < self.korean_check_min_chars:
            return False
        korean = sum(1 for ch in text
                     if "가" <= ch <= "힣" or "ᄀ" <= ch <= "ᇿ" or "㄰" <= ch <= "㆏")
        return korean / significant < self.korean_ratio_min

    def has_prompt_leak(self, text: str) -> bool:
        return any(p.search(text) for p in self.prompt_leak_patterns)


def _resolve() -> Path:
    env = os.getenv(ENV_PATH)
    if env and Path(env).is_file():
        return Path(env)
    for c in _CANDIDATES:
        if c.is_file():
            return c
    raise RuntimeError(f"llm-error-signatures.json not found; set {ENV_PATH}")


@lru_cache(maxsize=1)
def load() -> Signatures:
    data = json.loads(_resolve().read_text(encoding="utf-8"))
    sigs = tuple(s.strip().lower() for s in data["signatures"] if s.strip())
    if not sigs:
        raise RuntimeError("llm-error-signatures.json has no signatures")
    # Java (?m) 플래그 → Python re.M
    leaks = tuple(re.compile(p.replace("(?m)", ""), re.M) for p in data.get("prompt_leak_patterns", []))
    return Signatures(sigs, leaks, float(data.get("korean_ratio_min", 0.10)), int(data.get("korean_check_min_chars", 20)))


def looks_like_llm_error(text: str | None) -> bool:
    if not text or not text.strip():
        return False
    s = load()
    if s.has_insufficient_korean(text):
        return True
    lower = re.sub(r"\s+", " ", text).strip().lower()
    return s.contains_signature(lower)
