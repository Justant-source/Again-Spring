#!/usr/bin/env python3
"""
trigger_conflict_train.py — Step 91: trigger ML retrain (GPU) after conflict corpus ingest
Polls until COMPLETED or FAILED. No LLM calls.

Usage:
    python3 trigger_conflict_train.py THEQOO
    python3 trigger_conflict_train.py NATEPAN
"""

from __future__ import annotations

import json
import sys
import time
import urllib.request

ML_API = "http://100.115.252.61:8201"
AUTH = "Bearer aiuser-ml-api-token-dev-2026"
IDEMPOTENCY_KEY_PREFIX = "step91-conflict-retrain-2026-06-20"


def trigger_train(community: str, key: str) -> dict:
    payload = json.dumps({"community": community, "idempotencyKey": key}).encode("utf-8")
    req = urllib.request.Request(
        f"{ML_API}/train",
        data=payload,
        headers={"Authorization": AUTH, "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read())


def poll_job(job_id: str, timeout: int = 900) -> dict:
    deadline = time.time() + timeout
    last_status = ""
    while time.time() < deadline:
        req = urllib.request.Request(
            f"{ML_API}/train/{job_id}",
            headers={"Authorization": AUTH},
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            result = json.loads(resp.read())
        status = result.get("status", "UNKNOWN")
        if status != last_status:
            elapsed = int(time.time() - (deadline - timeout))
            print(f"  [{elapsed:3d}s] job={job_id[:12]}... status={status}")
            last_status = status
        if status in ("COMPLETED", "FAILED", "SKIPPED"):
            return result
        time.sleep(8)
    return {"status": "TIMEOUT", "job_id": job_id}


def main(community: str) -> None:
    community = community.upper()
    key = f"{IDEMPOTENCY_KEY_PREFIX}-{community.lower()}"
    print(f"\n=== Step 91: trigger {community} GPU retrain ===")
    print(f"Idempotency key: {key}")

    result = trigger_train(community, key)
    print(f"Trigger response: {json.dumps(result, ensure_ascii=False)}")

    job_id = result.get("job_id") or result.get("jobId")
    if not job_id:
        # Might already be queued/running from a previous call (idempotency)
        print("No new job_id returned (possibly already queued or running)")
        return

    print(f"Polling job {job_id}...")
    final = poll_job(job_id, timeout=900)

    print(f"\nFinal result: {json.dumps(final, ensure_ascii=False)}")
    auc = final.get("auc") or (final.get("result", {}) or {}).get("auc")
    n_train = final.get("n_train") or (final.get("result", {}) or {}).get("n_train")
    n_val = final.get("n_val") or (final.get("result", {}) or {}).get("n_val")
    if auc:
        print(f"\n✅ {community} retrain complete: AUC={auc:.4f}, n_train={n_train}, n_val={n_val}")
    elif final.get("status") == "SKIPPED":
        skip_reason = final.get("skip_reason", "unknown")
        print(f"\n⚠️ Training SKIPPED: {skip_reason}")
    elif final.get("status") == "FAILED":
        print(f"\n❌ Training FAILED: {final.get('error', 'unknown error')}")


if __name__ == "__main__":
    community = sys.argv[1] if len(sys.argv) > 1 else "THEQOO"
    main(community)
