package com.againspring.service.retention;

import com.againspring.domain.Report;
import com.againspring.domain.Session;
import com.againspring.domain.User;
import com.againspring.domain.enums.SessionStatus;
import com.againspring.repository.ReportRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.UserRepository;
import com.againspring.repository.UserRelationshipRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 탈퇴 시 데이터 삭제 서비스
 * GDPR 준수: 사용자가 탈퇴 요청 시 모든 개인정보 정리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDeletionService {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final ReportRepository reportRepository;
    private final UserRelationshipRepository userRelationshipRepository;

    /**
     * 사용자의 모든 데이터 삭제 (완전 삭제 + 익명화)
     *
     * JPA/MariaDB:
     * - 진행 중인 세션 = TERMINATED로 표시
     * - 리포트 = 삭제
     * - 완료된 세션 = 사용자 ID를 "DELETED_USER"로 익명화
     * - User = deletedAt 표시 (소프트 삭제)
     * - UserRelationship = 삭제 (관계는 더 이상 필요 없음)
     *
     * Audit:
     * - SafetyAuditLogger 또는 전용 logger에 기록
     */
    @Transactional
    public void deleteAllForUser(String userId) {
        log.warn("User deletion initiated for user: {}", userId);

        try {
            // Step 1: Find ongoing sessions and mark as TERMINATED
            List<Session> ongoingSessions = sessionRepository.findByCreatedByUserId(userId);
            for (Session session : ongoingSessions) {
                if (!session.getStatus().equals(SessionStatus.COMPLETED) &&
                    !session.getStatus().equals(SessionStatus.TERMINATED)) {
                    session.setStatus(SessionStatus.TERMINATED);
                    sessionRepository.save(session);
                }
            }

            // Step 2: Delete reports linked to user sessions
            for (Session session : ongoingSessions) {
                reportRepository.findBySessionId(session.getId()).ifPresent(reportRepository::delete);
            }

            // Step 3: Anonymize all sessions (both as creator and invitee)
            List<Session> allUserSessions = sessionRepository.findByCreatedByUserIdOrInviteeUserIdOrderByCreatedAtDesc(
                    userId, userId);
            for (Session session : allUserSessions) {
                if (session.getCreatedByUserId().equals(userId)) {
                    session.setCreatedByUserId("DELETED_USER");
                }
                if (session.getInviteeUserId() != null && session.getInviteeUserId().equals(userId)) {
                    session.setInviteeUserId("DELETED_USER");
                }
                sessionRepository.save(session);
            }

            // Step 4: Delete user relationships
            userRelationshipRepository.findByUserAIdOrUserBIdOrderByLastSessionAtDesc(userId, userId)
                    .forEach(userRelationshipRepository::delete);

            // Step 5: Soft delete user
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                user.setDeletedAt(Instant.now());
                userRepository.save(user);
            }

            // Step 6: Audit log
            log.warn("User {} deletion completed. Sessions anonymized, relationships deleted", userId);

        } catch (Exception e) {
            log.error("Error during user deletion for user {}", userId, e);
            throw new RuntimeException("User deletion failed", e);
        }
    }

}
