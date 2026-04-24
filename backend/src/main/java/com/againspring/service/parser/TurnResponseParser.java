package com.againspring.service.parser;

import com.againspring.domain.enums.ConflictType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses LLM responses for turn-by-turn processing.
 * Extracts mediator message, neutral summary, questions, and optionally conflict type.
 * Tolerant to schema drift: null-safe field access.
 */
@Slf4j
@Component
public class TurnResponseParser {

    private final ObjectMapper objectMapper;

    public TurnResponseParser() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Parses raw LLM response text into structured turn data.
     *
     * @param rawText JSON text from LLM
     * @param turnNumber turn number context (1-6)
     * @return parsed turn data with fallback flag if schema violated
     */
    public ParsedTurn parse(String rawText, int turnNumber) {
        try {
            JsonNode root = objectMapper.readTree(rawText);
            return extractTurnData(root, turnNumber);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse LLM response as JSON, turn={}: {}", turnNumber, e.getMessage());
            // Fallback: treat entire response as mediator message
            return ParsedTurn.builder()
                    .mediatorMessage(rawText)
                    .neutralSummary("상대방의 응답을 정리했습니다.")
                    .fallback(true)
                    .build();
        }
    }

    /**
     * Extracts fields from parsed JSON node.
     */
    private ParsedTurn extractTurnData(JsonNode root, int turnNumber) {
        ParsedTurn.ParsedTurnBuilder builder = ParsedTurn.builder();

        // Extract mediator message
        String mediatorMessage = getStringField(root, "mediatorMessage", null);
        if (mediatorMessage == null) {
            mediatorMessage = getStringField(root, "message", null);
        }
        if (mediatorMessage == null) {
            log.warn("Missing mediatorMessage field in turn {}, using fallback", turnNumber);
            mediatorMessage = "응답을 정리했습니다.";
        }
        builder.mediatorMessage(mediatorMessage);

        // Extract neutral summary for opponent
        String neutralSummary = getStringField(root, "neutralSummary", null);
        if (neutralSummary == null) {
            neutralSummary = getStringField(root, "summary", null);
        }
        if (neutralSummary == null) {
            neutralSummary = "상대방의 관점을 이해했습니다.";
        }
        builder.neutralSummary(neutralSummary);

        // Extract follow-up questions (turns 3-4)
        List<String> questions = extractQuestions(root);
        if (!questions.isEmpty()) {
            builder.questions(questions);
        }

        // Extract conflict type (turn 2 classification)
        ConflictType conflictType = extractConflictType(root);
        if (conflictType != null) {
            builder.conflictType(conflictType);
        }

        return builder.fallback(false).build();
    }

    /**
     * Extracts questions array from JSON.
     */
    private List<String> extractQuestions(JsonNode root) {
        List<String> questions = new ArrayList<>();
        JsonNode questionsNode = root.get("questions");

        if (questionsNode != null && questionsNode.isArray()) {
            for (JsonNode qNode : questionsNode) {
                String q = null;
                if (qNode.isObject()) {
                    q = getStringField(qNode, "question", null);
                    if (q == null) {
                        q = getStringField(qNode, "text", null);
                    }
                } else if (qNode.isTextual()) {
                    q = qNode.asText();
                }
                if (q != null && !q.isBlank()) {
                    questions.add(q);
                }
            }
        }

        return questions;
    }

    /**
     * Extracts conflict type classification from JSON.
     */
    private ConflictType extractConflictType(JsonNode root) {
        String typeStr = getStringField(root, "conflictType", null);
        if (typeStr == null) {
            typeStr = getStringField(root, "type", null);
        }
        if (typeStr == null) {
            return null;
        }

        try {
            return ConflictType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid conflictType value: {}", typeStr);
            return null;
        }
    }

    /**
     * Safely extract string field from JsonNode.
     */
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

    /**
     * Result of parsing turn response.
     */
    @Data
    @Builder
    public static class ParsedTurn {
        private String mediatorMessage;
        private String neutralSummary;
        private List<String> questions;
        private ConflictType conflictType;
        private boolean fallback;
    }
}
