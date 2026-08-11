"""Crawl-in-progress marker for ops-watchdog (skip restart while embedding)."""

from __future__ import annotations

import threading
from contextlib import contextmanager
from pathlib import Path

LOCK_PATH = Path("/tmp/ai_learning_crawl_in_progress")
_lock = threading.Lock()
_depth = 0


def _write_marker(reason: str) -> None:
    LOCK_PATH.write_text(f"{reason}\ndepth={_depth}\n", encoding="utf-8")


def acquire_crawl_guard(reason: str = "crawl") -> None:
    global _depth
    with _lock:
        _depth += 1
        _write_marker(reason)


def release_crawl_guard() -> None:
    global _depth
    with _lock:
        _depth = max(0, _depth - 1)
        if _depth == 0:
            LOCK_PATH.unlink(missing_ok=True)
        else:
            _write_marker("crawl")


@contextmanager
def crawl_guard(reason: str = "crawl"):
    acquire_crawl_guard(reason)
    try:
        yield
    finally:
        release_crawl_guard()
