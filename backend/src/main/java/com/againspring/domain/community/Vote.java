package com.againspring.domain.community;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 투표 (V17 커뮤니티)
 * 사용자 또는 배심원의 투표 기록
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "votes")
@EntityListeners(AuditingEntityListener.class)
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String postId;

    @Column(nullable = false)
    private Long optionId;

    @Column(name = "voter_user_id", length = 32)
    private String voterUserId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
