package com.againspring.repository.ai;

import com.againspring.domain.ai.AiContentCorrection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiContentCorrectionRepository extends JpaRepository<AiContentCorrection, Long> {

    /** 페르소나별 첨삭 이력 (최신순) */
    Page<AiContentCorrection> findByPersonaIdOrderByCreatedAtDesc(String personaId, Pageable pageable);

    /** 페르소나별 활성 주의사항이 있는 첨삭 이력 */
    Page<AiContentCorrection> findByPersonaIdAndPersonaCautionIsNotNullOrderByCreatedAtDesc(
            String personaId, Pageable pageable);
}
