package com.againspring.repository.marketing;

import com.againspring.domain.marketing.XPersonaExample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface XPersonaExampleRepository extends JpaRepository<XPersonaExample, Long> {

    boolean existsByTweetId(String tweetId);

    long countBySourceAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
        XPersonaExample.Source source, Instant startInclusive, Instant endExclusive);

    List<XPersonaExample> findTop40BySourceOrderByCreatedAtDesc(XPersonaExample.Source source);

    List<XPersonaExample> findTop20BySourceOrderByCreatedAtDesc(XPersonaExample.Source source);
}
