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
         * 1-based last front-half newline block for story capture split.
         * null → short post (no part2) or ASM falls back to visual-line cut.
         */
        @JsonProperty("capture_split_after_line")
        private Integer captureSplitAfterLine;

        /**
         * Candidate CSS Y for part1 crop (verification + ASM fallback).
         * Authoritative cut is DOM measurement at the split block boundary.
         */
        @JsonProperty("part1_height_css")
        private Double part1HeightCss;

        /**
         * When true, ASM also captures partner body ({@code /read?side=r}) as
         * partnerPart1[/2] and inserts them after author story parts in X/IG.
         */
        @JsonProperty("has_partner_story")
        private Boolean hasPartnerStory;

        /**
         * Partner body split (same semantics as {@link #captureSplitAfterLine}).
         * null when short partner body or no partner story.
         */
        @JsonProperty("partner_capture_split_after_line")
        private Integer partnerCaptureSplitAfterLine;

        /**
         * Candidate CSS Y for partner part1 crop on {@code /read?side=r}.
         */
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
