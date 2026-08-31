package com.againspring.service.community;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Schema guard for channel {@code sibom_plan} — normalizes entries in code, never retries LLM.
 *
 * @see docs/shared/marketing/70-policy/sibom-video-insertion.md §5.2
 */
public final class SibomPlanGuard {

    /**
     * Single audit log entry: action, image_id, and reason text.
     */
    public record GuardLogEntry(String action, String imageId, String reason) {}

    /**
     * Result of guarding with audit trail.
     */
    public record GuardResult(List<SibomPlanItem> items, List<GuardLogEntry> log) {}

    /**
     * Soft-fill pool (§4.3). Catalog ids only; situation-specific cuts forbidden.
     * Expanded 7→14 for the 60-image catalog (2026-08-22): every entry is people()==1
     * and each occupies a distinct swap_group, so a single top-up pass (topUpWithSoftFill)
     * can draw up to 14 non-duplicate generic reaction shots instead of always the same 7.
     * All captions are catalog defaults (&lt;=10 chars, verified against presets.maxChars).
     */
    public static final List<String> SOFT_FILL_POOL = List.of(
            "drained",
            "curled-up",
            "stunned",
            "swallow-words",
            "indignant",
            "side-glance",
            "relieved",
            "guilt-heavy",
            "walking-away",
            "overloaded",
            "money-trouble",
            "health-ignored",
            "jealous-envy",
            "burst-crying"
    );

    public static final Set<String> SOFT_FILL_POOL_SET = Set.copyOf(SOFT_FILL_POOL);

    public static final int MAX_REELS = 5;
    public static final int MAX_SHORTS = 7;
    /** Required renderable image counts. A video may not fall back to text-only. */
    public static final int MIN_REELS = 4;
    public static final int MIN_SHORTS = 4;
    public static final int CAPTION_MAX_CHARS = 10;

    private static final Set<String> ROLES = Set.of("intro", "peak", "punch", "soft_fill");

    /**
     * §5.1 caption content gate (2026-08-29, job 01M13K1KH1SYEMYSH5PCFFJP9N incident):
     * an LLM caption must be an emotion/situation label ("낯섦", "말못함"), never a story
     * fact lifted verbatim from the post ("상의없이", "오백만원" — copied from the body
     * "…상의도 없이…오백만원이…"). {@code marketing_generation_trace} confirmed this is an
     * LLM-output problem, not a fallback path: {@code sibom_plan_llm == sibom_plan_final},
     * i.e. the schema guard let the leak straight through because it only checked length.
     * A violation here never blocks the item — it replaces the caption text with the
     * catalog's own vetted default ({@link SibomCatalog.Entry#caption()}), which is always
     * ≤ {@link #CAPTION_MAX_CHARS} (see SibomPlanGuardTest#catalogLoaded), so the slide is
     * never silently dropped and never shows unvetted text.
     */
    private static final int LEAK_MIN_CHARS = 3;

    /** Digits (Arabic) are never an emotion label — catches amount-style captions. */
    private static final Pattern HAS_DIGIT = Pattern.compile(".*[0-9].*");

    private SibomPlanGuard() {}

    /**
     * Build a normalized leak-detection index from the post title/body (never the generated
     * script — the script can legitimately reuse an emotion word the caption also uses, e.g.
     * a narration closing on "...그 낯섦이 자리 잡고 있어" is fine even though "낯섦" is also a
     * caption; only literal story facts from the source post count as a leak). Only whitespace
     * and quote/punctuation marks are removed — no morphological (particle) stripping, since a
     * wrong guess there (Korean nouns and josa share syllables, e.g. "정의"/"동의" end in the
     * possessive particle "의") would risk false positives more than it gains coverage. This
     * still catches the incident case: "상의없이" appears space-free in the title as written,
     * and "오백만원" is a substring of the body's "오백만원이" (no particle sits between the
     * caption text and the rest of the token). Returns {@code null} when nothing to check.
     */
    public static String buildLeakIndex(String title, String body) {
        String combined = (title == null ? "" : title) + "\n" + (body == null ? "" : body);
        String normalized = combined.replaceAll("[\\s.,!?…\"'\\u201c\\u201d\\u2018\\u2019]+", "");
        return normalized.isEmpty() ? null : normalized;
    }

    /** True when {@code caption} is a literal chunk lifted from the post title/body. */
    static boolean isBodyLeak(String caption, String leakIndex) {
        if (leakIndex == null || caption == null) return false;
        String stripped = caption.replaceAll("\\s+", "");
        return stripped.length() >= LEAK_MIN_CHARS && leakIndex.contains(stripped);
    }

    /** True when {@code caption} contains a digit (never a valid emotion label — amounts/dates). */
    static boolean isRawNumberCaption(String caption) {
        return caption != null && HAS_DIGIT.matcher(caption).matches();
    }

