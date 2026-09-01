package com.againspring.marketing;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Distinguishes operator-typed X replies from automated x_thread chains.
 */
public final class XManualStatusClassifier {

    public record Status(String id, String text, String replyToHandle, boolean quote) {}

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
        if (status == null || status.id() == null || status.id().isBlank()) {
            return false;
        }
        if (autoPostedIds != null && autoPostedIds.contains(status.id())) {
            return false;
        }
        String text = status.text() != null ? status.text().trim() : "";
        if (isBrandHook(text)) {
            return false;
        }
        String ours = ourHandle != null ? ourHandle.trim() : "";
        String replyTo = status.replyToHandle();
        if (replyTo != null && !replyTo.isBlank() && replyTo.equalsIgnoreCase(ours)) {
            return false;
        }
        if (!hasHumanText(text)) {
            return false;
        }
        if (replyTo != null && !replyTo.isBlank()) {
            return true;
        }
        return status.quote();
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
