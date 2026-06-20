#!/usr/bin/env python3
"""
convert_r9_answers.py — Convert R9-era blind answer JSON to R14-era schema

R9 schema (input):
  {
    "type": "blind2_mixed",
    "date": "2026-06-17",
    "pairs": [
      {"pair": 1, "human_is": "A", "ai_type": "CASUAL", "ai_corpus_id": 12814, "human_post_id": "..."},
      ...
    ]
  }

R14 schema (output):
  {
    "type": "converted_from_r9",
    "community": "<from --community arg>",
    "source": "<input filename>",
    "n_pairs": 20,
    "label_map": {
      "0": {"A": "human", "B": "ai"},
      "1": {"A": "human", "B": "ai"},
      ...
    },
    "responses": {}
  }

Usage:
  python3 convert_r9_answers.py --input r9-blind2-mixed-answers.json \\
    --community CLIEN --output r9-blind2-mixed-answers-converted.json
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def load_json(path: str) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def dump_json(path: str, payload: dict) -> None:
    Path(path).write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def convert_r9_to_r14(payload: dict, community: str, source_filename: str) -> dict:
    """Convert R9 schema to R14-compatible schema."""
    pairs = payload.get("pairs", [])
    n_pairs = len(pairs)

    label_map = {}
    for pair in pairs:
        pair_num = pair.get("pair", 0)
        human_is = pair.get("human_is", "").upper()

        # 0-indexed key for label_map
        key = str(pair_num - 1)

        if human_is == "A":
            label_map[key] = {"A": "human", "B": "ai"}
        elif human_is == "B":
            label_map[key] = {"A": "ai", "B": "human"}
        else:
            # Fallback if human_is is invalid
            label_map[key] = {"A": "unknown", "B": "unknown"}

    return {
        "type": "converted_from_r9",
        "community": community,
        "source": source_filename,
        "n_pairs": n_pairs,
        "label_map": label_map,
        "responses": {},
    }


def main():
    parser = argparse.ArgumentParser(
        description="Convert R9-era blind answer JSON to R14-compatible schema"
    )
    parser.add_argument("--input", required=True, help="Path to R9 answers JSON")
    parser.add_argument(
        "--community",
        required=True,
        choices=["CLIEN", "THEQOO", "NATEPAN"],
        help="Community name",
    )
    parser.add_argument(
        "--output",
        default=None,
        help="Output path (default: <stem>-converted.json in same dir)",
    )
    args = parser.parse_args()

    input_path = Path(args.input)
    if not input_path.exists():
        raise SystemExit(f"Input file not found: {args.input}")

    payload = load_json(args.input)
    source_filename = input_path.name
    converted = convert_r9_to_r14(payload, args.community, source_filename)

    output_path = args.output
    if not output_path:
        stem = input_path.stem
        output_path = str(input_path.parent / f"{stem}-converted.json")

    dump_json(output_path, converted)

    print(
        f"Converted {converted['n_pairs']} pairs → {output_path}"
    )


if __name__ == "__main__":
    main()
