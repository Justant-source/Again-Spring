package com.againspring.repository.community;

import com.againspring.domain.community.VoteOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 투표 선택지 저장소 (V17 커뮤니티)
 */
@Repository
public interface VoteOptionRepository extends JpaRepository<VoteOption, Long> {

    /**
     * 포스트별 투표 선택지 조회 (정렬순)
     */
    List<VoteOption> findByPostIdOrderByOrderIdx(String postId);
}
