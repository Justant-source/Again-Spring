package com.againspring.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 게스트 대화 생성 전체 플로우 통합테스트.
 *
 * 각 테스트는 서로 다른 클라이언트 IP를 사용해 GuestSessionRateLimiter 버킷을 격리한다.
 * LLM(haiku) 호출 없음 — ClaudeCodeBridge는 GuestFlowITSupport에서 MockBean으로 교체됨.
 */
class GuestConversationFlowIT extends GuestFlowITSupport {

    // 각 테스트마다 고유 IP → GuestSessionRateLimiter 버킷 완전 격리
    private static final String IP_1 = "192.0.2.1";  // 케이스 1 (토큰만, 세션 없음)
    private static final String IP_2 = "192.0.2.2";  // 케이스 2
    private static final String IP_3 = "192.0.2.3";  // 케이스 3
    private static final String IP_4 = "192.0.2.4";  // 케이스 4
    private static final String IP_5 = "192.0.2.5";  // 케이스 5
    private static final String IP_6 = "192.0.2.6";  // 케이스 6
    private static final String IP_7 = "192.0.2.7";  // 케이스 7 (rate limit)
    private static final String IP_8 = "192.0.2.8";  // 케이스 8 (turn limit)

    // --- 케이스 1: 게스트 토큰 발급 ---

    @Test
    @DisplayName("1. POST /api/auth/guest → 200, accessToken 반환, guest User DB 영속 확인")
    void guestTokenIssuance() throws Exception {
        String body = """
                {"nickname":"플로우테스트"}
                """;
        MvcResult result = mockMvc.perform(
                        post("/api/auth/guest")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .header("X-Forwarded-For", IP_1))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = json.path("token").path("accessToken").asText();
        String userId      = json.path("user").path("id").asText();
        int expiresIn      = json.path("token").path("expiresIn").asInt();

        assertThat(accessToken).isNotBlank();
        assertThat(userId).isNotBlank();
        assertThat(expiresIn).isEqualTo(7200);
        assertThat(json.path("user").path("isGuest").asBoolean()).isTrue();
    }

    // --- 케이스 2: 세션 생성 + 첫마디 선저장 ---

