package com.againspring.repository.marketing;

import com.againspring.domain.marketing.XFollowAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface XFollowAccountRepository extends JpaRepository<XFollowAccount, Long> {

    Optional<XFollowAccount> findByHandle(String handle);

    List<XFollowAccount> findByHandleIn(Collection<String> handles);

    List<XFollowAccount> findByStatus(XFollowAccount.Status status);

    /** Unfollow candidates before the follower-count / interaction checks: FOLLOWING, not mutual, followed before cutoff. */
    List<XFollowAccount> findByStatusAndFollowsUsFalseAndFollowedAtLessThanEqual(
        XFollowAccount.Status status, Instant followedAtCutoff);

    List<XFollowAccount> findByStatusAndUnfollowedAtGreaterThanEqual(
        XFollowAccount.Status status, Instant since);
}
