#!/usr/bin/env python3
"""Heuristic contamination scanner for voice_profile example_comments / general_style.

Phase 0-C (#3 / §2.5). Read-only. Flags out-of-domain keywords
(games / sports / idols / beauty / other) and political-axis leakage in general_style.

Usage (from repo root):

  # scan local profiles/*/voice.yml
  python3 ai-user/tools/scan_voice_contamination.py

  # scan a JSON dump (no DB needed)
  python3 ai-user/tools/scan_voice_contamination.py \\
      --json ai-user/tools/samples/wave1-e/voice_profiles_sample.json

  python3 ai-user/tools/scan_voice_contamination.py --out /tmp/contamination.json

Keyword lists live in baseline_lib.OOD_KEYWORD_GROUPS / POLITICAL_KEYWORDS (intentionally simple).
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from baseline_lib import (
    DEFAULT_PROFILES_DIR,
    OOD_KEYWORD_GROUPS,
    POLITICAL_KEYWORDS,
    iter_yaml_voices,
    load_voice_dump_json,
    scan_contamination,
)


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument(
        "--profiles-dir",
        type=Path,
        default=DEFAULT_PROFILES_DIR,
        help=f"profiles root (default: {DEFAULT_PROFILES_DIR})",
    )
    p.add_argument("--json", type=Path, default=None, help="optional voice_profile JSON dump")
    p.add_argument(
        "--include-yaml",
        action="store_true",
        help="when --json is set, also merge local YAML (default: JSON-only if --json given)",
    )
    p.add_argument("--out", type=Path, default=None, help="optional JSON report path")
    p.add_argument("--limit", type=int, default=0, help="print at most N contaminated personas (0=all)")
    return p.parse_args()


def load_records(args: argparse.Namespace) -> list[dict]:
    by_id: dict[str, dict] = {}
    use_yaml = args.json is None or args.include_yaml
    if use_yaml:
        for rec in iter_yaml_voices(args.profiles_dir):
            if rec["id"]:
                by_id[rec["id"]] = rec
    if args.json is not None:
        for rec in load_voice_dump_json(args.json):
            if rec["id"]:
                by_id[rec["id"]] = rec
    return list(by_id.values())


def main() -> int:
    args = parse_args()
    records = load_records(args)
    if not records:
        print("ERROR: no voice profiles loaded", file=sys.stderr)
        return 2

    findings: list[dict] = []
    group_totals = {g: 0 for g in OOD_KEYWORD_GROUPS}
    political_persona_count = 0

    for rec in records:
        result = scan_contamination(rec["texts"])
        if not (result["is_contaminated"] or result["has_political_style"]):
            continue
        for group, hits in result["ood_by_group"].items():
            group_totals[group] = group_totals.get(group, 0) + len(hits)
        if result["has_political_style"]:
            political_persona_count += 1
        findings.append(
            {
                "id": rec["id"],
                "slug": rec.get("slug") or "",
                "nickname": rec.get("nickname") or "",
                "voice_type": rec.get("voice_type") or "",
                "source": rec.get("source"),
                "path": rec.get("path"),
                **result,
            }
        )

    findings.sort(key=lambda f: (-f["ood_hit_count"], -len(f["political_hits"]), f["slug"], f["id"]))
    ood_personas = sum(1 for f in findings if f["is_contaminated"])

    report = {
        "total_scanned": len(records),
        "contaminated_persona_count": ood_personas,
        "political_style_persona_count": political_persona_count,
        "ood_hit_totals_by_group": group_totals,
        "keyword_groups": {k: list(v) for k, v in OOD_KEYWORD_GROUPS.items()},
        "political_keywords": list(POLITICAL_KEYWORDS),
        "findings": findings,
    }

    print(f"scanned personas          : {len(records)}")
    print(f"OOD contaminated personas : {ood_personas}")
    print(f"political general_style   : {political_persona_count}")
    print("OOD hits by group:")
    for g, n in group_totals.items():
        print(f"  {g:12} {n}")

    to_show = findings if args.limit <= 0 else findings[: args.limit]
    print(f"findings ({len(to_show)}/{len(findings)}):")
    for f in to_show:
        groups = ",".join(f["ood_by_group"].keys()) or "-"
        pol = "POL" if f["has_political_style"] else "---"
        print(
            f"  [{pol}] {f['id']}  {f['voice_type']:10}  "
            f"ood={f['ood_hit_count']} ({groups})  {f['slug'] or '-'}  {f['nickname']}"
        )
        for group, hits in f["ood_by_group"].items():
            for h in hits[:2]:
                print(f"       · {group}/{h['field']}: {h['keywords']} | {h['excerpt'][:80]}")
        for h in f["political_hits"][:1]:
            print(f"       · political/{h['field']}: {h['keywords']} | {h['excerpt'][:80]}")

    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"wrote {args.out}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
