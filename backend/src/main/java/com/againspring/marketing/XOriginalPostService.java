package com.againspring.marketing;

import com.againspring.domain.community.Post;
import com.againspring.domain.marketing.XOpsAction;
import com.againspring.notification.TelegramNotifier;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.marketing.MarketingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Story-scoop original posts at 12:30 / 19:30 KST. Default off.
 * Do not enable in prod before the 95% mimicry gate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XOriginalPostService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final String SLOT_NOON = "1230";
    static final String SLOT_EVENING = "1930";
    static final String PLATFORM_X_THREAD = "x_thread";
    static final int CANDIDATE_LIMIT = 30;
    static final int ORIGINAL_MAX_CHARS = 140;
    static final int ORIGINAL_MAX_LINES = 3;

    private static final LocalTime NOON = LocalTime.of(12, 30);
    private static final LocalTime EVENING = LocalTime.of(19, 30);

    private final MarketingXOpsSettingsService settingsService;
    private final PostRepository postRepository;
    private final MarketingJobRepository marketingJobRepository;
    private final XCommentComposer composer;
    private final AsmClient asmClient;
    private final XOpsActionLedger ledger;
    private final TelegramNotifier telegramNotifier;

    @Value("${llm.enabled:true}")
    private boolean llmEnabled;

    public void runIfDue(Instant now) {
        MarketingXOpsSettingsService.XOpsSettings settings = settingsService.get();
        if (!settings.originalPostEnabled() || !llmEnabled) {
            return;
        }
        String slot = slotAt(now);
        if (slot == null) {
            return;
        }
        String sentinel = sentinelTarget(slot, now);
        if (ledger.alreadyHandled(sentinel)) {
            return;
        }
        int cap = Math.min(settings.originalPostDailyCap(), settings.storyScoopsPerDay());
        if (ledger.countPostedToday(XOpsAction.Kind.ORIGINAL, now) >= cap) {
            return;
        }

        List<Post> ranked;
        try {
            ranked = postRepository.findPublicRankedForMarketing(CANDIDATE_LIMIT, 0);
        } catch (Exception e) {
            log.warn("[x-original] ranked fetch failed: {}", e.getMessage());
            ledger.recordSkipped(XOpsAction.Kind.ORIGINAL, sentinel, "NO_MATERIAL", now);
            return;
        }
        if (ranked == null || ranked.isEmpty()) {
            ledger.recordSkipped(XOpsAction.Kind.ORIGINAL, sentinel, "NO_MATERIAL", now);
            return;
        }

        for (Post post : ranked) {
            if (post == null || post.getId() == null || post.getId().isBlank()) {
                continue;
            }
            Long refPostId = refPostIdOf(post.getId());
            if (refPostId == null || ledger.alreadyScooped(refPostId)) {
                continue;
            }
            if (hasXThreadJob(post.getId())) {
                continue;
            }
            String summary = storySummary(post);
            if (summary.isBlank()) {
                continue;
            }
            String link = MarketingUtmUrls.buildUrl(post.getId(), "x", "story_scoop");
            XCommentComposer.Draft draft = composeOriginal(summary, link);
            if (draft == null || draft.skip() || draft.body() == null || draft.body().isBlank()) {
                String reason = draft != null && draft.skipReason() != null
                    ? draft.skipReason() : "UNSURE";
                ledger.recordSkipped(XOpsAction.Kind.ORIGINAL, post.getId(), reason, now);
                continue;
            }
            String text = withLink(draft.body(), link);
            if (originalTooLong(text)) {
                ledger.recordSkipped(XOpsAction.Kind.ORIGINAL, post.getId(), "TOO_LONG", now);
                continue;
            }
            try {
                AsmClient.XPublishResult result = asmClient.publishX(text, null, null, null);
                if (result != null && result.ok()) {
                    ledger.recordPosted(
                        XOpsAction.Kind.ORIGINAL,
                        sentinel,
                        post.getId(),
                        null,
                        result.tweetId(),
                        text,
                        now,
                        refPostId);
                    telegramNotifier.send(XOpsTelegramAlerts.originalPosted(result, link, text));
                    return;
                }
                ledger.recordFailed(XOpsAction.Kind.ORIGINAL, sentinel, "PUBLISH_FAILED", now);
                return;
            } catch (Exception e) {
                log.warn("[x-original] publish failed postId={}: {}", post.getId(), e.getMessage());
                ledger.recordFailed(XOpsAction.Kind.ORIGINAL, sentinel, "ASM_ERROR", now);
                return;
            }
        }
        ledger.recordSkipped(XOpsAction.Kind.ORIGINAL, sentinel, "NO_MATERIAL", now);
    }

    /**
     * TODO INTEGRATION: keep calling {@link XCommentComposer#composeOriginal(String, String)}
     * (persona {@code post_style} + TIMELINE_POST few-shot live in the composer).
     */
    XCommentComposer.Draft composeOriginal(String storySummary, String publicLink) {
        return composer.composeOriginal(storySummary, publicLink);
    }

    static String slotAt(Instant now) {
        LocalTime current = now.atZone(KST).toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        if (current.equals(NOON)) {
            return SLOT_NOON;
        }
        if (current.equals(EVENING)) {
            return SLOT_EVENING;
        }
        return null;
    }

    static String sentinelTarget(String slot, Instant now) {
        LocalDate kstDate = now.atZone(KST).toLocalDate();
        return "original:" + slot + ":" + kstDate;
    }

    /** posts.id is VARCHAR; ledger column is BIGINT — parse decimal ids, else a stable hash. */
    static Long refPostIdOf(String postId) {
        if (postId == null || postId.isBlank()) {
            return null;
        }
        String t = postId.trim();
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException e) {
            return Math.abs((long) t.hashCode());
        }
    }

    static String storySummary(Post post) {
        String title = post.getTitle() != null ? post.getTitle().strip() : "";
        String body = post.getBodyPublished() != null ? post.getBodyPublished().strip() : "";
        String raw = title.isBlank() ? body : (body.isBlank() ? title : title + "\n" + body);
        if (raw.length() > 800) {
            return raw.substring(0, 800);
        }
        return raw;
    }

    static String withLink(String body, String link) {
        String text = body.strip();
        if (link == null || link.isBlank() || text.contains(link)) {
            return text;
        }
        return text + "\n" + link;
    }

    static boolean originalTooLong(String body) {
        if (body == null) {
            return true;
        }
        String t = body.trim();
        String withoutUrls = t.replaceAll("https?://\\S+", "").trim();
        if (withoutUrls.length() > ORIGINAL_MAX_CHARS) {
            return true;
        }
        int lines = 1;
        for (int i = 0; i < t.length(); i++) {
            if (t.charAt(i) == '\n') {
                lines++;
                if (lines > ORIGINAL_MAX_LINES) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasXThreadJob(String postId) {
        try {
            return marketingJobRepository.countAnyPlatformJobs(postId, PLATFORM_X_THREAD) > 0
                || marketingJobRepository.countActivePlatformJobs(postId, PLATFORM_X_THREAD) > 0;
        } catch (Exception e) {
            log.warn("[x-original] job lookup failed postId={}: {}", postId, e.getMessage());
            return true;
        }
    }
}
