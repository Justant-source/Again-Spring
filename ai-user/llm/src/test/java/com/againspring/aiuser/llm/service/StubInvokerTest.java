package com.againspring.aiuser.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StubInvokerTest {

    @Test
    void schemaFixtureIsValidJsonWithPlanShape() throws Exception {
        StubInvoker stub = new StubInvoker(null);
        String out = stub.invokeSingleAttempt("ignored", "ignored", StructuredOutputSchema.THREAD_PLAN);
        assertTrue(out.trim().startsWith("{"), "fixture must be a JSON object");
        assertTrue(out.contains("\"comments\""), "thread-plan fixture must carry comments[]");
    }

    @Test
    void plainInvokeReturnsKoreanText() throws Exception {
        StubInvoker stub = new StubInvoker(null);
        String out = stub.invoke("ignored", "ignored");
        assertFalse(out.isBlank());
        long hangul = out.chars().filter(c -> c >= 0xAC00 && c <= 0xD7A3).count();
        assertTrue(hangul > 10, "stub text must be Korean so LlmErrorSignature language guard passes");
    }

    @Test
    void fixtureDirOverridesClasspath() throws Exception {
        Path dir = Files.createTempDirectory("stub");
        Files.writeString(dir.resolve("plain.txt"), "오버라이드 본문입니다 테스트 테스트 테스트");
        StubInvoker stub = new StubInvoker(dir.toString());
        assertTrue(stub.invoke("x", "y").startsWith("오버라이드"));
    }

    /**
     * StructuredSchemaCatalog에 스키마별 파일명 매핑 메서드가 따로 없어(THREAD_PLAN 케이스만
     * 브리프에 실려 있음), 나머지 3개 스키마는 여기서 JSON 유효성 + required 최상위 키만 검증한다.
     * (전체 파서 왕복 검증은 ThreadPlanRequest/PairedPhaseNRequest 빌드가 필요해 이 테스트 범위 밖.)
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void pairedPhase1FixtureIsValidJsonWithRequiredKeys() throws Exception {
        StubInvoker stub = new StubInvoker(null);
        String out = stub.invokeSingleAttempt("ignored", "ignored", StructuredOutputSchema.PAIRED_PHASE1);
        JsonNode root = JSON.readTree(out);
        assertTrue(root.has("post"), "paired-phase1 fixture must carry post");
        assertTrue(root.has("comments"), "paired-phase1 fixture must carry comments[]");
        assertTrue(root.path("post").has("title"));
        assertTrue(root.path("post").has("body"));
    }

    @Test
    void pairedPhase2FixtureIsValidJsonWithRequiredKeys() throws Exception {
        StubInvoker stub = new StubInvoker(null);
        String out = stub.invokeSingleAttempt("ignored", "ignored", StructuredOutputSchema.PAIRED_PHASE2);
        JsonNode root = JSON.readTree(out);
        assertTrue(root.has("partner_post"), "paired-phase2 fixture must carry partner_post");
        assertTrue(root.has("comments"), "paired-phase2 fixture must carry comments[]");
        assertTrue(root.path("partner_post").has("body"));
    }

    @Test
    void humanRepliesFixtureIsValidJsonWithRequiredKeys() throws Exception {
        StubInvoker stub = new StubInvoker(null);
        String out = stub.invokeSingleAttempt("ignored", "ignored", StructuredOutputSchema.HUMAN_REPLIES);
        JsonNode root = JSON.readTree(out);
        assertTrue(root.has("replies"), "human-replies fixture must carry replies[]");
        JsonNode first = root.path("replies").get(0);
        assertTrue(first.has("humanCommentId"));
        assertTrue(first.has("personaId"));
        assertTrue(first.has("body"));
    }

    @Test
    void substitutesPersonaPlaceholdersFromPromptIds() throws Exception {
        StubInvoker stub = new StubInvoker(null);
        String prompt = "personas:\n- id: a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4\n- id: ffffffffffffffffffffffffffffffff\n- id: 0123456789abcdef0123456789abcdef\n";
        String out = stub.invokeSingleAttempt(prompt, "m", StructuredOutputSchema.THREAD_PLAN);
        assertFalse(out.contains("__PERSONA_"), "all placeholders must be substituted");
        assertTrue(out.contains("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"));
    }

    @Test
    void dropsCommentsWhosePlaceholderHasNoPersona() throws Exception {
        StubInvoker stub = new StubInvoker(null);
        String prompt = "- id: a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4\n"; // 1개만
        String out = stub.invokeSingleAttempt(prompt, "m", StructuredOutputSchema.THREAD_PLAN);
        assertFalse(out.contains("__PERSONA_"));
        assertEquals(1, out.split("\"ref\"").length - 1, "only c1 survives");
    }
}
