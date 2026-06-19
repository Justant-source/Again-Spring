#!/usr/bin/env python3
"""
survey_fingerprints.py — blind survey text fingerprint helpers
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import tempfile
import fcntl
from pathlib import Path

PAIR_HEADER_RE = re.compile(r"^##\s+(\d+)번\s*$", re.MULTILINE)
AB_BLOCK_RE = re.compile(r"\*\*\[A\]\*\*\s*(.*?)\s*\*\*\[B\]\*\*\s*(.*?)(?:\*\*정답:\*\*|$)", re.DOTALL)


def normalize_text(text: str) -> str:
    s = (text or "").replace("\r\n", "\n").replace("\r", "\n")
    s = re.sub(r"[ \t]+", " ", s)
    s = re.sub(r"\n{3,}", "\n\n", s)
    return s.strip()


def text_fingerprint(text: str) -> str:
    normalized = normalize_text(text)
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def extract_pairs(text: str) -> list[tuple[int, str]]:
    matches = list(PAIR_HEADER_RE.finditer(text))
    pairs = []
    for idx, match in enumerate(matches):
        pair_num = int(match.group(1))
        start = match.end()
        end = matches[idx + 1].start() if idx + 1 < len(matches) else len(text)
        pairs.append((pair_num, text[start:end]))
    return pairs


def extract_ab_texts(block: str) -> tuple[str, str] | None:
    m = AB_BLOCK_RE.search(block)
    if not m:
        return None
    return normalize_text(m.group(1)), normalize_text(m.group(2))


def collect_text_fingerprints_from_survey(survey_path: str) -> dict:
    text = Path(survey_path).read_text(encoding="utf-8")
    pair_fingerprints = []
    all_fingerprints = []
    for pair_num, block in extract_pairs(text):
        ab = extract_ab_texts(block)
        if not ab:
            continue
        text_a, text_b = ab
        fp_a = text_fingerprint(text_a)
        fp_b = text_fingerprint(text_b)
        pair_fingerprints.append({
            "pair": pair_num,
            "a_fingerprint": fp_a,
            "b_fingerprint": fp_b,
        })
        all_fingerprints.extend([fp_a, fp_b])
    return {
        "pair_fingerprints": pair_fingerprints,
        "all_fingerprints": sorted(set(all_fingerprints)),
    }


def load_registry(path: str) -> dict:
    p = Path(path)
    if not p.exists():
        return default_registry()
    text = p.read_text(encoding="utf-8").strip()
    if not text:
        return default_registry()
    payload = json.loads(text)
    payload.setdefault("tests", [])
    payload.setdefault("all_used_ai_corpus_ids", [])
    payload.setdefault("all_used_human_post_ids", [])
    payload.setdefault("all_used_text_fingerprints", [])
    return payload


def default_registry() -> dict:
    return {
        "description": "Blind survey registry",
        "tests": [],
        "all_used_ai_corpus_ids": [],
        "all_used_human_post_ids": [],
        "all_used_text_fingerprints": [],
    }


def save_registry(path: str, payload: dict) -> None:
    dest = Path(path)
    dest.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False, dir=str(dest.parent)) as tmp:
        tmp.write(json.dumps(payload, ensure_ascii=False, indent=2) + "\n")
        tmp_path = tmp.name
    os.replace(tmp_path, path)


def update_registry(path: str, mutator) -> dict:
    lock_path = f"{path}.lock"
    Path(lock_path).parent.mkdir(parents=True, exist_ok=True)
    with open(lock_path, "w", encoding="utf-8") as lock_file:
        fcntl.flock(lock_file.fileno(), fcntl.LOCK_EX)
        payload = load_registry(path)
        mutator(payload)
        save_registry(path, payload)
        fcntl.flock(lock_file.fileno(), fcntl.LOCK_UN)
        return payload


def upsert_test_entry(payload: dict, entry: dict) -> None:
    tests = payload.setdefault("tests", [])
    replaced = False
    for idx, existing in enumerate(tests):
        if existing.get("test_id") == entry.get("test_id"):
            tests[idx] = entry
            replaced = True
            break
    if not replaced:
        tests.append(entry)


def merge_unique(items) -> list:
    seen = set()
    out = []
    for item in items:
        key = json.dumps(item, ensure_ascii=False, sort_keys=True) if isinstance(item, dict) else str(item)
        if key in seen:
            continue
        seen.add(key)
        out.append(item)
    return out
