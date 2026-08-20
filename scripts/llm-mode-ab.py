#!/usr/bin/env python3
"""
LLM mode A/B validation harness.

Triggers N structured-generation runs on dev orchestrator, harvests LLM stats
from logs, counts failure signals, and reports per-run metrics (token usage,
duration, success rate).

Endpoints:
  - scheduled (default): /admin/trigger/generate-scheduled-posts (PLAN mode, holds posts in future slots)
  - legacy: /admin/trigger/generate-posts (ActionExecutor path, immediate generation)
  - direct: POST /v2/generate/thread-plan directly to LLM worker (structured code path, no DB side effects)

Side effects:
  - scheduled mode: Creates AiScheduledPost rows in DB (scheduled for future times)
  - legacy mode: Creates Post rows immediately
  - direct mode: No DB writes, pure service-layer validation

Usage:
  ./scripts/llm-mode-ab.py --runs 5 --gap 20 --label schema-mode
  ./scripts/llm-mode-ab.py --runs 5 --endpoint legacy --label legacy-baseline
  ./scripts/llm-mode-ab.py --runs 3 --endpoint direct --label schema-mode-direct
  ./scripts/llm-mode-ab.py --compare before.json after.json
"""

import subprocess
import json
import re
import sys
import time
import os
import requests
from datetime import datetime, timedelta, timezone
from pathlib import Path
from statistics import mean, median, StatisticsError
from uuid import uuid4


def run_shell(cmd, check=False):
    """Run shell command and return stdout (str). On error, return None if not check."""
    try:
        result = subprocess.run(
            cmd, shell=True, capture_output=True, text=True, timeout=600
        )
        if result.returncode != 0 and check:
            print(f"ERROR: Command failed: {cmd}", file=sys.stderr)
            print(f"stderr: {result.stderr}", file=sys.stderr)
            return None
        return result.stdout
    except subprocess.TimeoutExpired:
        print(f"ERROR: Timeout running: {cmd}", file=sys.stderr)
        return None
    except Exception as e:
        print(f"ERROR: {e}", file=sys.stderr)
        return None


def container_exists(name):
    """Check if a container exists."""
    result = subprocess.run(
        f"docker ps -a --filter name={name} --format '{{.ID}}'",
        shell=True,
        capture_output=True,
        text=True,
    )
    return bool(result.stdout.strip())


def build_thread_plan_payload():
    """
    Build a minimal, realistic ThreadPlanRequest payload.
    Based on test fixtures from StructuredGenerationServiceTest.java.
    Returns JSON-serializable dict.
    """
    payload = {
        "kind": "AI_POST",
        "provider": "CLAUDE",  # Use CLI provider (model selection is automatic)
        "correlationId": f"test-{uuid4().hex[:8]}",
        "timeoutMs": 60000,
        "category": "conflict",
        "topicHint": "relationship",
        # Minimal source context
        "sourceContext": {
            "source": "test",
            "register": "neutral"
        },
        # Minimal cast: 3 personas for comments (author removed from comments)
        "personas": [
            {
                "personaId": "p1",
                "nickname": "작성자",
                "formality": "casual",
                "voiceProfile": {
                    "formality": "casual",
                    "voice_type": "NATEPAN"
                }
            },
            {
                "personaId": "p2",
                "nickname": "다른사람1",
                "formality": "casual",
                "voiceProfile": {
                    "formality": "casual",
                    "voice_type": "BLIND"
                }
            },
            {
                "personaId": "p3",
                "nickname": "다른사람2",
                "formality": "polite",
                "voiceProfile": {
                    "formality": "polite",
                    "voice_type": "CLIEN"
                }
            }
        ],
        "author": {
            "personaId": "p1",
            "nickname": "작성자"
        },
        "maxTopLevel": 6,
        "maxReplies": 3,
        "minTopLevel": 1,
        "minItems": 1
    }
    return payload


