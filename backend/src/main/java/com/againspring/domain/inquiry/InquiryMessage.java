package com.againspring.domain.inquiry;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 문의 메시지 (V64)
 * 문의에 포함된 개별 메시지 (사용자/관리자)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inquiry_messages")
@EntityListeners(AuditingEntityListener.class)
public class InquiryMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String inquiryId;

    @Column(name = "sender_role", nullable = false, length = 10)
    private String senderRole;

    @Column(name = "sender_user_id", length = 32)
    private String senderUserId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
