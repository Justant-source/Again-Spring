package com.againspring.marketing;

/**
 * Telegram copy for X growth-loop replies (outbound 선댓글 / inbound 대댓글).
 */
final class XOpsTelegramAlerts {

    private XOpsTelegramAlerts() {}

    static String posted(String kindLabel, AsmClient.XPublishResult result,
        String targetTweetId, String body) {
        return String.format(
            "💬 [Again-Spring] X %s%n댓글 URL: %s%n대상 글: %s%n댓글: %s",
            kindLabel,
            commentUrl(result),
            statusUrl(targetTweetId),
            body != null ? body : "");
    }

    /** Notification only — not a persona drill. */
    static String originalPosted(AsmClient.XPublishResult result, String storyUrl, String body) {
        return String.format(
            "📝 [Again-Spring] X 원글 (사연 스쿱)%n글 URL: %s%n사연: %s%n본문: %s",
            commentUrl(result),
            storyUrl != null && !storyUrl.isBlank() ? storyUrl : "(없음)",
            body != null ? body : "");
    }

    static String commentUrl(AsmClient.XPublishResult result) {
        if (result != null && result.url() != null && !result.url().isBlank()) {
            return result.url();
        }
        if (result != null) {
            return statusUrl(result.tweetId());
        }
        return "(url 없음)";
    }

    static String statusUrl(String tweetId) {
        if (tweetId == null || tweetId.isBlank()) {
            return "(없음)";
        }
        return "https://x.com/i/web/status/" + tweetId;
    }
}
