package com.againspring.repository.marketing;

import com.againspring.domain.marketing.XPersonaEval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface XPersonaEvalRepository extends JpaRepository<XPersonaEval, Long> {

    boolean existsByExampleId(Long exampleId);

    List<XPersonaEval> findByCreatedAtGreaterThanEqual(Instant since);

    List<XPersonaEval> findTop500ByIdGreaterThanOrderByIdAsc(long sinceId);
}
