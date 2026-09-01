package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.repository.ai.SystemSettingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Post-compose filters for X outbound drafts. Thresholds live in
 * {@code marketing.x.outbound_guards} JSON, with code defaults when unset.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboundDraftGuard {

    public static final String KEY = "marketing.x.outbound_guards";

    public static final int DEFAULT_MAX_NON_WHITESPACE = 40;
    public static final int DEFAULT_MAX_LINES = 2;
    public static final int DEFAULT_LAUGH_RUN = 4;
    public static final double DEFAULT_LAUGH_RATIO = 0.5;
    public static final double DEFAULT_ECHO_SIMILARITY = 0.9;

    private static final Pattern LAUGH_RUN = Pattern.compile("[ㅋㅎ]{4,}|haha{2,}|lolol+", Pattern.CASE_INSENSITIVE);

    private final SystemSettingRepository systemSettingRepository;
    private final ObjectMapper objectMapper;

    @FunctionalInterface
    interface Rule {
        String reason(String body, List<String> peerReplies, Config config);
    }

    public record Config(
        int maxNonWhitespaceChars,
        int maxLines,
        int laughRun,
        double laughRatio,
        double echoSimilarity
    ) {
        public static Config defaults() {
            return new Config(
                DEFAULT_MAX_NON_WHITESPACE,
                DEFAULT_MAX_LINES,
                DEFAULT_LAUGH_RUN,
                DEFAULT_LAUGH_RATIO,
                DEFAULT_ECHO_SIMILARITY);
        }
    }

    private static final List<Rule> CHAIN = List.of(
        OutboundDraftGuard::tooLong,
        OutboundDraftGuard::laughSpam,
        OutboundDraftGuard::echo
    );

    public Optional<String> firstViolation(String body, List<String> peerReplies) {
        return firstViolation(body, null, peerReplies);
    }

    public Optional<String> firstViolation(String body, String postText, List<String> peerReplies) {
        if (body == null || body.isBlank()) {
            return Optional.of("UNSURE");
        }
        Config config = loadConfig();
        List<String> peers = peerReplies == null ? List.of() : peerReplies;
        for (Rule rule : CHAIN) {
            String reason = rule.reason(body, peers, config);
            if (reason != null) {
                return Optional.of(reason);
            }
        }
        String lang = langMismatch(body, postText);
        if (lang != null) {
            return Optional.of(lang);
        }
        return Optional.empty();
    }

    static String tooLong(String body, List<String> peerReplies, Config config) {
        int nonWs = body.replaceAll("\\s+", "").length();
        if (nonWs > config.maxNonWhitespaceChars()) {
            return "TOO_LONG";
        }
        long lines = body.strip().lines().count();
        if (lines >= 3 || lines > config.maxLines()) {
            return "TOO_LONG";
        }
        return null;
    }

    static String laughSpam(String body, List<String> peerReplies, Config config) {
        int run = Math.max(config.laughRun(), 1);
        Pattern runPat = run == 4 ? LAUGH_RUN : Pattern.compile("[ㅋㅎ]{" + run + ",}|haha{2,}|lolol+", Pattern.CASE_INSENSITIVE);
        if (runPat.matcher(body).find()) {
            return "LAUGH_SPAM";
        }
        int laugh = 0;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == 'ㅋ' || ch == 'ㅎ') {
                laugh++;
            }
        }
        int len = body.length();
        if (len > 0 && laugh >= len * config.laughRatio()) {
            return "LAUGH_SPAM";
        }
        return null;
    }

    static String echo(String body, List<String> peerReplies, Config config) {
        String compact = compact(body);
        if (compact.isEmpty() || peerReplies == null) {
            return null;
        }
        for (String peer : peerReplies) {
            if (peer == null || peer.isBlank()) {
                continue;
            }
            String other = compact(peer);
            if (other.isEmpty()) {
                continue;
            }
            if (compact.equals(other) || similarity(compact, other) >= config.echoSimilarity()) {
                return "ECHO";
            }
        }
        return null;
    }

    /** Latin-majority post vs Hangul-majority body (or the reverse). Short/mixed captions skipped. */
    static String langMismatch(String body, String postText) {
        if (postText == null || postText.isBlank()) {
            return null;
        }
        Script post = scriptOf(postText);
        Script reply = scriptOf(body);
        if (post == Script.LATIN && reply == Script.HANGUL) {
            return "LANG_MISMATCH";
        }
        if (post == Script.HANGUL && reply == Script.LATIN) {
            return "LANG_MISMATCH";
        }
        return null;
    }

    enum Script { LATIN, HANGUL, MIXED }

    static Script scriptOf(String s) {
        int latin = 0;
        int hangul = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) {
                latin++;
            } else if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HANGUL) {
                hangul++;
            }
        }
        int letters = latin + hangul;
        if (letters < 4) {
            return Script.MIXED;
        }
        if (latin >= letters * 0.7) {
            return Script.LATIN;
        }
        if (hangul >= letters * 0.7) {
            return Script.HANGUL;
        }
        return Script.MIXED;
    }

    Config loadConfig() {
        Config d = Config.defaults();
        String raw = systemSettingRepository.findById(KEY)
            .map(SystemSetting::getSettingValue)
            .filter(v -> v != null && !v.isBlank())
            .orElse(null);
        if (raw == null) {
            return d;
        }
        try {
            JsonNode n = objectMapper.readTree(raw);
            return new Config(
                n.path("maxNonWhitespaceChars").asInt(d.maxNonWhitespaceChars()),
                n.path("maxLines").asInt(d.maxLines()),
                n.path("laughRun").asInt(d.laughRun()),
                n.path("laughRatio").asDouble(d.laughRatio()),
                n.path("echoSimilarity").asDouble(d.echoSimilarity()));
        } catch (Exception e) {
            log.warn("[x-outbound-guard] invalid {}: {}", KEY, e.getMessage());
            return d;
        }
    }

    static String compact(String s) {
        return s.replaceAll("\\s+", "");
    }

    static double similarity(String a, String b) {
        if (a.equals(b)) {
            return 1.0;
        }
        int dist = levenshtein(a, b);
        int max = Math.max(a.length(), b.length());
        return max == 0 ? 1.0 : 1.0 - (dist / (double) max);
    }

    static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[m];
    }
}
