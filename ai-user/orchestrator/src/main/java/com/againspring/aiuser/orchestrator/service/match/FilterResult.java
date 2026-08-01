package com.againspring.aiuser.orchestrator.service.match;

import java.util.List;

/** Outcome of {@link PersonaHardFilter} — reasons include PASS:/FAIL:/UNEVALUATED: axes. */
public record FilterResult(boolean passed, List<String> reasons) {
    public FilterResult {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
