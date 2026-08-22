#!/usr/bin/env python3
"""
Phase 3 Measurement (batch): Compare plaza classifier v1 vs v2.

Pulls real data from prod example_bank, runs both classifiers offline (read-only),
generates transition matrix, and samples 30 changed rows for manual review.
"""

import sys
import subprocess
import json
import os
from collections import defaultdict
from typing import Tuple, List, Dict

# Add path for imports
sys.path.insert(0, "/home/justant/Data/Again-Spring/ai-user/learning")

from app.services.plaza_classifier import classify_plaza as classify_v1
from app.services.plaza_classifier import score_all_plazas as score_v1
from app.services.plaza_classifier_v2 import classify_plaza as classify_v2
from app.services.plaza_classifier_v2 import score_all_plazas as score_v2


def fetch_prod_examples(limit: int = 1200) -> List[Tuple[str, str, str]]:
    """
    Fetch example_bank rows from prod database (read-only).
    Returns: list of (content, title, example_id) tuples.
    """
    pwd = os.getenv("MARIADB_ROOT_PASSWORD")
    if not pwd:
        # Try reading from .env.prod
        try:
            with open("/home/justant/Data/Again-Spring/env/.env.prod") as f:
                for line in f:
                    if line.startswith("MARIADB_ROOT_PASSWORD="):
                        pwd = line.split("=", 1)[1].strip()
                        break
        except:
            pass

    if not pwd:
        print("ERROR: Could not find MARIADB_ROOT_PASSWORD")
        return []

    # Note: category in example_bank contains plaza values (COUPLE, MARRIED, FAMILY, FRIEND, WORK, OTHER)
    # and also non-standard categories (marriage, romance, talk, workplace)
    db_query = f"""
    SELECT
        b.content,
        COALESCE(b.title, ''),
        b.id
    FROM example_bank b
    WHERE b.content_type = 'POST'
    AND b.category IN ('COUPLE', 'MARRIED', 'FAMILY', 'FRIEND', 'WORK', 'OTHER', 'marriage', 'romance', 'workplace')
    LIMIT {limit}
    """

    cmd = [
        "docker",
        "exec",
        "againspring-mariadb-prod",
        "mariadb",
        "-uroot",
        "-p" + pwd,
        "againspring_prod",
        "-se",
        db_query,
    ]

    try:
        output = subprocess.check_output(cmd, stderr=subprocess.PIPE, timeout=30).decode()
    except subprocess.TimeoutExpired:
        print("ERROR: Database query timeout")
        return []
    except subprocess.CalledProcessError as e:
        print(f"ERROR: {e.stderr.decode()}")
        return []

    rows = []
    for line in output.strip().split("\n"):
        if not line or line.startswith("ERROR"):
            continue
        parts = line.split("\t", 2)  # Split on first 2 tabs only to preserve content
        if len(parts) >= 3:
            content, title, ex_id = parts[0], parts[1] if len(parts) > 1 else "", parts[2] if len(parts) > 2 else ""
            if content and ex_id:
                rows.append((content, title, ex_id))

    return rows


def main():
    print("\n" + "=" * 80)
    print("PHASE 3 MEASUREMENT: Plaza Classifier v1 → v2 (Batch Mode)")
    print("=" * 80)
    print()

    # Fetch sample
    print("Fetching from prod example_bank (read-only)...")
    examples = fetch_prod_examples(limit=1200)
    print(f"Fetched {len(examples)} rows")

    if len(examples) < 500:
        print(f"WARNING: Only {len(examples)} rows fetched (expected 1000+)")

    if not examples:
        print("ERROR: Could not fetch prod data.")
        return 1

    # Run both classifiers
    print("\nRunning classifiers (v1 and v2)...")
    results = []
    for i, (content, title, ex_id) in enumerate(examples):
        if i % 200 == 0:
            print(f"  Processed {i}/{len(examples)}")

        v1_plaza = classify_v1(content, title)
        v2_plaza = classify_v2(content, title)
        v1_scores = score_v1(content, title)
        v2_scores = score_v2(content, title)

        results.append({
            "id": ex_id,
            "content": content,
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

    # Save sample for hand-verification
    print("\n" + "=" * 80)
    print("SAVING 30 CHANGED ROWS FOR HAND-VERIFICATION")
    print("=" * 80)

    changed = [r for r in results if r["changed"]]
    if not changed:
        print("No changes detected.")
        return 0

    sample_size = min(30, len(changed))
    import random
    sample = random.sample(changed, sample_size)

    # Save to JSON for review
    out_file = "/tmp/plaza_classifier_v2_samples.json"
    with open(out_file, "w") as f:
        json.dump(sample, f, ensure_ascii=False, indent=2)
    print(f"\nSaved {sample_size} changed rows to: {out_file}")

    # Print samples inline
    print("\n" + "=" * 80)
    print("SAMPLE CHANGED ROWS (for manual review)")
    print("=" * 80)

    for i, r in enumerate(sample[:5], 1):  # Show first 5
        print(f"\n[Sample {i}/{min(5, sample_size)}]")
        print(f"Title: {r['title'][:80]}")
        print(f"Content: {r['content'][:150]}...")
        print(f"  v1: {r['v1_plaza']:>10} (scores: {r['v1_scores']})")
        print(f"  v2: {r['v2_plaza']:>10} (scores: {r['v2_scores']})")

    print(f"\n... ({sample_size - 5} more rows saved to {out_file}")

    # Summary
    print("\n" + "=" * 80)
    print("SUMMARY")
    print("=" * 80)
    print(f"Sample size: {len(examples)} rows")
    print(f"Changed: {changed_count}/{len(results)} ({100*changed_count/len(results):.1f}%)")
    print(f"\nInventory Changes:")
    print(f"  FAMILY:  {v1_inventory['FAMILY']:>3} → {v2_inventory['FAMILY']:>3}  (Δ {v2_inventory['FAMILY'] - v1_inventory['FAMILY']:+d})")
    print(f"  FRIEND:  {v1_inventory['FRIEND']:>3} → {v2_inventory['FRIEND']:>3}  (Δ {v2_inventory['FRIEND'] - v1_inventory['FRIEND']:+d})")
    print(f"  MARRIED: {v1_inventory['MARRIED']:>3} → {v2_inventory['MARRIED']:>3}  (Δ {v2_inventory['MARRIED'] - v1_inventory['MARRIED']:+d})")
    print(f"  COUPLE:  {v1_inventory['COUPLE']:>3} → {v2_inventory['COUPLE']:>3}  (Δ {v2_inventory['COUPLE'] - v1_inventory['COUPLE']:+d})")
    print(f"  WORK:    {v1_inventory['WORK']:>3} → {v2_inventory['WORK']:>3}  (Δ {v2_inventory['WORK'] - v1_inventory['WORK']:+d})")
    print(f"  OTHER:   {v1_inventory['OTHER']:>3} → {v2_inventory['OTHER']:>3}  (Δ {v2_inventory['OTHER'] - v1_inventory['OTHER']:+d})")
    print(f"\n⚠️  Review the {sample_size} changed samples manually before accepting v2.")
    print("=" * 80 + "\n")

    return 0


if __name__ == "__main__":
    sys.exit(main())
