package com.againspring.service.marketing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Calculates token-level edit ratio between original and rewritten text.
 * V15.2: Rewrite quality metric for marketing stories.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class RewriteRatioCalculator {

    public double calculate(String original, String rewritten) {
        if (original == null) {
            original = "";
        }
        if (rewritten == null) {
            rewritten = "";
        }

        boolean origEmpty = original.isBlank();
        boolean rewriteEmpty = rewritten.isBlank();

        if (origEmpty && rewriteEmpty) {
            return 0.0;
        }
        if (origEmpty || rewriteEmpty) {
            return 1.0;
        }

        List<String> origTokens = tokenize(original);
        List<String> rewriteTokens = tokenize(rewritten);

        int editDistance = levenshteinDistance(origTokens, rewriteTokens);
        int maxLen = Math.max(origTokens.size(), rewriteTokens.size());

        if (maxLen == 0) {
            return 0.0;
        }

        return Math.min(1.0, (double) editDistance / maxLen);
    }

    private List<String> tokenize(String text) {
        return Arrays.asList(text.trim().split("\\s+"));
    }

    private int levenshteinDistance(List<String> s1, List<String> s2) {
        int len1 = s1.size();
        int len2 = s2.size();

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                if (s1.get(i - 1).equals(s2.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(
                        Math.min(dp[i - 1][j], dp[i][j - 1]),
                        dp[i - 1][j - 1]
                    );
                }
            }
        }

        return dp[len1][len2];
    }
}