def get_llm_worker_endpoint():
    """
    Resolve the LLM worker container IP and return the base endpoint URL.
    Container: againspring-llm-ai-user, port: 8092
    Returns: "http://<IP>:8092" or None on error
    """
    cmd = "docker inspect againspring-llm-ai-user --format '{{(index .NetworkSettings.Networks \"againspring\").IPAddress}}' 2>/dev/null"
    output = run_shell(cmd)
    if not output:
        print("ERROR: Could not resolve LLM worker container IP", file=sys.stderr)
        return None
    ip = output.strip()
    if not ip:
        print("ERROR: LLM worker container IP is empty", file=sys.stderr)
        return None
    return f"http://{ip}:8092"


def trigger_generation(endpoint="scheduled"):
    """
    Trigger one generation.
    endpoint: "scheduled" (default), "legacy", or "direct" (POST to LLM worker)
    Returns: (start_time, response_json) or (None, None) on error
    """
    start_time = datetime.now(timezone.utc)

    if endpoint == "direct":
        # Direct endpoint: POST to LLM worker /v2/generate/thread-plan
        base_url = get_llm_worker_endpoint()
        if not base_url:
            return None, None

        url = f"{base_url}/v2/generate/thread-plan"
        payload = build_thread_plan_payload()

        try:
            response = requests.post(url, json=payload, timeout=120)
            response_json = response.json() if response.ok else None
            if not response.ok:
                print(f"WARNING: Direct endpoint returned {response.status_code}: {response.text[:200]}", file=sys.stderr)
            return start_time, response_json
        except requests.exceptions.RequestException as e:
            print(f"WARNING: Direct endpoint request failed: {e}", file=sys.stderr)
            return None, None

    elif endpoint == "legacy":
        # Legacy path: /generate-posts (ActionExecutor)
        cmd = (
            'docker exec againspring-ai-user-orchestrator-dev sh -c '
            '"wget -qO- --timeout=300 --post-data=\\"\\\" '
            '"http://localhost:8096/admin/trigger/generate-posts?count=1""'
        )
    else:
        # Structured/PLAN path: /generate-scheduled-posts (NightlyScheduledFillService)
        cmd = (
            'docker exec againspring-ai-user-orchestrator-dev sh -c '
            '"wget -qO- --timeout=300 --post-data=\\"\\\" '
            '"http://localhost:8096/admin/trigger/generate-scheduled-posts?count=1""'
        )

    output = run_shell(cmd)
    if output is None:
        print(f"WARNING: Failed to trigger generation (endpoint={endpoint})", file=sys.stderr)
        return None, None

    # Try to parse as JSON for structured endpoint
    response_json = None
    if endpoint == "scheduled" and output.strip():
        try:
            response_json = json.loads(output.strip())
        except json.JSONDecodeError:
            print(f"WARNING: Failed to parse JSON response: {output[:200]}", file=sys.stderr)

    return start_time, response_json


def get_logs_since(since_time, container="againspring-llm-ai-user", duration_s=120):
    """
    Fetch container logs since a given datetime.
    Returns raw log text.
    """
    # Convert datetime to RFC3339 format for docker logs --since
    # Format: 2026-01-01T12:34:56Z
    since_str = since_time.strftime("%Y-%m-%dT%H:%M:%SZ")
    until_time = since_time + timedelta(seconds=duration_s)
    until_str = until_time.strftime("%Y-%m-%dT%H:%M:%SZ")

    cmd = f"docker logs --since {since_str} --until {until_str} {container} 2>&1"
    logs = run_shell(cmd)
    return logs or ""


def parse_llmstats(log_text):
    """
    Parse [LLMSTATS] lines from logs.
    Returns list of dicts: {ts, model, attempt, retryReason, in, out, cache_read, cache_write, result, duration_ms, ...}
    """
    stats = []
    # Pattern: [LLMSTATS] ts=... model=... attempt=... retryReason=... in=... out=... cache_read=... cache_write=... result=... duration_ms=...
    pattern = r'\[LLMSTATS\]\s+(.+?)(?:\n|$)'
    for match in re.finditer(pattern, log_text, re.MULTILINE):
        line = match.group(1)
        fields = {}
        for kv in re.finditer(r'(\w+)=([^\s]+)', line):
            key, val = kv.groups()
            fields[key] = val
        if fields:
            stats.append(fields)
    return stats


