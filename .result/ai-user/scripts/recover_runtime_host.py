#!/usr/bin/env python3
"""
recover_runtime_host.py - dev host helper for R14 runtime recovery

Purpose:
1. Start the llm-ai-user runtime on a host that actually has docker
2. Poll :8092 health until it becomes UP
3. Fail fast with machine-readable output when recovery is blocked

Usage:
    python3 .result/ai-user/scripts/recover_runtime_host.py
    python3 .result/ai-user/scripts/recover_runtime_host.py --skip-up
"""

import argparse
import json
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path


DEFAULT_REPO_ROOT = Path("/home/justant/Data/Again-Spring")
DEFAULT_COMPOSE_FILE = Path("env/docker-compose.dev.yml")
DEFAULT_ENV_FILE = Path("env/.env.dev")
DEFAULT_SERVICE = "llm-ai-user"
DEFAULT_HEALTH_URL = "http://localhost:8092/actuator/health"


def run_command(cmd, cwd):
    proc = subprocess.run(
        cmd,
        cwd=str(cwd),
        text=True,
        capture_output=True,
        check=False,
    )
    return {
        "args": cmd,
        "returncode": proc.returncode,
        "stdout": proc.stdout.strip(),
        "stderr": proc.stderr.strip(),
    }


def fetch_health(url):
    with urllib.request.urlopen(url, timeout=5) as response:
        return json.loads(response.read().decode())


def poll_health(url, deadline_epoch):
    last_error = None
    while time.time() < deadline_epoch:
        try:
            data = fetch_health(url)
            if data.get("status") == "UP":
                return {"ok": True, "health": data, "last_error": last_error}
            last_error = data
        except Exception as exc:  # noqa: BLE001
            last_error = str(exc)
        time.sleep(2)
    return {"ok": False, "health": None, "last_error": last_error}


def main():
    parser = argparse.ArgumentParser(description="Bring up llm-ai-user and wait for :8092 UP")
    parser.add_argument("--repo-root", default=str(DEFAULT_REPO_ROOT))
    parser.add_argument("--compose-file", default=str(DEFAULT_COMPOSE_FILE))
    parser.add_argument("--env-file", default=str(DEFAULT_ENV_FILE))
    parser.add_argument("--service", default=DEFAULT_SERVICE)
    parser.add_argument("--health-url", default=DEFAULT_HEALTH_URL)
    parser.add_argument("--timeout-sec", type=int, default=90)
    parser.add_argument("--skip-up", action="store_true", help="Only poll health; do not run docker compose up")
    args = parser.parse_args()

    repo_root = Path(args.repo_root).resolve()
    compose_file = (repo_root / args.compose_file).resolve()
    env_file = (repo_root / args.env_file).resolve()

    if shutil.which("docker") is None:
        print(json.dumps({
            "status": "HALT",
            "reason": "docker_missing",
            "repo_root": str(repo_root),
        }, ensure_ascii=False))
        sys.exit(2)

    if not compose_file.exists():
        print(json.dumps({
            "status": "HALT",
            "reason": "compose_file_missing",
            "compose_file": str(compose_file),
        }, ensure_ascii=False))
        sys.exit(3)

    if not env_file.exists():
        print(json.dumps({
            "status": "HALT",
            "reason": "env_file_missing",
            "env_file": str(env_file),
        }, ensure_ascii=False))
        sys.exit(4)

    up_result = None
    if not args.skip_up:
        up_result = run_command(
            [
                "docker",
                "compose",
                "-f",
                str(compose_file),
                "--env-file",
                str(env_file),
                "up",
                "-d",
                args.service,
            ],
            cwd=repo_root,
        )
        if up_result["returncode"] != 0:
            print(json.dumps({
                "status": "HALT",
                "reason": "docker_compose_failed",
                "up_result": up_result,
            }, ensure_ascii=False))
            sys.exit(5)

    health_result = poll_health(args.health_url, time.time() + args.timeout_sec)
    if not health_result["ok"]:
        print(json.dumps({
            "status": "HALT",
            "reason": "runtime_not_up",
            "health_url": args.health_url,
            "last_error": health_result["last_error"],
            "up_result": up_result,
        }, ensure_ascii=False))
        sys.exit(6)

    print(json.dumps({
        "status": "OK",
        "health_url": args.health_url,
        "health": health_result["health"],
        "up_result": up_result,
        "next": [
            "python3 .result/ai-user/scripts/probe_runtime_pipeline.py --community THEQOO --strict-runtime",
            "python3 .result/ai-user/scripts/probe_runtime_pipeline.py --community CLIEN --strict-runtime",
            "python3 .result/ai-user/scripts/probe_runtime_pipeline.py --community NATEPAN --strict-runtime",
        ],
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
