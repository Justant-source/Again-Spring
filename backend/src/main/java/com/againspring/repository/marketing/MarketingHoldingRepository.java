package com.againspring.repository.marketing;

import com.againspring.domain.marketing.MarketingHolding;
import com.againspring.domain.marketing.MarketingHoldingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface MarketingHoldingRepository extends JpaRepository<MarketingHolding, String> {

    List<MarketingHolding> findByStatusIn(Collection<MarketingHoldingStatus> statuses);

    List<MarketingHolding> findByPostIdIn(Collection<String> postIds);

    /**
     * @deprecated Phase 1 shared-pool count. Prefer {@link #countCommittedForPlatformSince}.
     */
    @Deprecated
    @Query(nativeQuery = true, value = """
        SELECT COUNT(*) FROM marketing_holding mh
        WHERE mh.status = 'COMMITTED'
        AND mh.locked_at >= :since
        """)
    long countCommittedSince(@Param("since") Instant since);

    /**
     * @deprecated Phase 1 video subset. Prefer {@link #countCommittedForPlatformSince}.
     */
    @Deprecated
    @Query(nativeQuery = true, value = """
        SELECT COUNT(*) FROM marketing_holding mh
        WHERE mh.status = 'COMMITTED'
        AND mh.locked_at >= :since
        AND (
            mh.pin_format = 'VIDEO'
            OR EXISTS (
                SELECT 1 FROM marketing_job mj
                WHERE mj.post_id = mh.post_id
                AND (
                    JSON_CONTAINS(mj.targets, '"instagram_reels"') = TRUE
                    OR JSON_CONTAINS(mj.targets, '"youtube_shorts"') = TRUE
                )
            )
        )
        """)
    long countCommittedVideosSince(@Param("since") Instant since);

    /**
     * Phase 2: stories COMMITTED today (KST window via {@code since}) that have a job
     * targeting {@code platform}. One story counts once per platform.
     */
    @Query(nativeQuery = true, value = """
        SELECT COUNT(*) FROM marketing_holding mh
        WHERE mh.status = 'COMMITTED'
        AND mh.locked_at >= :since
        AND EXISTS (
            SELECT 1 FROM marketing_job mj
            WHERE mj.post_id = mh.post_id
            AND JSON_CONTAINS(mj.targets, CONCAT('"', :platform, '"')) = TRUE
        )
        """)
    long countCommittedForPlatformSince(
        @Param("platform") String platform,
        @Param("since") Instant since);

    /**
     * Active waiting-board candidates: still inside the 24h window, not soft-deleted,
     * and not already COMMITTED/DROPPED on the holding table.
     */
    @Query(nativeQuery = true, value = """
        SELECT
            p.id AS id,
            COALESCE(p.view_count, 0) AS viewCount,
            (
                SELECT COUNT(*) FROM post_comments c
                WHERE c.post_id = p.id
                AND c.parent_comment_id IS NULL
                AND c.deleted_at IS NULL
            ) AS commentCount,
            (
                SELECT COUNT(*) FROM votes v
                WHERE v.post_id = p.id
            ) AS voteCount,
            (
                SELECT COUNT(*) FROM votes v
                INNER JOIN vote_options vo ON vo.id = v.option_id
                WHERE v.post_id = p.id
                AND vo.order_idx = 0
            ) AS authorVoteCount,
            CASE
                WHEN p.partner_answered_at IS NOT NULL
                 AND p.partner_body_published IS NOT NULL
                 AND TRIM(p.partner_body_published) <> ''
                THEN 1 ELSE 0
            END AS hasPartner,
            COALESCE(NULLIF(TRIM(p.promo_title), ''), p.title, p.user_title, '') AS hookText,
            COALESCE(p.title, p.user_title, '') AS title,
            COALESCE(p.body_published, '') AS bodyPublished,
            p.created_at AS createdAt
        FROM posts p
        WHERE p.created_at > DATE_SUB(NOW(), INTERVAL 24 HOUR)
        AND p.deleted_at IS NULL
        AND NOT EXISTS (
            SELECT 1 FROM marketing_holding mh
            WHERE mh.post_id = p.id
            AND mh.status IN ('COMMITTED', 'DROPPED')
        )
        """)
    List<HoldingCandidateProjection> findActiveCandidates();

    interface HoldingCandidateProjection {
        String getId();
        Number getViewCount();
        Number getCommentCount();
        Number getVoteCount();
        Number getAuthorVoteCount();
        Number getHasPartner();
        String getHookText();
        String getTitle();
        String getBodyPublished();
        java.time.Instant getCreatedAt();
    }

    /**
     * Holdings past the 24h window still awaiting commit/drop (S4 tick).
     * Rank/score fields come from the live post so commit ordering stays current.
     */
    @Query(nativeQuery = true, value = """
        SELECT
            mh.post_id AS postId,
            mh.status AS status,
            mh.pin_format AS pinFormat,
            mh.score_snapshot AS scoreSnapshot,
            p.created_at AS postCreatedAt,
            COALESCE(p.view_count, 0) AS viewCount,
            (
                SELECT COUNT(*) FROM post_comments c
                WHERE c.post_id = p.id
                AND c.parent_comment_id IS NULL
                AND c.deleted_at IS NULL
            ) AS commentCount,
            (
                SELECT COUNT(*) FROM votes v
                WHERE v.post_id = p.id
            ) AS voteCount,
            (
                SELECT COUNT(*) FROM votes v
                INNER JOIN vote_options vo ON vo.id = v.option_id
                WHERE v.post_id = p.id
                AND vo.order_idx = 0
            ) AS authorVoteCount,
            CASE
                WHEN p.partner_answered_at IS NOT NULL
                 AND p.partner_body_published IS NOT NULL
                 AND TRIM(p.partner_body_published) <> ''
                THEN 1 ELSE 0
            END AS hasPartner,
            COALESCE(NULLIF(TRIM(p.promo_title), ''), p.title, p.user_title, '') AS hookText
        FROM marketing_holding mh
        INNER JOIN posts p ON p.id = mh.post_id
        WHERE mh.status IN ('IN_POOL', 'PINNED', 'OUT_OF_CUT')
        AND p.created_at >= :since
        AND p.created_at <= DATE_SUB(NOW(), INTERVAL 24 HOUR)
        AND p.deleted_at IS NULL
        """)
    List<DueHoldingProjection> findDueHoldings(@Param("since") Instant since);

    interface DueHoldingProjection {
        String getPostId();
        String getStatus();
        String getPinFormat();
        Double getScoreSnapshot();
        Instant getPostCreatedAt();
        Number getViewCount();
        Number getCommentCount();
        Number getVoteCount();
        Number getAuthorVoteCount();
        Number getHasPartner();
        String getHookText();
    }
}
