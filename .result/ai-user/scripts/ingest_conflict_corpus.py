#!/usr/bin/env python3
"""
ingest_conflict_corpus.py — Step 91: conflict-specific corpus ingest
Reads conflict-human-corpus-draft.json + platform-posts-draft.json
Ingests community-specific conflict posts into ML service
No LLM calls.

Usage:
    python3 ingest_conflict_corpus.py THEQOO
    python3 ingest_conflict_corpus.py NATEPAN
"""

from __future__ import annotations

import json
import sys
import urllib.request
from pathlib import Path

ML_API = "http://100.115.252.61:8201"
AUTH = "Bearer aiuser-ml-api-token-dev-2026"
SCRIPTS_DIR = Path(__file__).parent
BLIND_DIR = SCRIPTS_DIR.parent / "blind"

COMMUNITY_SOURCE_MAP = {
    "THEQOO": ["theqoo"],
    "NATEPAN": ["natepan", "nate_pann", "nate"],
    "CLIEN": ["clien"],
}

STRONG_CONFLICT_KW = [
    "남자친구", "남친", "여친", "여자친구", "남편", "아내",
    "싸웠", "싸움", "갈등", "억울", "배신", "서운", "섭섭",
    "친구가", "동료가", "상사가", "부모님이", "엄마가", "아빠가",
    "헤어지", "이별", "절교", "사이가 나", "관계가",
]


def _has_strong_conflict(text: str) -> bool:
    return any(k in text for k in STRONG_CONFLICT_KW)


def corpus_stats() -> dict:
    req = urllib.request.Request(
        f"{ML_API}/corpus/stats",
        headers={"Authorization": AUTH},
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read())


def ingest_batch(items: list[dict]) -> dict:
    payload = json.dumps({"items": items}).encode("utf-8")
    req = urllib.request.Request(
        f"{ML_API}/corpus/ingest",
        data=payload,
        headers={"Authorization": AUTH, "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read())


def ingest_texts(community: str, texts: list[str], source: str) -> tuple[int, int]:
    BATCH = 50
    total_ins = total_skip = 0
    for i in range(0, len(texts), BATCH):
        batch = texts[i : i + BATCH]
        items = [
            {
                "community": community,
                "contentType": "POST",
                "text": t.strip(),
                "label": "human",
                "source": source,
            }
            for t in batch
            if t and len(t.strip()) >= 40
        ]
        if not items:
            continue
        result = ingest_batch(items)
        ins = result.get("inserted", 0)
        skip = result.get("skipped", 0)
        total_ins += ins
        total_skip += skip
        print(f"  [{source}] batch {i // BATCH + 1}: inserted={ins}, skipped={skip}")
    return total_ins, total_skip


def main(community: str) -> None:
    community = community.upper()
    sources = COMMUNITY_SOURCE_MAP.get(community)
    if not sources:
        print(f"Unknown community: {community}")
        sys.exit(1)

    print(f"\n=== Step 91: ingest conflict corpus for {community} ===")

    stats_before = corpus_stats()
    before = stats_before.get(community, {})
    print(f"Before: n_human={before.get('human', 0)}, n_ai={before.get('ai', 0)}")

    total_ins = total_skip = 0

    # 1. Learning service conflict posts
    conflict_path = BLIND_DIR / "conflict-human-corpus-draft.json"
    if conflict_path.exists():
        data = json.loads(conflict_path.read_text(encoding="utf-8"))
        all_posts = data.get("posts", [])
        community_posts = [
            p for p in all_posts
            if p.get("source", "").lower() in sources
        ]
        # Strong conflict filter: remove obvious non-conflict false positives
        conflict_posts = [
            p for p in community_posts
            if _has_strong_conflict(p.get("content", ""))
        ]
        print(f"Learning service: community_match={len(community_posts)}, strong_conflict={len(conflict_posts)}")
        texts = [p.get("content", "") for p in conflict_posts]
        ins, skip = ingest_texts(community, texts, sources[0])
        total_ins += ins
        total_skip += skip
        print(f"Learning service subtotal: inserted={ins}, skipped={skip}")

    # 2. Platform posts (AS platform — always conflict by definition)
    platform_path = BLIND_DIR / "platform-posts-draft.json"
    if platform_path.exists():
        data = json.loads(platform_path.read_text(encoding="utf-8"))
        all_posts = data.get("posts", [])
        platform_posts = [
            p for p in all_posts
            if p.get("source_community", "").lower() in sources
        ]
        print(f"Platform posts: {len(platform_posts)}")
        texts = [p.get("body_raw", p.get("content", "")) for p in platform_posts]
        ins, skip = ingest_texts(community, texts, "again_spring")
        total_ins += ins
        total_skip += skip
        print(f"Platform subtotal: inserted={ins}, skipped={skip}")

    # Final stats
    stats_after = corpus_stats()
    after = stats_after.get(community, {})
    delta_human = after.get("human", 0) - before.get("human", 0)
    print(f"\nAfter: n_human={after.get('human', 0)}, n_ai={after.get('ai', 0)}")
    print(f"Net added: +{delta_human} human posts")
    print(f"Total: inserted={total_ins}, skipped={total_skip}")


if __name__ == "__main__":
    community = sys.argv[1] if len(sys.argv) > 1 else "THEQOO"
    main(community)
