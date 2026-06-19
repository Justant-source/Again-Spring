#!/usr/bin/env python3
"""LLM 거절/오류가 섞인 persona history 엔트리를 제거한다."""

from __future__ import annotations

import argparse
import re
from pathlib import Path

MIN_KOREAN_RATIO = 0.10
MIN_KOREAN_CHECK_LEN = 20
ERROR_SIGNATURES = [
    "i can't write",
    "i can't do this",
    "i can't fulfill",
    "i appreciate the context",
    "i appreciate the detailed",
    "these instructions ask me",
    "the instructions ask me",
    "actual operating online community",
    "operating online community",
    "authentic community member",
    "genuine community member",
    "designed to appear authentic",
    "community participation",
    "이 요청은 도와드릴 수 없습니다",
    "이 요청은 수행할 수 없습니다",
    "실제 운영 중인",
    "실제 온라인 커뮤니티",
    "진정성 있는 사용자",
    "허위 정보 및 스푸핑",
    "조작된 커뮤니티 활동",
    "가짜 페르소나",
    "신원 위장",
    "사용자 조작",
    "진정성에 손상",
]


def has_insufficient_korean(text: str) -> bool:
    significant = sum(1 for ch in text if not ch.isspace())
    if significant < MIN_KOREAN_CHECK_LEN:
        return False
    korean = sum(
        1
        for ch in text
        if "\uac00" <= ch <= "\ud7a3"
        or "\u1100" <= ch <= "\u11ff"
        or "\u3130" <= ch <= "\u318f"
    )
    return korean / significant < MIN_KOREAN_RATIO


def looks_like_llm_error(text: str) -> bool:
    if not text or not text.strip():
        return False
    if has_insufficient_korean(text):
        return True
    lower = re.sub(r"\s+", " ", text).strip().lower()
    return any(sig in lower for sig in ERROR_SIGNATURES)


def extract_body(block: str, kind: str) -> str | None:
    if not block.strip():
        return None
    if kind == "posts":
        match = re.search(r"(?:^|\n)### [^\n]*\n", block)
        return block[match.end():].strip() if match else None
    match = re.search(r"(?:^|\n)> ", block)
    return block[match.end():].strip() if match else None


def sanitize_history_file(path: Path, kind: str, dry_run: bool) -> tuple[int, bool]:
    try:
        raw = path.read_text(encoding="utf-8")
    except OSError:
        return 0, True

    removed = 0
    kept: list[str] = []
    for block in raw.split("\n---\n"):
        stripped = block.strip()
        if not stripped:
            continue
        body = extract_body(stripped, kind)
        if body and looks_like_llm_error(body):
            removed += 1
            continue
        kept.append(stripped)

    if removed and not dry_run:
        new_raw = ""
        if kept:
            new_raw = "\n\n---\n".join(kept) + "\n\n---\n"
        path.write_text(new_raw, encoding="utf-8")
    return removed, False


def main() -> int:
    parser = argparse.ArgumentParser(description="persona history refusal sanitizer")
    parser.add_argument(
        "--profiles",
        default="ai-user/docs/personas/profiles",
        help="persona profiles directory",
    )
    parser.add_argument(
        "--kind",
        choices=["comments", "posts", "both"],
        default="comments",
        help="history file kind to sanitize",
    )
    parser.add_argument("--dry-run", action="store_true", help="report only")
    args = parser.parse_args()

    base = Path(args.profiles)
    kinds = ["comments", "posts"] if args.kind == "both" else [args.kind]
    total_removed = 0
    skipped = 0

    for kind in kinds:
        for path in sorted(base.glob(f"*/history/{kind}.md")):
            removed, unreadable = sanitize_history_file(path, kind, args.dry_run)
            total_removed += removed
            skipped += int(unreadable)
            if removed:
                print(f"{path}: removed {removed}")
            elif unreadable:
                print(f"{path}: skipped (unreadable)")

    print(f"total_removed={total_removed} skipped={skipped} dry_run={args.dry_run}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
