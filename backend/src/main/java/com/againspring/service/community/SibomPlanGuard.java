package com.againspring.service.community;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Schema guard for channel {@code sibom_plan} — normalizes entries in code, never retries LLM.
 *
 * @see docs/shared/marketing/sibom-video-insertion.md §5.2
 */
public final class SibomPlanGuard {

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

    private SibomPlanGuard() {}

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
     */
    public static List<SibomPlanItem> guard(List<SibomPlanItem> raw, Channel channel) {
        if (raw == null || raw.isEmpty() || channel == null) {
            return List.of();
        }

        List<SibomPlanItem> working = new ArrayList<>();
        for (SibomPlanItem item : raw) {
            SibomPlanItem fixed = normalizeAndValidate(item);
            if (fixed != null) {
                working.add(fixed);
            }
        }

        working = dedupeIdAndSwapGroup(working);
        working = applyPeakPositionGuards(working);
        working = topUpWithSoftFill(working, channel);
        working = trimToBudget(working, channel.maxSlots());

        List<SibomPlanItem> out = new ArrayList<>(working.size());
        for (SibomPlanItem item : working) {
            out.add(normalizeSizeDwell(item));
        }
        return List.copyOf(out);
    }

    private static SibomPlanItem normalizeAndValidate(SibomPlanItem item) {
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
    static List<SibomPlanItem> dedupeIdAndSwapGroup(List<SibomPlanItem> items) {
        Set<String> seenIds = new HashSet<>();
        Set<String> seenGroups = new HashSet<>();
        List<SibomPlanItem> out = new ArrayList<>();
        for (SibomPlanItem item : items) {
            String id = item.imageId();
            if (!seenIds.add(id)) continue;
            String group = SibomCatalog.get(id).map(SibomCatalog.Entry::swapGroup).orElse("");
            if (!group.isEmpty() && !seenGroups.add(group)) continue;
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
    static List<SibomPlanItem> topUpWithSoftFill(List<SibomPlanItem> items, Channel channel) {
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
    static List<SibomPlanItem> applyPeakPositionGuards(List<SibomPlanItem> items) {
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
                    out.add(item.withRole("punch"));
                }
            }
        }
        return out;
    }

    /**
     * §5.2.3 over budget → drop punch/soft_fill from the end first; keep intro/peak longer.
     */
    static List<SibomPlanItem> trimToBudget(List<SibomPlanItem> items, int max) {
        if (items.size() <= max) return items;
        List<SibomPlanItem> mutable = new ArrayList<>(items);
        while (mutable.size() > max) {
            int dropIdx = indexOfLastRemovable(mutable);
            if (dropIdx < 0) {
                mutable.remove(mutable.size() - 1);
            } else {
                mutable.remove(dropIdx);
            }
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
