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
}
