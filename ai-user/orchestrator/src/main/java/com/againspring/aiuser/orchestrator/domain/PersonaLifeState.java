package com.againspring.aiuser.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "persona_life_state")
public class PersonaLifeState {

    @Id
    @Column(name = "persona_id", length = 32, nullable = false)
    private String personaId;

    @Column(name = "casual_streak", nullable = false)
    @Builder.Default
    private int casualStreak = 0;

    @Column(name = "ongoing_situation", length = 255, nullable = false)
    @Builder.Default
    private String ongoingSituation = "";

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
