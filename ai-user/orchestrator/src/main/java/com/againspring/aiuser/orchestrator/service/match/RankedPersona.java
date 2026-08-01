package com.againspring.aiuser.orchestrator.service.match;

import java.util.List;

/** Scored persona after hard filter + author/comment score aggregation. */
public record RankedPersona(
        String personaId,
        double score,
        double semanticScore,
        double registerMatch,
        double explicitFactMatchRatio,
        double interestCategoryScore,
        List<String> reasons,
        List<String> matchedCapsuleTypes,
        boolean fromFallback
) {
    public RankedPersona {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        matchedCapsuleTypes = matchedCapsuleTypes == null ? List.of() : List.copyOf(matchedCapsuleTypes);
    }
}
