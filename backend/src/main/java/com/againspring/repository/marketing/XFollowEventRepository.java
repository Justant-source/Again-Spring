package com.againspring.repository.marketing;

import com.againspring.domain.marketing.XFollowEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface XFollowEventRepository extends JpaRepository<XFollowEvent, Long> {

    boolean existsByHandleAndDirectionAndKindAndOccurredAt(
        String handle, XFollowEvent.Direction direction, XFollowEvent.Kind kind, Instant occurredAt);

    /** Any action by the account toward us after {@code since} (like / repost / reply). */
    boolean existsByHandleAndDirectionAndOccurredAtGreaterThanEqual(
        String handle, XFollowEvent.Direction direction, Instant since);

    /** Events of a kind/direction in [from, to) — used for KST-day counts (e.g. today's unfollows). */
    long countByDirectionAndKindAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
        XFollowEvent.Direction direction, XFollowEvent.Kind kind, Instant from, Instant to);
}
