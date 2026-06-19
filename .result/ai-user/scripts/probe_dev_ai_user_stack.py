#!/usr/bin/env python3
"""
probe_dev_ai_user_stack.py - external probe for dev AI-user stack

What it verifies from this shell:
1. dev backend is reachable
2. admin login works
3. backend -> orchestrator proxy works
4. backend -> llm-ai-user prompt reload path works

This does NOT replace strict runtime h2h measurement.
It is only an external reachability probe for the live dev stack.
"""

import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone


def http_json(method, url, data=None, headers=None, timeout=20):
    body = json.dumps(data).encode() if data is not None else None
    req = urllib.request.Request(url, data=body, method=method)
    for key, value in (headers or {}).items():
        req.add_header(key, value)
    with urllib.request.urlopen(req, timeout=timeout) as response:
        raw = response.read().decode()
        return response.status, json.loads(raw)


def iso_now():
    return datetime.now(timezone.utc).isoformat()


def main():
    parser = argparse.ArgumentParser(description="Probe dev backend/orchestrator/llm-ai-user from external shell")
    parser.add_argument("--base-url", default="http://100.81.189.92:8090")
    parser.add_argument("--email", default="test1@again.com")
    parser.add_argument("--password", default="test123")
    parser.add_argument("--probe-orchestrator", action="store_true",
                        help="Call /api/admin/ai-user/reset-counter and backfill-comment-likes")
    parser.add_argument("--log-limit", type=int, default=50)
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")
    ua = {"User-Agent": "Mozilla/5.0", "Content-Type": "application/json"}

    report = {
        "checked_at": iso_now(),
        "base_url": base_url,
        "status": "OK",
        "checks": [],
    }

    try:
        status, data = http_json("GET", base_url + "/api/health", headers={"User-Agent": "Mozilla/5.0"})
        report["checks"].append({
            "name": "backend_health",
            "status": status,
            "body": data,
        })
    except Exception as exc:  # noqa: BLE001
        report["status"] = "HALT"
        report["checks"].append({
            "name": "backend_health",
            "error": str(exc),
        })
        print(json.dumps(report, ensure_ascii=False))
        sys.exit(2)

    try:
        _, login = http_json(
            "POST",
            base_url + "/api/auth/login",
            data={"email": args.email, "password": args.password},
            headers=ua,
        )
        token = login["token"]["accessToken"]
        auth = {"Authorization": f"Bearer {token}", "User-Agent": "Mozilla/5.0"}
        report["checks"].append({
            "name": "admin_login",
            "roles": login.get("user", {}).get("roles", []),
        })
    except Exception as exc:  # noqa: BLE001
        report["status"] = "HALT"
        report["checks"].append({
            "name": "admin_login",
            "error": str(exc),
        })
        print(json.dumps(report, ensure_ascii=False))
        sys.exit(3)

    for path, name in [
        ("/api/admin/health/system", "system_health"),
        ("/api/admin/ai-user/generation-config", "generation_config"),
        ("/api/admin/ai-user/generation-status", "generation_status"),
    ]:
        status, data = http_json("GET", base_url + path, headers=auth)
        report["checks"].append({
            "name": name,
            "status": status,
            "body": data,
        })

    prompt_status, prompt = http_json(
        "GET",
        base_url + "/api/admin/ai-rules/prompts/voice/post",
        headers=auth,
    )
    report["checks"].append({
        "name": "prompt_fetch",
        "status": prompt_status,
        "content_len": len(prompt.get("content", "")),
    })

    probe_started_at = iso_now()
    reload_status, _ = http_json(
        "PUT",
        base_url + "/api/admin/ai-rules/prompts/voice/post",
        data={"content": prompt.get("content", "")},
        headers={**auth, "Content-Type": "application/json"},
    )
    report["checks"].append({
        "name": "llm_reload_noop_put",
        "status": reload_status,
        "probe_started_at": probe_started_at,
    })

    _, warn_logs = http_json(
        "GET",
        base_url + f"/api/admin/system/logs?level=WARN&limit={args.log_limit}",
        headers=auth,
    )
    llm_reload_failed = [
        row for row in warn_logs
        if "llm-ai-user reload failed" in row.get("message", "")
    ]
    report["checks"].append({
        "name": "llm_reload_warn_scan",
        "failed_warn_count": len(llm_reload_failed),
        "assumption": "0 means backend did not log a known llm reload failure after the noop PUT",
    })

    if args.probe_orchestrator:
        reset_status, reset_body = http_json(
            "POST",
            base_url + "/api/admin/ai-user/reset-counter",
            data={},
            headers={**auth, "Content-Type": "application/json"},
        )
        report["checks"].append({
            "name": "orchestrator_reset_counter_proxy",
            "status": reset_status,
            "body": reset_body,
        })

        backfill_status, backfill_body = http_json(
            "POST",
            base_url + "/api/admin/ai-user/backfill-comment-likes?days=1&personasPerPost=1",
            data={},
            headers={**auth, "Content-Type": "application/json"},
        )
        report["checks"].append({
            "name": "orchestrator_backfill_proxy",
            "status": backfill_status,
            "body": backfill_body,
            "note": "side effect: queues low-volume comment-like backfill",
        })

    direct_checks = []
    for path in [
        "/admin/trigger/reset-counter",
        "/admin/trigger/generate-posts?voice=THEQOO&count=1",
    ]:
        req = urllib.request.Request(base_url + path, data=b"", method="POST", headers=auth)
        try:
            with urllib.request.urlopen(req, timeout=20) as response:
                body = response.read().decode(errors="replace")
                direct_checks.append({"path": path, "status": response.status, "body": body[:400]})
        except urllib.error.HTTPError as exc:
            body = exc.read().decode(errors="replace")
            direct_checks.append({"path": path, "status": exc.code, "body": body[:400]})
        except Exception as exc:  # noqa: BLE001
            direct_checks.append({"path": path, "error": str(exc)})
    report["checks"].append({
        "name": "direct_admin_trigger_paths",
        "results": direct_checks,
        "note": "These are currently expected to fail externally; backend proxies are the supported path.",
    })

    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
