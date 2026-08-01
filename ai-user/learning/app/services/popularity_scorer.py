"""
Per-source relative popularity for example_bank POSTs.

Config contract (corpus.popularity):
  mode: per_source_relative
  metrics: [view_count, like_count, comment_count]
  missing_metric: renormalize   # never fill missing with 0
  no_metric_grade: UNRANKED     # popularity_pct = NULL
  applies_to: [POST]            # COMMENT has no engagement metrics
"""
from __future__ import annotations

import logging
import math
from collections import defaultdict
from typing import Any, Iterable, Mapping, Optional, Sequence

from app.db.session import get_db

logger = logging.getLogger(__name__)

# §16.7 corpus.popularity
POPULARITY_MODE = "per_source_relative"
METRICS: tuple[str, ...] = ("view_count", "like_count", "comment_count")
MISSING_METRIC_POLICY = "renormalize"
NO_METRIC_GRADE = "UNRANKED"
APPLIES_TO: tuple[str, ...] = ("POST",)

# Equal defaults; §15.5 calibration may retune later.
DEFAULT_METRIC_WEIGHTS: dict[str, float] = {
    "view_count": 1.0,
    "like_count": 1.0,
    "comment_count": 1.0,
}


def _percentile_rank(scores: Sequence[float], value: float) -> float:
    """'mean' percentile: (count_less + count_equal/2) / n, range 0~1."""
    n = len(scores)
    if n == 0:
        return 0.0
    less = sum(1 for s in scores if s < value)
    equal = sum(1 for s in scores if s == value)
    return (less + equal / 2.0) / n


def _mean_std(values: Sequence[float]) -> tuple[Optional[float], Optional[float]]:
    """Population mean/std for a metric within a (source, content_type) group."""
    n = len(values)
    if n == 0:
        return None, None
    mean = sum(values) / n
    if n == 1:
        return mean, 0.0
    variance = sum((x - mean) ** 2 for x in values) / n
    return mean, math.sqrt(variance)


def _z_score(value: float, mean: float, std: float) -> float:
    if std == 0 or math.isnan(std):
        return 0.0
    return (value - mean) / std


def popularity_grade(popularity_pct: Optional[float]) -> Optional[str]:
    """NULL pct → UNRANKED (includable, low priority). Ranked rows have no grade label."""
    if popularity_pct is None:
        return NO_METRIC_GRADE
    return None


def compute_group_metric_stats(
    rows: Iterable[Mapping[str, Any]],
    metrics: Sequence[str] = METRICS,
) -> dict[str, tuple[Optional[float], Optional[float]]]:
    """
    mean/std per metric from non-null values only.
    Missing values are excluded from that metric's distribution (not treated as 0).
    """
    buckets: dict[str, list[float]] = {m: [] for m in metrics}
    for row in rows:
        for m in metrics:
            val = row.get(m)
            if val is not None:
                buckets[m].append(float(val))
    return {m: _mean_std(vals) for m, vals in buckets.items()}


def per_source_score(
    row: Mapping[str, Any],
    group_stats: Mapping[str, tuple[Optional[float], Optional[float]]],
    weights: Mapping[str, float] | None = None,
    metrics: Sequence[str] = METRICS,
) -> Optional[float]:
    """
    Weighted sum of z-scores for available metrics.
    Missing metrics are dropped and remaining weights renormalized.
    Returns None when no usable metric remains (UNRANKED).
    """
    w = weights or DEFAULT_METRIC_WEIGHTS
    parts: list[tuple[str, float]] = []  # (metric, z)

    for m in metrics:
        val = row.get(m)
        if val is None:
            continue
        mean, std = group_stats.get(m, (None, None))
        if mean is None or std is None:
            continue
        parts.append((m, _z_score(float(val), mean, std)))

    if not parts:
        return None

    total_w = sum(float(w.get(m, 0.0)) for m, _ in parts)
    if total_w <= 0:
        return None

    return sum((float(w.get(m, 0.0)) / total_w) * z for m, z in parts)


