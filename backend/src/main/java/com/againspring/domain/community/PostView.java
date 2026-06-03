package com.againspring.domain.community;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "post_views",
        uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "device_id"}))
public class PostView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false, length = 32)
    private String postId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "viewed_at", nullable = false, updatable = false)
    private Instant viewedAt;

    @PrePersist
    void prePersist() {
        viewedAt = Instant.now();
    }
}
