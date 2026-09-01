package com.againspring.repository.marketing;

import com.againspring.domain.marketing.XPersonaExample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface XPersonaExampleRepository extends JpaRepository<XPersonaExample, Long> {

    boolean existsByTweetId(String tweetId);

    Optional<XPersonaExample> findByTweetId(String tweetId);

    boolean existsBySourceAndOperatorBody(XPersonaExample.Source source, String operatorBody);

    long countBySourceAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
        XPersonaExample.Source source, Instant startInclusive, Instant endExclusive);

    List<XPersonaExample> findTop40BySourceOrderByCreatedAtDesc(XPersonaExample.Source source);

    List<XPersonaExample> findTop20BySourceOrderByCreatedAtDesc(XPersonaExample.Source source);
}
