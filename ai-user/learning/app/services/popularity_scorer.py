import logging
from app.db.session import get_db
from collections import defaultdict

logger = logging.getLogger(__name__)


def _percentile_rank(scores, value):
    """scipy 없이 'mean' 방식 백분위 계산: (미만 개수 + 동일값 개수/2) / n, 0~1 범위"""
    n = len(scores)
    less = sum(1 for s in scores if s < value)
    equal = sum(1 for s in scores if s == value)
    return (less + equal / 2.0) / n


def _get_age_bucket(age_hours):
    """나이 구간 판정: 0~3h / 3~12h / 12h+"""
    if age_hours is None:
        return None
    if age_hours < 3:
        return "0-3h"
    elif age_hours < 12:
        return "3-12h"
    else:
        return "12h+"


def recompute_popularity_scores():
    """
    크롤링 데이터의 engagement metrics을 바탕으로 popularity_pct을 재계산.

    1. view_count IS NOT NULL AND posted_at IS NOT NULL AND source IS NOT NULL 행 조회
    2. 각 행의 나이 구간 판정 (age_bucket)
    3. 속도 점수 계산: (view_count + like_count*3) / max(age_hours, 0.1)
       — 좋아요는 능동적 반응이므로 가중치 3
    4. 같은 (source, age_bucket) 그룹 내에서만 백분위 계산 (0~1)
    5. 표본 30건 미만인 그룹은 NULL 유지 (초기 데이터 부족 방지)
    6. UPDATE로 반영 (배치 커밋)
    """
    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                # 1. 조회 가능한 행 선택
                cur.execute("""
                    SELECT id, source, view_count, like_count, posted_at, created_at
                    FROM example_bank
                    WHERE view_count IS NOT NULL
                      AND posted_at IS NOT NULL
                      AND source IS NOT NULL
                    ORDER BY source, created_at DESC
                """)
                rows = cur.fetchall()

        if not rows:
            logger.info("No rows with view_count available for popularity scoring")
            return {"processed": 0, "groups": {}}

        # 2-3. 나이 구간과 속도 점수 계산
        group_scores = defaultdict(list)  # (source, age_bucket) -> [(id, speed_score), ...]
        for row in rows:
            row_id = row["id"]
            source = row["source"]
            view_count = row["view_count"]
            like_count = row["like_count"] or 0
            posted_at = row["posted_at"]
            created_at = row["created_at"]

            # age_hours 계산 (created_at은 크롤 시점, posted_at은 원본 게시 시점)
            age_delta = created_at - posted_at
            age_hours = age_delta.total_seconds() / 3600

            age_bucket = _get_age_bucket(age_hours)
            if age_bucket is None:
                continue

            # 속도 점수 = (조회 + 좋아요*3) / 나이시간
            speed_score = (view_count + like_count * 3) / max(age_hours, 0.1)
            group_scores[(source, age_bucket)].append((row_id, speed_score))

        # 4-5. 그룹별 백분위 계산
        popularity_updates = {}  # row_id -> popularity_pct (or None)
        group_stats = {}  # (source, age_bucket) -> {"sample_size": ..., "processed": ...}

        for (source, age_bucket), id_score_pairs in group_scores.items():
            sample_size = len(id_score_pairs)
            group_stats[(source, age_bucket)] = {
                "sample_size": sample_size,
                "processed": 0
            }

            # 표본 30건 미만이면 NULL 유지
            if sample_size < 30:
                logger.info(f"Group ({source}, {age_bucket}): sample_size={sample_size} < 30, skipping percentile calculation")
                for row_id, _ in id_score_pairs:
                    popularity_updates[row_id] = None
                continue

            # 백분위 계산 (0~1 범위, 그룹 내 상대 순위)
            scores = [score for _, score in id_score_pairs]
            for row_id, speed_score in id_score_pairs:
                popularity_updates[row_id] = _percentile_rank(scores, speed_score)
                group_stats[(source, age_bucket)]["processed"] += 1

        # 6. UPDATE 배치 커밋
        BATCH_SIZE = 50
        update_count = 0
        with get_db() as conn:
            with conn.cursor() as cur:
                for row_id, popularity_pct in popularity_updates.items():
                    cur.execute(
                        "UPDATE example_bank SET popularity_pct = %s WHERE id = %s",
                        (popularity_pct, row_id)
                    )
                    update_count += 1
                    if update_count % BATCH_SIZE == 0:
                        conn.commit()
                # 남은 것 커밋
                if update_count % BATCH_SIZE != 0:
                    conn.commit()

        logger.info(f"Popularity scoring completed: updated {update_count} rows")
        logger.info(f"Group statistics: {dict(group_stats)}")
        return {"processed": update_count, "groups": dict(group_stats)}

    except Exception as e:
        logger.error(f"Popularity score recomputation failed: {e}", exc_info=True)
        raise
