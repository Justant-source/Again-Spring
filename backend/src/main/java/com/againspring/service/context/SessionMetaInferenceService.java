package com.againspring.service.context;

import com.againspring.domain.Session;
import com.againspring.domain.enums.MessageSender;
import com.againspring.domain.enums.RelationType;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.prompt.PromptLoader;
import com.againspring.repository.SessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.againspring.service.ChatService.MODEL_HAIKU;

/**
 * V47 신규 — 자유 서술 기반 세션 메타 자동 추론 서비스.
 *
 * 2-step 처리 (모두 @Async 비차단):
 * ① infer: 자유 서술 → Haiku → {relationType, koreanTag, keywords[2], title} → 세션 DB 저장
 * ② respond: ChatService.sendUserMessage로 첫 중재자 응답 동적 생성 (relations/*.md + 한국 보정 적용)
 *
 * 기존 FirstMessageService 4단계 폴백 체인 완전 대체.
 * ChatService와의 순환 의존은 @Lazy setter 주입으로 해소.
 */
@Slf4j
@Service
public class SessionMetaInferenceService {

    private final LLMProvider chatLlmProvider;
    private final PromptLoader promptLoader;
    private final SessionRepository sessionRepository;

    // 순환 의존 방지: SessionService → SessionMetaInferenceService → ChatService
    private com.againspring.service.ChatService chatService;

    public SessionMetaInferenceService(
            @Qualifier("chatLlmProvider") LLMProvider chatLlmProvider,
            PromptLoader promptLoader,
            SessionRepository sessionRepository) {
        this.chatLlmProvider = chatLlmProvider;
        this.promptLoader = promptLoader;
        this.sessionRepository = sessionRepository;
    }

    @Autowired
    public void setChatService(@Lazy com.againspring.service.ChatService chatService) {
        this.chatService = chatService;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 자유 서술에서 세션 메타(대분류·한국태그·키워드·제목)를 추론하고,
     * 사용자의 서술을 첫 메시지로 저장한 뒤, 표준 채팅 턴으로 첫 중재자 응답을 생성한다.
     *
     * @param session 방금 생성된 세션 (relationType=null 상태)
     * @param description 사용자가 입력한 자유 서술 (null이면 fallback 메시지 사용)
     */
    @Async
    public void inferAndRespondAsync(Session session, String description) {
        try {
            String text = (description != null && !description.isBlank())
                    ? description.strip()
                    : "어떤 일이 있었는지 자유롭게 말씀해 주세요.";

            // Step 1: 메타 추론
            inferMeta(session, text);

            // Step 2: sendUserMessage가 사용자 메시지 저장 + LLM 호출 + 중재자 응답 저장을 모두 처리.
            // 세션의 relationType이 추론되었으므로 relations/*.md 및 한국 보정이 정상 적용됨.
            chatService.sendUserMessage(session.getId(), MessageSender.USER_A, text);
            log.debug("[SessionMeta] First mediator response generated: session={}", session.getId());

        } catch (Exception e) {
            log.warn("[SessionMeta] Async inference failed for session={}: {}", session.getId(), e.getMessage(), e);
            // 실패해도 세션 자체는 정상 — 사용자는 대화를 시작할 수 있고 AI가 첫 턴에서 질문
        }
    }

    /**
     * 자유 서술 → Haiku 추론 → relationType / koreanTag / keywords / title 세션에 저장.
     */
    private void inferMeta(Session session, String text) {
        try {
            String inferPrompt = promptLoader.get("chat/infer_session_meta.md");
            String prompt = inferPrompt + "\n\n<user_description>\n" + text + "\n</user_description>";

            String raw = chatLlmProvider.invoke(prompt, MODEL_HAIKU);
            if (raw == null || raw.isBlank()) {
                log.warn("[SessionMeta] Empty inference response for session={}", session.getId());
                return;
            }

            // JSON 파싱
            String json = raw.strip();
            // 코드 블록 잔재 제거
            if (json.startsWith("```")) {
                json = json.replaceAll("```[a-zA-Z]*\\n?", "").replaceAll("```", "").strip();
            }

            JsonNode root = objectMapper.readTree(json);

            // relationType
            JsonNode rtNode = root.get("relationType");
            if (rtNode != null && !rtNode.isNull() && rtNode.isTextual()) {
                try {
                    RelationType rt = RelationType.fromValue(rtNode.asText());
                    session.setRelationType(rt);
                } catch (IllegalArgumentException ex) {
                    log.warn("[SessionMeta] Unknown relationType '{}' for session={}", rtNode.asText(), session.getId());
                }
            }

            // koreanTag
            JsonNode ktNode = root.get("koreanTag");
            if (ktNode != null && !ktNode.isNull() && ktNode.isTextual()) {
                session.setKoreanTag(ktNode.asText());
            }

            // keywords
            JsonNode kwNode = root.get("keywords");
            if (kwNode != null && kwNode.isArray()) {
                List<String> keywords = new ArrayList<>();
                for (JsonNode kw : kwNode) {
                    if (kw.isTextual()) keywords.add(kw.asText());
                    if (keywords.size() >= 2) break;
                }
                session.setKeywords(keywords);
            }

            // title (사용자가 직접 수정하지 않은 경우에만 저장)
            if (!Boolean.TRUE.equals(session.getTitleEditedByUser())) {
                JsonNode titleNode = root.get("title");
                if (titleNode != null && !titleNode.isNull() && titleNode.isTextual()) {
                    String title = titleNode.asText().strip();
                    if (!title.isBlank()) session.setTitle(title);
                }
            }

            sessionRepository.save(session);
            log.info("[SessionMeta] Meta inferred: session={}, relationType={}, koreanTag={}, title={}",
                    session.getId(), session.getRelationType(), session.getKoreanTag(), session.getTitle());

        } catch (Exception e) {
            log.warn("[SessionMeta] Meta inference parse failed for session={}: {}", session.getId(), e.getMessage());
        }
    }
}
