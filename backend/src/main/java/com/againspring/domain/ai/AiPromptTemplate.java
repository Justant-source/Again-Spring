package com.againspring.domain.ai;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_prompt_template")
public class AiPromptTemplate {

    @Id
    @Column(name = "`key`", nullable = false, length = 100)
    private String key;

    @Column(name = "description", length = 500)
    private String description;

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
