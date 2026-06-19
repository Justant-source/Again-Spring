#!/usr/bin/env python3
"""
reserve_blind_set.py — survey texts를 used-corpus registry에 예약(backfill 포함)
"""

import argparse
import json
from datetime import datetime

from survey_fingerprints import (
    collect_text_fingerprints_from_survey,
    merge_unique,
    upsert_test_entry,
    update_registry,
)


def load_json(path: str) -> dict:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def extract_ids_from_answers(payload: dict) -> tuple[list, list]:
    ai_ids = []
    human_ids = []
    for row in payload.get("pair_metadata") or []:
        for side in ("a_meta", "b_meta"):
            meta = row.get(side) or {}
            ai_corpus_id = meta.get("ai_corpus_id")
            human_post_id = meta.get("human_post_id")
            if ai_corpus_id is not None:
                ai_ids.append(ai_corpus_id)
            if human_post_id is not None:
                human_ids.append(human_post_id)
    return ai_ids, human_ids


def main():
    parser = argparse.ArgumentParser(description="Reserve survey texts into used-corpus registry")
    parser.add_argument("--registry", required=True)
    parser.add_argument("--survey", required=True)
    parser.add_argument("--test-id", required=True)
    parser.add_argument("--answers", default=None)
    parser.add_argument("--date", default=datetime.now().strftime("%Y-%m-%d"))
    parser.add_argument("--note", default="")
    args = parser.parse_args()

    fp_info = collect_text_fingerprints_from_survey(args.survey)
    ai_ids = []
    human_ids = []
    if args.answers:
        answers = load_json(args.answers)
        ai_ids, human_ids = extract_ids_from_answers(answers)

    entry = {
        "test_id": args.test_id,
        "date": args.date,
        "survey_path": args.survey,
        "answers_path": args.answers,
        "ai_corpus_ids": merge_unique(ai_ids),
        "human_post_ids": merge_unique(human_ids),
        "text_fingerprints": fp_info["all_fingerprints"],
        "pair_fingerprints": fp_info["pair_fingerprints"],
        "note": args.note,
    }
    def mutator(registry):
        upsert_test_entry(registry, entry)
        registry["all_used_ai_corpus_ids"] = merge_unique(registry.get("all_used_ai_corpus_ids", []) + entry["ai_corpus_ids"])
        registry["all_used_human_post_ids"] = merge_unique(registry.get("all_used_human_post_ids", []) + entry["human_post_ids"])
        registry["all_used_text_fingerprints"] = merge_unique(
            registry.get("all_used_text_fingerprints", []) + entry["text_fingerprints"]
        )

    update_registry(args.registry, mutator)
    print(json.dumps({
        "registry": args.registry,
        "test_id": args.test_id,
        "text_fingerprints": len(entry["text_fingerprints"]),
        "ai_ids": len(entry["ai_corpus_ids"]),
        "human_ids": len(entry["human_post_ids"]),
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
