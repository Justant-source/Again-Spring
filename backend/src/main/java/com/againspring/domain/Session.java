package com.againspring.domain;

import com.againspring.domain.enums.ConflictType;
import com.againspring.domain.enums.RelationType;
import com.againspring.domain.enums.SessionStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import io.hypersistence.utils.hibernate.type.json.JsonType;

/**
 * 세션 엔티티 (MariaDB JPA)
 * A(초대 발신자)와 B(초대 수신자) 간 중재 세션
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sessions")
@EntityListeners(AuditingEntityListener.class)
public class Session {

    @Id
    @Column(length = 32)
    private String id;

    @Column(length = 32, nullable = false, name = "created_by_user_id")
    private String createdByUserId;

    @Column(length = 32, name = "invitee_user_id")
    private String inviteeUserId;

    @Column(length = 100, name = "invitee_guest_name")
    private String inviteeGuestName;

    @Column(length = 64, unique = true, name = "invite_token")
    private String inviteToken;

    @Column(name = "invite_expires_at")
    private Instant inviteExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private RelationType relationType;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ConflictType conflictType;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private SessionStatus status;

    @Column
    @Builder.Default
    private Integer currentTurn = 0;

    @Column(length = 8, name = "current_role_value")
    private String currentRole;

    @Column
    @Builder.Default
    private Boolean soloMode = false;

    // ===== V1.5 신규 필드 (카톡식 채팅) =====

    @Column(name = "user_a_message_count", nullable = false)
    @Builder.Default
    private Integer userAMessageCount = 0;

    @Column(name = "user_b_message_count")
    @Builder.Default
    private Integer userBMessageCount = 0;

    @Column(name = "partner_joined_at")
    private Instant partnerJoinedAt;        // 상대 합류 시점 (Solo→Duo 전이 시각)

    @Column(name = "finalize_suggested_at")
    private Instant finalizeSuggestedAt;    // AI가 종료 권유한 시각

    @Column(name = "finalize_agreed_by_a", nullable = false)
    @Builder.Default
    private Boolean finalizeAgreedByA = false;

    @Column(name = "finalize_agreed_by_b", nullable = false)
    @Builder.Default
    private Boolean finalizeAgreedByB = false;

    // ===== Phase B: 턴 간 심리 점수 누적 =====

    @Type(JsonType.class)
    @Column(name = "horsemen_history", columnDefinition = "JSON")
    private List<HorsemenTurnEntry> horsemenHistory;

    @Type(JsonType.class)
    @Column(name = "nvc_completion_history", columnDefinition = "JSON")
    private List<NvcTurnEntry> nvcCompletionHistory;

    @Column(name = "current_focus", length = 50)
    private String currentFocus;

    // ===== Phase C: Duo 균형 추적 =====

    @Column(name = "user_a_emotion_intensity", precision = 3, scale = 2)
    private java.math.BigDecimal userAEmotionIntensity;

    @Column(name = "user_b_emotion_intensity", precision = 3, scale = 2)
    private java.math.BigDecimal userBEmotionIntensity;

    // ===== Phase D: 컨텍스트 알고리즘 =====
    // 권위본: shared/docs/policies/context-algorithm.md §4.1
    // 모두 *유도값* — psychology-model.md "추적 변수 4개 제한" 준수 (새 변수 아님, §1.3 참조)

    @Type(JsonType.class)
    @Column(name = "user_state_history", columnDefinition = "JSON")
    private List<UserStateEntry> userStateHistory;

    @Type(JsonType.class)
    @Column(name = "issue_context", columnDefinition = "JSON")
    private IssueContext issueContext;

    @Type(JsonType.class)
    @Column(name = "question_queue_a", columnDefinition = "JSON")
    private List<PendingQuestion> questionQueueA;

    @Type(JsonType.class)
    @Column(name = "question_queue_b", columnDefinition = "JSON")
    private List<PendingQuestion> questionQueueB;

    // ===== 기존 필드 유지 =====

    @Column(length = 32, name = "report_id")
    private String reportId;

    @Column(name = "content_expires_at")
    private Instant contentExpiresAt;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON")
    private List<String> crisisFlags;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON")
    private List<String> crisisDetections;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    // ===== V1.5: 6턴 관계 제거 (Turn 엔티티 삭제됨) =====
    // @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    // @OrderBy("turnNumber ASC")
    // private List<Turn> turns = new ArrayList<>();
    // public void addTurn(Turn turn) { ... } — REMOVED

    // ===== Convenience methods =====

    public String getUserAId() {
        return createdByUserId;
    }

    public String getUserBId() {
        return inviteeUserId;
    }

    public void setUserAId(String id) {
        this.createdByUserId = id;
    }

    public void setUserBId(String id) {
        this.inviteeUserId = id;
    }

    /**
     * Per-turn 4 Horsemen intensity entry (Phase B).
     * Intensities are 0.0–1.0; 0 if not detected.
     */
    public static class HorsemenTurnEntry {
        public Integer turn;
        public String sender;
        public Double criticism;
        public Double contempt;
        public Double defensiveness;
        public Double stonewalling;
    }

    /**
     * Per-turn NVC 4-step completion entry (Phase B).
     */
    public static class NvcTurnEntry {
        public Integer turn;
        public String sender;
        public Boolean observation;
        public Boolean feeling;
        public Boolean need;
        public Boolean request;
    }

    /**
     * 카테고리 임베디드 (JSON 저장)
     */
    public static class Category {
        public String majorId;
        public String middleId;
        public String minorId;
        public String customText;

        @Override
        public String toString() {
            return "Category{"
                    + "majorId='"
                    + majorId
                    + '\''
                    + ", middleId='"
                    + middleId
                    + '\''
                    + ", minorId='"
                    + minorId
                    + '\''
                    + ", customText='"
                    + customText
                    + '\''
                    + '}';
        }
    }

    // ===== Phase D inner classes =====
    // 권위본: shared/docs/policies/context-algorithm.md §4
    // 모두 public static + public 필드 — 기존 HorsemenTurnEntry/NvcTurnEntry 패턴과 동일

    /** Phase D UserState 7종 — 4 Horsemen + NVC 신호에서 *유도*되는 라벨. 사용자에게 절대 노출 금지. */
    public enum UserState {
        OPENING, VENTING, DEFENSIVE, BLAMING, REFLECTING, NEGOTIATING, RESOLVING
    }

    /** Phase D QuestionQueue Intent 7종 — Gottman 4 Antidotes 및 NVC 4단계와 1:1 대응. */
    public enum Intent {
        SEEK_FACT,
        SEEK_FEELING,
        SEEK_NEED,
        BRIDGE_PERSPECTIVE,
        REFLECT_PATTERN,
        INVITE_REPAIR,        // psychology-model.md "한 응답 1제안" 원칙으로 세션당 1회만 발화
        WELCOME_PARTNER       // B 진입 시 1회만 — 큐에서 절대 evict 금지
    }

    /** Phase D ratio-calculation.md §5요소와의 매핑. */
    public enum RatioElement {
        BOUNDARY,     // boundaryViolation
        HORSEMEN,     // fourHorsemenUsage (자동 매핑)
        REPAIR,       // repairAttemptLack
        PERSPECTIVE,  // perspectiveTakingLack
        ESCALATION    // escalationContribution
    }

    /** Phase D — 매 턴 UserState 이력 항목. turn/sender는 horsemenHistory와 동일 인덱싱. */
    public static class UserStateEntry {
        public Integer turn;
        public String sender;          // USER_A | USER_B
        public UserState state;
        public String evidenceSnippet; // 30자 이내, 메시지 발췌
        public Double confidence;      // 0.0~1.0
        public String derivedFrom;     // "horsemen.criticism=0.5" 같은 산출 근거 (디버그용)
    }

    /** Phase D — 누적 이슈 컨텍스트 4슬롯. session.currentFocus를 대체하는 구조화된 형태. */
    public static class IssueContext {
        public String headline;                      // 50자 이내, currentFocus 대체
        public List<IssueFact> facts = new ArrayList<>();
        public List<NeedSlot> namedNeeds = new ArrayList<>();
        public List<UnresolvedThread> threads = new ArrayList<>();
        public Integer revision = 0;
        public Instant lastUpdatedAt;
    }

    /** Phase D — NVC §관찰 단계 누적 사실. */
    public static class IssueFact {
        public String text;               // 80자 이내
        public String source;             // "USER_A_T3" 형식
        public Boolean confirmedByOther = false;  // Duo 모드에서만 의미
        public RatioElement contributesTo;         // nullable — ratio 5요소 중 하나
        public String categoryRule;                // nullable — categories.md 룰 ID
    }

    /** Phase D — NVC §욕구 단계 누적. */
    public static class NeedSlot {
        public String text;                  // 60자 이내
        public String owner;                 // USER_A | USER_B
        public Integer firstMentionedTurn;
        public RatioElement contributesTo;   // 주로 PERSPECTIVE
    }

    /** Phase D — Gottman *unaddressed bid* 응용. 미해결 갈래. */
    public static class UnresolvedThread {
        public String text;                   // 60자 이내
        public String origin;                 // 어느 메시지에서 등장했나
        public Integer mentionedTurn;
        public Boolean addressedByQueue = false;  // PQ에 이미 있으면 true
        public Integer ageInTurns = 0;
    }

    /** Phase D — 질문 큐 항목. LLM에게 의도 단서만 전달, 그대로 발화하지 않음. */
    public static class PendingQuestion {
        public String id;                    // UUID
        public Intent intent;
        public String target;                // USER_A | USER_B
        public String text;                  // 80자 이내, 의도 단서
        public String hookFromIssue;         // 어느 thread/fact/need에서 나왔나
        public RatioElement antidoteFor;     // nullable
        public Double priority = 0.0;        // 0.0~1.0, 매 턴 재계산
        public Integer createdTurn;
        public Integer ageInTurns = 0;
        public Boolean asked = false;
        public Integer askedTurn;
        public String categoryRuleApplied;   // nullable
    }
}
