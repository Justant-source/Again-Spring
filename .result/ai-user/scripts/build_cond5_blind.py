#!/usr/bin/env python3
"""
build_cond5_blind.py — per-community cond5 blind survey builder

입력:
1. `/corpus/export/blind` raw response json
2. 또는 endpoint 직접 fetch

출력:
- survey markdown
- answers template json
"""

import argparse
import json
import os
import random
import urllib.parse
import urllib.request
from datetime import datetime

DEFAULT_API_BASE = os.environ.get("AI_USER_ML_BASE_URL", "http://100.115.252.61:8201")
DEFAULT_API_TOKEN = os.environ.get("AI_USER_ML_API_TOKEN", "aiuser-ml-api-token-dev-2026")
DEFAULT_OUTPUT_DIR = "/home/justant/Data/Again-Spring/.result/ai-user/blind"


def load_json(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError as e:
        raise SystemExit(f"Input json not found: {path}") from e


def fetch_export(api_base, api_token, community, n_per_class, seed):
    query = urllib.parse.urlencode({
        "community": community,
        "nPerClass": n_per_class,
        "seed": seed,
    })
    req = urllib.request.Request(
        f"{api_base.rstrip('/')}/corpus/export/blind?{query}",
        headers={"Authorization": f"Bearer {api_token}"},
        method="GET",
    )
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode())


def extract_items(payload):
    blind_items = payload.get("blind_items") or []
    ground_truth = payload.get("ground_truth") or {}
    humans = []
    ais = []
    for item in blind_items:
        item_id = str(item.get("id"))
        label = ground_truth.get(item_id)
        text = (item.get("text") or "").strip()
        if not text or label not in {"human", "ai"}:
            continue
        normalized = {
            "id": item_id,
            "text": text,
            "label": label,
            "meta": item.get("meta") or {},
        }
        if label == "human":
            humans.append(normalized)
        else:
            ais.append(normalized)
    return humans, ais


def filter_used(items, used_ids, kind):
    if not used_ids:
        return items
    kept = []
    skipped = 0
    for item in items:
        meta = item.get("meta") or {}
        candidate_id = meta.get("ai_corpus_id") if kind == "ai" else meta.get("human_post_id")
        if candidate_id is not None and candidate_id in used_ids:
            skipped += 1
            continue
        kept.append(item)
    return kept, skipped


def pair_items(community, humans, ais, n_pairs, seed):
    rng = random.Random(seed)
    rng.shuffle(humans)
    rng.shuffle(ais)
    pairs = []
    label_map = {}
    for idx, (human, ai) in enumerate(zip(humans[:n_pairs], ais[:n_pairs])):
        labels = ["human", "ai"]
        rng2 = random.Random(seed + idx + 7)
        rng2.shuffle(labels)
        if labels[0] == "human":
            text_a, text_b = human["text"], ai["text"]
            meta_a, meta_b = human, ai
        else:
            text_a, text_b = ai["text"], human["text"]
            meta_a, meta_b = ai, human
        label_map[str(idx)] = {"A": labels[0], "B": labels[1]}
        pairs.append({
            "pair": idx + 1,
            "text_a": text_a,
            "text_b": text_b,
            "meta_a": meta_a,
            "meta_b": meta_b,
        })
    return pairs, label_map


