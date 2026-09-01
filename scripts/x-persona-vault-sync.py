#!/usr/bin/env python3
"""Mirror Justant-Bot persona data from the BE internal export API into the host vault.

Runtime SSOT is the database. This script is a host-only append/version mirror.
Never run inside a container (writes would be root-owned).

Cron example (do not install from this script):

    0 5 * * * python3 /home/justant/Data/Again-Spring/scripts/x-persona-vault-sync.py --env prod >> /home/justant/Data/Again-Spring/.temp/x-justant-bot/sync.log 2>&1
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

PORTS = {"dev": 8090, "prod": 8091}
CONTAINER = "againspring-backend-{env}"
TIMEOUT_SEC = 60


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def vault_dir(root: Path) -> Path:
    return root / ".temp" / "x-justant-bot"


def die(msg: str, code: int = 1) -> None:
    print(msg, file=sys.stderr)
    raise SystemExit(code)


def _docker_sh(container: str, shell: str) -> str:
    return subprocess.check_output(
        ["docker", "exec", container, "sh", "-c", shell],
        text=True,
    ).strip()


def read_token(env: str) -> str:
    """Prefer env in the backend container; else decrypt vault `asm.callback_token`.

    Compose does not inject ASM_CALLBACK_TOKEN — runtime value comes from
    encrypted_secret (same as jwt.secret).
    """
    backend = CONTAINER.format(env=env)
    mariadb = f"againspring-mariadb-{env}"
    try:
        env_token = _docker_sh(backend, 'echo -n "$ASM_CALLBACK_TOKEN"')
    except subprocess.CalledProcessError as exc:
        die(f"docker exec failed for {backend}: {exc}")
    if env_token:
        return env_token
    try:
        import base64
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    except ImportError:
        die("empty ASM_CALLBACK_TOKEN in container and python package 'cryptography' missing")
    try:
        master_b64 = _docker_sh(backend, 'echo -n "$AS_SECRET_MASTER_KEY"')
        blob_b64 = _docker_sh(
            mariadb,
            'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE" -N -e '
            '"SELECT enc_blob FROM encrypted_secret WHERE secret_key=\'asm.callback_token\' LIMIT 1;"',
        )
    except subprocess.CalledProcessError as exc:
        die(f"vault token lookup failed: {exc}")
    if not master_b64 or not blob_b64:
        die("could not decrypt asm.callback_token (empty master key or blob)")
    raw = base64.b64decode(blob_b64)
    token = AESGCM(base64.b64decode(master_b64)).decrypt(raw[:12], raw[12:], None).decode("utf-8").strip()
    if not token:
        die("decrypted asm.callback_token was empty")
    return token


def fetch_export(port: int, token: str, since_example: int, since_eval: int) -> dict[str, Any]:
    qs = urllib.parse.urlencode(
        {"sinceExampleId": since_example, "sinceEvalId": since_eval}
    )
    url = f"http://localhost:{port}/api/internal/marketing/persona-export?{qs}"
    req = urllib.request.Request(
        url,
        headers={"Authorization": f"Bearer {token}", "Accept": "application/json"},
        method="GET",
    )
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT_SEC) as resp:
            body = resp.read().decode("utf-8")
            status = resp.status
    except urllib.error.HTTPError as exc:
        die(f"export HTTP {exc.code}: {exc.read().decode('utf-8', errors='replace')[:500]}")
    except urllib.error.URLError as exc:
        die(f"export request failed: {exc}")
    if status != 200:
        die(f"export HTTP {status}")
    return json.loads(body)


def load_json(path: Path, default: Any) -> Any:
    if not path.is_file():
        return default
    with path.open(encoding="utf-8") as fh:
        return json.load(fh)


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    with tmp.open("w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    tmp.replace(path)


def existing_tweet_ids(path: Path) -> set[str]:
    ids: set[str] = set()
    if not path.is_file():
        return ids
    with path.open(encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError:
                continue
            tid = row.get("tweet_id") or row.get("tweetId")
            if tid:
                ids.add(str(tid))
    return ids


def append_jsonl(path: Path, rows: list[dict[str, Any]]) -> int:
    if not rows:
        return 0
    path.parent.mkdir(parents=True, exist_ok=True)
    known = existing_tweet_ids(path)
    added = 0
    with path.open("a", encoding="utf-8") as fh:
        for row in rows:
            tid = row.get("tweetId") or row.get("tweet_id")
            key = str(tid) if tid else None
            if key and key in known:
                continue
            if key:
                known.add(key)
            out = {
                "id": row.get("id"),
                "tweet_id": tid,
                "source": row.get("source"),
                "post_text": row.get("postText"),
                "has_photo": row.get("hasPhoto"),
                "operator_body": row.get("operatorBody"),
                "created_at": row.get("createdAt"),
            }
            fh.write(json.dumps(out, ensure_ascii=False) + "\n")
            added += 1
    return added


def sync_corpus(vault: Path, examples: list[dict[str, Any]]) -> None:
    by_source: dict[str, list[dict[str, Any]]] = {
        "TIMELINE": [],
        "TIMELINE_POST": [],
        "DELETED_AUTO": [],
    }
    for ex in examples:
        source = ex.get("source") or ""
        if source in by_source:
            by_source[source].append(ex)
    corpus = vault / "corpus"
    append_jsonl(corpus / "gold.jsonl", by_source["TIMELINE"])
    append_jsonl(corpus / "gold-posts.jsonl", by_source["TIMELINE_POST"])
    append_jsonl(corpus / "avoid.jsonl", by_source["DELETED_AUTO"])


def profile_fingerprint(profile: Any) -> str:
    return json.dumps(profile, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def sync_profile(vault: Path, profile: Any, today: str) -> None:
    if profile is None:
        return
    profile_dir = vault / "profile"
    latest = profile_dir / "latest.json"
    new_fp = profile_fingerprint(profile)
    old: Any = None
    if latest.is_file():
        old = load_json(latest, None)
    if old is not None and profile_fingerprint(old) == new_fp:
        return
    write_json(latest, profile)
    write_json(profile_dir / f"{today}.json", profile)
    changelog = profile_dir / "CHANGELOG.md"
    changelog.parent.mkdir(parents=True, exist_ok=True)
    with changelog.open("a", encoding="utf-8") as fh:
        fh.write(f"{today}: profile snapshot updated\n")


def append_evals(vault: Path, evals: list[dict[str, Any]]) -> None:
    path = vault / "eval" / "scores.jsonl"
    if not evals:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.touch()
        return
    known_ids: set[str] = set()
    if path.is_file():
        with path.open(encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    row = json.loads(line)
                except json.JSONDecodeError:
                    continue
                eid = row.get("id")
                if eid is not None:
                    known_ids.add(str(eid))
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as fh:
        for row in evals:
            eid = row.get("id")
            if eid is not None and str(eid) in known_ids:
                continue
            fh.write(json.dumps(row, ensure_ascii=False) + "\n")


def iso_week_key(now: datetime) -> tuple[int, int, str]:
    iso = now.isocalendar()
    return iso.year, iso.week, f"{iso.year:04d}-W{iso.week:02d}"


def regenerate_week_report(vault: Path, now: datetime) -> None:
    year, week, label = iso_week_key(now)
    scores_path = vault / "eval" / "scores.jsonl"
    rows: list[dict[str, Any]] = []
    if scores_path.is_file():
        with scores_path.open(encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    row = json.loads(line)
                except json.JSONDecodeError:
                    continue
                created = row.get("createdAt") or row.get("created_at") or ""
                dt = parse_instant(created)
                if dt is None:
                    continue
                iso = dt.astimezone(timezone.utc).isocalendar()
                if iso.year == year and iso.week == week:
                    rows.append(row)
    report = vault / "eval" / f"report-{label}.md"
    report.parent.mkdir(parents=True, exist_ok=True)
    overalls: list[float] = []
    for row in rows:
        val = row.get("scoreOverall") or row.get("score_overall")
        if isinstance(val, (int, float)):
            overalls.append(float(val))
    avg = sum(overalls) / len(overalls) if overalls else None
    lines = [
        f"# Persona eval {label}",
        "",
        f"- generatedAt: {now.astimezone(timezone.utc).isoformat()}",
        f"- n: {len(rows)}",
        f"- overall avg: {avg if avg is not None else 'n/a'}",
        "",
        "Judge model / prompt version are stored on each `eval/scores.jsonl` line when present.",
        "",
    ]
    report.write_text("\n".join(lines), encoding="utf-8")


def parse_instant(raw: str) -> datetime | None:
    if not raw:
        return None
    text = raw.replace("Z", "+00:00")
    try:
        return datetime.fromisoformat(text)
    except ValueError:
        return None


def max_id(rows: list[dict[str, Any]], current: int) -> int:
    best = current
    for row in rows:
        val = row.get("id")
        if isinstance(val, int) and val > best:
            best = val
        elif isinstance(val, str) and val.isdigit() and int(val) > best:
            best = int(val)
    return best


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Sync persona vault from internal export API")
    parser.add_argument("--env", choices=("dev", "prod"), required=True)
    args = parser.parse_args(argv)

    if hasattr(os, "geteuid") and os.geteuid() == 0:
        die("refuse to write vault as root (ownership pollution)")

    root = repo_root()
    vault = vault_dir(root)
    vault.mkdir(parents=True, exist_ok=True)
    (vault / "corpus").mkdir(exist_ok=True)
    (vault / "profile").mkdir(exist_ok=True)
    (vault / "eval").mkdir(exist_ok=True)

    state_path = vault / ".sync-state.json"
    state = load_json(state_path, {"lastExampleId": 0, "lastEvalId": 0})
    since_ex = int(state.get("lastExampleId") or 0)
    since_ev = int(state.get("lastEvalId") or 0)

    token = read_token(args.env)
    payload = fetch_export(PORTS[args.env], token, since_ex, since_ev)

    examples = payload.get("examples") or []
    evals = payload.get("evals") or []
    sync_corpus(vault, examples)
    today = datetime.now().astimezone().date().isoformat()
    sync_profile(vault, payload.get("profile"), today)
    append_evals(vault, evals)
    now = datetime.now(timezone.utc)
    regenerate_week_report(vault, now)

    state["lastExampleId"] = max_id(examples, since_ex)
    state["lastEvalId"] = max_id(evals, since_ev)
    write_json(state_path, state)
    print(
        f"ok env={args.env} examples={len(examples)} evals={len(evals)} "
        f"lastExampleId={state['lastExampleId']} lastEvalId={state['lastEvalId']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
