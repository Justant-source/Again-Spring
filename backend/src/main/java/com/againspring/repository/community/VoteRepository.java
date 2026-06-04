package com.againspring.repository.community;

import com.againspring.domain.community.Vote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 투표 저장소 (V17 커뮤니티)
 */
@Repository
public interface VoteRepository extends JpaRepository<Vote, Long> {

    /**
     * 포스트 및 선택지별 투표 수
     */
    Long countByPostIdAndOptionId(String postId, Long optionId);

    Long countByPostId(String postId);

    /**
     * 포스트 및 투표자별 투표 조회
     */
    Optional<Vote> findByPostIdAndVoterUserId(String postId, String voterUserId);

    /**
     * 포스트 및 투표자별 투표 존재 여부
     */
    boolean existsByPostIdAndVoterUserId(String postId, String voterUserId);

    /**
     * 사용자가 투표한 모든 Vote (최신순)
     */
    List<Vote> findByVoterUserIdOrderByCreatedAtDesc(String voterUserId);
}