def count_failure_signals(log_text):
    """Count failure signal lines: PARSE_FAIL, [CIRCUIT], REFUSAL_RETRY_EXHAUSTED, ContentSafetyGuard block."""
    counts = {
        'PARSE_FAIL': len(re.findall(r'PARSE_FAIL', log_text)),
        'CIRCUIT': len(re.findall(r'\[CIRCUIT\]', log_text)),
        'REFUSAL_RETRY_EXHAUSTED': len(re.findall(r'REFUSAL_RETRY_EXHAUSTED', log_text)),
        'ContentSafetyGuard_BLOCK': len(re.findall(r'ContentSafetyGuard.*[Bb]lock', log_text)),
    }
    return counts


def query_post_count():
    """Get the total number of posts in the DB."""
    sql_query = """SELECT COUNT(*) FROM posts;"""
    sql_query = sql_query.replace('"', '\\"')
    cmd = f"""docker exec againspring-mariadb-dev sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" againspring_dev -N -e "{sql_query}" 2>/dev/null' """
    output = run_shell(cmd)
    if not output:
        return 0
    try:
        return int(output.strip())
    except ValueError:
        return 0


def query_latest_posts(limit=5):
    """
    Query dev DB for the N most recent posts.
    Returns list of dicts: {id, title, body_length, created_at}.
    """
    sql_query = f"""SELECT id, title, LENGTH(COALESCE(body_published, '')), DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%S') FROM posts ORDER BY created_at DESC LIMIT {limit};"""

    sql_query = sql_query.replace('"', '\\"')
    cmd = f"""docker exec againspring-mariadb-dev sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" againspring_dev -N -e "{sql_query}" 2>/dev/null' """
    output = run_shell(cmd)
    if not output:
        return []

    posts = []
    for line in output.strip().split('\n'):
        if not line.strip():
            continue
        parts = line.split('\t')
        if len(parts) >= 4:
            try:
                posts.append({
                    'id': parts[0],
                    'title': parts[1],
                    'body_length': int(parts[2]),
                    'created_at': parts[3],
                })
            except (ValueError, IndexError):
                pass
    return posts


