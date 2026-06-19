#!/usr/bin/env python3
"""
probe_dev_ai_user_stack.py - read-only external probe for dev AI-user stack

Purpose:
1. Verify dev backend is reachable
2. Verify admin login works
3. Verify read-only admin AI-user endpoints respond

This script intentionally avoids write actions.
It does not prove strict runtime /generate/post availability.
"""

import argparse
import json
import sys
import urllib.request
from datetime import datetime, timezone


def http_json(method, url, data=None, headers=None, timeout=20):
    body = json.dumps(data).encode() if data is not None else None
    req = urllib.request.Request(url, data=body, method=method)
    for key, value in (headers or {}).items():
        req.add_header(key, value)
    with urllib.request.urlopen(req, timeout=timeout) as response:
        return response.status, json.loads(response.read().decode())


def iso_now():
    return datetime.now(timezone.utc).isoformat()


def main():
    parser = argparse.ArgumentParser(description="Read-only probe for dev backend/admin AI-user surfaces")
    parser.add_argument("--base-url", default="http://100.81.189.92:8090")
    parser.add_argument("--email", default="test1@again.com")
    parser.add_argument("--password", default="test123")
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")
    ua = {"User-Agent": "Mozilla/5.0", "Content-Type": "application/json"}

    report = {
        "checked_at": iso_now(),
        "base_url": base_url,
        "status": "OK",
        "checks": [],
        "limitations": [
            "read-only probe only",
            "does not call /generate/post",
            "does not prove runtime h2h path availability",
        ],
    }

    try:
        status, data = http_json("GET", base_url + "/api/health", headers={"User-Agent": "Mozilla/5.0"})
        report["checks"].append({"name": "backend_health", "status": status, "body": data})
    except Exception as exc:  # noqa: BLE001
        report["status"] = "HALT"
        report["checks"].append({"name": "backend_health", "error": str(exc)})
        print(json.dumps(report, ensure_ascii=False))
        sys.exit(2)

    try:
        _, login = http_json(
            "POST",
            base_url + "/api/auth/login",
            data={"email": args.email, "password": args.password},
            headers=ua,
        )
    except Exception as exc:  # noqa: BLE001
        report["status"] = "HALT"
        report["checks"].append({"name": "admin_login", "error": str(exc)})
        print(json.dumps(report, ensure_ascii=False))
        sys.exit(3)

    token = login["token"]["accessToken"]
    auth = {"Authorization": f"Bearer {token}", "User-Agent": "Mozilla/5.0"}
    report["checks"].append({
        "name": "admin_login",
        "roles": login.get("user", {}).get("roles", []),
    })

    for path, name in [
        ("/api/admin/health/system", "system_health"),
        ("/api/admin/ai-user/generation-config", "generation_config"),
        ("/api/admin/ai-user/generation-status", "generation_status"),
        ("/api/admin/ai-rules/prompts/voice/post", "prompt_fetch"),
    ]:
        status, data = http_json("GET", base_url + path, headers=auth)
        record = {"name": name, "status": status}
        if name == "prompt_fetch":
            record["content_len"] = len(data.get("content", ""))
        else:
            record["body"] = data
        report["checks"].append(record)

    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
