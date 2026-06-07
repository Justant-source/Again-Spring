package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.PostAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 글 분석 캐시 리포지토리. findById(postId) inherited. */
@Repository
public interface PostAnalysisRepository extends JpaRepository<PostAnalysis, String> {
}
