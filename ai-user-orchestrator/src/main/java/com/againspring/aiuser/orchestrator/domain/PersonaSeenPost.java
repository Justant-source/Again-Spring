package com.againspring.aiuser.orchestrator.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "persona_seen_posts")
@IdClass(PersonaSeenPostId.class)
public class PersonaSeenPost {

    @Id
    @Column(name = "persona_id", length = 32, nullable = false)
    private String personaId;

    @Id
    @Column(name = "post_id", length = 32, nullable = false)
    private String postId;

    @Column(name = "seen_at", nullable = false)
    @Builder.Default
    private Instant seenAt = Instant.now();

    @Column(nullable = false)
    @Builder.Default
    private boolean acted = false;
}
