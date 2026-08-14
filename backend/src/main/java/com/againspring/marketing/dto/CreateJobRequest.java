package com.againspring.marketing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Request to create a marketing job in ASM
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateJobRequest {

    @JsonProperty("source_id")
    private String sourceId;

    private BriefDto brief;

    private List<String> targets;

    private OptionsDto options;

    @JsonProperty("callback_url")
    private String callbackUrl;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BriefDto {
        private String title;

        /** 마케팅/IG 훅 제목 (원제 복제+\\n, 줄≤10). 없으면 ASM이 title 폴백. */
        @JsonProperty("promo_title")
        private String promoTitle;

        /**
         * IG 훅 감정 키 (nullable). {@code Post.getHookEmotion()}이 있으면 전달;
         * 컬럼/게터가 아직 없으면 null.
         */
        @JsonProperty("hook_emotion")
        private String hookEmotion;

        /**
         * Instagram Reels variant hook (stage-2 of H3). Set at video job create only.
         */
        @JsonProperty("hook_reels")
        private String hookReels;

        /** YouTube Shorts variant hook (stage-2). */
        @JsonProperty("hook_shorts")
        private String hookShorts;

        /**
         * Reels summary narration + cliffhanger CTA (not full-story read). ≤~30s spoken.
         */
        @JsonProperty("script_reels")
        private String scriptReels;

        /** Shorts summary narration + cliffhanger CTA. ≤~45s spoken. */
        @JsonProperty("script_shorts")
        private String scriptShorts;

        /** Reels hard cap seconds (default 30). */
        @JsonProperty("max_duration_reels_sec")
        private Integer maxDurationReelsSec;

        /** Shorts hard cap seconds (default 45). */
        @JsonProperty("max_duration_shorts_sec")
        private Integer maxDurationShortsSec;

        /**
         * When the job targets a single video platform, the active cap (30 or 45).
         * Dual-target jobs prefer {@link #maxDurationReelsSec}/{@link #maxDurationShortsSec}.
         */
        @JsonProperty("max_duration_sec")
        private Integer maxDurationSec;

        /**
         * 시봄이 keyword shortlist (≤12) from {@code posts.sibom_candidates}.
         * Video intro/body use {@link #sibomPlan} (not metaphor).
         */
        @JsonProperty("sibom_candidates")
        private List<String> sibomCandidates;

        /**
         * Active channel 시봄이 insert plan for alone video jobs (Reels-only or Shorts-only).
         * Dual-target jobs leave this null and use {@link #sibomPlanReels}/{@link #sibomPlanShorts}.
         * Video renderer reads this (or channel fields) — not {@code metaphor_*}.
         */
        @JsonProperty("sibom_plan")
        private List<SibomPlanItem> sibomPlan;

        /** Instagram Reels 시봄이 plan (stage-2). */
        @JsonProperty("sibom_plan_reels")
        private List<SibomPlanItem> sibomPlanReels;

        /** YouTube Shorts 시봄이 plan (stage-2). */
        @JsonProperty("sibom_plan_shorts")
        private List<SibomPlanItem> sibomPlanShorts;

        /**
         * @deprecated Video path unplugged — keep for backward compat / non-video clients.
         * AS no longer injects this into renderer-critical briefs; prefer {@link #sibomPlan}.
         */
        @Deprecated
        @JsonProperty("metaphor_id")
        private String metaphorId;

        /**
         * @deprecated Video path unplugged — DB may still hold values; not sent as intro source.
         */
        @Deprecated
        @JsonProperty("metaphor_ids")
        private List<String> metaphorIds;

        /** Post category enum name (e.g. "COUPLE"). Stable key for ASM chip color. */
        private String category;

        /** Post view count. For ASM analytics/filtering. */
        @JsonProperty("view_count")
        private Integer viewCount;

        @JsonProperty("neutral_summary")
        private String neutralSummary;

        @JsonProperty("side_a")
        private String sideA;

        @JsonProperty("side_b")
        private String sideB;

        /**
         * 작성자 본문 전문 (미절단). {@code side_a}는 X/IG 캡처용 300자 절단이라
         * youtube_shorts 낭독처럼 전문이 필요한 채널을 위해 별도 필드로 둔다.
         */
        @JsonProperty("author_body")
        private String authorBody;

        /**
         * 상대방 본문 전문 (미절단, paired일 때만). {@code side_b}는 300자 절단본.
         */
        @JsonProperty("partner_body")
        private String partnerBody;

        @JsonProperty("empathy_ratio")
        private EmpathyRatioDto empathyRatio;

        /** 좋아요 순 상위 3, 본문 전문(미절단). */
        @JsonProperty("top_comments")
        private List<TopCommentDto> topComments;

        @JsonProperty("vote_labels")
        private Map<String, Integer> voteLabels;

        @JsonProperty("post_url")
        private String postUrl;

        private List<String> tags;

        private PolicyDto policy;

        /**
         * 1-based last-block indices for each story part except the final
         * ({@code capture_split_after_lines}). Empty/null → short single-part (or ASM heuristic).
         */
        @JsonProperty("capture_split_after_lines")
        private List<Integer> captureSplitAfterLines;

        /**
         * Candidate CSS Y for each cut (same order as {@link #captureSplitAfterLines}).
         */
        @JsonProperty("part_heights_css")
        private List<Double> partHeightsCss;

        /**
         * When true, ASM also captures partner body ({@code /read?side=r}) as
         * partnerPart1..N and inserts them after author story parts in X/IG.
         */
        @JsonProperty("has_partner_story")
        private Boolean hasPartnerStory;

        /**
         * Partner body cuts (same semantics as {@link #captureSplitAfterLines}).
         */
        @JsonProperty("partner_capture_split_after_lines")
        private List<Integer> partnerCaptureSplitAfterLines;

        /**
         * Candidate CSS Y for partner cuts on {@code /read?side=r}.
         */
        @JsonProperty("partner_part_heights_css")
        private List<Double> partnerPartHeightsCss;

        /** Blocks from start of author body included in marketing capture (may truncate). */
        @JsonProperty("capture_block_count")
        private Integer captureBlockCount;

        /** Blocks from start of partner body included in marketing capture. */
        @JsonProperty("partner_capture_block_count")
        private Integer partnerCaptureBlockCount;

        /**
         * @deprecated first cut only — prefer {@link #captureSplitAfterLines}
         */
        @Deprecated
        @JsonProperty("capture_split_after_line")
        private Integer captureSplitAfterLine;

        /**
         * @deprecated prefer {@link #partHeightsCss}
         */
        @Deprecated
        @JsonProperty("part1_height_css")
        private Double part1HeightCss;

        /**
         * @deprecated prefer {@link #partnerCaptureSplitAfterLines}
         */
        @Deprecated
        @JsonProperty("partner_capture_split_after_line")
        private Integer partnerCaptureSplitAfterLine;

        /**
         * @deprecated prefer {@link #partnerPartHeightsCss}
         */
        @Deprecated
        @JsonProperty("partner_part1_height_css")
        private Double partnerPart1HeightCss;
    }

    /**
     * One 시봄이 insert beat for Shorts/Reels ({@code sibom_plan*}).
     * Spec: {@code docs/shared/marketing/sibom-video-insertion.md}.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SibomPlanItem {
        /** {@code intro|peak|punch|soft_fill} */
        private String role;

        @JsonProperty("image_id")
        private String imageId;

        private String caption;

        @JsonProperty("beat_index")
        private Integer beatIndex;

        /** {@code large|small} */
        private String size;

        /** {@code hold|punch} */
        private String dwell;
    }

    /**
     * Top comment for enriched briefs (e.g. youtube_shorts narration). {@code author}
     * is the display nickname (resolved via {@code UserRepository}, "익명" fallback) so
     * Shorts renders a real name instead of a raw authorId hash.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopCommentDto {
        /** Display nickname (never raw authorId). */
        private String author;

        /** Raw user id, kept for faction/analytics — optional, may be null for anon. */
        @JsonProperty("author_id")
        private String authorId;

        private String body;

        @JsonProperty("like_count")
        private Integer likeCount;

        @JsonProperty("created_at")
        private java.time.Instant createdAt;

        /** {@code "author"} | {@code "partner"} | {@code "neutral"} — for faction-color styling. */
        private String side;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmpathyRatioDto {
        private int a;
        private int b;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyDto {
        @JsonProperty("no_emoji")
        private boolean noEmoji;

        @JsonProperty("forbidden_terms")
        private List<String> forbiddenTerms;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionsDto {
        @JsonProperty("voice_id")
        private String voiceId;

        private String tone;

        @JsonProperty("auto_publish")
        private boolean autoPublish;

        /** {@code story_{localJobId}} — same value embedded in post_url query. */
        @JsonProperty("utm_campaign")
        private String utmCampaign;

        /**
         * Per-target landing URLs (ASM platform id → full community URL with UTM).
         * Multi-target jobs (e.g. reels+shorts) send each platform's {@code utm_source};
         * {@link BriefDto#postUrl} remains the primary/fallback for current ASM pipelines.
         */
        @JsonProperty("post_urls")
        private Map<String, String> postUrls;

        /** Highest-priority SLA lane for scheduled Again-Spring short-form renders. */
        private String priority;

        /** Absolute render-ready deadline in UTC; used for deadline-aware degradation. */
        @JsonProperty("deadline_at")
        private String deadlineAt;

        /** The channel-specific script is already produced by Again-Spring. */
        @JsonProperty("pre_scripted")
        private boolean preScripted;

        /** Render policy understood by the WaggleBot marketing fast lane. */
        @JsonProperty("render_profile")
        private String renderProfile;
    }
}
