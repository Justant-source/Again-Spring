package com.againspring.repository.announcement;

import com.againspring.domain.announcement.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 공지사항 저장소 (V65)
 */
@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, String> {

    /**
     * 현재 활성 공지사항 조회 (시간 범위 내)
     */
    List<Announcement> findByIsActiveTrueAndStartsAtBeforeAndEndsAtAfter(
            Instant startsAt, Instant endsAt);

    /**
     * 활성 공지사항 조회 (startsAt이 NULL이거나 과거, endsAt이 NULL이거나 미래)
     */
    List<Announcement> findByIsActiveTrueOrderByCreatedAtDesc();

    /**
     * 활성 공지사항 조회 (페이지네이션)
     */
    Page<Announcement> findByIsActiveTrueOrderByCreatedAtDesc(Pageable pageable);
}
