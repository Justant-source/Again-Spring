#!/usr/bin/env python3
"""Scan voice profiles for voice_type NOT in {NATEPAN, BLIND} → reassignment candidates.

Phase 0-C (#2 / §2.6). Read-only. Works on local YAML and/or a JSON dump.

Usage (from repo root):

  # scan local profiles/*/voice.yml
  python3 ai-user/tools/scan_voice_type_reassign.py

  # scan a prod/export JSON dump (no DB needed)
  python3 ai-user/tools/scan_voice_type_reassign.py \\
      --json ai-user/tools/samples/wave1-e/voice_profiles_sample.json

  # both: YAML + dump (union by id; dump wins on conflict)
  python3 ai-user/tools/scan_voice_type_reassign.py --include-yaml \\
      --json ai-user/tools/samples/wave1-e/voice_profiles_sample.json

  python3 ai-user/tools/scan_voice_type_reassign.py --out /tmp/reassign-candidates.json
"""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from baseline_lib import (
    ALLOWED_VOICE_TYPES,
    DEFAULT_PROFILES_DIR,
    iter_yaml_voices,
    load_voice_dump_json,
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

    dist = Counter(r["voice_type"] or "(empty)" for r in records)
    candidates = [
        {
            "id": r["id"],
            "slug": r.get("slug") or "",
            "nickname": r.get("nickname") or "",
            "voice_type": r["voice_type"] or "(empty)",
            "source": r.get("source"),
            "path": r.get("path"),
        }
        for r in records
        if (r["voice_type"] or "").upper() not in ALLOWED_VOICE_TYPES
    ]
    candidates.sort(key=lambda c: (c["voice_type"], c["slug"], c["id"]))

    allowed_count = sum(1 for r in records if (r["voice_type"] or "").upper() in ALLOWED_VOICE_TYPES)
    report = {
        "allowed_voice_types": sorted(ALLOWED_VOICE_TYPES),
        "total": len(records),
        "allowed_count": allowed_count,
        "reassign_candidate_count": len(candidates),
        "voice_type_distribution": dict(sorted(dist.items(), key=lambda kv: (-kv[1], kv[0]))),
        "candidates": candidates,
    }

    print(f"scanned              : {len(records)}")
    print(f"allowed (NATEPAN|BLIND): {allowed_count}")
    print(f"reassign candidates  : {len(candidates)}")
    print("distribution:")
    for vt, n in report["voice_type_distribution"].items():
        mark = "OK" if vt in ALLOWED_VOICE_TYPES else "→ reassign"
        print(f"  {vt:12} {n:4}  {mark}")
    print("candidates (id / voice_type / slug):")
    for c in candidates:
        print(f"  {c['id']}  {c['voice_type']:12}  {c['slug'] or '-'}  {c['nickname']}")

    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"wrote {args.out}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
