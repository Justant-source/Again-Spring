package com.againspring.repository.marketing;

import com.againspring.domain.marketing.XOpsAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface XOpsActionRepository extends JpaRepository<XOpsAction, Long> {

    boolean existsByTargetTweetId(String targetTweetId);

    long countByKindAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
        XOpsAction.Kind kind, XOpsAction.Status status, Instant startInclusive, Instant endExclusive);

    long countByOurPostTweetIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
        String ourPostTweetId, XOpsAction.Status status, Instant startInclusive, Instant endExclusive);
}
