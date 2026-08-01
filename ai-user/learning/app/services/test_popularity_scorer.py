"""Unit tests for per-source relative popularity scoring."""
from __future__ import annotations

import math

import pytest

from .popularity_scorer import (
    APPLIES_TO,
    DEFAULT_METRIC_WEIGHTS,
    METRICS,
    MISSING_METRIC_POLICY,
    NO_METRIC_GRADE,
    POPULARITY_MODE,
    _mean_std,
    _percentile_rank,
    _z_score,
    compute_group_metric_stats,
    per_source_score,
    popularity_grade,
    score_posts,
)


class TestConfigContract:
    def test_section_16_7_constants(self):
        assert POPULARITY_MODE == "per_source_relative"
        assert list(METRICS) == ["view_count", "like_count", "comment_count"]
        assert MISSING_METRIC_POLICY == "renormalize"
        assert NO_METRIC_GRADE == "UNRANKED"
        assert list(APPLIES_TO) == ["POST"]
        assert set(DEFAULT_METRIC_WEIGHTS) == set(METRICS)


class TestMeanStdAndZ:
    def test_mean_std_empty(self):
        assert _mean_std([]) == (None, None)

    def test_mean_std_single(self):
        mean, std = _mean_std([10.0])
        assert mean == 10.0
        assert std == 0.0

    def test_mean_std_known(self):
        mean, std = _mean_std([2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0])
        assert mean == pytest.approx(5.0)
        # population variance = 4 → std = 2
        assert std == pytest.approx(2.0)

    def test_z_zero_std(self):
        assert _z_score(5.0, 5.0, 0.0) == 0.0

    def test_z_known(self):
        assert _z_score(7.0, 5.0, 2.0) == pytest.approx(1.0)


class TestPercentileRank:
    def test_unique_sorted(self):
        scores = [1.0, 2.0, 3.0, 4.0]
        assert _percentile_rank(scores, 1.0) == pytest.approx(0.125)
        assert _percentile_rank(scores, 4.0) == pytest.approx(0.875)

    def test_all_equal(self):
        scores = [1.0, 1.0, 1.0]
        assert _percentile_rank(scores, 1.0) == pytest.approx(0.5)


class TestMissingMetricRenormalize:
    def test_missing_not_filled_with_zero(self):
        """Only view present: weight renormalizes to 1.0 on view; like/comment ignored."""
        group_stats = {
            "view_count": (100.0, 10.0),
            "like_count": (10.0, 2.0),
            "comment_count": (5.0, 1.0),
        }
        row = {"view_count": 110, "like_count": None, "comment_count": None}
        # If missing were filled with 0, like/comment z would be strongly negative.
        score = per_source_score(row, group_stats)
        assert score == pytest.approx(1.0)  # only view z=(110-100)/10

    def test_partial_metrics_renormalize_equal_weights(self):
        group_stats = {
            "view_count": (100.0, 10.0),
            "like_count": (10.0, 5.0),
            "comment_count": (5.0, 1.0),
        }
        row = {"view_count": 110, "like_count": 15, "comment_count": None}
        # view z=1, like z=1 → equal weights → 1.0
        assert per_source_score(row, group_stats) == pytest.approx(1.0)

    def test_no_metrics_returns_none(self):
        group_stats = {
            "view_count": (100.0, 10.0),
            "like_count": (10.0, 2.0),
            "comment_count": (5.0, 1.0),
        }
        row = {"view_count": None, "like_count": None, "comment_count": None}
        assert per_source_score(row, group_stats) is None


class TestPopularityGrade:
    def test_none_is_unranked(self):
        assert popularity_grade(None) == "UNRANKED"

    def test_ranked_has_no_grade_label(self):
        assert popularity_grade(0.0) is None
        assert popularity_grade(0.75) is None


class TestGroupStatsExcludeNulls:
    def test_nulls_not_treated_as_zero(self):
        rows = [
            {"view_count": 100, "like_count": None, "comment_count": 2},
            {"view_count": 200, "like_count": 10, "comment_count": None},
        ]
        stats = compute_group_metric_stats(rows)
        view_mean, _ = stats["view_count"]
        like_mean, _ = stats["like_count"]
        comment_mean, _ = stats["comment_count"]
        assert view_mean == pytest.approx(150.0)
        assert like_mean == pytest.approx(10.0)  # not (0+10)/2
        assert comment_mean == pytest.approx(2.0)  # not (2+0)/2


class TestScorePosts:
    def test_comment_excluded(self):
        rows = [
            {
                "id": 1,
                "source": "natepan",
                "content_type": "COMMENT",
                "view_count": 999,
                "like_count": 9,
                "comment_count": 9,
            },
            {
                "id": 2,
                "source": "natepan",
                "content_type": "POST",
                "view_count": 100,
                "like_count": 10,
                "comment_count": 5,
            },
        ]
        out = score_posts(rows)
        assert 1 not in out
        assert out[2] == pytest.approx(0.5)  # sole ranked POST → mid percentile

    def test_no_metrics_unranked(self):
        rows = [
            {
                "id": 1,
                "source": "BLIND",
                "content_type": "POST",
                "view_count": None,
                "like_count": None,
                "comment_count": None,
            },
            {
                "id": 2,
                "source": "BLIND",
                "content_type": "POST",
                "view_count": 50,
                "like_count": 5,
                "comment_count": 1,
            },
        ]
        out = score_posts(rows)
        assert out[1] is None
        assert popularity_grade(out[1]) == "UNRANKED"
        assert out[2] is not None
        assert 0.0 <= out[2] <= 1.0

    def test_relative_across_sources(self):
        """Same absolute views can yield different relative ranks per source."""
        rows = [
            # natepan: 10 is low
            {
                "id": 1,
                "source": "natepan",
                "content_type": "POST",
                "view_count": 10,
                "like_count": None,
                "comment_count": None,
            },
            {
                "id": 2,
                "source": "natepan",
                "content_type": "POST",
                "view_count": 100,
                "like_count": None,
                "comment_count": None,
            },
            {
                "id": 3,
                "source": "natepan",
                "content_type": "POST",
                "view_count": 1000,
                "like_count": None,
                "comment_count": None,
            },
            # BLIND: 10 is high
            {
                "id": 4,
                "source": "BLIND",
                "content_type": "POST",
                "view_count": 1,
                "like_count": None,
                "comment_count": None,
            },
            {
                "id": 5,
                "source": "BLIND",
                "content_type": "POST",
                "view_count": 5,
                "like_count": None,
                "comment_count": None,
            },
            {
                "id": 6,
                "source": "BLIND",
                "content_type": "POST",
                "view_count": 10,
                "like_count": None,
                "comment_count": None,
            },
        ]
        out = score_posts(rows)
        assert out[1] < out[2] < out[3]
        assert out[4] < out[5] < out[6]
        # absolute view=10: low in natepan, high in BLIND
        assert out[1] < out[6]

    def test_higher_engagement_ranks_higher(self):
        rows = [
            {
                "id": i,
                "source": "natepan",
                "content_type": "POST",
                "view_count": views,
                "like_count": likes,
                "comment_count": comments,
            }
            for i, (views, likes, comments) in enumerate(
                [
                    (10, 1, 0),
                    (50, 5, 2),
                    (200, 20, 10),
                ],
                start=1,
            )
        ]
        out = score_posts(rows)
        assert out[1] < out[2] < out[3]
        assert all(not math.isnan(v) for v in out.values())


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
