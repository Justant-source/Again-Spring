package com.againspring.service.admin;

import com.againspring.api.dto.request.CreateAnnouncementRequest;
import com.againspring.domain.User;
import com.againspring.domain.announcement.Announcement;
import com.againspring.domain.enums.NotificationType;
import com.againspring.repository.UserRepository;
import com.againspring.repository.announcement.AnnouncementRepository;
import com.againspring.service.NotificationWriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 공지사항 관리 서비스
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;
    private final NotificationWriteService notificationWriteService;

    /**
     * 공지사항 생성
     */
    @Transactional
    public Announcement createAnnouncement(CreateAnnouncementRequest request, String createdBy) {
        String id = "announce_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        Announcement announcement = Announcement.builder()
            .id(id)
            .title(request.getTitle())
            .body(request.getBody())
            .level(request.getLevel() != null ? request.getLevel() : "INFO")
            .isActive(false)
            .startsAt(request.getStartsAt())
            .endsAt(request.getEndsAt())
            .createdBy(createdBy)
            .build();

        return announcementRepository.save(announcement);
    }

    /**
     * 공지사항 목록 조회 (페이지네이션)
     */
    @Transactional(readOnly = true)
    public Page<Announcement> getAnnouncements(Pageable pageable, Boolean isActive) {
        if (isActive != null && isActive) {
            return announcementRepository.findByIsActiveTrueOrderByCreatedAtDesc(pageable);
        }
        return announcementRepository.findAll(pageable);
    }

    /**
     * 공지사항 상세 조회
     */
    @Transactional(readOnly = true)
    public Optional<Announcement> getAnnouncement(String id) {
        return announcementRepository.findById(id);
    }

    /**
     * 공지사항 수정
     */
    @Transactional
    public Announcement updateAnnouncement(String id, CreateAnnouncementRequest request) {
        Announcement announcement = announcementRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다"));

        announcement.setTitle(request.getTitle());
        announcement.setBody(request.getBody());
        announcement.setLevel(request.getLevel() != null ? request.getLevel() : "INFO");
        announcement.setStartsAt(request.getStartsAt());
        announcement.setEndsAt(request.getEndsAt());

        return announcementRepository.save(announcement);
    }

    /**
     * 공지사항 삭제
     */
    @Transactional
    public void deleteAnnouncement(String id) {
        announcementRepository.deleteById(id);
    }

    /**
     * 공지사항 활성화
     */
    @Transactional
    public void activateAnnouncement(String id) {
        Announcement announcement = announcementRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다"));
        announcement.setIsActive(true);
        announcementRepository.save(announcement);
    }

    /**
     * 공지사항 비활성화
     */
    @Transactional
    public void deactivateAnnouncement(String id) {
        Announcement announcement = announcementRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다"));
        announcement.setIsActive(false);
        announcementRepository.save(announcement);
    }

    /**
     * 공지사항에 대한 알림 발송 (모든 활성 비게스트 사용자에게)
     */
    @Transactional
    public void notifyAnnouncement(String announcementId) {
        Announcement announcement = announcementRepository.findById(announcementId)
            .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다"));

        // 모든 활성 사용자를 조회 (게스트 제외, 탈퇴 제외, status=ACTIVE)
        userRepository.findAll().stream()
            .filter(user -> !user.isGuest() && user.getDeletedAt() == null && "ACTIVE".equals(user.getStatus()))
            .forEach(user -> {
                String subtitle = announcement.getBody();
                if (subtitle != null && subtitle.length() > 100) {
                    subtitle = subtitle.substring(0, 100) + "...";
                }
                notificationWriteService.send(
                    user.getId(),
                    NotificationType.ADMIN_BROADCAST,
                    announcement.getTitle(),
                    subtitle,
                    null
                );
            });
    }
}
