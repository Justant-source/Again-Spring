package com.againspring.repository.community;

import com.againspring.domain.community.Juror;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 배심원 저장소 (V17 커뮤니티)
 */
@Repository
public interface JurorRepository extends JpaRepository<Juror, Long> {

    /**
     * 포스트별 배심원 조회
     */
    List<Juror> findByPostId(String postId);

    /**
     * 포스트별 배심원 수
     */
    Long countByPostId(String postId);

    /**
     * 포스트별 배심원 전체 삭제 (파트너 답변 후 재평가 시)
     */
    void deleteByPostId(String postId);
}
