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

        @JsonProperty("neutral_summary")
        private String neutralSummary;

        @JsonProperty("side_a")
        private String sideA;

        @JsonProperty("side_b")
        private String sideB;

        @JsonProperty("empathy_ratio")
        private EmpathyRatioDto empathyRatio;

        @JsonProperty("jury_gist")
        private String juryGist;

        @JsonProperty("jury_opinions")
        private List<String> juryOpinions;

        @JsonProperty("top_comments")
        private List<String> topComments;

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

        @JsonProperty("utm_campaign")
        private String utmCampaign;
    }
}
