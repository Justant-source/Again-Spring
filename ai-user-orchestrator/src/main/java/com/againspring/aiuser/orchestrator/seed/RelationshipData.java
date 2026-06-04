package com.againspring.aiuser.orchestrator.seed;

import lombok.*;

@Getter @Setter @NoArgsConstructor
public class RelationshipData {
    private String personaId;
    private String otherId;
    private String relationType;
    private double closeness;
    private String status;
}
