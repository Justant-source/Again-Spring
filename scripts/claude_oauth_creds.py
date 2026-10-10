#!/usr/bin/env python3
"""Claude Code oauth 자격증명 병합 (AS ↔ WSL).

SSH `python3 -c "멀티라인"` 은 원격 셸이 인자를 잘라 먹는다.
이 파일은 로컬/원격 모두 `python3 claude_oauth_creds.py <cmd>` 로만 실행한다.

stdin/stdout 의 JSON 에는 토큰이 들어 있다. 로그에 그대로 찍지 말 것.
"""
from __future__ import annotations

import json
import os
import shutil
import sys
import tempfile
import time

# Claude CLI expiresAt 은 epoch milliseconds
MIN_EXPIRES_AT_MS = 10**12


def creds_path() -> str:
    return os.path.expanduser("~/.claude/.credentials.json")


def _die(msg: str, code: int = 1) -> None:
    sys.stderr.write(msg.rstrip() + "\n")
    raise SystemExit(code)


def load_creds(path: str | None = None) -> dict:
    path = path or creds_path()
    if not os.path.isfile(path):
        return {}
    with open(path, encoding="utf-8") as f:
        raw = f.read()
    if not raw.strip():
        _die(f"credentials empty: {path}", 4)
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as e:
        _die(f"credentials JSON 파싱 실패: {path}: {e}", 4)
    if not isinstance(data, dict):
        _die(f"credentials 최상위는 object 여야 함: {path}", 4)
    return data


def oauth_expires_at(oauth: dict | None) -> int:
    if not oauth:
        return 0
    try:
        return int(oauth.get("expiresAt") or 0)
    except (TypeError, ValueError):
        return 0


def validate_oauth(oauth: dict, label: str = "oauth") -> None:
    if not isinstance(oauth, dict):
        _die(f"{label}: object 아님", 3)
    exp = oauth_expires_at(oauth)
    if exp < MIN_EXPIRES_AT_MS:
        _die(
            f"{label}: expiresAt 무효 ({exp}). "
            "만료·손상 토큰은 병합하지 않는다",
            5,
        )
    if not oauth.get("accessToken") or not oauth.get("refreshToken"):
        _die(f"{label}: accessToken/refreshToken 없음", 5)


def atomic_write_json(path: str, data: dict) -> None:
    directory = os.path.dirname(path) or "."
    os.makedirs(directory, exist_ok=True)
    fd, tmp = tempfile.mkstemp(prefix=".credentials.", suffix=".tmp", dir=directory)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
            f.write("\n")
            f.flush()
            os.fsync(f.fileno())
        os.chmod(tmp, 0o600)
        os.replace(tmp, path)
    except Exception:
        try:
            os.unlink(tmp)
        except OSError:
            pass
        raise


def cmd_expires() -> None:
    data = load_creds()
    print(oauth_expires_at(data.get("claudeAiOauth")))


def cmd_extract() -> None:
    data = load_creds()
    if "claudeAiOauth" not in data:
        _die("claudeAiOauth missing", 3)
    oauth = data["claudeAiOauth"]
    validate_oauth(oauth, "local claudeAiOauth")
    json.dump({"claudeAiOauth": oauth}, sys.stdout, separators=(",", ":"))
    sys.stdout.write("\n")


def cmd_merge() -> None:
    raw = sys.stdin.read()
    if not raw.strip():
        _die("incoming oauth 없음 (stdin empty)", 1)
    try:
        incoming = json.loads(raw)
    except json.JSONDecodeError as e:
        _die(f"incoming JSON 파싱 실패: {e}", 1)
    if "claudeAiOauth" not in incoming:
        _die("incoming oauth 없음", 1)
    oauth = incoming["claudeAiOauth"]
    validate_oauth(oauth, "incoming claudeAiOauth")

    path = creds_path()
    current: dict = {}
    if os.path.exists(path):
        backup = path + ".bak-" + time.strftime("%Y%m%d-%H%M%S")
        shutil.copy2(path, backup)
        print("backup :", backup)
        current = load_creds(path)

    before = current.get("claudeAiOauth") or {}
    current["claudeAiOauth"] = oauth
    atomic_write_json(path, current)
    after = current["claudeAiOauth"]
    print("written:", path, "(mode 600)")
    print(
        "  subscriptionType:",
        before.get("subscriptionType"),
        "->",
        after.get("subscriptionType"),
    )
    print("  expiresAt:", oauth_expires_at(before), "->", oauth_expires_at(after))


def main(argv: list[str] | None = None) -> None:
    args = list(sys.argv[1:] if argv is None else argv)
    if not args:
        _die("usage: claude_oauth_creds.py extract|merge|expires", 2)
    cmd = args[0]
    if cmd == "extract":
        cmd_extract()
    elif cmd == "merge":
        cmd_merge()
    elif cmd == "expires":
        cmd_expires()
    else:
        _die(f"unknown command: {cmd}", 2)


if __name__ == "__main__":
    main()
