package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.AiPostInterestedPersona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiPostInterestedPersonaRepository extends JpaRepository<AiPostInterestedPersona, Long> {

    boolean existsByPostIdAndPersonaId(String postId, String personaId);

    List<AiPostInterestedPersona> findByPostIdOrderByIdAsc(String postId);

    /** Higher score first; null scores last. Used as human-reply candidate pool. */
    @Query("select p from AiPostInterestedPersona p where p.postId = :postId " +
           "order by case when p.score is null then 1 else 0 end, p.score desc, p.id asc")
    List<AiPostInterestedPersona> findByPostIdOrderByScoreDesc(@Param("postId") String postId);
}
