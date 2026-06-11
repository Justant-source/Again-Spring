package com.againspring.aiuser.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 프롬프트 캐싱 복원 테스트 (캐싱 P1, 2026-06-11).
 * 핵심 불변:
 * - system 필드 절대 미사용 (clcocloud Kiro 라우팅 버그 회피)
 * - 캐싱 on + PERSONA_SECTION 존재 → user content 2블록, block1에만 cache_control
 * - 기본 TTL 5m → cache_control에 ttl 필드 없음 (beta 헤더도 call()에서 미부착)
 * - 두 블록을 이으면 단일 블록 프롬프트와 의미 동일 (모델 동작 불변)
 */
class ClaudeApiInvokerCacheTest {

    private static final String STATIC_PART = "정적 코어 규칙\n## 커뮤니티 스타일 가이드\n가이드 본문";
    private static final String DYNAMIC_PART = "## 말투 규칙\n반말\n## 페르소나 특성\n40대 주부";
    private static final String SYSTEM = STATIC_PART + "\n\n<<<PERSONA_SECTION>>>\n" + DYNAMIC_PART;
    private static final String USER = "글 제목: 테스트\n댓글을 작성해주세요.";

    private ClaudeApiInvoker invoker;

    @BeforeEach
    void setUp() {
        invoker = new ClaudeApiInvoker(null);
        ReflectionTestUtils.setField(invoker, "promptCaching", true);
        ReflectionTestUtils.setField(invoker, "cacheTtl", "5m");
    }

    @Test
    void cachingSplitsUserContentIntoTwoBlocks() {
        ObjectNode body = invoker.buildRequestBody(SYSTEM, USER, "claude-haiku-4-5-20251001");

        assertFalse(body.has("system"), "system 필드는 절대 사용 금지 (Kiro 라우팅 버그)");
        JsonNode content = body.path("messages").get(0).path("content");
        assertEquals(2, content.size(), "user content 2블록");

        JsonNode block1 = content.get(0);
        assertTrue(block1.path("text").asText().startsWith("<instructions>\n정적 코어 규칙"));
        assertEquals("ephemeral", block1.path("cache_control").path("type").asText());
        assertFalse(block1.path("cache_control").has("ttl"), "기본 5m은 ttl 필드 없음 (GA)");

        JsonNode block2 = content.get(1);
        assertFalse(block2.has("cache_control"), "가변 블록은 캐싱 금지");
        String b2 = block2.path("text").asText();
        assertTrue(b2.contains("페르소나 특성"));
        assertTrue(b2.contains("</instructions>"));
        assertTrue(b2.endsWith(USER));
    }

    @Test
    void joinedBlocksPreserveFullPrompt() {
        ObjectNode body = invoker.buildRequestBody(SYSTEM, USER, "m");
        JsonNode content = body.path("messages").get(0).path("content");
        String joined = content.get(0).path("text").asText() + content.get(1).path("text").asText();
        // 마커 제거 외에 정적·페르소나·유저 내용이 순서대로 전부 보존
        assertTrue(joined.contains(STATIC_PART));
        assertTrue(joined.contains(DYNAMIC_PART));
        assertTrue(joined.contains(USER));
        assertFalse(joined.contains("<<<PERSONA_SECTION>>>"));
        assertTrue(joined.indexOf(STATIC_PART) < joined.indexOf(DYNAMIC_PART));
        assertTrue(joined.indexOf(DYNAMIC_PART) < joined.indexOf(USER));
    }

    @Test
    void oneHourTtlAddsTtlField() {
        ReflectionTestUtils.setField(invoker, "cacheTtl", "1h");
        ObjectNode body = invoker.buildRequestBody(SYSTEM, USER, "m");
        JsonNode cc = body.path("messages").get(0).path("content").get(0).path("cache_control");
        assertEquals("1h", cc.path("ttl").asText());
    }

    @Test
    void cachingDisabledFallsBackToSingleBlock() {
        ReflectionTestUtils.setField(invoker, "promptCaching", false);
        ObjectNode body = invoker.buildRequestBody(SYSTEM, USER, "m");
        JsonNode content = body.path("messages").get(0).path("content");
        assertEquals(1, content.size());
        assertFalse(content.get(0).has("cache_control"));
        String text = content.get(0).path("text").asText();
        assertTrue(text.startsWith("<instructions>\n"));
        assertFalse(text.contains("<<<PERSONA_SECTION>>>"));
        assertTrue(text.endsWith(USER));
    }

    @Test
    void missingPersonaMarkerFallsBackToSingleBlock() {
        ObjectNode body = invoker.buildRequestBody("마커 없는 시스템", USER, "m");
        JsonNode content = body.path("messages").get(0).path("content");
        assertEquals(1, content.size());
        assertFalse(content.get(0).has("cache_control"));
    }

    @Test
    void blankSystemSendsUserOnly() {
        ObjectNode body = invoker.buildRequestBody("", USER, "m");
        JsonNode content = body.path("messages").get(0).path("content");
        assertEquals(1, content.size());
        assertEquals(USER, content.get(0).path("text").asText());
    }
}
