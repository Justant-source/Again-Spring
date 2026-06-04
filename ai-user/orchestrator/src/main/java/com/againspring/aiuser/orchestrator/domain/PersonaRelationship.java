package com.againspring.aiuser.orchestrator.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "persona_relationships")
public class PersonaRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "persona_id", length = 32, nullable = false)
    private String personaId;

    @Column(name = "other_id", length = 32, nullable = false)
    private String otherId;

    @Column(name = "relation_type", length = 20, nullable = false)
    private String relationType;  // COUPLE/MARRIAGE/FRIEND/FAMILY/PARENT_CHILD/WORK/KOREAN_SPECIFIC

    @Column(precision = 3, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal closeness = new BigDecimal("0.50");

    @Column(length = 12, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";
}
