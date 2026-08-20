package com.againspring.aiuser.orchestrator.service.threadplan;

/** One fill attempt that did not save a scheduled post. */
public record NightlySlotFailure(
        String kind,
        String source,
        String plaza,
        String personaId,
        HoldResult.Outcome outcome,
        String detail
) {
    public static NightlySlotFailure fromHold(String kind, HoldResult result) {
        return new NightlySlotFailure(
                kind,
                result.source(),
                result.plaza(),
                result.personaId(),
                result.outcome(),
                result.detailedReason());
    }

    public String format() {
        return String.format("%s %s", kind == null ? "solo" : kind, detail == null ? "" : detail);
    }
}