    @Test
    @DisplayName("2. POST /api/sessions → 201 Created, CHATTING_SOLO 세션 + MEDIATOR_TO_A 첫마디 저장")
    void sessionCreationWithFirstMessage() throws Exception {
        String token = guestToken(IP_2);

        MvcResult result = mockMvc.perform(
                        post("/api/sessions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(sessionBody())
                                .header("Authorization", "Bearer " + token)
                                .header("X-Forwarded-For", IP_2))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String sessionId = json.path("id").asText();
        assertThat(sessionId).as("세션 ID가 반환돼야 함").isNotBlank();

        // 세션 생성 직후 첫마디가 저장됐는지 확인
        JsonNode messages = getMessages(sessionId, token);
        assertThat(messages.isArray()).isTrue();
        assertThat(messages.size()).isGreaterThanOrEqualTo(1);

        boolean hasFirstMessage = false;
        for (JsonNode msg : messages) {
            String sender = msg.path("sender").asText("");
            if (sender.equals("MEDIATOR_TO_A") || sender.startsWith("MEDIATOR")) {
                hasFirstMessage = true;
                assertThat(msg.path("content").asText()).isNotBlank();
            }
        }
        assertThat(hasFirstMessage).as("세션 생성 후 mediator 첫마디가 DB에 저장돼야 함").isTrue();
    }

    // --- 케이스 3: 첫마디 조회 ---

    @Test
    @DisplayName("3. GET /api/sessions/{id}/messages → 첫마디 포함된 메시지 목록 반환")
    void getMessagesReturnsFirstMessage() throws Exception {
        String token     = guestToken(IP_3);
        String sessionId = createSession(token, IP_3);

        JsonNode messages = getMessages(sessionId, token);
        assertThat(messages.isArray()).isTrue();
        assertThat(messages.size()).isGreaterThanOrEqualTo(1);

        String firstContent = messages.get(0).path("content").asText();
        assertThat(firstContent).isNotBlank();
    }

    // --- 케이스 4: 사용자 메시지 전송 ---

    @Test
    @DisplayName("4. POST /api/sessions/{id}/messages → 200, mediatorMessages null (폴링 전제)")
    void sendUserMessage() throws Exception {
        String token     = guestToken(IP_4);
        String sessionId = createSession(token, IP_4);

        MvcResult result = mockMvc.perform(
                        post("/api/sessions/{id}/messages", sessionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"content":"안녕하세요, 테스트 메시지입니다."}
                                        """)
                                .header("Authorization", "Bearer " + token))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        // mediatorMessages는 null 또는 비어있어야 함 (폴링으로 수신)
        assertThat(json.has("mediatorMessages") && json.path("mediatorMessages").isNull()
                || !json.has("mediatorMessages"))
                .as("mediatorMessages는 null이어야 함 (FE 폴링 전제 계약)").isTrue();
    }

    // --- 케이스 5: mediator 응답 폴링 ---

    @Test
    @DisplayName("5. 메시지 전송 후 폴링 → mediator 응답이 3초 내 GET /messages에 나타남")
    void mediatorResponseAppearsAfterSend() throws Exception {
        String token     = guestToken(IP_5);
        String sessionId = createSession(token, IP_5);

        // 첫마디 이후 사용자 메시지 전송
        int status = sendMessage(sessionId, token, "관계에 대해 이야기하고 싶어요.");
        assertThat(status).isEqualTo(200);

        // ClaudeCodeBridge 스텁 resultFuture가 즉시 완료 → handleSuccessfulResponse 동기 실행
        // Awaitility로 방어적 폴링 (비동기 경로에도 대응)
        awaitMediatorResponse(sessionId, token);

        JsonNode messages = getMessages(sessionId, token);
        long mediatorCount = 0;
        for (JsonNode msg : messages) {
            if (msg.path("sender").asText("").startsWith("MEDIATOR")) mediatorCount++;
        }
        // 첫마디(MEDIATOR_TO_A) + 사용자 메시지 이후 mediator 응답 = 최소 2건
        assertThat(mediatorCount).as("첫마디 + mediator 응답으로 2건 이상").isGreaterThanOrEqualTo(2);
    }

    // --- 케이스 6: 인증 누락 거부 ---

    @Test
    @DisplayName("6. 토큰 없이 POST /api/sessions → 401/403 반환")
    void unauthenticatedSessionCreationIsRejected() throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/sessions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(sessionBody())
                                .header("X-Forwarded-For", IP_6))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertThat(status).as("인증 없는 요청은 401 또는 403이어야 함")
                .isIn(401, 403);
    }

    // --- 케이스 7: 게스트 일일 세션 한도 (IP당 3건) ---

    @Test
    @DisplayName("7. IP당 세션 3건 초과 시 POST /api/sessions → 429 GUEST_SESSION_LIMIT")
    void guestIpRateLimitExceeded() throws Exception {
        int dailyLimit = 3; // user-permissions.json guest.sessions.dailyLimit

        // 한도 내 세션 생성: 각기 다른 게스트 계정, 같은 IP
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < dailyLimit; i++) {
            String body = String.format("""
                    {"nickname":"레이트테스트%d"}
                    """, i);
            MvcResult tr = mockMvc.perform(
                            post("/api/auth/guest")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body)
                                    .header("X-Forwarded-For", IP_7))
                    .andReturn();
            tokens.add(objectMapper.readTree(
                    tr.getResponse().getContentAsString()).path("token").path("accessToken").asText());
        }

        for (String token : tokens) {
            MvcResult r = mockMvc.perform(
                            post("/api/sessions")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(sessionBody())
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Forwarded-For", IP_7))
                    .andReturn();
            assertThat(r.getResponse().getStatus())
                    .as("한도 내 세션 생성은 201 Created여야 함").isEqualTo(201);
        }

        // 한도 초과 — 새 게스트가 동일 IP에서 세션 생성 시도
        String extraBody = """
                {"nickname":"한도초과게스트"}
                """;
        MvcResult extraToken = mockMvc.perform(
                        post("/api/auth/guest")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(extraBody)
                                .header("X-Forwarded-For", IP_7))
                .andReturn();
        String over = objectMapper.readTree(
                extraToken.getResponse().getContentAsString()).path("token").path("accessToken").asText();

        MvcResult overResult = mockMvc.perform(
                        post("/api/sessions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(sessionBody())
                                .header("Authorization", "Bearer " + over)
                                .header("X-Forwarded-For", IP_7))
                .andReturn();

        assertThat(overResult.getResponse().getStatus())
                .as("IP 세션 한도 초과 시 429").isEqualTo(429);

        String errorJson = overResult.getResponse().getContentAsString();
        assertThat(errorJson).contains("GUEST_SESSION_LIMIT");
    }

    // --- 케이스 8: 게스트 메시지 턴 한도 (세션당 3턴) ---

    @Test
    @DisplayName("8. 게스트 messageTurnLimit(3) 초과 시 메시지 전송 → 비-200 반환")
    void guestMessageTurnLimitExceeded() throws Exception {
        int turnLimit = 3; // user-permissions.json guest.sessions.messageTurnLimit

        String token     = guestToken(IP_8);
        String sessionId = createSession(token, IP_8);

        // 한도 내 메시지
        for (int i = 0; i < turnLimit; i++) {
            int status = sendMessage(sessionId, token, "테스트 메시지 " + (i + 1));
            assertThat(status).as("turnLimit 내 메시지는 200").isEqualTo(200);
        }

        // 한도 초과
        int overStatus = sendMessage(sessionId, token, "한도 초과 메시지");
        assertThat(overStatus)
                .as("게스트 턴 한도 초과 시 2xx가 아닌 오류 응답")
                .isNotIn(200, 201);
    }

    // --- helpers ---

    private static String sessionBody() {
        return """
                {
                  "relationType": "couple",
                  "category": {
                    "majorId": "couple",
                    "middleId": "couple_affection",
                    "minorId": "forget_anni"
                  }
                }
                """;
    }
}
