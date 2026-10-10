package com.againspring.repository.marketing;

import com.againspring.domain.marketing.XOpsAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface XOpsActionRepository extends JpaRepository<XOpsAction, Long> {

    boolean existsByTargetTweetId(String targetTweetId);

    boolean existsByRefPostId(Long refPostId);

    List<XOpsAction> findByStatusAndKindInAndCreatedAtGreaterThanEqual(
        XOpsAction.Status status, Collection<XOpsAction.Kind> kinds, Instant since);

    long countByKindAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
        XOpsAction.Kind kind, XOpsAction.Status status, Instant startInclusive, Instant endExclusive);

    interface AuthorPosted {
        String getHandle();

        long getCnt();

        Instant getLastAt();
    }

    @Query("select a.targetAuthorHandle as handle, count(a) as cnt, max(a.createdAt) as lastAt "
        + "from XOpsAction a where a.kind = :kind and a.status = :status "
        + "and a.targetAuthorHandle in :handles and a.createdAt >= :since "
        + "group by a.targetAuthorHandle")
    List<AuthorPosted> postedByAuthorSince(@Param("kind") XOpsAction.Kind kind,
        @Param("status") XOpsAction.Status status,
        @Param("handles") Collection<String> handles, @Param("since") Instant since);

    long countByOurPostTweetIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
        String ourPostTweetId, XOpsAction.Status status, Instant startInclusive, Instant endExclusive);
}