def run_validation(num_runs=5, gap_s=20, label=None, endpoint="scheduled"):
    """
    Run N generations, harvest stats and logs, and return aggregated results.
    endpoint: "scheduled" (default) or "legacy"
    Returns dict with per-run details and summary stats.
    """
    # Pre-flight checks
    if not container_exists('againspring-ai-user-orchestrator-dev'):
        print("ERROR: Container againspring-ai-user-orchestrator-dev not found", file=sys.stderr)
        return None
    if not container_exists('againspring-llm-ai-user'):
        print("ERROR: Container againspring-llm-ai-user not found", file=sys.stderr)
        return None
    if not container_exists('againspring-mariadb-dev'):
        print("ERROR: Container againspring-mariadb-dev not found", file=sys.stderr)
        return None

    results = {
        'label': label or 'unlabeled',
        'endpoint': endpoint,
        'run_time': datetime.now(timezone.utc).isoformat(),
        'runs': [],
        'summary': {}
    }

    for run_idx in range(num_runs):
        print(f"\n[RUN {run_idx+1}/{num_runs}] Triggering generation (endpoint={endpoint})...", file=sys.stderr)

        # Snapshot post count before trigger (for legacy endpoint comparison)
        count_before = query_post_count() if endpoint != "direct" else 0
        print(f"  Posts before: {count_before}", file=sys.stderr)

        start_time, response_json = trigger_generation(endpoint=endpoint)
        if not start_time:
            print(f"  SKIP: Trigger failed", file=sys.stderr)
            continue

        # Wait for generation to complete (empirical: ~10-30s typical, 60s max)
        # Direct endpoint is faster (no orchestrator overhead), but we still wait for logs
        wait_time = 60
        print(f"  Waiting {wait_time}s for generation to complete...", file=sys.stderr)
        time.sleep(wait_time)

        # Check post count after (direct endpoint doesn't create posts)
        count_after = query_post_count() if endpoint != "direct" else 0
        print(f"  Posts after: {count_after}", file=sys.stderr)

        # Harvest logs
        log_text = get_logs_since(start_time, duration_s=wait_time)
        llm_stats = parse_llmstats(log_text)
        failure_signals = count_failure_signals(log_text)

        # Determine success based on endpoint
        if endpoint == "direct":
            # Direct endpoint: check response has valid structure (post + comments)
            if response_json:
                post = response_json.get('post')
                items = response_json.get('items', [])
                post_success = (post is not None and
                               post.get('title') and post.get('body') and
                               len(items) > 0)
            else:
                post_success = False
            llm_used = 1 if post_success else 0
            llm_max = 1
            attempted = 1
            saved = 1 if post_success else 0
            response_failures = [] if post_success else ['response_parse_or_structure']
        elif endpoint == "scheduled" and response_json:
            # Structured endpoint: check response.saved > 0
            post_success = response_json.get('saved', 0) > 0
            llm_used = response_json.get('llmUsed', 0)
            llm_max = response_json.get('llmMax', 0)
            attempted = response_json.get('attempted', 0)
            saved = response_json.get('saved', 0)
            response_failures = response_json.get('failures', [])
        else:
            # Legacy endpoint: check if post count increased
            post_success = count_after > count_before
            llm_used = None
            llm_max = None
            attempted = count_after - count_before
            saved = attempted
            response_failures = []

        # Query for recent posts (skip for direct endpoint)
        recent_posts = query_latest_posts(limit=3) if endpoint != "direct" else []
        posts = recent_posts

        # Aggregate per-run
        run_result = {
            'run': run_idx + 1,
            'trigger_time': start_time.isoformat(),
            'post_created': post_success,
            'posts': posts,
            'llm_stats_count': len(llm_stats),
            'llm_stats': llm_stats,
            'failure_signals': failure_signals,
        }

        # Add endpoint-specific fields
        if endpoint == "scheduled" and response_json:
            run_result['response'] = {
                'attempted': attempted,
                'saved': saved,
                'llmUsed': llm_used,
                'llmMax': llm_max,
                'failures': response_failures,
            }

        if llm_stats:
            # Extract numeric fields for stats
            try:
                in_tokens = [int(s.get('in', 0)) for s in llm_stats if s.get('in')]
                out_tokens = [int(s.get('out', 0)) for s in llm_stats if s.get('out')]
                durations = [int(s.get('duration_ms', 0)) for s in llm_stats if s.get('duration_ms')]
                cache_reads = [int(s.get('cache_read', 0)) for s in llm_stats if s.get('cache_read')]
                cache_writes = [int(s.get('cache_write', 0)) for s in llm_stats if s.get('cache_write')]

                run_result['tokens'] = {
                    'input': {'count': len(in_tokens), 'mean': mean(in_tokens) if in_tokens else 0, 'total': sum(in_tokens)},
                    'output': {'count': len(out_tokens), 'mean': mean(out_tokens) if out_tokens else 0, 'total': sum(out_tokens)},
                    'cache_read': {'count': len(cache_reads), 'total': sum(cache_reads)},
                    'cache_write': {'count': len(cache_writes), 'total': sum(cache_writes)},
                }
                run_result['duration_ms'] = {
                    'count': len(durations),
                    'mean': mean(durations) if durations else 0,
                    'median': median(durations) if durations else 0,
                    'min': min(durations) if durations else 0,
                    'max': max(durations) if durations else 0,
                }
            except (ValueError, StatisticsError):
                pass

        results['runs'].append(run_result)

        print(f"  Post created: {post_success}", file=sys.stderr)
        print(f"  LLM stats entries: {len(llm_stats)}", file=sys.stderr)
        print(f"  Failure signals: {failure_signals}", file=sys.stderr)

        if endpoint == "scheduled" and response_json:
            print(f"  Response: attempted={attempted} saved={saved} llmUsed={llm_used}/{llm_max} failures={len(response_failures)}", file=sys.stderr)

        if run_idx < num_runs - 1:
            print(f"  Waiting {gap_s}s before next run...", file=sys.stderr)
            time.sleep(gap_s)

    # Compute summary
    if results['runs']:
        all_llm_stats = []
        for run in results['runs']:
            all_llm_stats.extend(run.get('llm_stats', []))

        success_count = sum(1 for r in results['runs'] if r['post_created'])
        all_failures = {'PARSE_FAIL': 0, 'CIRCUIT': 0, 'REFUSAL_RETRY_EXHAUSTED': 0, 'ContentSafetyGuard_BLOCK': 0}
        for run in results['runs']:
            for k, v in run['failure_signals'].items():
                all_failures[k] += v

        try:
            all_in = [int(s.get('in', 0)) for s in all_llm_stats if s.get('in')]
            all_out = [int(s.get('out', 0)) for s in all_llm_stats if s.get('out')]
            all_durations = [int(s.get('duration_ms', 0)) for s in all_llm_stats if s.get('duration_ms')]
        except (ValueError, TypeError):
            all_in, all_out, all_durations = [], [], []

        results['summary'] = {
            'runs_completed': len(results['runs']),
            'success_rate': round(100.0 * success_count / len(results['runs']), 1) if results['runs'] else 0,
            'posts_created': success_count,
            'total_llm_calls': len(all_llm_stats),
            'failure_signals': all_failures,
            'tokens': {
                'input_mean': round(mean(all_in), 0) if all_in else 0,
                'input_total': sum(all_in),
                'output_mean': round(mean(all_out), 0) if all_out else 0,
                'output_total': sum(all_out),
            },
            'duration_ms': {
                'mean': round(mean(all_durations), 0) if all_durations else 0,
                'median': round(median(all_durations), 0) if all_durations else 0,
                'min': min(all_durations) if all_durations else 0,
                'max': max(all_durations) if all_durations else 0,
            },
        }

    return results


