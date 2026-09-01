package com.againspring.marketing;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Distinguishes operator-typed X posts/replies from automated x_thread chains.
 */
public final class XManualStatusClassifier {

    public enum Classification {
        MANUAL_REPLY,
        MANUAL_POST,
        NOT_MANUAL
    }

    public record Status(
            String id,
            String text,
            String replyToHandle,
            boolean quote,
            String replyToStatusId,
            String quoteText,
            boolean hasMedia) {

        public static Status reply(String id, String text, String replyToHandle) {
            return reply(id, text, replyToHandle, null, false);
        }

        public static Status reply(
                String id, String text, String replyToHandle, String replyToStatusId, boolean hasMedia) {
            return new Status(id, text, replyToHandle, false, replyToStatusId, null, hasMedia);
        }

        public static Status post(String id, String text) {
            return post(id, text, false);
        }

        public static Status post(String id, String text, boolean hasMedia) {
            return new Status(id, text, null, false, null, null, hasMedia);
        }

        public static Status quote(String id, String text, String quoteText) {
            return quote(id, text, quoteText, false);
        }

        public static Status quote(String id, String text, String quoteText, boolean hasMedia) {
            return new Status(id, text, null, true, null, quoteText, hasMedia);
        }
    }

    private static final Pattern URL_ONLY = Pattern.compile("(?is)^\\s*https?://\\S+\\s*$");
    private static final Pattern MENTION = Pattern.compile("@[A-Za-z0-9_]+");

    private XManualStatusClassifier() {}

    public static boolean isManual(Status status, String ourHandle) {
        return isManual(status, ourHandle, Set.of());
    }

    /**
     * {@code autoPostedIds} are tweet ids Justant-Bot already posted (ledger).
     * Those look like human replies on the timeline but must not become gold.
     */
    public static boolean isManual(Status status, String ourHandle, Set<String> autoPostedIds) {
        return classify(status, ourHandle, autoPostedIds) != Classification.NOT_MANUAL;
    }

    public static Classification classify(Status status, String ourHandle) {
        return classify(status, ourHandle, Set.of());
    }

    public static Classification classify(Status status, String ourHandle, Set<String> autoPostedIds) {
        if (status == null || status.id() == null || status.id().isBlank()) {
            return Classification.NOT_MANUAL;
        }
        if (autoPostedIds != null && autoPostedIds.contains(status.id())) {
            return Classification.NOT_MANUAL;
        }
        String text = status.text() != null ? status.text().trim() : "";
        if (isBrandHook(text)) {
            return Classification.NOT_MANUAL;
        }
        String ours = ourHandle != null ? ourHandle.trim() : "";
        String replyTo = status.replyToHandle();
        if (replyTo != null && !replyTo.isBlank() && replyTo.equalsIgnoreCase(ours)) {
            return Classification.NOT_MANUAL;
        }
        if (!hasHumanText(text)) {
            return Classification.NOT_MANUAL;
        }
        if (replyTo != null && !replyTo.isBlank()) {
            return Classification.MANUAL_REPLY;
        }
        if (status.quote()) {
            return Classification.MANUAL_REPLY;
        }
        return Classification.MANUAL_POST;
    }

    static boolean isBrandHook(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("#다시봄") || lower.contains("#againspring");
    }

    static boolean hasHumanText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (URL_ONLY.matcher(text).matches()) {
            return false;
        }
        String stripped = MENTION.matcher(text).replaceAll("").trim();
        return !stripped.isBlank() && !URL_ONLY.matcher(stripped).matches();
    }
}
