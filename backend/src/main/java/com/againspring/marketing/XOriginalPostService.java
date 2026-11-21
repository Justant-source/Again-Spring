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
 * Story-scoop original posts at the KST slots in {@code marketing.x.original_post_times}
 * (fallback 12:30,19:30). The body carries no link (links cut reach); the story link is posted
 * as a self-reply after the original succeeds. Alternates opinion / question posts. Default off.
 * Do not enable in prod before the 95% mimicry gate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XOriginalPostService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final String PLATFORM_X_THREAD = "x_thread";
    static final int CANDIDATE_LIMIT = 30;
    /** Max LLM compose attempts per slot (token guard). */
    static final int MAX_COMPOSE_ATTEMPTS = 3;

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
        List<LocalTime> times = settingsService.originalPostTimes();
        String slot = slotAt(now, times);
        if (slot == null) {
            return;
        }
        XCommentComposer.OriginalKind kind = kindFor(now, times, slot);
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

        int attempts = 0;
        for (Post post : ranked) {
            if (attempts >= MAX_COMPOSE_ATTEMPTS) {
                break;
            }
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
            attempts++;
            XCommentComposer.Draft draft = composeOriginal(summary, kind);
            if (draft == null || draft.skip() || draft.body() == null || draft.body().isBlank()) {
                String reason = draft != null && draft.skipReason() != null
                    ? draft.skipReason() : "UNSURE";
                ledger.recordSkipped(XOpsAction.Kind.ORIGINAL, post.getId(), reason, now);
                continue;
            }
            String text = draft.body().strip();
            if (XCommentComposer.originalTooLong(text)) {
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
                    postLinkReply(result.tweetId(), link, post.getId());
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

    /** Link self-reply. Failure never affects the already-recorded original. */
    private void postLinkReply(String originalTweetId, String link, String postId) {
        if (originalTweetId == null || originalTweetId.isBlank()) {
            log.warn("[x-original] no tweetId, link reply skipped postId={}", postId);
            return;
        }
        try {
            AsmClient.XPublishResult r = asmClient.publishX(linkReplyText(link), originalTweetId, null, null);
            if (r == null || !r.ok()) {
                log.warn("[x-original] link reply not ok postId={}", postId);
            }
        } catch (Exception e) {
            log.warn("[x-original] link reply failed postId={}: {}", postId, e.getMessage());
        }
    }

    static String linkReplyText(String link) {
        return "전문은 여기서 볼 수 있어요\n" + link;
    }

    XCommentComposer.Draft composeOriginal(String storySummary, XCommentComposer.OriginalKind kind) {
        return composer.composeOriginal(storySummary, kind);
    }

    /** Slot id ("HHmm") when {@code now} (KST, minute precision) equals a configured time; else null. */
    static String slotAt(Instant now, List<LocalTime> times) {
        LocalTime current = now.atZone(KST).toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        if (times != null && times.contains(current)) {
            return String.format("%02d%02d", current.getHour(), current.getMinute());
        }
        return null;
    }

    /** Deterministic alternation across slots and days: (epochDay + slotIndex) parity. */
    static XCommentComposer.OriginalKind kindFor(Instant now, List<LocalTime> times, String slot) {
        int idx = 0;
        for (int i = 0; i < times.size(); i++) {
            LocalTime t = times.get(i);
            if (String.format("%02d%02d", t.getHour(), t.getMinute()).equals(slot)) {
                idx = i;
                break;
            }
        }
        long day = now.atZone(KST).toLocalDate().toEpochDay();
        return (day + idx) % 2 == 0
            ? XCommentComposer.OriginalKind.OPINION
            : XCommentComposer.OriginalKind.QUESTION;
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