def save_results(results, label):
    """Save results to JSON under .temp/stabilation/."""
    results_dir = Path('.temp/stabilation')
    results_dir.mkdir(parents=True, exist_ok=True)

    filename = results_dir / f"{label or 'run'}.json"
    with open(filename, 'w') as f:
        json.dump(results, f, indent=2)
    print(f"\nResults saved to: {filename}")
    return filename


def print_summary_table(results):
    """Print a human-readable summary table."""
    summary = results['summary']
    print("\n" + "="*80)
    print("SUMMARY TABLE")
    print("="*80)
    print(f"Endpoint:              {results.get('endpoint', 'unknown')}")
    print(f"Label:                 {results.get('label', 'unlabeled')}")
    print(f"Runs completed:        {summary.get('runs_completed', 0)}")
    print(f"Success rate:          {summary.get('success_rate', 0):.1f}%")
    print(f"Posts created:         {summary.get('posts_created', 0)}")
    print(f"Total LLM calls:       {summary.get('total_llm_calls', 0)}")
    print(f"\nFailure signals:")
    for sig, count in summary.get('failure_signals', {}).items():
        print(f"  {sig}: {count}")
    print(f"\nTokens:")
    tokens = summary.get('tokens', {})
    print(f"  Input mean:    {tokens.get('input_mean', 0):.0f}")
    print(f"  Input total:   {tokens.get('input_total', 0)}")
    print(f"  Output mean:   {tokens.get('output_mean', 0):.0f}")
    print(f"  Output total:  {tokens.get('output_total', 0)}")
    print(f"\nDuration (ms):")
    dur = summary.get('duration_ms', {})
    print(f"  Mean:          {dur.get('mean', 0):.0f}")
    print(f"  Median:        {dur.get('median', 0):.0f}")
    print(f"  Min/Max:       {dur.get('min', 0)} / {dur.get('max', 0)}")
    print("="*80)


