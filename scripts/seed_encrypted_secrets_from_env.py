#!/usr/bin/env python3
"""Seed AS MariaDB encrypted_secret from local .env files (one-shot).

Never prints secret values. Requires:
  AS_SECRET_MASTER_KEY  — base64 32-byte AES key
  DB reachable (dev :3307 or docker exec)

Usage:
  AS_SECRET_MASTER_KEY=... \\
  ENV_FILE=env/.env.prod \\
  DB_URL='...' DB_USER=... DB_PASSWORD=... \\
  python3 scripts/seed_encrypted_secrets_from_env.py
"""
from __future__ import annotations

import base64
import os
import sys
from pathlib import Path

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

# vault_key -> env var name(s) to read (first non-empty wins)
ENV_MAP: list[tuple[str, tuple[str, ...]]] = [
    ("jwt.secret", ("JWT_SECRET",)),
    ("oauth.google.client_secret", ("GOOGLE_CLIENT_SECRET",)),
    ("oauth.kakao.client_secret", ("KAKAO_CLIENT_SECRET",)),
    ("oauth.naver.client_secret", ("NAVER_CLIENT_SECRET",)),
    ("mail.gmail_app_password", ("GMAIL_APP_PASSWORD",)),
    ("llm.anthropic_api_key", ("ANTHROPIC_API_KEY",)),
    ("ai_user.bot_password", ("AI_USER_BOT_PASSWORD",)),
    ("sync.dev_mariadb_password", ("DEV_MARIADB_PASSWORD",)),
    ("asm.api_token", ("ASM_API_TOKEN",)),
    ("asm.callback_token", ("ASM_CALLBACK_TOKEN",)),
    ("telegram.bot_token", ("TELEGRAM_BOT_TOKEN",)),
    ("telegram.chat_id", ("TELEGRAM_CHAT_ID",)),
    ("telegram.webhook_secret", ("TELEGRAM_WEBHOOK_SECRET",)),
]


def load_env(path: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    if not path.exists():
        return out
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        out[k.strip()] = v.strip().strip('"').strip("'")
    return out


def encrypt_blob(master_b64: str, plaintext: str) -> str:
    key = base64.b64decode(master_b64)
    if len(key) != 32:
        raise SystemExit(f"AS_SECRET_MASTER_KEY must decode to 32 bytes (got {len(key)})")
    iv = os.urandom(12)
    ct = AESGCM(key).encrypt(iv, plaintext.encode("utf-8"), None)
    return base64.b64encode(iv + ct).decode("ascii")


def parse_git_credentials(path: Path) -> dict[str, str]:
    """Return username -> token from git-credentials lines."""
    out: dict[str, str] = {}
    if not path.exists():
        return out
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or "@" not in line or "://" not in line:
            continue
        left, _host = line.rsplit("@", 1)
        try:
            _proto, rest = left.split("://", 1)
            user, token = rest.split(":", 1)
        except ValueError:
            continue
        if user and token:
            out[user] = token
    return out


def main() -> int:
    master = os.environ.get("AS_SECRET_MASTER_KEY", "").strip()
    if not master:
        print("ERR: AS_SECRET_MASTER_KEY required", file=sys.stderr)
        return 1

    env_file = Path(os.environ.get("ENV_FILE", "env/.env.prod"))
    file_env = load_env(env_file)
    # process env overrides file
    merged = {**file_env, **{k: v for k, v in os.environ.items() if v}}

    pairs: list[tuple[str, str]] = []
    for vault_key, env_names in ENV_MAP:
        val = ""
        for n in env_names:
            val = (merged.get(n) or "").strip()
            if val:
                break
        if val:
            pairs.append((vault_key, val))
            print(f"will_seed {vault_key} len={len(val)}")
        else:
            print(f"skip {vault_key} (empty)")

    # GitHub PATs
    for cred_path in (
        Path.home() / ".git-credentials",
        Path("Again-Spring/.git-credentials") if False else Path(".git-credentials"),
    ):
        for user, token in parse_git_credentials(cred_path).items():
            vk = f"github.pat.{user}"
            pairs.append((vk, token))
            print(f"will_seed {vk} len={len(token)} from {cred_path}")

    if not pairs:
        print("ERR: nothing to seed", file=sys.stderr)
        return 1

    # DB connect
    try:
        import pymysql
    except ImportError:
        print("ERR: pip install pymysql", file=sys.stderr)
        return 1

    host = os.environ.get("DB_HOST", "127.0.0.1")
    port = int(os.environ.get("DB_PORT", "3306"))
    user = os.environ.get("DB_USER") or merged.get("MARIADB_USER") or "againspring"
    password = os.environ.get("DB_PASSWORD") or merged.get("MARIADB_PASSWORD") or ""
    database = os.environ.get("DB_NAME") or merged.get("MARIADB_DATABASE") or "againspring"

    conn = pymysql.connect(
        host=host, port=port, user=user, password=password, database=database, charset="utf8mb4"
    )
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) FROM information_schema.tables "
                "WHERE table_schema=%s AND table_name='encrypted_secret'",
                (database,),
            )
            if cur.fetchone()[0] == 0:
                print("ERR: encrypted_secret missing — run Flyway/backend migrate first", file=sys.stderr)
                return 2
            for vault_key, plain in pairs:
                blob = encrypt_blob(master, plain)
                cur.execute(
                    "INSERT INTO encrypted_secret (secret_key, enc_blob) VALUES (%s, %s) "
                    "ON DUPLICATE KEY UPDATE enc_blob=VALUES(enc_blob)",
                    (vault_key, blob),
                )
        conn.commit()
    finally:
        conn.close()

    print(f"OK seeded {len(pairs)} secrets into {database}.encrypted_secret")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
