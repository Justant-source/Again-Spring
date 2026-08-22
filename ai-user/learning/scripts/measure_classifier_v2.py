#!/usr/bin/env python3
"""
Phase 3 Measurement: Compare plaza classifier v1 (current) vs v2 (improved).

Pulls real data from prod example_bank, runs both classifiers offline (read-only),
and generates:
1. Before/after transition matrix
2. Hand-verified accuracy spot-check (30 samples)
3. Inventory changes (MARRIED/COUPLE/WORK/FAMILY/FRIEND)
"""

import sys
import subprocess
import json
import random
from collections import defaultdict
from typing import Tuple, List, Dict

# Add path for imports
sys.path.insert(0, "/home/justant/Data/Again-Spring/ai-user/learning")

from app.services.plaza_classifier import classify_plaza as classify_v1
from app.services.plaza_classifier_v2 import classify_plaza as classify_v2


def fetch_prod_examples(limit: int = 1000) -> List[Tuple[str, str, str]]:
    """
    Fetch example_bank rows from prod database (read-only).
    Returns: list of (content, title, example_id) tuples.
    """
    # Use docker exec to query prod database
    db_query = """
    SELECT
        b.body AS content,
        COALESCE(b.title, '') AS title,
        b.id
    FROM example_bank b
    WHERE b.content_type = 'POST'
    AND b.plaza IS NOT NULL
    AND b.created_at > DATE_SUB(NOW(), INTERVAL 14 DAY)
    LIMIT %d
    """

    cmd = [
        "docker",
        "exec",
        "againspring-mariadb-prod",
        "mariadb",
        "-uroot",
        "-p" + subprocess.check_output(
            "echo $MARIADB_ROOT_PASSWORD", shell=True
        ).decode().strip(),
        "againspring_prod",
        "-se",
        db_query % limit,
    ]

    try:
        # Try with env var first
        output = subprocess.check_output(cmd, stderr=subprocess.PIPE).decode()
    except subprocess.CalledProcessError:
        # Fallback: prompt for password
        print("Error connecting to prod. Ensure prod containers are running.")
        return []

    rows = []
    for line in output.strip().split("\n"):
        if not line:
            continue
        parts = line.split("\t")
        if len(parts) >= 3:
            content, title, ex_id = parts[0], parts[1] if parts[1] else "", parts[2]
            rows.append((content, title, ex_id))

    return rows


def measure_classifiers(
    content: str, title: str
) -> Tuple[str, str, Dict[str, int], Dict[str, int]]:
    """
    Run both classifiers on same input.
    Returns: (plaza_v1, plaza_v2, scores_v1, scores_v2)
    """
    from app.services.plaza_classifier import score_all_plazas as score_v1
    from app.services.plaza_classifier_v2 import score_all_plazas as score_v2

    v1_result = classify_v1(content, title)
    v2_result = classify_v2(content, title)
    v1_scores = score_v1(content, title)
    v2_scores = score_v2(content, title)

    return v1_result, v2_result, v1_scores, v2_scores