def compare_results(before_path, after_path):
    """Load two result JSON files and print side-by-side comparison."""
    with open(before_path) as f:
        before = json.load(f)
    with open(after_path) as f:
        after = json.load(f)

    print("\n" + "="*100)
    print(f"COMPARISON: {before['label']} vs {after['label']}")
    print("="*100)

    headers = ["Metric", "Before", "After", "Delta", "% Change"]
    print(f"{headers[0]:<30} {headers[1]:>15} {headers[2]:>15} {headers[3]:>15} {headers[4]:>12}")
    print("-" * 100)

    b_summary = before.get('summary', {})
    a_summary = after.get('summary', {})

    metrics = [
        ("Success rate (%)", b_summary.get('success_rate', 0), a_summary.get('success_rate', 0)),
        ("Posts created", b_summary.get('posts_created', 0), a_summary.get('posts_created', 0)),
        ("Total LLM calls", b_summary.get('total_llm_calls', 0), a_summary.get('total_llm_calls', 0)),
        ("Input tokens/call",
         b_summary.get('tokens', {}).get('input_mean', 0),
         a_summary.get('tokens', {}).get('input_mean', 0)),
        ("Output tokens/call",
         b_summary.get('tokens', {}).get('output_mean', 0),
         a_summary.get('tokens', {}).get('output_mean', 0)),
        ("Duration (ms)",
         b_summary.get('duration_ms', {}).get('mean', 0),
         a_summary.get('duration_ms', {}).get('mean', 0)),
    ]

    for name, before_val, after_val in metrics:
        if before_val == 0 and after_val == 0:
            delta, pct = 0, 0
        elif before_val == 0:
            delta = after_val
            pct = 100 if after_val > 0 else 0
        else:
            delta = after_val - before_val
            pct = 100 * delta / before_val if before_val != 0 else 0

        delta_str = f"{delta:+.0f}"
        pct_str = f"{pct:+.1f}%" if before_val != 0 else "—"
        print(f"{name:<30} {before_val:>15.0f} {after_val:>15.0f} {delta_str:>15} {pct_str:>12}")

    print("\nFailure signals:")
    for sig in ['PARSE_FAIL', 'CIRCUIT', 'REFUSAL_RETRY_EXHAUSTED', 'ContentSafetyGuard_BLOCK']:
        b_count = b_summary.get('failure_signals', {}).get(sig, 0)
        a_count = a_summary.get('failure_signals', {}).get(sig, 0)
        delta = a_count - b_count
        delta_str = f"{delta:+d}"
        print(f"  {sig:<28} {b_count:>15} {a_count:>15} {delta_str:>15}")

    print("="*100)


def main():
    import argparse

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runs', type=int, default=5, help='Number of generation runs (default: 5)')
    parser.add_argument('--gap', type=int, default=20, help='Gap between runs in seconds (default: 20)')
    parser.add_argument('--label', type=str, help='Label for this run (e.g., schema-mode, prompt-mode)')
    parser.add_argument('--endpoint', type=str, default='scheduled', choices=['scheduled', 'legacy', 'direct'],
                        help='Endpoint to test: scheduled (default, PLAN mode via orchestrator), legacy (ActionExecutor), or direct (LLM worker /v2/generate/thread-plan)')
    parser.add_argument('--compare', nargs=2, metavar=('BEFORE', 'AFTER'),
                        help='Compare two result JSON files')

    args = parser.parse_args()

    if args.compare:
        before_path = Path(args.compare[0])
        after_path = Path(args.compare[1])
        if not before_path.exists() or not after_path.exists():
            print("ERROR: One or both comparison files not found", file=sys.stderr)
            sys.exit(1)
        compare_results(before_path, after_path)
        return

    # Run validation
    print(f"Starting validation: {args.runs} runs, {args.gap}s gap, endpoint={args.endpoint}", file=sys.stderr)
    results = run_validation(num_runs=args.runs, gap_s=args.gap, label=args.label, endpoint=args.endpoint)

    if not results:
        print("ERROR: Validation failed", file=sys.stderr)
        sys.exit(1)

    # Print summary and save
    print_summary_table(results)
    save_results(results, args.label or 'unlabeled')


if __name__ == '__main__':
    main()
