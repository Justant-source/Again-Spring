package com.againspring.service.parser;

import com.againspring.domain.Session;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Splits an LLM chat response into the user-facing text and an optional <turn_meta>
 * JSON block carrying 4 Horsemen / NVC completion scores. Tolerates a missing or
 * malformed meta block — the user text always reflows.
 */
@Slf4j
@Component
public class ChatTurnMetaParser {

    private static final Pattern META_BLOCK = Pattern.compile(
        "<turn_meta>\\s*(\\{.*?})\\s*</turn_meta>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern WRAPPER_BLOCK = Pattern.compile(
        "<mediator_response>\\s*(.*?)\\s*</mediator_response>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Result parse(String rawResponse, int turn, String senderTag) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return new Result("", null, null);
        }

        String working = rawResponse;
        Session.HorsemenTurnEntry horsemen = null;
        Session.NvcTurnEntry nvc = null;

        Matcher meta = META_BLOCK.matcher(working);
        if (meta.find()) {
            String json = meta.group(1);
            try {
                JsonNode root = objectMapper.readTree(json);
                horsemen = readHorsemen(root.get("horsemen"), turn, senderTag);
                nvc = readNvc(root.get("nvc_completion"), turn, senderTag);
            } catch (Exception e) {
                log.warn("turn_meta JSON parse failed (turn={}): {}", turn, e.getMessage());
            }
            working = META_BLOCK.matcher(working).replaceAll("").trim();
        }

        Matcher wrapper = WRAPPER_BLOCK.matcher(working);
        if (wrapper.find()) {
            working = wrapper.group(1).trim();
        }

        return new Result(working.strip(), horsemen, nvc);
    }

    private Session.HorsemenTurnEntry readHorsemen(JsonNode node, int turn, String sender) {
        if (node == null || !node.isObject()) return null;
        Session.HorsemenTurnEntry e = new Session.HorsemenTurnEntry();
        e.turn = turn;
        e.sender = sender;
        e.criticism = readDouble(node, "criticism");
        e.contempt = readDouble(node, "contempt");
        e.defensiveness = readDouble(node, "defensiveness");
        e.stonewalling = readDouble(node, "stonewalling");
        return e;
    }

    private Session.NvcTurnEntry readNvc(JsonNode node, int turn, String sender) {
        if (node == null || !node.isObject()) return null;
        Session.NvcTurnEntry e = new Session.NvcTurnEntry();
        e.turn = turn;
        e.sender = sender;
        e.observation = readBool(node, "observation");
        e.feeling = readBool(node, "feeling");
        e.need = readBool(node, "need");
        e.request = readBool(node, "request");
        return e;
    }

    private Double readDouble(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return 0.0;
        if (v.isNumber()) {
            double d = v.asDouble();
            if (d < 0) d = 0;
            if (d > 1) d = 1;
            return d;
        }
        return 0.0;
    }

    private Boolean readBool(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return false;
        if (v.isBoolean()) return v.asBoolean();
        return false;
    }

    public record Result(
        String mediatorMessage,
        Session.HorsemenTurnEntry horsemen,
        Session.NvcTurnEntry nvc) {}
}