def main():
    print("\n" + "=" * 80)
    print("PHASE 3 MEASUREMENT: Plaza Classifier v1 → v2")
    print("=" * 80)
    print()

    # Fetch sample (at least 1000 rows)
    print("Fetching from prod example_bank (read-only)...")
    examples = fetch_prod_examples(limit=1200)
    print(f"Fetched {len(examples)} rows")

    if not examples:
        print("ERROR: Could not fetch prod data. Ensure prod containers running.")
        return 1

    # Run both classifiers
    print("\nRunning classifiers (v1 and v2)...")
    results = []
    for content, title, ex_id in examples:
        v1_plaza, v2_plaza, v1_scores, v2_scores = measure_classifiers(
            content, title
        )
        results.append({
            "id": ex_id,
            "content_snippet": content[:100],
            "title": title,
            "v1_plaza": v1_plaza,
            "v2_plaza": v2_plaza,
            "v1_scores": v1_scores,
            "v2_scores": v2_scores,
            "changed": v1_plaza != v2_plaza,
        })

    # Generate transition matrix
    print("\n" + "=" * 80)
    print("TRANSITION MATRIX (v1 → v2)")
    print("=" * 80)

    transition = defaultdict(lambda: defaultdict(int))
    changed_count = 0

    for r in results:
        from_plaza = r["v1_plaza"]
        to_plaza = r["v2_plaza"]
        transition[from_plaza][to_plaza] += 1
        if r["changed"]:
            changed_count += 1

    # Print matrix
    plazas = ["COUPLE", "MARRIED", "FRIEND", "FAMILY", "WORK", "OTHER"]
    print(f"{'':12}", end="")
    for p in plazas:
        print(f"{p:>10}", end="")
    print()
    print("-" * 80)

    for from_p in plazas:
        print(f"{from_p:>10}", end="")
        for to_p in plazas:
            count = transition[from_p][to_p]
            if count > 0:
                marker = "→" if from_p != to_p else " "
                print(f"{count:>9}{marker}", end="")
            else:
                print(f"{' ':>10}", end="")
        print()

    # Inventory changes
    print("\n" + "=" * 80)
    print("INVENTORY CHANGES")
    print("=" * 80)

    v1_inventory = defaultdict(int)
    v2_inventory = defaultdict(int)
    for r in results:
        v1_inventory[r["v1_plaza"]] += 1
        v2_inventory[r["v2_plaza"]] += 1

    print(f"{'Plaza':>12} {'v1 Count':>12} {'v2 Count':>12} {'Change':>12} {'Δ%':>8}")
    print("-" * 60)
    for p in plazas:
        v1_c = v1_inventory[p]
        v2_c = v2_inventory[p]
        delta = v2_c - v1_c
        delta_pct = (delta / v1_c * 100) if v1_c > 0 else 0
        print(
            f"{p:>12} {v1_c:>12} {v2_c:>12} {delta:>+12} {delta_pct:>7.1f}%"
        )

    print(f"\nTotal changed: {changed_count}/{len(results)} ({100*changed_count/len(results):.1f}%)")

    # Hand-verify sample of 30 changed rows
    print("\n" + "=" * 80)
    print("HAND-VERIFICATION (30 CHANGED ROWS)")
    print("=" * 80)

    changed = [r for r in results if r["changed"]]
    if not changed:
        print("No changes detected; skipping hand verification.")
        return 0

    sample_size = min(30, len(changed))
    sample = random.sample(changed, sample_size)

    correct_v2 = 0
    for i, r in enumerate(sample, 1):
        print(f"\n[{i}/{sample_size}] {r['title']}")
        print(f"   Content: {r['content_snippet']}...")
        print(f"   v1: {r['v1_plaza']:>10} → v2: {r['v2_plaza']:>10}")
        print(f"   v1 scores: {r['v1_scores']}")
        print(f"   v2 scores: {r['v2_scores']}")
        print(f"   Your verdict (Y/N/skip): ", end="", flush=True)

        try:
            verdict = input().strip().lower()
            if verdict == "y":
                correct_v2 += 1
            elif verdict == "n":
                pass
            # skip does nothing
        except EOFError:
            print("(EOF — assuming skip)")

    accuracy = (correct_v2 / sample_size * 100) if sample_size > 0 else 0
    print(f"\n\nHand-verified accuracy: {correct_v2}/{sample_size} = {accuracy:.1f}%")

    # Summary
    print("\n" + "=" * 80)
    print("SUMMARY")
    print("=" * 80)
    print(f"Sample size: {len(examples)} rows")
    print(f"Changed: {changed_count}/{len(results)} ({100*changed_count/len(results):.1f}%)")
    print(f"FAMILY gain: {v2_inventory['FAMILY'] - v1_inventory['FAMILY']}")
    print(f"FRIEND gain: {v2_inventory['FRIEND'] - v1_inventory['FRIEND']}")
    print(f"MARRIED change: {v2_inventory['MARRIED'] - v1_inventory['MARRIED']}")
    print(f"COUPLE change: {v2_inventory['COUPLE'] - v1_inventory['COUPLE']}")
    print(f"WORK change: {v2_inventory['WORK'] - v1_inventory['WORK']}")
    print(f"OTHER change: {v2_inventory['OTHER'] - v1_inventory['OTHER']}")
    print(f"Hand-verified accuracy (sample of {sample_size}): {accuracy:.1f}%")
    print("=" * 80 + "\n")

    return 0


if __name__ == "__main__":
    sys.exit(main())
