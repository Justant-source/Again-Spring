package com.againspring.repository.marketing;

import com.againspring.domain.marketing.XTargetAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface XTargetAccountRepository extends JpaRepository<XTargetAccount, String> {

    @Modifying
    @Query(value = "INSERT INTO x_target_account (handle, first_seen_at, last_seen_at, seen_count, posted_count) "
        + "VALUES (:handle, :now, :now, 1, 0) "
        + "ON DUPLICATE KEY UPDATE last_seen_at = :now, seen_count = seen_count + 1",
        nativeQuery = true)
    void upsertSeen(@Param("handle") String handle, @Param("now") Instant now);

    @Modifying
    @Query(value = "INSERT INTO x_target_account (handle, first_seen_at, last_seen_at, seen_count, posted_count, last_posted_at) "
        + "VALUES (:handle, :now, :now, 0, 1, :now) "
        + "ON DUPLICATE KEY UPDATE posted_count = posted_count + 1, last_posted_at = :now",
        nativeQuery = true)
    void upsertPosted(@Param("handle") String handle, @Param("now") Instant now);
}
