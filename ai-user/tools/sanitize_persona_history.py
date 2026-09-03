#!/usr/bin/env python3
"""LLM 거절/오류가 섞인 persona history 엔트리를 제거한다."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "learning"))
from app.services.llm_error_signatures import looks_like_llm_error  # noqa: E402


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
