#!/usr/bin/env python3
"""Diff persona IDs: input list/CSV/JSON vs local YAML profiles → YAML-missing list.

Phase 0-C (#1). Read-only. No DB connection required.

Usage (from repo root):

  # one ID per line (optional # comments)
  python3 ai-user/tools/diff_persona_yaml_ids.py \\
      --ids-file ai-user/tools/samples/wave1-e/persona_ids_sample.txt

  # CSV dump (column id / persona_id auto-detected, or --id-column)
  python3 ai-user/tools/diff_persona_yaml_ids.py \\
      --csv ai-user/tools/samples/wave1-e/personas_dump_sample.csv

  # JSON list of {id: ...} or ["uuid", ...]
  python3 ai-user/tools/diff_persona_yaml_ids.py \\
      --json ai-user/tools/samples/wave1-e/voice_profiles_sample.json

  # write machine-readable report
  python3 ai-user/tools/diff_persona_yaml_ids.py --ids-file ... --out /tmp/yaml-missing.json

YAML-missing = IDs present in the input but absent from profiles/*/profile.yml `id`.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from baseline_lib import (
    DEFAULT_PROFILES_DIR,
    load_id_csv,
    load_id_list_file,
    load_voice_dump_json,
    load_yaml_profile_ids,
)


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    src = p.add_mutually_exclusive_group(required=True)
    src.add_argument("--ids-file", type=Path, help="plain text: one persona id per line")
    src.add_argument("--csv", type=Path, help="DB dump CSV with an id column")
    src.add_argument("--json", type=Path, help="JSON list/object dump containing persona ids")
    p.add_argument("--id-column", default=None, help="CSV id column name (default: auto)")
    p.add_argument(
        "--profiles-dir",
        type=Path,
        default=DEFAULT_PROFILES_DIR,
        help=f"profiles root (default: {DEFAULT_PROFILES_DIR})",
    )
    p.add_argument("--out", type=Path, default=None, help="optional JSON report path")
    p.add_argument("--show-orphans", action="store_true", help="also list YAML ids not in input")
    return p.parse_args()


def load_input_ids(args: argparse.Namespace) -> list[str]:
    if args.ids_file:
        return load_id_list_file(args.ids_file)
    if args.csv:
        return load_id_csv(args.csv, args.id_column)
    assert args.json is not None
    return [rec["id"] for rec in load_voice_dump_json(args.json) if rec.get("id")]


def main() -> int:
    args = parse_args()
    input_ids = load_input_ids(args)
    if not input_ids:
        print("ERROR: no persona ids loaded from input", file=sys.stderr)
        return 2

    yaml_map = load_yaml_profile_ids(args.profiles_dir)
    yaml_ids = set(yaml_map)
    input_set = set(input_ids)
    missing = sorted(input_set - yaml_ids)
    orphans = sorted(yaml_ids - input_set)

    report = {
        "profiles_dir": str(args.profiles_dir),
        "input_count": len(input_ids),
        "input_unique": len(input_set),
        "yaml_count": len(yaml_ids),
        "yaml_missing_count": len(missing),
        "yaml_missing": missing,
        "yaml_orphan_count": len(orphans) if args.show_orphans else None,
        "yaml_orphans": orphans if args.show_orphans else None,
    }

    print(f"input unique ids : {len(input_set)}")
    print(f"YAML profile ids : {len(yaml_ids)}  ({args.profiles_dir})")
    print(f"YAML-missing     : {len(missing)}")
    for pid in missing:
        print(f"  - {pid}")
    if args.show_orphans:
        print(f"YAML orphans     : {len(orphans)}")
        for pid in orphans[:20]:
            print(f"  - {pid} ({yaml_map.get(pid, '?')})")
        if len(orphans) > 20:
            print(f"  ... +{len(orphans) - 20} more")

    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"wrote {args.out}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
