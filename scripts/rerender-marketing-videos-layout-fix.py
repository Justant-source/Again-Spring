#!/usr/bin/env python3
"""Regenerate Shorts/Reels jobs after layout/outro fix (sequential)."""
from __future__ import annotations

import json
import subprocess
import sys
import time
from datetime import datetime, timedelta, timezone
from urllib import error, request
from zoneinfo import ZoneInfo

# Parent jobs to fix (2026-08-17 publish batch)
JOBS = [703, 704, 705, 706]
BASE_URL = "http://localhost:8091"
REGEN_TIMEOUT_SEC = 600
CREATE_TIMEOUT_SEC = 600
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


def forge_token() -> str:
    import base64
    import uuid

    import jwt
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM

    master_b64 = subprocess.check_output(
        ["docker", "exec", "againspring-backend-prod", "sh", "-c", 'echo -n "$AS_SECRET_MASTER_KEY"'],
        text=True,
    ).strip()
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


def api_post_json(path: str, token: str, body: dict, timeout: int) -> dict:
    req = request.Request(
        BASE_URL + path,
        method="POST",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        data=json.dumps(body).encode(),
    )
    with request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode())


def api_post(path: str, token: str, timeout: int) -> dict:
    return api_post_json(path, token, {}, timeout)


def create_replacement_job(source: dict, token: str) -> dict:
    status = source.get("status")
    if status == "FAILED":
        try:
            return api_post(f"/api/admin/marketing/jobs/{source['id']}/regenerate", token, REGEN_TIMEOUT_SEC)
        except error.HTTPError as exc:
            if exc.code != 409:
                raise
            log(f"  regenerate 409 for #{source['id']} — falling back to createJob")
    if status not in ("PUBLISHED", "FAILED", "PARTIAL", "READY"):
        raise RuntimeError(f"source #{source['id']} status={status} — cannot replace")
    body = {
        "postId": source["postId"],
        "targets": source.get("targets") or [],
        "autoPublish": bool(source.get("autoPublish", True)),
    }
    if not body["targets"]:
        raise RuntimeError(f"source #{source['id']} has no targets")
    return api_post_json("/api/admin/marketing/jobs", token, body, CREATE_TIMEOUT_SEC)


def api_get(path: str, token: str) -> dict:
    req = request.Request(
        BASE_URL + path,
        headers={"Authorization": f"Bearer {token}"},
    )
    with request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode())


def wait_job(job_id: int, token: str) -> dict:
    deadline = time.time() + JOB_WAIT_SEC
    while time.time() < deadline:
        job = api_get(f"/api/admin/marketing/jobs/{job_id}", token)
        status = job.get("status")
        log(f"  job #{job_id} status={status} failure={job.get('failureCode')}")
        if status in ("PUBLISHED", "FAILED", "PARTIAL"):
            return job
        time.sleep(JOB_POLL_SEC)
    raise TimeoutError(f"job #{job_id} timed out")


def main() -> int:
    log("forge admin token")
    token = forge_token()
    for source_id in JOBS:
        log(f"replace source #{source_id}")
        source = api_get(f"/api/admin/marketing/jobs/{source_id}", token)
        child = create_replacement_job(source, token)
        child_id = child.get("id")
        log(f"  new job #{child_id} (source #{source_id}, status was {source.get('status')})")
        result = wait_job(child_id, token)
        pubs = result.get("publications") or []
        for p in pubs:
            log(f"  published {p.get('platform')}: {p.get('url')}")
        time.sleep(GAP_SEC)
    log("done")
    return 0


if __name__ == "__main__":
    sys.exit(main())