    /** Reuses {@link VideoVariantService#FORBIDDEN} so the 판결/처방/승패 list stays single-sourced. */
    static boolean isForbiddenCaption(String caption) {
        if (caption == null || caption.isEmpty()) return false;
        String lower = caption.toLowerCase(Locale.ROOT);
        for (String f : VideoVariantService.FORBIDDEN) {
            if (lower.contains(f.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    public enum Channel {
        REELS,
        SHORTS;

        public int maxSlots() {
            return this == REELS ? MAX_REELS : MAX_SHORTS;
        }

        public int minSlots() {
            return this == REELS ? MIN_REELS : MIN_SHORTS;
        }

        public static Channel from(String raw) {
            if (raw == null) return null;
            String t = raw.trim().toLowerCase(Locale.ROOT);
            if (t.equals("reels") || t.equals("instagram_reels")) return REELS;
            if (t.equals("shorts") || t.equals("youtube_shorts")) return SHORTS;
            return null;
        }
    }

    /**
     * Apply §5.2 entry normalization. After soft-fill auto-top-up, callers must fail the video quality gate
     * when the result is empty or below the channel minimum; text-only and metaphor fallbacks are forbidden.
     *
     * Delegates to {@link #guardWithLog(List, Channel)} and returns items only.
     */
    public static List<SibomPlanItem> guard(List<SibomPlanItem> raw, Channel channel) {
        return guardWithLog(raw, channel).items();
    }

    /**
     * Guard with audit trail, no source-post leak check (legacy — prefer the 3-arg overload
     * so §5.1 caption-leak validation runs). Kept for callers/tests without post text on hand.
     */
    public static GuardResult guardWithLog(List<SibomPlanItem> raw, Channel channel) {
        return guardWithLog(raw, channel, null);
    }

    /**
     * Guard with audit trail. Like {@link #guard(List, Channel)} but includes log entries
     * documenting each normalization, deduplication, promotion/demotion, and trim decision.
     *
     * @param leakIndex normalized post title/body index from {@link #buildLeakIndex(String, String)},
     *                   or {@code null} to skip the §5.1 caption-leak check
     */
    public static GuardResult guardWithLog(List<SibomPlanItem> raw, Channel channel, String leakIndex) {
        if (raw == null || raw.isEmpty() || channel == null) {
            return new GuardResult(List.of(), List.of());
        }

        List<GuardLogEntry> log = new ArrayList<>();
        List<SibomPlanItem> working = new ArrayList<>();
        for (SibomPlanItem item : raw) {
            SibomPlanItem fixed = normalizeAndValidate(item, leakIndex, log);
            if (fixed != null) {
                working.add(fixed);
            } else if (item != null && blankToNull(item.imageId()) != null) {
                log.add(new GuardLogEntry("normalize_drop", item.imageId(), "unknown or invalid image_id"));
            }
        }

        working = dedupeIdAndSwapGroup(working, log);
        working = applyPeakPositionGuards(working, log);
        working = topUpWithSoftFill(working, channel, log);
        working = trimToBudget(working, channel.maxSlots(), log);

        List<SibomPlanItem> out = new ArrayList<>(working.size());
        for (SibomPlanItem item : working) {
            out.add(normalizeSizeDwell(item));
        }
        return new GuardResult(List.copyOf(out), List.copyOf(log));
    }

    private static SibomPlanItem normalizeAndValidate(SibomPlanItem item, String leakIndex, List<GuardLogEntry> log) {
        if (item == null) return null;
        String imageId = blankToNull(item.imageId());
        if (imageId == null || !SibomCatalog.isKnown(imageId)) {
            return null; // §5.2.1 unknown → drop
        }

        String role = normalizeRole(item.role());
        if (role == null) {
            role = "punch";
        }

        // §5.2.4 soft_fill cannot present as intro/peak → punch
        if ("soft_fill".equals(role) && isHeroPresentation(item)) {
            role = "punch";
        }

        Optional<SibomCatalog.Entry> entryOpt = SibomCatalog.get(imageId);
        if (entryOpt.isEmpty()) return null;
        SibomCatalog.Entry entry = entryOpt.get();

        if ("soft_fill".equals(role)) {
            if (!SOFT_FILL_POOL_SET.contains(imageId) || entry.people() != 1) {
                role = "punch";
            }
        }

        String caption = item.caption() != null ? item.caption().trim() : "";

        // §5.1 caption content gate: never silently pass a story-fact leak, an amount, or a
        // forbidden word through as a screen label — replace with the catalog's vetted
        // default caption for this image (always ≤ maxChars) and log it (2026-08-29).
        if (!caption.isEmpty()) {
            String violation = isForbiddenCaption(caption) ? "forbidden_word"
                    : isRawNumberCaption(caption) ? "amount_or_number"
                    : isBodyLeak(caption, leakIndex) ? "body_leak"
                    : null;
            if (violation != null) {
                log.add(new GuardLogEntry("caption_replaced", imageId,
                        violation + ": '" + caption + "' replaced with catalog default caption"));
                caption = entry.caption() != null ? entry.caption().trim() : "";
            }
        }

        // §5.2.2 caption maxChars=10 → sibling_bottom swap → empty caption
        if (!caption.isEmpty() && caption.length() > entry.maxChars()) {
            String siblingId = entry.siblingBottom();
            if (siblingId != null && SibomCatalog.isKnown(siblingId) && !siblingId.equals(imageId)) {
                Optional<SibomCatalog.Entry> sib = SibomCatalog.get(siblingId);
                if (sib.isPresent()) {
                    imageId = siblingId;
                    entry = sib.get();
                    if (caption.length() > entry.maxChars()) {
                        caption = "";
                    }
                } else {
                    caption = "";
                }
            } else {
                // no sibling / self-sibling / unknown → empty caption
                caption = "";
            }
        }

        Integer beat = item.beatIndex() != null && item.beatIndex() >= 0 ? item.beatIndex() : 0;
        return new SibomPlanItem(role, imageId, caption, beat, item.size(), item.dwell());
    }

    private static boolean isHeroPresentation(SibomPlanItem item) {
        String size = blankToNull(item.size());
        String dwell = blankToNull(item.dwell());
        return (size != null && size.equalsIgnoreCase("large"))
                || (dwell != null && dwell.equalsIgnoreCase("hold"));
    }

    static String normalizeRole(String role) {
        if (role == null || role.isBlank()) return null;
        String r = role.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if ("softfill".equals(r)) r = "soft_fill";
        return ROLES.contains(r) ? r : null;
    }

    static SibomPlanItem normalizeSizeDwell(SibomPlanItem item) {
        String role = normalizeRole(item.role());
        if (role == null) role = "punch";
        return switch (role) {
            case "intro", "peak" -> item.withRole(role).withSizeDwell("large", "hold");
            case "soft_fill" -> item.withRole(role).withSizeDwell("small", "punch");
            default -> item.withRole("punch").withSizeDwell("small", "punch");
        };
    }

    /** §5.2.5 keep first occurrence of each image_id and each swap_group. */
    static List<SibomPlanItem> dedupeIdAndSwapGroup(List<SibomPlanItem> items, List<GuardLogEntry> log) {
        Set<String> seenIds = new HashSet<>();
        Set<String> seenGroups = new HashSet<>();
        List<SibomPlanItem> out = new ArrayList<>();
        for (SibomPlanItem item : items) {
            String id = item.imageId();
            if (!seenIds.add(id)) {
                log.add(new GuardLogEntry("dedup_id", id, "duplicate image_id — kept first occurrence"));
                continue;
            }
            String group = SibomCatalog.get(id).map(SibomCatalog.Entry::swapGroup).orElse("");
            if (!group.isEmpty() && !seenGroups.add(group)) {
                log.add(new GuardLogEntry("dedup_swap_group", id, "duplicate swap_group '" + group + "' — kept first"));
                continue;
            }
            out.add(item);
        }
        return out;
    }

    /**
     * Auto-top-up with soft-fill pool when below channel minimum.
     * §4.2 / 결정 #20 — no additional LLM call, code downgrade only.
     * <ul>
     *   <li>If current size &lt; channel.minSlots(), fill from SOFT_FILL_POOL</li>
     *   <li>Excludes already-used image_id and swap_group (dedupe rules)</li>
     *   <li>Only people()==1 entries</li>
     *   <li>role=soft_fill, size=small, dwell=punch fixed</li>
     *   <li>No upgrade to intro/peak (handled by normalizeSizeDwell)</li>
     *   <li>beat_index in gaps between existing items, or after last</li>
     *   <li>caption from catalog; LLM not invoked</li>
     *   <li>If pool exhausted, return as-is without exception</li>
     * </ul>
     */
    static List<SibomPlanItem> topUpWithSoftFill(List<SibomPlanItem> items, Channel channel, List<GuardLogEntry> log) {
        if (items.size() >= channel.minSlots()) {
            return items;
        }

        Set<String> usedIds = new HashSet<>();
        Set<String> usedGroups = new HashSet<>();
        int maxBeat = 0;
        for (SibomPlanItem item : items) {
            usedIds.add(item.imageId());
            Optional<SibomCatalog.Entry> entryOpt = SibomCatalog.get(item.imageId());
            if (entryOpt.isPresent()) {
                String group = entryOpt.get().swapGroup();
                if (group != null && !group.isEmpty()) {
                    usedGroups.add(group);
                }
            }
            if (item.beatIndex() != null) {
                maxBeat = Math.max(maxBeat, item.beatIndex());
            }
        }

        List<SibomPlanItem> result = new ArrayList<>(items);
        boolean poolExhausted = false;

        while (result.size() < channel.minSlots()) {
            SibomPlanItem filled = null;
            for (String candidateId : SOFT_FILL_POOL) {
                if (usedIds.contains(candidateId)) {
                    continue;
                }
                Optional<SibomCatalog.Entry> entryOpt = SibomCatalog.get(candidateId);
                if (entryOpt.isEmpty() || entryOpt.get().people() != 1) {
                    continue;
                }
                SibomCatalog.Entry entry = entryOpt.get();
                String group = entry.swapGroup();
                if (group != null && !group.isEmpty() && usedGroups.contains(group)) {
                    continue;
                }
                // Found valid candidate
                filled = new SibomPlanItem("soft_fill", candidateId, entry.caption() != null ? entry.caption() : "", maxBeat + 1, "small", "punch");
                log.add(new GuardLogEntry("soft_fill_added", candidateId, "top-up to " + channel.minSlots() + " items"));
                usedIds.add(candidateId);
                if (group != null && !group.isEmpty()) {
                    usedGroups.add(group);
                }
                maxBeat++;
                break;
            }
            if (filled == null) {
                poolExhausted = true;
                break;
            }
            result.add(filled);
        }

        return result;
    }

    /**
     * §5.2.6 soft peak-position checks:
     * <ul>
     *   <li>1st peak must not sit in the earliest ~15% of the plan beat span</li>
     *   <li>2nd+ peak kept only if resolution arc and in the later half; else demote to punch</li>
     * </ul>
     */
    static List<SibomPlanItem> applyPeakPositionGuards(List<SibomPlanItem> items, List<GuardLogEntry> log) {
        if (items.isEmpty()) return items;

        int maxBeat = 0;
        for (SibomPlanItem i : items) {
            if (i.beatIndex() != null) {
                maxBeat = Math.max(maxBeat, i.beatIndex());
            }
        }
        int earlyCutoff = Math.max(1, (int) Math.ceil(maxBeat * 0.15));
        int lateCutoff = (int) Math.floor(maxBeat * 0.5);

        List<SibomPlanItem> out = new ArrayList<>(items.size());
        int peakOrdinal = 0;
        for (SibomPlanItem item : items) {
            if (!"peak".equals(item.role())) {
                out.add(item);
                continue;
            }
            peakOrdinal++;
            int beat = item.beatIndex() != null ? item.beatIndex() : 0;
            if (peakOrdinal == 1) {
                if (maxBeat > 0 && beat < earlyCutoff) {
                    log.add(new GuardLogEntry("peak_too_early", item.imageId(), "beat " + beat + " < cutoff " + earlyCutoff));
                    out.add(item.withRole("punch"));
                } else {
                    out.add(item);
                }
            } else {
                String arc = SibomCatalog.get(item.imageId()).map(SibomCatalog.Entry::arc).orElse("");
                boolean resolution = "resolution".equalsIgnoreCase(arc);
                if (resolution && beat >= lateCutoff) {
                    out.add(item);
                } else {
                    String reason = !resolution ? "arc='" + arc + "' (not resolution)" : "beat " + beat + " < cutoff " + lateCutoff;
                    log.add(new GuardLogEntry("peak_not_resolution", item.imageId(), reason));
                    out.add(item.withRole("punch"));
                }
            }
        }
        return out;
    }

    /**
     * §5.2.3 over budget → drop punch/soft_fill from the end first; keep intro/peak longer.
     */
    static List<SibomPlanItem> trimToBudget(List<SibomPlanItem> items, int max, List<GuardLogEntry> log) {
        if (items.size() <= max) return items;
        List<SibomPlanItem> mutable = new ArrayList<>(items);
        while (mutable.size() > max) {
            int dropIdx = indexOfLastRemovable(mutable);
            SibomPlanItem dropped;
            if (dropIdx < 0) {
                dropped = mutable.remove(mutable.size() - 1);
            } else {
                dropped = mutable.remove(dropIdx);
            }
            log.add(new GuardLogEntry("budget_trim", dropped.imageId(), "over budget (max " + max + ")"));
        }
        return mutable;
    }

    private static int indexOfLastRemovable(List<SibomPlanItem> items) {
        for (int i = items.size() - 1; i >= 0; i--) {
            String role = items.get(i).role();
            if ("soft_fill".equals(role) || "punch".equals(role)) {
                return i;
            }
        }
        return -1;
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }
}
