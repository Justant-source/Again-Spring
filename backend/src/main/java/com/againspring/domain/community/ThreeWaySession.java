package com.againspring.domain.community;

import com.againspring.domain.enums.ThreeWayStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 3자 중재 세션 (V17 커뮤니티)
 * 포스트 투표 결과 후 중재자와 양쪽 당사자의 3자 대화
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "three_way_sessions")
@EntityListeners(AuditingEntityListener.class)
public class ThreeWaySession {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "party_a_user_id", nullable = false, length = 32)
    private String partyAUserId;

    @Column(name = "party_b_user_id", length = 32)
    private String partyBUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ThreeWayStatus status = ThreeWayStatus.WAITING;

    @Column(length = 64, unique = true)
    private String inviteToken;

    @Column(length = 50)
    private String category;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
