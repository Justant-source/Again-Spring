package com.againspring.service.report;

import com.againspring.domain.enums.ConflictType;
import com.againspring.domain.Report;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses LLM responses for report generation.
 * Extracts fields: ratio, horsemen, NVC scripts, needs map, temperature, repair suggestions.
 * Tolerant to schema drift with fallback values.
 */
@Slf4j
@Component
public class ReportResponseParser {

    private final ObjectMapper objectMapper;

    public ReportResponseParser() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Parses raw LLM response into report data.
     *
     * @param rawText JSON text from LLM
     * @return parsed report data
     */
    public ParsedReport parse(String rawText) {
        try {
            JsonNode root = objectMapper.readTree(rawText);
            return extractReportData(root);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse report LLM response as JSON: {}", e.getMessage());
            return ParsedReport.builder().fallback(true).build();
        }
    }

    private ParsedReport extractReportData(JsonNode root) {
        ParsedReport.ParsedReportBuilder builder = ParsedReport.builder();

        // Extract contribution ratio
        JsonNode ratioNode = root.get("contributionRatio");
        if (ratioNode != null) {
            ParsedReport.ContributionRatio ratio = extractRatio(ratioNode);
            builder.contributionRatio(ratio);
        }

        // Extract conflict type
        String conflictTypeStr = getStringField(root, "conflictType", null);
        if (conflictTypeStr != null) {
            try {
                builder.conflictType(ConflictType.valueOf(conflictTypeStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid conflictType: {}", conflictTypeStr);
            }
        }

        // Extract temperature
        Double temperature = getDoubleField(root, "temperature", null);
        if (temperature != null && temperature >= 0 && temperature <= 100) {
            builder.temperature(temperature);
        }

        // Extract NVC scripts
        JsonNode nvcNode = root.get("nvcScripts");
        if (nvcNode != null) {
            ParsedReport.NVCScripts nvc = extractNVC(nvcNode);
            builder.nvcScripts(nvc);
        }

        // Extract four horsemen
        JsonNode horsemenNode = root.get("fourHorsemen");
        if (horsemenNode != null) {
            ParsedReport.FourHorsemen horsemen = extractHorsemen(horsemenNode);
            builder.fourHorsemen(horsemen);
        }

        // Extract needs map
        JsonNode needsNode = root.get("needsMap");
        if (needsNode != null) {
            ParsedReport.NeedsMap needsMap = extractNeedsMap(needsNode);
            builder.needsMap(needsMap);
        }

        // Extract repair suggestions
        List<String> suggestions = extractSuggestions(root);
        builder.repairSuggestions(suggestions);

        return builder.fallback(false).build();
    }

    private ParsedReport.ContributionRatio extractRatio(JsonNode node) {
        int a = getIntField(node, "a", 50);
        int b = getIntField(node, "b", 50);
        String rationale = getStringField(node, "rationale", "");

        JsonNode labelNode = node.get("label");
        ParsedReport.RatioLabel label = null;
        if (labelNode != null) {
            label = ParsedReport.RatioLabel.builder()
                    .a(getStringField(labelNode, "a", ""))
                    .b(getStringField(labelNode, "b", ""))
                    .build();
        }

        return ParsedReport.ContributionRatio.builder()
                .a(a)
                .b(b)
                .label(label)
                .rationale(rationale)
                .build();
    }

    private ParsedReport.NVCScripts extractNVC(JsonNode node) {
        ParsedReport.NVCScript aToB = extractNVCScript(node.get("aToB"));
        ParsedReport.NVCScript bToA = extractNVCScript(node.get("bToA"));

        return ParsedReport.NVCScripts.builder()
                .aToB(aToB)
                .bToA(bToA)
                .build();
    }

    private ParsedReport.NVCScript extractNVCScript(JsonNode node) {
        if (node == null) {
            return null;
        }

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
        if (node == null) {
            return ParsedReport.HorsemenItem.builder()
                    .detected(false)
                    .build();
        }

        boolean detected = getBooleanField(node, "detected", false);
        String intensity = getStringField(node, "intensity", "");
        List<String> examples = extractStringList(node, "examples");

        return ParsedReport.HorsemenItem.builder()
                .detected(detected)
                .intensity(intensity)
                .examples(examples)
                .build();
    }

    private ParsedReport.NeedsMap extractNeedsMap(JsonNode node) {
        ParsedReport.Position posA = extractPosition(node.get("positionA"));
        ParsedReport.Position posB = extractPosition(node.get("positionB"));

        return ParsedReport.NeedsMap.builder()
                .axisX(getStringField(node, "axisX", ""))
                .axisXLabel(getStringField(node, "axisXLabel", ""))
                .axisY(getStringField(node, "axisY", ""))
                .axisYLabel(getStringField(node, "axisYLabel", ""))
                .positionA(posA)
                .positionB(posB)
                .interpretation(getStringField(node, "interpretation", ""))
                .build();
    }

    private ParsedReport.Position extractPosition(JsonNode node) {
        if (node == null) {
            return null;
        }

        return ParsedReport.Position.builder()
                .x(getIntField(node, "x", 0))
                .y(getIntField(node, "y", 0))
                .build();
    }

    private List<String> extractSuggestions(JsonNode root) {
        List<String> suggestions = new ArrayList<>();
        JsonNode sugNode = root.get("repairSuggestions");

        if (sugNode != null && sugNode.isArray()) {
            for (JsonNode item : sugNode) {
                if (item.isTextual()) {
                    String text = item.asText();
                    if (text != null && !text.isBlank()) {
                        suggestions.add(text);
                    }
                }
            }
        }

        return suggestions;
    }

    private List<String> extractStringList(JsonNode node, String fieldName) {
        List<String> result = new ArrayList<>();
        if (node == null || !node.has(fieldName)) {
            return result;
        }

        JsonNode listNode = node.get(fieldName);
        if (listNode != null && listNode.isArray()) {
            for (JsonNode item : listNode) {
                if (item.isTextual()) {
                    String text = item.asText();
                    if (text != null && !text.isBlank()) {
                        result.add(text);
                    }
                }
            }
        }

        return result;
    }

    private String getStringField(JsonNode node, String fieldName, String defaultValue) {
        if (node == null || !node.has(fieldName)) {
            return defaultValue;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return defaultValue;
        }
        return field.asText(defaultValue);
    }

    private int getIntField(JsonNode node, String fieldName, int defaultValue) {
        if (node == null || !node.has(fieldName)) {
            return defaultValue;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull() || !field.isIntegralNumber()) {
            return defaultValue;
        }
        return field.asInt(defaultValue);
    }

    private Double getDoubleField(JsonNode node, String fieldName, Double defaultValue) {
        if (node == null || !node.has(fieldName)) {
            return defaultValue;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull() || !field.isNumber()) {
            return defaultValue;
        }
        return field.asDouble(defaultValue);
    }

    private boolean getBooleanField(JsonNode node, String fieldName, boolean defaultValue) {
        if (node == null || !node.has(fieldName)) {
            return defaultValue;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return defaultValue;
        }
        return field.asBoolean(defaultValue);
    }

    // Parsed report data classes
    public static class ParsedReport {
        public ConflictType conflictType;
        public ContributionRatio contributionRatio;
        public Double temperature;
        public NVCScripts nvcScripts;
        public FourHorsemen fourHorsemen;
        public NeedsMap needsMap;
        public List<String> repairSuggestions;
        public boolean fallback;

        @lombok.Builder
        public ParsedReport(ConflictType conflictType, ContributionRatio contributionRatio, Double temperature,
                           NVCScripts nvcScripts, FourHorsemen fourHorsemen, NeedsMap needsMap,
                           List<String> repairSuggestions, boolean fallback) {
            this.conflictType = conflictType;
            this.contributionRatio = contributionRatio;
            this.temperature = temperature;
            this.nvcScripts = nvcScripts;
            this.fourHorsemen = fourHorsemen;
            this.needsMap = needsMap;
            this.repairSuggestions = repairSuggestions != null ? repairSuggestions : new ArrayList<>();
            this.fallback = fallback;
        }

        @lombok.Data
        @lombok.Builder
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        public static class ContributionRatio {
            public int a;
            public int b;
            public RatioLabel label;
            public String rationale;
        }

        @lombok.Data
        @lombok.Builder
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        public static class RatioLabel {
            public String a;
            public String b;
        }

        @lombok.Data
        @lombok.Builder
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        public static class NVCScripts {
            public NVCScript aToB;
            public NVCScript bToA;
        }

        @lombok.Data
        @lombok.Builder
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        public static class NVCScript {
            public String observation;
            public String feeling;
            public String need;
            public String request;
        }

        @lombok.Data
        @lombok.Builder
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        public static class FourHorsemen {
            public HorsemenItem criticism;
            public HorsemenItem defensiveness;
            public HorsemenItem contempt;
            public HorsemenItem stonewalling;
        }

        @lombok.Data
        @lombok.Builder
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        public static class HorsemenItem {
            public boolean detected;
            public String intensity;
            public List<String> examples;
        }

        @lombok.Data
        @lombok.Builder
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        public static class NeedsMap {
            public String axisX;
            public String axisXLabel;
            public String axisY;
            public String axisYLabel;
            public Position positionA;
            public Position positionB;
            public String interpretation;
        }

        @lombok.Data
        @lombok.Builder
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        public static class Position {
            public int x;
            public int y;
        }
    }
}
