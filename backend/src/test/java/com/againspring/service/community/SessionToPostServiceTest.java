package com.againspring.service.community;

import com.againspring.api.dto.response.SessionDraftDto;
import com.againspring.domain.Session;
import com.againspring.domain.enums.SessionStatus;
import com.againspring.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * SessionToPostService 테스트 (Phase 5)
 */
@ExtendWith(MockitoExtension.class)
class SessionToPostServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    private SessionToPostService service;

    @BeforeEach
    void setUp() {
        service = new SessionToPostService(sessionRepository);
    }

    @Test
    @DisplayName("참여자가 세션 초안을 정상적으로 추출")
    void extractDraft_successForParticipant() {
        // Given
        String sessionId = "ses_test_001";
        String userA = "user_a_123";
        String userB = "user_b_456";

        Session.Category category = new Session.Category();
        category.majorId = "family";
        category.customText = "결혼/부부";

        Session session = Session.builder()
            .id(sessionId)
            .createdByUserId(userA)
            .inviteeUserId(userB)
            .title("결혼 관계 상담")
            .status(SessionStatus.COMPLETED)
            .completedAt(Instant.now())
            .category(category)
            .build();

        // IssueContext 설정
        Session.IssueContext context = new Session.IssueContext();
        context.headline = "감정 표현 방식의 차이";
        context.revision = 1;
        context.lastUpdatedAt = Instant.now();

        Session.IssueFact fact1 = new Session.IssueFact();
        fact1.text = "USER_A가 직접적 피드백을 선호";
        fact1.source = "USER_A_T3";
        context.facts.add(fact1);

        Session.NeedSlot need1 = new Session.NeedSlot();
        need1.text = "더 공감해주기";
        need1.owner = "USER_A";
        need1.firstMentionedTurn = 3;
        context.namedNeeds.add(need1);

        session.setIssueContext(context);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        // When
        SessionDraftDto draft = service.extractDraft(sessionId, userA);

        // Then
        assertThat(draft).isNotNull();
        assertThat(draft.getSessionId()).isEqualTo(sessionId);
        assertThat(draft.getTitle()).isEqualTo("결혼 관계 상담");
        assertThat(draft.getCategory()).isEqualTo("family");
        assertThat(draft.getBodyRaw()).contains("A님");
        assertThat(draft.getBodyRaw()).doesNotContain("USER_A");
    }

    @Test
    @DisplayName("USER_A/USER_B 라벨이 A님/B님으로 익명화됨")
    void extractDraft_anonymizesUserLabels() {
        // Given
        String sessionId = "ses_test_002";
        String userA = "user_a_123";
        String userB = "user_b_456";

        Session session = Session.builder()
            .id(sessionId)
            .createdByUserId(userA)
            .inviteeUserId(userB)
            .title("갈등 상담")
            .status(SessionStatus.COMPLETED)
            .build();

        Session.IssueContext context = new Session.IssueContext();
        context.headline = "USER_A와 USER_B 간의 소통 문제";
        session.setIssueContext(context);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        // When
        SessionDraftDto draft = service.extractDraft(sessionId, userA);

        // Then
        assertThat(draft.getBodyRaw())
            .contains("A님")
            .contains("B님")
            .doesNotContain("USER_A")
            .doesNotContain("USER_B");
    }

    @Test
    @DisplayName("비참여자 접근 시 AccessDeniedException 발생")
    void extractDraft_throwsForNonParticipant() {
        // Given
        String sessionId = "ses_test_003";
        String userA = "user_a_123";
        String userB = "user_b_456";
        String stranger = "stranger_999";

        Session session = Session.builder()
            .id(sessionId)
            .createdByUserId(userA)
            .inviteeUserId(userB)
            .status(SessionStatus.COMPLETED)
            .build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        // When & Then
        assertThatThrownBy(() -> service.extractDraft(sessionId, stranger))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("세션이 없으면 RuntimeException 발생")
    void extractDraft_throwsWhenSessionNotFound() {
        // Given
        String sessionId = "ses_nonexistent";
        String userId = "user_123";

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.extractDraft(sessionId, userId))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("SESSION_NOT_FOUND");
    }

    @Test
    @DisplayName("IssueContext의 모든 슬롯(facts, needs, threads)이 포함됨")
    void extractDraft_includesAllIssueContextSlots() {
        // Given
        String sessionId = "ses_test_004";
        String userA = "user_a_123";

        Session session = Session.builder()
            .id(sessionId)
            .createdByUserId(userA)
            .inviteeUserId("user_b_456")
            .title("전체 컨텍스트 테스트")
            .status(SessionStatus.COMPLETED)
            .build();

        Session.IssueContext context = new Session.IssueContext();
        context.headline = "전체 컨텍스트 테스트 헤드라인";

        Session.IssueFact fact = new Session.IssueFact();
        fact.text = "사실 1";
        context.facts.add(fact);

        Session.NeedSlot need = new Session.NeedSlot();
        need.text = "욕구 1";
        need.owner = "USER_A";
        context.namedNeeds.add(need);

        Session.UnresolvedThread thread = new Session.UnresolvedThread();
        thread.text = "미해결 갈래 1";
        context.threads.add(thread);

        session.setIssueContext(context);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        // When
        SessionDraftDto draft = service.extractDraft(sessionId, userA);

        // Then
        String body = draft.getBodyRaw();
        assertThat(body).contains("전체 컨텍스트 테스트 헤드라인");
        assertThat(body).contains("사실 1");
        assertThat(body).contains("욕구 1");
        assertThat(body).contains("미해결 갈래 1");
    }

    @Test
    @DisplayName("카테고리가 null일 때 draft의 category도 null")
    void extractDraft_categoryNullWhenNotSet() {
        // Given
        String sessionId = "ses_test_005";
        String userA = "user_a_123";

        Session session = Session.builder()
            .id(sessionId)
            .createdByUserId(userA)
            .inviteeUserId("user_b_456")
            .title("카테고리 없음")
            .status(SessionStatus.COMPLETED)
            .category(null)
            .build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        // When
        SessionDraftDto draft = service.extractDraft(sessionId, userA);

        // Then
        assertThat(draft.getCategory()).isNull();
    }

    @Test
    @DisplayName("User B도 초안 추출 가능 (참여자 검증)")
    void extractDraft_worksForUserB() {
        // Given
        String sessionId = "ses_test_006";
        String userA = "user_a_123";
        String userB = "user_b_456";

        Session session = Session.builder()
            .id(sessionId)
            .createdByUserId(userA)
            .inviteeUserId(userB)
            .title("User B 접근 테스트")
            .status(SessionStatus.COMPLETED)
            .build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        // When
        SessionDraftDto draft = service.extractDraft(sessionId, userB);

        // Then
        assertThat(draft).isNotNull();
        assertThat(draft.getSessionId()).isEqualTo(sessionId);
    }

    @Test
    @DisplayName("IssueContext가 없을 때 기본 메시지 포함")
    void extractDraft_includesDefaultMessageWhenNoIssueContext() {
        // Given
        String sessionId = "ses_test_007";
        String userA = "user_a_123";

        Session session = Session.builder()
            .id(sessionId)
            .createdByUserId(userA)
            .inviteeUserId("user_b_456")
            .title("컨텍스트 없음")
            .status(SessionStatus.COMPLETED)
            .issueContext(null)
            .build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        // When
        SessionDraftDto draft = service.extractDraft(sessionId, userA);

        // Then
        assertThat(draft.getBodyRaw()).contains("이 세션의 대화 내용을 바탕으로 작성되었습니다.");
    }
}
