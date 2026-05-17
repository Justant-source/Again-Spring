package com.againspring.integration;

import com.againspring.llm.LLMProvider;
import com.againspring.llm.bridge.CancelableInvocation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 게스트 대화 플로우 통합테스트 공통 베이스.
 * ClaudeCodeBridge는 MockBean으로 교체해 실 haiku 호출 없이 결정론적 응답.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
// @MockBean LLMProvider가 CancelableChatService에 주입됨.
// application-test.yml의 llm.provider=mock → MockLLMProvider 빈 대신 이 mock이 우선 적용.
@TestPropertySource(properties = "llm.provider=none")
public abstract class GuestFlowITSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockBean
    protected LLMProvider mockClaudeCodeBridge;

    // CWD = backend/ in Gradle → resolves to backend/src/test/resources/test-templates/...
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String templatePath = Paths.get("src/test/resources/test-templates/first_message")
                .toAbsolutePath().toString();
        registry.add("app.templates.path", () -> templatePath);
    }

    @BeforeEach
    void stubLlmBridge() {
        Answer<CancelableInvocation> deterministicAnswer = invoc -> {
            String sessionId = invoc.getArgument(2);
            CancelableInvocation ci = new CancelableInvocation(
                    UUID.randomUUID().toString(), sessionId);
            // resultFuture 즉시 완료 → whenComplete 콜백이 동기로 실행됨
            ci.getResultFuture().complete("[통합테스트] 두 분의 이야기를 잘 들었어요.");
            return ci;
        };
        when(mockClaudeCodeBridge.invokeCancelable(any(), any(), any()))
                .thenAnswer(deterministicAnswer);
    }

    // --- 헬퍼 ---

    /** POST /api/auth/guest → accessToken 반환 */
    protected String guestToken(String clientIp) throws Exception {
        String body = """
                {"nickname":"테스트게스트"}
                """;
        MvcResult result = mockMvc.perform(
                        post("/api/auth/guest")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .header("X-Forwarded-For", clientIp))
                .andReturn();
        String json = result.getResponse().getContentAsString();
        return objectMapper.readTree(json).path("token").path("accessToken").asText();
    }

    /** POST /api/sessions → session id 반환 (표준 소분류 — 템플릿 경로, haiku 미호출) */
    protected String createSession(String token, String clientIp) throws Exception {
        String body = """
                {
                  "relationType": "couple",
                  "category": {
                    "majorId": "couple",
                    "middleId": "couple_affection",
                    "minorId": "forget_anni"
                  }
                }
                """;
        MvcResult result = mockMvc.perform(
                        post("/api/sessions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Forwarded-For", clientIp))
                .andReturn();
        String json = result.getResponse().getContentAsString();
        return objectMapper.readTree(json).path("id").asText();
    }

    /** POST /api/sessions/{id}/messages → HTTP 상태코드 반환 */
    protected int sendMessage(String sessionId, String token, String content) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("content", content));
        return mockMvc.perform(
                        post("/api/sessions/{id}/messages", sessionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    /** GET /api/sessions/{id}/messages → 메시지 배열 JsonNode */
    protected com.fasterxml.jackson.databind.JsonNode getMessages(
            String sessionId, String token) throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/sessions/{id}/messages", sessionId)
                                .header("Authorization", "Bearer " + token))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** mediator 응답이 2건 이상(첫마디 + 사용자 메시지 이후 응답)이 될 때까지 최대 3초 폴링 */
    protected void awaitMediatorResponse(String sessionId, String token) {
        Awaitility.await()
                .atMost(3, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> {
                    com.fasterxml.jackson.databind.JsonNode msgs = getMessages(sessionId, token);
                    long mediatorCount = 0;
                    for (var msg : msgs) {
                        if (msg.path("sender").asText("").startsWith("MEDIATOR")) mediatorCount++;
                    }
                    return mediatorCount >= 2;  // 첫마디 + 메시지 이후 응답
                });
    }
}
