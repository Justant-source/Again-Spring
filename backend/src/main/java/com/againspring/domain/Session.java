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

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("turnNumber ASC")
    private List<Turn> turns = new ArrayList<>();

    public void addTurn(Turn turn) {
        turn.setSession(this);
        turns.add(turn);
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
