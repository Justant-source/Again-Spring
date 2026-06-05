package com.againspring.domain.inquiry;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 문의 (V64)
 * 사용자가 제출한 문의와 관리자 응답 추적
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inquiries")
@EntityListeners(AuditingEntityListener.class)
public class Inquiry {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, length = 32)
    private String userId;

    @Column(length = 200)
    private String subject;

    @Column(length = 50)
    private String category;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";

    @Column(name = "assignee_user_id", length = 32)
    private String assigneeUserId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
