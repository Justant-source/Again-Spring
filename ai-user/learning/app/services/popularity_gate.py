"""
Crawl ingest popularity gate.

Posts/comments must not be saved indiscriminately. Only posts with engagement
metrics and above-median (configurable) per-batch relative popularity, and
comments only from those accepted parent posts (or already-ranked parents in DB).

Contract aligned with corpus.popularity (§16.7) + operator rule 2026-08-01:
popularity must be guaranteed before ingest.
"""
from __future__ import annotations

import logging
import os
from typing import Any, Mapping, Optional, Sequence
from urllib.parse import urldefrag

from app.services.popularity_scorer import (
    METRICS,
    score_posts,
)

logger = logging.getLogger(__name__)

# Keep posts at/above this in-batch relative percentile (0~1). Default = median+.
MIN_POPULARITY_PCT = float(os.getenv("CRAWL_MIN_POPULARITY_PCT", "0.50"))

# Absolute floors — fail closed when all metrics are below these (before relative rank).
# Missing metrics are ignored for the OR check; at least one metric must still exist.
ABSOLUTE_FLOOR: dict[str, dict[str, int]] = {
    "natepan": {"view_count": 50, "like_count": 3, "comment_count": 5},
    "blind": {"view_count": 30, "like_count": 1, "comment_count": 3},
}


def parent_post_url(source_url: Optional[str]) -> Optional[str]:
    """Strip fragment (#cmtN / #comment-…) to get parent post URL."""
    if not source_url:
        return None
    base, _frag = urldefrag(source_url)
    return base or None


def has_any_metric(item: Mapping[str, Any]) -> bool:
    for m in METRICS:
        v = item.get(m)
        if v is not None:
            try:
                if int(v) >= 0:
                    return True
            except (TypeError, ValueError):
                continue
    return False


def passes_absolute_floor(item: Mapping[str, Any], source: str) -> bool:
    """True if any present metric meets its floor for the source."""
    floors = ABSOLUTE_FLOOR.get(source.lower(), ABSOLUTE_FLOOR["natepan"])
    for metric, floor in floors.items():
        v = item.get(metric)
        if v is None:
            continue
        try:
            if int(v) >= floor:
                return True
        except (TypeError, ValueError):
            continue
    return False


def select_popular_posts(
    posts: Sequence[Mapping[str, Any]],
    *,
    source: str,
    min_pct: float = MIN_POPULARITY_PCT,
) -> tuple[list[Mapping[str, Any]], dict[str, float]]:
    """
    Filter POST items: metrics required → absolute floor → in-batch relative pct gate.
    Returns (accepted_posts, url→popularity_pct).
    """
    source_key = source.lower()
    eligible: list[dict[str, Any]] = []
    skipped_no_metric = 0
    skipped_floor = 0
    for p in posts:
        if not has_any_metric(p):
            skipped_no_metric += 1
            continue
        if not passes_absolute_floor(p, source_key):
            skipped_floor += 1
            continue
        row = dict(p)
        row["source"] = source_key
        row["content_type"] = "POST"
        row["id"] = len(eligible)  # temporary id for score_posts
        eligible.append(row)

    if not eligible:
        logger.info(
            "popularity_gate: source=%s no eligible posts (no_metric=%s floor=%s)",
            source_key, skipped_no_metric, skipped_floor,
        )
        return [], {}

    pct_by_id = score_posts(eligible)

    accepted: list[Mapping[str, Any]] = []
    url_pct: dict[str, float] = {}
    skipped_pct = 0
    for row in eligible:
        pct = pct_by_id.get(row["id"])
        if pct is None or pct < min_pct:
            skipped_pct += 1
            continue
        # stash for ingest so DB gets an initial popularity_pct
        row["popularity_pct"] = float(pct)
        accepted.append(row)
        url = row.get("source_url")
        if url:
            url_pct[str(url)] = float(pct)

    logger.info(
        "popularity_gate: source=%s posts in=%s eligible=%s accepted=%s "
        "(no_metric=%s floor=%s below_pct=%s) min_pct=%.2f",
        source_key, len(posts), len(eligible), len(accepted),
        skipped_no_metric, skipped_floor, skipped_pct, min_pct,
    )
    return accepted, url_pct


def filter_comments_for_parents(
    comments: Sequence[Mapping[str, Any]],
    accepted_post_urls: set[str],
) -> list[Mapping[str, Any]]:
    """Keep COMMENT rows whose parent post URL is in the accepted popular set."""
    kept: list[Mapping[str, Any]] = []
    skipped = 0
    for c in comments:
        parent = parent_post_url(c.get("source_url")) or c.get("parent_source_url")
        if parent and parent in accepted_post_urls:
            kept.append(c)
        else:
            skipped += 1
    logger.info(
        "popularity_gate: comments in=%s kept=%s skipped_nonpopular_parent=%s",
        len(comments), len(kept), skipped,
    )
    return kept


def load_ranked_parent_urls(cursor, source: str, min_pct: float = MIN_POPULARITY_PCT) -> set[str]:
    """DB parents already ranked popular — allow attaching new comments to them."""
    cursor.execute(
        """
        SELECT source_url FROM example_bank
        WHERE LOWER(source)=%s AND content_type='POST'
          AND source_url IS NOT NULL
          AND popularity_pct IS NOT NULL AND popularity_pct >= %s
        """,
        (source.lower(), min_pct),
    )
    return {row["source_url"] for row in cursor.fetchall() if row.get("source_url")}
