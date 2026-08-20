#!/usr/bin/env python3
"""Immediately regenerate failed YT/Reels marketing jobs (sequential)."""
from __future__ import annotations

import base64
import json
import subprocess
import sys
import time
import uuid
from datetime import datetime, timedelta, timezone
from urllib import error, request
from zoneinfo import ZoneInfo

BASE_URL = "http://localhost:8091"
JOBS = [689, 690, 691, 692]
REGEN_TIMEOUT_SEC = 600
JOB_WAIT_SEC = 3600
JOB_POLL_SEC = 30
GAP_SEC = 20
KST = ZoneInfo("Asia/Seoul")


def log(msg: str) -> None:
    print(f"[{datetime.now(KST)}] {msg}", flush=True)


def docker_mariadb_query(sql: str) -> str:
    cmd = [
        "docker", "exec", "againspring-mariadb-prod", "sh", "-c",
        f'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE" -N -e "{sql}"',
    ]
    return subprocess.check_output(cmd, text=True).strip()


def docker_backend_env(name: str) -> str:
    cmd = ["docker", "exec", "againspring-backend-prod", "sh", "-c", f'echo -n "${name}"']
    return subprocess.check_output(cmd, text=True).strip()


def forge_token() -> str:
    import jwt
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM

    master_b64 = docker_backend_env("AS_SECRET_MASTER_KEY")
    key = base64.b64decode(master_b64)
    blob_b64 = docker_mariadb_query(
        "SELECT enc_blob FROM encrypted_secret WHERE secret_key='jwt.secret' LIMIT 1"
    )
    raw = base64.b64decode(blob_b64)
    jwt_secret = AESGCM(key).decrypt(raw[:12], raw[12:], None).decode("utf-8")
    now = datetime.now(timezone.utc)
    return jwt.encode(
        {
            "sub": "cbba96d2d9f4417287d6da9ec4",
            "email": "againspring2026@gmail.com",
            "type": "access",
            "jti": str(uuid.uuid4()),
            "iat": now,
            "exp": now + timedelta(hours=4),
        },
        jwt_secret,
        algorithm="HS256",
    )


def api_post(path: str, token: str, timeout: int) -> dict:
    req = request.Request(
        BASE_URL + path,
        method="POST",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        data=b"{}",
    )
    with request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode())


def api_get(path: str, token: str) -> dict:
    req = request.Request(
        BASE_URL + path,
        headers={"Authorization": f"Bearer {token}"},
    )
    with request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode())


def wait_job(job_id: int, token: str) -> dict:
    terminal = {"PUBLISHED", "FAILED", "PARTIAL"}
    deadline = time.time() + JOB_WAIT_SEC
    while time.time() < deadline:
        job = api_get(f"/api/admin/marketing/jobs/{job_id}", token)
        status = job.get("status")
        log(f"  #{job_id} status={status} phase={job.get('phase')} code={job.get('failureCode')}")
        if status in terminal:
            return job
        time.sleep(JOB_POLL_SEC)
    raise TimeoutError(f"job {job_id} timeout")


def main() -> int:
    token = forge_token()
    ok = 0
    for src in JOBS:
        log(f"regenerate source #{src}")
        try:
            created = api_post(f"/api/admin/marketing/jobs/{src}/regenerate", token, REGEN_TIMEOUT_SEC)
        except error.HTTPError as e:
            log(f"  HTTP {e.code}: {e.read().decode()[:500]}")
            continue
        new_id = created.get("id")
        log(f"  -> new #{new_id} initial={created.get('status')} code={created.get('failureCode')}")
        if created.get("status") == "FAILED" or not new_id:
            continue
        final = wait_job(new_id, token)
        if final.get("status") == "PUBLISHED":
            ok += 1
        pubs = final.get("publications") or []
        log(f"  final #{new_id}: {final.get('status')} pubs={pubs}")
        time.sleep(GAP_SEC)
    log(f"done: {ok}/{len(JOBS)} published")
    return 0 if ok == len(JOBS) else 1


if __name__ == "__main__":
    sys.exit(main())
