package com.againspring.domain.community;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 포스트/댓글 좋아요 (V17 커뮤니티)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "post_likes")
@EntityListeners(AuditingEntityListener.class)
public class PostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 32)
    private String postId;

    @Column(name = "comment_id")
    private Long commentId;

    @Column(nullable = false, length = 32)
    private String userId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
