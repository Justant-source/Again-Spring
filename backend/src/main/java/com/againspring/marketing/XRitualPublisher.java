package com.againspring.marketing;

import com.againspring.domain.marketing.XOpsAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Morning/night ritual photo posts at the configured KST minute, once per slot per day.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XRitualPublisher {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final String SLOT_MORNING = "morning";
    static final String SLOT_NIGHT = "night";

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final MarketingXOpsSettingsService settingsService;
    private final AsmClient asmClient;
    private final XCommentComposer composer;
    private final XOpsActionLedger ledger;

    @Value("${llm.enabled:true}")
    private boolean llmEnabled;

    public void runIfDue(Instant now) {
        MarketingXOpsSettingsService.XOpsSettings settings = settingsService.get();
        if (!settings.ritualEnabled() || !llmEnabled) {
            return;
        }
        String slot = slotAt(now, settings);
        if (slot == null) {
            return;
        }
        String sentinel = sentinelTarget(slot, now);
        if (ledger.alreadyHandled(sentinel)) {
            return;
        }

        XCommentComposer.Draft draft = composer.composeRitual(slot);
        if (draft == null || draft.skip() || draft.body() == null || draft.body().isBlank()) {
            String reason = draft != null && draft.skipReason() != null
                ? draft.skipReason() : "NO_VOICE";
            ledger.recordSkipped(XOpsAction.Kind.RITUAL, sentinel, reason, now);
            return;
        }

        try {
            AsmClient.XPublishResult result = asmClient.publishRitual(slot, draft.body());
            if (result != null && result.ok()) {
                ledger.recordPosted(
                    XOpsAction.Kind.RITUAL,
                    sentinel,
                    null,
                    null,
                    result.tweetId(),
                    draft.body(),
                    now);
            } else {
                ledger.recordFailed(XOpsAction.Kind.RITUAL, sentinel, "PUBLISH_FAILED", now);
            }
        } catch (Exception e) {
            log.warn("[x-ritual] publish failed slot={}: {}", slot, e.getMessage());
            ledger.recordFailed(XOpsAction.Kind.RITUAL, sentinel, "ASM_ERROR", now);
        }
    }

    static String sentinelTarget(String slot, Instant now) {
        LocalDate kstDate = now.atZone(KST).toLocalDate();
        return "ritual:" + slot + ":" + kstDate;
    }

    static String slotAt(Instant now, MarketingXOpsSettingsService.XOpsSettings settings) {
        LocalTime current = now.atZone(KST).toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        LocalTime morning = parseHhMm(settings.morningTime());
        LocalTime night = parseHhMm(settings.nightTime());
        if (morning != null && current.equals(morning)) {
            return SLOT_MORNING;
        }
        if (night != null && current.equals(night)) {
            return SLOT_NIGHT;
        }
        return null;
    }

    private static LocalTime parseHhMm(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(raw.trim(), HH_MM);
        } catch (Exception e) {
            return null;
        }
    }
}