def score_posts(
    rows: Sequence[Mapping[str, Any]],
    weights: Mapping[str, float] | None = None,
    metrics: Sequence[str] = METRICS,
    applies_to: Sequence[str] = APPLIES_TO,
) -> dict[Any, Optional[float]]:
    """
    Compute popularity_pct (0~1) per row id for applies_to content types.

    Grouping key: (source, content_type).
    Within each group:
      1. mean/std per metric
      2. per_source_score with missing-metric renormalization
      3. percentile of that score among scored rows in the group
    Rows with no metrics → None (UNRANKED).
    Rows outside applies_to are omitted from the result.
    """
    by_group: dict[tuple[Any, Any], list[Mapping[str, Any]]] = defaultdict(list)
    for row in rows:
        content_type = row.get("content_type")
        if content_type not in applies_to:
            continue
        source = row.get("source")
        if not source:
            continue
        by_group[(source, content_type)].append(row)

    result: dict[Any, Optional[float]] = {}

    for (_source, _ctype), group_rows in by_group.items():
        stats = compute_group_metric_stats(group_rows, metrics=metrics)
        scored: list[tuple[Any, float]] = []

        for row in group_rows:
            row_id = row["id"]
            score = per_source_score(row, stats, weights=weights, metrics=metrics)
            if score is None:
                result[row_id] = None
            else:
                scored.append((row_id, score))

        if not scored:
            continue

        score_values = [s for _, s in scored]
        for row_id, score in scored:
            result[row_id] = _percentile_rank(score_values, score)

    return result


def recompute_popularity_scores(
    weights: Mapping[str, float] | None = None,
) -> dict[str, Any]:
    """
    Recompute example_bank.popularity_pct for POSTs using per-source relative scoring.

    - Groups by (source, content_type)
    - Missing metrics: drop weight + renormalize (never fill 0)
    - No metrics: popularity_pct = NULL (UNRANKED)
    - COMMENT rows are not scored (applies_to = POST only)
    """
    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT id, source, content_type,
                           view_count, like_count, comment_count
                    FROM example_bank
                    WHERE content_type = 'POST'
                      AND source IS NOT NULL
                    ORDER BY source, id
                    """
                )
                rows = cur.fetchall()

        if not rows:
            logger.info("No POST rows available for popularity scoring")
            return {"processed": 0, "ranked": 0, "unranked": 0, "groups": {}}

        popularity_updates = score_posts(rows, weights=weights)

        group_stats: dict[tuple[Any, Any], dict[str, int]] = defaultdict(
            lambda: {"sample_size": 0, "ranked": 0, "unranked": 0}
        )
        for row in rows:
            key = (row["source"], row.get("content_type", "POST"))
            group_stats[key]["sample_size"] += 1
            pct = popularity_updates.get(row["id"])
            if pct is None:
                group_stats[key]["unranked"] += 1
            else:
                group_stats[key]["ranked"] += 1

        BATCH_SIZE = 50
        update_count = 0
        ranked = 0
        unranked = 0
        with get_db() as conn:
            with conn.cursor() as cur:
                for row_id, popularity_pct in popularity_updates.items():
                    cur.execute(
                        "UPDATE example_bank SET popularity_pct = %s WHERE id = %s",
                        (popularity_pct, row_id),
                    )
                    update_count += 1
                    if popularity_pct is None:
                        unranked += 1
                    else:
                        ranked += 1
                    if update_count % BATCH_SIZE == 0:
                        conn.commit()
                if update_count % BATCH_SIZE != 0:
                    conn.commit()

        groups_out = {f"{s}|{c}": v for (s, c), v in group_stats.items()}
        logger.info(
            "Popularity scoring completed: updated=%s ranked=%s unranked=%s",
            update_count,
            ranked,
            unranked,
        )
        logger.info("Group statistics: %s", groups_out)
        return {
            "processed": update_count,
            "ranked": ranked,
            "unranked": unranked,
            "groups": groups_out,
        }

    except Exception as e:
        logger.error("Popularity score recomputation failed: %s", e, exc_info=True)
        raise