def write_outputs(community, pairs, label_map, output_prefix, provenance):
    os.makedirs(DEFAULT_OUTPUT_DIR, exist_ok=True)
    survey_path = os.path.join(DEFAULT_OUTPUT_DIR, f"{output_prefix}-survey.md")
    answers_path = os.path.join(DEFAULT_OUTPUT_DIR, f"{output_prefix}-answers-template.json")
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    survey = f"""# R14 cond5 blind — {community}
> 생성: {now}
> provenance: `{provenance}`
> 지시: 각 번호에서 **AI가 쓴 것처럼 느껴지는 쪽**을 `A` 또는 `B`로 적고, 바로 아래 이유를 적으세요.
> 유효 응답: `A/B`만 집계. `판단불가`/빈칸/기타 응답은 무효 처리됩니다.

---

"""
    for pair in pairs:
        survey += f"""## {pair['pair']}번
**[A]**
{pair['text_a']}

**[B]**
{pair['text_b']}

**정답:**

**이유:**

---

"""

    answers = {
        "type": "cond5_blind",
        "community": community,
        "generated_at": now,
        "n_pairs": len(pairs),
        "label_map": label_map,
        "provenance": provenance,
        "response_instructions": {
            "accepted_keys": "responses.<respondent> 에는 pair 번호를 1-based(1..N) 또는 0-based(0..N-1)로 넣을 수 있음",
            "accepted_values": ["A", "B", "답변불가", "판단불가", "미응답"],
            "validity_rule": "공식 집계는 A/B만 유효. 나머지는 invalid",
        },
        "pair_metadata": [
            {
                "pair": pair["pair"],
                "a_label": label_map[str(pair["pair"] - 1)]["A"],
                "b_label": label_map[str(pair["pair"] - 1)]["B"],
                "a_meta": pair["meta_a"].get("meta") or {},
                "b_meta": pair["meta_b"].get("meta") or {},
            }
            for pair in pairs
        ],
        "responses": {
            "friend": {},
            "owner": {},
        },
    }

    with open(survey_path, "w", encoding="utf-8") as f:
        f.write(survey)
    with open(answers_path, "w", encoding="utf-8") as f:
        json.dump(answers, f, ensure_ascii=False, indent=2)
    return survey_path, answers_path


def main():
    parser = argparse.ArgumentParser(description="Build per-community cond5 blind survey")
    parser.add_argument("--community", required=True)
    parser.add_argument("--export-json", default=None)
    parser.add_argument("--fetch-export", action="store_true")
    parser.add_argument("--api-base", default=DEFAULT_API_BASE)
    parser.add_argument("--api-token", default=DEFAULT_API_TOKEN)
    parser.add_argument("--n-per-class", type=int, default=20)
    parser.add_argument("--n-pairs", type=int, default=20)
    parser.add_argument("--seed", type=int, default=2026)
    parser.add_argument("--used-ids", default=None, help="Optional used-corpus-ids.json")
    parser.add_argument("--output-prefix", default=None)
    args = parser.parse_args()

    if bool(args.export_json) == bool(args.fetch_export):
        raise SystemExit("Choose exactly one of --export-json or --fetch-export")

    community = args.community.upper()
    if args.fetch_export:
        try:
            payload = fetch_export(args.api_base, args.api_token, community, args.n_per_class, args.seed)
        except Exception as e:
            raise SystemExit(f"Failed to fetch blind export: {e}") from e
        provenance = f"{args.api_base.rstrip('/')}/corpus/export/blind?community={community}&nPerClass={args.n_per_class}&seed={args.seed}"
    else:
        payload = load_json(args.export_json)
        provenance = os.path.abspath(args.export_json)

    humans, ais = extract_items(payload)
    used_ai_ids = set()
    used_human_ids = set()
    if args.used_ids:
        used = load_json(args.used_ids)
        used_ai_ids = set(used.get("all_used_ai_corpus_ids") or [])
        used_human_ids = set(used.get("all_used_human_post_ids") or [])
        ais, skipped_ai = filter_used(ais, used_ai_ids, "ai")
        humans, skipped_human = filter_used(humans, used_human_ids, "human")
    else:
        skipped_ai = skipped_human = 0

    if len(humans) < args.n_pairs or len(ais) < args.n_pairs:
        raise SystemExit(
            f"Not enough items after filtering: humans={len(humans)} ais={len(ais)} need={args.n_pairs}"
        )

    pairs, label_map = pair_items(community, humans, ais, args.n_pairs, args.seed)
    prefix = args.output_prefix or f"r14-cond5-{community.lower()}"
    survey_path, answers_path = write_outputs(community, pairs, label_map, prefix, provenance)
    print(json.dumps({
        "community": community,
        "survey_path": survey_path,
        "answers_path": answers_path,
        "pairs": len(pairs),
        "filtered_used_ai": skipped_ai,
        "filtered_used_human": skipped_human,
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
