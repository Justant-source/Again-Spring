package com.againspring.aiuser.orchestrator.domain;

import java.io.Serializable;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PersonaSeenPostId implements Serializable {
    private String personaId;
    private String postId;
}
