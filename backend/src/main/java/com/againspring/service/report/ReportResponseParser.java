package com.againspring.service.report;

import com.againspring.domain.enums.ConflictType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses LLM responses for report generation (V12).
 * Supports new V12 schema: coreSummary, fourStageFlow, metaphor, nvcReflection,
 * recommendedActions, externalResourceGuidance.
 * Also handles legacy Duo fields for backward compat.
 */
@Slf4j
@Component
public class ReportResponseParser {

    private final ObjectMapper objectMapper;

    public ReportResponseParser() {
        this.objectMapper = new ObjectMapper();
    }

    public ParsedReport parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            log.warn("Empty LLM response for report");
            return ParsedReport.builder().fallback(true).build();
        }
        String trimmed = rawText.trim();
        // Strip markdown code block if present
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        try {
            JsonNode root = objectMapper.readTree(trimmed);
            return extractReportData(root);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse report LLM response as JSON: {}", e.getMessage());
            return ParsedReport.builder().fallback(true).build();
        }
    }

    private ParsedReport extractReportData(JsonNode root) {
        ParsedReport.ParsedReportBuilder builder = ParsedReport.builder();

        // V12 fields
        builder.coreSummary(getStringField(root, "coreSummary", null));
        builder.fourStageFlow(extractFourStageFlow(root));
        builder.metaphor(extractMetaphor(root));
        builder.nvcReflection(extractNvcReflection(root));
        builder.recommendedActions(extractRecommendedActions(root));
        builder.externalResourceGuidance(extractExternalResource(root));

        // Duo-specific: rawContributionRatio (also check legacy key)
        JsonNode ratioNode = root.get("rawContributionRatio");
        if (ratioNode == null) ratioNode = root.get("contributionRatio");
        if (ratioNode != null) {
            builder.contributionRatio(extractRatio(ratioNode));
        }

        // Duo-specific: fourHorsemenObservation (number scores) + legacy fourHorsemen object
        JsonNode horsemenScoreNode = root.get("fourHorsemenObservation");
        if (horsemenScoreNode != null) {
            builder.fourHorsemenScores(extractHorsemenScores(horsemenScoreNode));
        }
        JsonNode horsemenNode = root.get("fourHorsemen");
        if (horsemenNode != null) {
            builder.fourHorsemen(extractHorsemen(horsemenNode));
        }

        // Duo-specific: conflictType
        String conflictTypeStr = getStringField(root, "conflictType", null);
        if (conflictTypeStr != null) {
            try {
                builder.conflictType(ConflictType.valueOf(conflictTypeStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid conflictType: {}", conflictTypeStr);
            }
        }

        // Legacy fields (still used for some Duo paths)
        JsonNode nvcNode = root.get("nvcScripts");
        if (nvcNode != null) {
            builder.nvcScripts(extractNVC(nvcNode));
        }
        builder.patternFeedback(getStringField(root, "patternFeedback", ""));
        builder.suggestedApproach(getStringField(root, "suggestedApproach", ""));
        builder.inviteAgainCta(getStringField(root, "inviteAgainCta", ""));

        return builder.fallback(false).build();
    }

    private List<ParsedReport.StageFlow> extractFourStageFlow(JsonNode root) {
        List<ParsedReport.StageFlow> result = new ArrayList<>();
        JsonNode arr = root.get("fourStageFlow");
        if (arr == null || !arr.isArray()) return result;
        for (JsonNode item : arr) {
            int stage = getIntField(item, "stage", 0);
            String stageName = getStringField(item, "stageName", "");
            String userQuote = getStringField(item, "userQuote", "");
            String interpretation = getStringField(item, "interpretation", "");
            if (!stageName.isBlank() || !userQuote.isBlank()) {
                result.add(ParsedReport.StageFlow.builder()
                    .stage(stage).stageName(stageName)
                    .userQuote(userQuote).interpretation(interpretation)
                    .build());
            }
        }
        return result;
    }

    private ParsedReport.MetaphorSelection extractMetaphor(JsonNode root) {
        JsonNode node = root.get("metaphor");
        if (node == null) return null;
        String id = getStringField(node, "id", null);
        String displayName = getStringField(node, "displayName", null);
        String reason = getStringField(node, "reason", null);
        if (id == null) return null;
        return ParsedReport.MetaphorSelection.builder()
            .id(id).displayName(displayName).reason(reason).build();
    }

    private ParsedReport.NVCReflection extractNvcReflection(JsonNode root) {
        JsonNode node = root.get("nvcReflection");
        if (node == null) return null;
        String observation = getStringField(node, "observation", null);
        String feeling = getStringField(node, "feeling", null);
        String need = getStringField(node, "need", null);
        String request = getStringField(node, "request", null);
        return ParsedReport.NVCReflection.builder()
            .observation(observation).feeling(feeling)
            .need(need).request(request).build();
    }

    private List<ParsedReport.RecommendedAction> extractRecommendedActions(JsonNode root) {
        List<ParsedReport.RecommendedAction> result = new ArrayList<>();
        JsonNode arr = root.get("recommendedActions");
        if (arr == null || !arr.isArray()) return result;
        for (JsonNode item : arr) {
            String action = getStringField(item, "action", null);
            String rationale = getStringField(item, "rationale", null);
            boolean isUserChosen = getBooleanField(item, "isUserChosen", false);
            if (action != null && !action.isBlank()) {
                result.add(ParsedReport.RecommendedAction.builder()
                    .action(action).rationale(rationale).isUserChosen(isUserChosen).build());
            }
        }
        return result;
    }

    private ParsedReport.ExternalResourceGuidance extractExternalResource(JsonNode root) {
        JsonNode node = root.get("externalResourceGuidance");
        if (node == null || node.isNull()) return null;
        String domain = getStringField(node, "domain", null);
        String resource = getStringField(node, "resource", null);
        String rationale = getStringField(node, "rationale", null);
        if (domain == null) return null;
        return ParsedReport.ExternalResourceGuidance.builder()
            .domain(domain).resource(resource).rationale(rationale).build();
    }

    private ParsedReport.FourHorsemenScores extractHorsemenScores(JsonNode node) {
        return ParsedReport.FourHorsemenScores.builder()
            .criticism(getIntField(node, "criticism", 0))
            .contempt(getIntField(node, "contempt", 0))
            .defensiveness(getIntField(node, "defensiveness", 0))
            .stonewalling(getIntField(node, "stonewalling", 0))
            .build();
    }

    private ParsedReport.ContributionRatio extractRatio(JsonNode node) {
        double a = getDoubleField(node, "a", 50.0);
        double b = getDoubleField(node, "b", 50.0);
        String rationale = getStringField(node, "rationale", "");
        return ParsedReport.ContributionRatio.builder()
            .a((int) Math.round(a)).b((int) Math.round(b)).rationale(rationale).build();
    }

    private ParsedReport.NVCScripts extractNVC(JsonNode node) {
        return ParsedReport.NVCScripts.builder()
            .aToB(extractNVCScript(node.get("aToB")))
            .bToA(extractNVCScript(node.get("bToA")))
            .build();
    }

    private ParsedReport.NVCScript extractNVCScript(JsonNode node) {
        if (node == null) return null;
        return ParsedReport.NVCScript.builder()
            .observation(getStringField(node, "observation", ""))
            .feeling(getStringField(node, "feeling", ""))
            .need(getStringField(node, "need", ""))
            .request(getStringField(node, "request", ""))
            .build();
    }

    private ParsedReport.FourHorsemen extractHorsemen(JsonNode node) {
        return ParsedReport.FourHorsemen.builder()
            .criticism(extractHorsemenItem(node.get("criticism")))
            .defensiveness(extractHorsemenItem(node.get("defensiveness")))
            .contempt(extractHorsemenItem(node.get("contempt")))
            .stonewalling(extractHorsemenItem(node.get("stonewalling")))
            .build();
    }

    private ParsedReport.HorsemenItem extractHorsemenItem(JsonNode node) {
        if (node == null) return ParsedReport.HorsemenItem.builder().detected(false).build();
        return ParsedReport.HorsemenItem.builder()
            .detected(getBooleanField(node, "detected", false))
            .intensity(getStringField(node, "intensity", ""))
            .examples(extractStringList(node, "examples"))
            .build();
    }

    private List<String> extractStringList(JsonNode node, String fieldName) {
        List<String> result = new ArrayList<>();
        if (node == null || !node.has(fieldName)) return result;
        JsonNode listNode = node.get(fieldName);
        if (listNode != null && listNode.isArray()) {
            for (JsonNode item : listNode) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    result.add(item.asText());
                }
            }
        }
        return result;
    }

    private String getStringField(JsonNode node, String fieldName, String defaultValue) {
        if (node == null || !node.has(fieldName)) return defaultValue;
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) return defaultValue;
        return field.asText(defaultValue);
    }

    private int getIntField(JsonNode node, String fieldName, int defaultValue) {
        if (node == null || !node.has(fieldName)) return defaultValue;
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull() || !field.isNumber()) return defaultValue;
        return field.asInt(defaultValue);
    }

    private double getDoubleField(JsonNode node, String fieldName, double defaultValue) {
        if (node == null || !node.has(fieldName)) return defaultValue;
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull() || !field.isNumber()) return defaultValue;
        return field.asDouble(defaultValue);
    }

    private boolean getBooleanField(JsonNode node, String fieldName, boolean defaultValue) {
        if (node == null || !node.has(fieldName)) return defaultValue;
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) return defaultValue;
        return field.asBoolean(defaultValue);
    }

    // ── Parsed data classes ──────────────────────────────────────────────────

    public static class ParsedReport {
        // V12 fields
        public String coreSummary;
        public List<StageFlow> fourStageFlow;
        public MetaphorSelection metaphor;
        public NVCReflection nvcReflection;
        public List<RecommendedAction> recommendedActions;
        public ExternalResourceGuidance externalResourceGuidance;

        // Duo-specific
        public ConflictType conflictType;
        public ContributionRatio contributionRatio;
        public FourHorsemenScores fourHorsemenScores;
        public FourHorsemen fourHorsemen;

        // Legacy fields (kept for backward compat)
        public NVCScripts nvcScripts;
        public String patternFeedback;
        public String suggestedApproach;
        public String inviteAgainCta;
        public boolean fallback;

        @lombok.Builder
        public ParsedReport(String coreSummary, List<StageFlow> fourStageFlow,
                           MetaphorSelection metaphor, NVCReflection nvcReflection,
                           List<RecommendedAction> recommendedActions,
                           ExternalResourceGuidance externalResourceGuidance,
                           ConflictType conflictType, ContributionRatio contributionRatio,
                           FourHorsemenScores fourHorsemenScores, FourHorsemen fourHorsemen,
                           NVCScripts nvcScripts, String patternFeedback,
                           String suggestedApproach, String inviteAgainCta, boolean fallback) {
            this.coreSummary = coreSummary;
            this.fourStageFlow = fourStageFlow != null ? fourStageFlow : new ArrayList<>();
            this.metaphor = metaphor;
            this.nvcReflection = nvcReflection;
            this.recommendedActions = recommendedActions != null ? recommendedActions : new ArrayList<>();
            this.externalResourceGuidance = externalResourceGuidance;
            this.conflictType = conflictType;
            this.contributionRatio = contributionRatio;
            this.fourHorsemenScores = fourHorsemenScores;
            this.fourHorsemen = fourHorsemen;
            this.nvcScripts = nvcScripts;
            this.patternFeedback = patternFeedback;
            this.suggestedApproach = suggestedApproach;
            this.inviteAgainCta = inviteAgainCta;
            this.fallback = fallback;
        }

        public String getCoreSummary() { return coreSummary; }
        public List<StageFlow> getFourStageFlow() { return fourStageFlow; }
        public MetaphorSelection getMetaphor() { return metaphor; }
        public NVCReflection getNvcReflection() { return nvcReflection; }
        public List<RecommendedAction> getRecommendedActions() { return recommendedActions; }
        public ExternalResourceGuidance getExternalResourceGuidance() { return externalResourceGuidance; }
        public ConflictType getConflictType() { return conflictType; }
        public ContributionRatio getContributionRatio() { return contributionRatio; }
        public FourHorsemenScores getFourHorsemenScores() { return fourHorsemenScores; }
        public FourHorsemen getFourHorsemen() { return fourHorsemen; }
        public NVCScripts getNvcScripts() { return nvcScripts; }
        public String getPatternFeedback() { return patternFeedback; }
        public String getSuggestedApproach() { return suggestedApproach; }
        public String getInviteAgainCta() { return inviteAgainCta; }
        public boolean isFallback() { return fallback; }

        public boolean hasV12Content() {
            return coreSummary != null && !coreSummary.isBlank()
                && metaphor != null
                && nvcReflection != null;
        }

        @lombok.Data @lombok.Builder @lombok.AllArgsConstructor @lombok.NoArgsConstructor
        public static class StageFlow {
            public int stage;
            public String stageName;
            public String userQuote;
            public String interpretation;
        }

        @lombok.Data @lombok.Builder @lombok.AllArgsConstructor @lombok.NoArgsConstructor
        public static class MetaphorSelection {
            public String id;
            public String displayName;
            public String reason;
        }

        @lombok.Data @lombok.Builder @lombok.AllArgsConstructor @lombok.NoArgsConstructor
        public static class NVCReflection {
            public String observation;
            public String feeling;
            public String need;
            public String request;
        }

        @lombok.Data @lombok.Builder @lombok.AllArgsConstructor @lombok.NoArgsConstructor
        public static class RecommendedAction {
            public String action;
            public String rationale;
            public boolean isUserChosen;
        }

        @lombok.Data @lombok.Builder @lombok.AllArgsConstructor @lombok.NoArgsConstructor
        public static class ExternalResourceGuidance {
            public String domain;
            public String resource;
            public String rationale;
        }

        @lombok.Data @lombok.Builder @lombok.AllArgsConstructor @lombok.NoArgsConstructor
        public static class FourHorsemenScores {
            public int criticism;
            public int contempt;
            public int defensiveness;
            public int stonewalling;
        }

        @lombok.Data @lombok.Builder @lombok.AllArgsConstructor @lombok.NoArgsConstructor
        public static class ContributionRatio {
            public int a;
            public int b;
            public RatioLabel label;
            public String rationale;
        }

        @lombok.Data @lombok.Builder @lombok.AllArgsConstructor @lombok.NoArgsConstructor
        public static class RatioLabel {
            public String a;
            public String b;
        }

        @lombok.Data @lombok.Builder @lombok.AllArgsConstructor @lombok.NoArgsConstructor
        public static class NVCScripts {
            public NVCScript aToB;
            public NVCScript bToA;
        }

        @lombok.Data @lombok.Builder @lombok.AllArgsConstructor @lombok.NoArgsConstructor
        public static class NVCScript {
            public String observation;
            public String feeling;
            public String need;
            public String request;
        }

        @lombok.Data @lombok.Builder @lombok.AllArgsConstructor @lombok.NoArgsConstructor
        public static class FourHorsemen {
            public HorsemenItem criticism;
            public HorsemenItem defensiveness;
            public HorsemenItem contempt;
            public HorsemenItem stonewalling;
        }

        @lombok.Data @lombok.Builder @lombok.AllArgsConstructor @lombok.NoArgsConstructor
        public static class HorsemenItem {
            public boolean detected;
            public String intensity;
            public List<String> examples;
        }

        @lombok.Data @lombok.Builder @lombok.AllArgsConstructor @lombok.NoArgsConstructor
        public static class NeedsMap {
            public String axisX;
            public String axisXLabel;
            public String axisY;
            public String axisYLabel;
            public Position positionA;
            public Position positionB;
            public String interpretation;
        }

        @lombok.Data @lombok.Builder @lombok.AllArgsConstructor @lombok.NoArgsConstructor
        public static class Position {
            public int x;
            public int y;
        }
    }
}
