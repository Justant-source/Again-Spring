package com.againspring.seed;

import com.againspring.domain.Message;
import com.againspring.domain.Report;
import com.againspring.domain.Session;
import com.againspring.domain.User;
import com.againspring.domain.enums.ConflictType;
import com.againspring.domain.enums.MessageSender;
import com.againspring.domain.enums.RelationType;
import com.againspring.domain.enums.SessionStatus;
import com.againspring.repository.MessageRepository;
import com.againspring.repository.ReportRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.seed.dto.SeedMessage;
import com.againspring.seed.dto.SeedReport;
import com.againspring.seed.dto.SeedScenario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 시드 시나리오를 DB 엔티티로 변환 및 저장
 * 시간 역산, 메시지 변환, 리포트 생성 담당
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeedScenarioBuilder {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final ReportRepository reportRepository;

    /**
     * 모든 시나리오를 DB에 저장
     * @return [sessionCount, messageCount, reportCount]
     */
    @Transactional
    public int[] buildAll(List<SeedScenario> scenarios, List<User> users) {
        Map<String, User> userByEmail = users.stream()
                .collect(Collectors.toMap(User::getEmail, u -> u));

        int sessionCount = 0;
        int messageCount = 0;
        int reportCount = 0;

        for (SeedScenario sc : scenarios) {
            User owner = userByEmail.get(sc.ownerEmail());
            if (owner == null) {
                log.warn("Scenario {} owner not found: {}", sc.id(), sc.ownerEmail());
                continue;
            }

            // 1. Session 생성
            Session session = buildSession(sc, owner);
            Session savedSession = sessionRepository.save(session);
            sessionCount++;

            // 2. Messages 생성 및 저장
            List<Message> messages = buildMessages(savedSession, sc);
            messageRepository.saveAll(messages);
            messageCount += messages.size();

            // 3. Report 생성 (있으면)
            if (sc.report() != null) {
                Report report = buildReport(savedSession, sc, userByEmail);
                reportRepository.save(report);
                savedSession.setReportId(report.getId());
                sessionRepository.save(savedSession);
                reportCount++;
            }

            log.debug("Built scenario {}: session={}, messages={}, report={}",
                    sc.id(), savedSession.getId(), messages.size(), sc.report() != null ? "yes" : "no");
        }

        return new int[]{sessionCount, messageCount, reportCount};
    }

    /**
     * Session 엔티티 생성
     */
    private Session buildSession(SeedScenario sc, User owner) {
        Instant now = Instant.now();
        Instant sessionCreatedAt = now.minus(Duration.ofMinutes(sc.sessionCreatedMinutesAgo()));

        // V47~: 중·소분류 제거 — majorId만 잔존
        Session.Category category = new Session.Category();
        category.majorId = sc.categoryMajor();
        category.customText = null;

        Session session = Session.builder()
                .id(UUID.randomUUID().toString().replace("-", "").substring(0, 32))
                .createdByUserId(owner.getId())
                .inviteeUserId(sc.soloMode() ? null : (sc.inviteeGuestName() == null ?
                    UUID.randomUUID().toString().replace("-", "").substring(0, 32) : null))
                .inviteeGuestName(sc.inviteeGuestName())
                .relationType(RelationType.valueOf(sc.relationType()))
                .category(category)
                .status(SessionStatus.valueOf(sc.status()))
                .soloMode(sc.soloMode())
                .userAMessageCount(0)
                .userBMessageCount(0)
                .partnerJoinedAt(sc.partnerJoinedMinutesAgo() != null ?
                    sessionCreatedAt.plus(Duration.ofMinutes(sc.partnerJoinedMinutesAgo())) : null)
                .finalizeSuggestedAt(sc.finalizeSuggestedSet() && sc.report() != null ?
                    (sessionCreatedAt.plus(Duration.ofMinutes(sc.sessionCreatedMinutesAgo() * 2))
                        .minus(Duration.ofMinutes(2))) : null)
                .finalizeAgreedByA(sc.finalizeAgreedByA())
                .finalizeAgreedByB(sc.finalizeAgreedByB())
                .crisisFlags(sc.crisisFlags())
                .inviteToken(sc.inviteToken())
                .contentExpiresAt(sessionCreatedAt.plus(Duration.ofDays(30)))
                .completedAt(SessionStatus.COMPLETED.toString().equals(sc.status()) ?
                    sessionCreatedAt.plus(Duration.ofMinutes(sc.sessionCreatedMinutesAgo())) : null)
                .createdAt(sessionCreatedAt)
                .updatedAt(now)
                .build();

        return session;
    }

    /**
     * Message 리스트 생성
     */
    private List<Message> buildMessages(Session session, SeedScenario sc) {
        List<Message> messages = new ArrayList<>();
        Instant sessionCreatedAt = session.getCreatedAt();

        for (SeedMessage seedMsg : sc.messages()) {
            Instant messageCreatedAt = sessionCreatedAt.plus(Duration.ofMinutes(seedMsg.deltaMinutes()));

            Message msg = Message.builder()
                    .sessionId(session.getId())
                    .sender(MessageSender.valueOf(seedMsg.sender()))
                    .content(seedMsg.content())
                    .charCount(seedMsg.content().length())
                    .isFinalizeSuggestion(seedMsg.isFinalizeSuggestion())
                    .isPartnerJoinNotice(seedMsg.isPartnerJoinNotice())
                    .crisisLevel(seedMsg.crisisLevel())
                    .createdAt(messageCreatedAt)
                    .build();

            messages.add(msg);
        }

        return messages;
    }

    /**
     * Report 엔티티 생성
     */
    private Report buildReport(Session session, SeedScenario sc, Map<String, User> userByEmail) {
        SeedReport seedReport = sc.report();
        User ownerUser = userByEmail.get(sc.ownerEmail());

        // 참여자 A
        Report.Participant participantA = Report.Participant.builder()
                .userId(session.getCreatedByUserId())
                .nicknameSnapshot(ownerUser.getNickname())
                .guestName(null)
                .build();

        // 참여자 B (solo이면 null)
        Report.Participant participantB = null;
        if (!sc.soloMode() && session.getInviteeUserId() != null) {
            User userB = userByEmail.get(sc.ownerEmail()); // 실제로는 다른 사용자여야 함
            participantB = Report.Participant.builder()
                    .userId(session.getInviteeUserId())
                    .nicknameSnapshot(userB != null ? userB.getNickname() : "Partner")
                    .guestName(sc.inviteeGuestName())
                    .build();
        }

        // 4 Horsemen 분석
        Report.FourHorsemenAnalysis fourHorsemen = Report.FourHorsemenAnalysis.builder()
                .criticism(buildHorsemenItem(seedReport.criticismScore(), List.of()))
                .defensiveness(buildHorsemenItem(seedReport.defensivenessScore(), List.of()))
                .contempt(buildHorsemenItem(seedReport.contemptScore(), List.of()))
                .stonewalling(buildHorsemenItem(seedReport.stonewallingScore(), List.of()))
                .build();

        // NVC 스크립트
        Report.NVCScripts nvcScripts = Report.NVCScripts.builder()
                .aToB(Report.NVCScripts.NVCScript.builder()
                        .observation(seedReport.nvcObservationA())
                        .feeling(seedReport.nvcFeelingA())
                        .need(seedReport.nvcNeedA())
                        .request(seedReport.nvcRequestA())
                        .build())
                .bToA(!sc.soloMode() ? Report.NVCScripts.NVCScript.builder()
                        .observation(seedReport.nvcObservationB())
                        .feeling(seedReport.nvcFeelingB())
                        .need(seedReport.nvcNeedB())
                        .request(seedReport.nvcRequestB())
                        .build() : null)
                .build();

        // 화해 기여도 (solo면 null)
        Report.ContributionRatio contributionRatio = null;
        if (!sc.soloMode() && seedReport.ratioA() != null && seedReport.ratioB() != null) {
            contributionRatio = Report.ContributionRatio.builder()
                    .a(seedReport.ratioA())
                    .b(seedReport.ratioB())
                    .label(Report.ContributionRatio.RatioLabel.builder()
                            .a(seedReport.ratioA() > seedReport.ratioB() ? "더 많은 노력" : "균형")
                            .b(seedReport.ratioB() > seedReport.ratioA() ? "더 많은 노력" : "균형")
                            .build())
                    .rationale("Seed data generated ratio")
                    .build();
        }

        return Report.builder()
                .id(UUID.randomUUID().toString().replace("-", "").substring(0, 32))
                .sessionId(session.getId())
                .participantA(participantA)
                .participantB(participantB)
                .conflictType(ConflictType.valueOf(seedReport.conflictType()))
                .soloMode(sc.soloMode())
                .contributionRatio(contributionRatio)
                .fourHorsemen(fourHorsemen)
                .nvcScripts(nvcScripts)
                .repairSuggestions(seedReport.repairSuggestions())
                .suggestedApproach(seedReport.fourSentenceDraft())
                .createdAt(session.getCompletedAt() != null ?
                    session.getCompletedAt() : Instant.now())
                .build();
    }

    /**
     * 점수(0~10)를 HorsemenItem으로 변환
     */
    private Report.FourHorsemenAnalysis.HorsemenItem buildHorsemenItem(int score, List<String> examples) {
        String intensity;
        if (score <= 3) {
            intensity = "low";
        } else if (score <= 6) {
            intensity = "medium";
        } else {
            intensity = "high";
        }

        return Report.FourHorsemenAnalysis.HorsemenItem.builder()
                .detected(score >= 4)
                .intensity(intensity)
                .examples(examples.isEmpty() ? List.of("Example from seed data") : examples)
                .build();
    }
}
