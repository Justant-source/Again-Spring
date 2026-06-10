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

        private List<String> tags;

        private PolicyDto policy;
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
    }
}
