package com.againspring.marketing;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses operator Telegram messages for Justant-Bot voice drills.
 */
public final class TelegramDrillCommands {

    public enum Kind {
        DRILL,
        SKIP,
        REPLY,
        IGNORE
    }

    public record Parsed(
        Kind kind,
        int drillCount,
        long chatId,
        Long messageId,
        Long replyToMessageId,
        String text
    ) {}

    private static final Pattern DRILL = Pattern.compile(
        "(?is)^/drill(?:@\\S+)?(?:\\s+(\\d+))?\\s*$");
    private static final Pattern SKIP = Pattern.compile(
        "(?is)^/skip(?:@\\S+)?\\s*$");

    private TelegramDrillCommands() {}

    public static Parsed parse(JsonNode update) {
        if (update == null || !update.isObject()) {
            return ignored();
        }
        JsonNode msg = update.path("message");
        if (!msg.isObject()) {
            msg = update.path("edited_message");
        }
        if (!msg.isObject()) {
            return ignored();
        }
        long chatId = msg.path("chat").path("id").asLong(0);
        long messageId = msg.path("message_id").asLong(0);
        String text = msg.path("text").asText("");
        if (text.isBlank()) {
            text = msg.path("caption").asText("");
        }
        Long replyTo = null;
        JsonNode reply = msg.path("reply_to_message");
        if (reply.isObject() && reply.path("message_id").asLong(0) > 0) {
            replyTo = reply.path("message_id").asLong();
        }
        String trimmed = text == null ? "" : text.trim();
        Matcher drill = DRILL.matcher(trimmed);
        if (drill.matches()) {
            int n = 1;
            if (drill.group(1) != null) {
                try {
                    n = Integer.parseInt(drill.group(1));
                } catch (NumberFormatException ignored) {
                    n = 1;
                }
            }
            n = Math.max(1, Math.min(5, n));
            return new Parsed(Kind.DRILL, n, chatId, messageId > 0 ? messageId : null, replyTo, trimmed);
        }
        if (SKIP.matcher(trimmed).matches()) {
            return new Parsed(Kind.SKIP, 0, chatId, messageId > 0 ? messageId : null, replyTo, trimmed);
        }
        if (replyTo != null && !trimmed.isBlank()) {
            return new Parsed(Kind.REPLY, 0, chatId, messageId > 0 ? messageId : null, replyTo, trimmed);
        }
        return new Parsed(Kind.IGNORE, 0, chatId, messageId > 0 ? messageId : null, replyTo, trimmed);
    }

    private static Parsed ignored() {
        return new Parsed(Kind.IGNORE, 0, 0, null, null, "");
    }
}
