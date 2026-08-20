#!/usr/bin/env python3
"""Wait until Claude session-limit reset, then sequentially regenerate failed YT/Reels jobs."""
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
# Latest failed reels/shorts for the two posts (2026-08-17)
JOBS = [689, 690, 691, 692]
NOT_BEFORE_KST = datetime(2026, 8, 17, 21, 40, 0, tzinfo=ZoneInfo("Asia/Seoul"))
LLM_POLL_SEC = 60
REGEN_TIMEOUT_SEC = 600
JOB_WAIT_SEC = 3600
JOB_POLL_SEC = 30
GAP_BETWEEN_JOBS_SEC = 20


def log(msg: str) -> None:
    print(f"[{datetime.now(KST)}] {msg}", flush=True)


KST = ZoneInfo("Asia/Seoul")


def sh(cmd: str) -> str:
    return subprocess.check_output(cmd, shell=True, text=True).strip()


def forge_token() -> str:
    import jwt
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM

    master_b64 = sh('docker exec againspring-backend-prod sh -c \'echo -n "$AS_SECRET_MASTER_KEY"\'')
    key = base64.b64decode(master_b64)
    blob_b64 = sh(
        "docker exec againspring-mariadb-prod sh -c "
        "'mariadb -uroot -p\"$MARIADB_ROOT_PASSWORD\" \"$MARIADB_DATABASE\" -N -e "
        "\"SELECT enc_blob FROM encrypted_secret WHERE secret_key=\\'jwt.secret\\' LIMIT 1;\"'"
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


def llm_blocked() -> bool:
    payload = json.dumps(
        {"prompt": "Reply exactly: OK", "model": "claude-haiku-4-5-20251001", "timeoutMs": 60000}
    ).encode()
    open("/tmp/llm_probe.json", "wb").write(payload)
    subprocess.run(
        ["docker", "cp", "/tmp/llm_probe.json", "againspring-backend-prod:/tmp/llm_probe.json"],
        check=True,
    )
    out = sh(
        'docker exec againspring-backend-prod sh -c '
        '\'wget -qO- --header="Content-Type: application/json" --post-file=/tmp/llm_probe.json '
        'http://againspring-llm:8090/v1/invoke\''
    )
    data = json.loads(out)
    text = (data.get("text") or "").lower()
    blocked = "session limit" in text or "credit balance" in text
    if blocked:
        log(f"LLM blocked: {data.get('text', '')[:80]}")
    else:
        log(f"LLM OK: {(data.get('text') or '')[:40]}")
    return blocked


def wait_until_not_before() -> None:
    now = datetime.now(KST)
    if now < NOT_BEFORE_KST:
        secs = (NOT_BEFORE_KST - now).total_seconds()
        log(f"sleeping {int(secs)}s until {NOT_BEFORE_KST.strftime('%H:%M')} KST …")
        time.sleep(secs)
    log("not-before window reached")


def wait_llm_ready() -> None:
    log("polling LLM until session limit clears …")
    while llm_blocked():
        time.sleep(LLM_POLL_SEC)
    log("LLM ready")


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
        log(f"  job {job_id} status={status} phase={job.get('phase')} code={job.get('failureCode')}")
        if status in terminal:
            return job
        time.sleep(JOB_POLL_SEC)
    raise TimeoutError(f"job {job_id} not terminal after {JOB_WAIT_SEC}s")


def regenerate_one(source_job_id: int, token: str) -> dict | None:
    log(f"regenerate source job {source_job_id}")
    try:
        created = api_post(
            f"/api/admin/marketing/jobs/{source_job_id}/regenerate",
            token,
            REGEN_TIMEOUT_SEC,
        )
    except error.HTTPError as e:
        log(f"  HTTP {e.code}: {e.read().decode()[:400]}")
        return None
    except Exception as e:
        log(f"  error: {e}")
        return None

    new_id = created.get("id")
    log(f"  -> new job {new_id} initial={created.get('status')} code={created.get('failureCode')}")
    if not new_id:
        return created
    if created.get("status") == "FAILED":
        return created
    return wait_job(new_id, token)


def main() -> int:
    log(f"scheduled run — target jobs {JOBS}")
    wait_until_not_before()
    wait_llm_ready()

    token = forge_token()
    results: list[tuple[int, str, str | None]] = []

    for job_id in JOBS:
        final = regenerate_one(job_id, token)
        if final is None:
            results.append((job_id, "ERROR", None))
        else:
            results.append((job_id, final.get("status") or "?", final.get("failureCode")))
            if final.get("status") != "PUBLISHED":
                log(f"  WARN job chain from {job_id} ended {final.get('status')} code={final.get('failureCode')}")
        time.sleep(GAP_BETWEEN_JOBS_SEC)

    log("=== summary ===")
    for src, status, code in results:
        log(f"  source #{src} -> {status} {code or ''}")
    failed = [r for r in results if r[1] != "PUBLISHED"]
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
