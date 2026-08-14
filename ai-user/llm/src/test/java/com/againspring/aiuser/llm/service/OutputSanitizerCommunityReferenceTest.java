package com.againspring.aiuser.llm.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputSanitizerCommunityReferenceTest {

    private final OutputSanitizer sanitizer = new OutputSanitizer();

    @Test
    void replacesCrawledCommunityReferencesWithGenericTerms() {
        String result = sanitizer.sanitizePost("네이트판에 올린 글인데 블라와 블라인드에서도 반응이 컸음");

        assertFalse(result.contains("네이트판"), result);
        assertFalse(result.contains("블라"), result);
        assertFalse(result.contains("블라인드"), result);
        assertTrue(result.contains("온라인 커뮤니티"));
    }

    @Test
    void onlyReplacesPannInCommunityContext() {
        String result = sanitizer.sanitizePost("판글 보고 판에 올릴까 했음. 판사님도 판결문을 읽었음");

        assertTrue(result.contains("커뮤니티글"), result);
        assertTrue(result.contains("커뮤니티에"), result);
        assertTrue(result.contains("판사님"), result);
    }

    @Test
    void repairsParticlesAfterReplacingACommunityName() {
        String result = sanitizer.sanitizePost("네이트판은 시끄럽고 판녀들도 많았음");

        assertTrue(result.contains("온라인 커뮤니티는"), result);
        assertTrue(result.contains("커뮤니티 이용자들도"), result);
    }
}
