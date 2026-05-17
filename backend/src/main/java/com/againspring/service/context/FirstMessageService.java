package com.againspring.service.context;

import com.againspring.domain.Message;
import com.againspring.domain.Session;
import com.againspring.domain.enums.MessageSender;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.prompt.PromptLoader;
import com.againspring.repository.MessageRepository;
import com.againspring.service.category.CategoryCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.againspring.service.ChatService.MODEL_HAIKU;

/**
 * V13 Phase 1 — mediator 첫마디 결정 서비스.
 *
 * 결정 흐름:
 * ① 표준 소분류(allowCustomInput=false) → 템플릿 풀 랜덤 1개
 * ② custom/자유텍스트 소분류 → Haiku 실시간 호출 (first_message_freeinput.md 프롬프트)
 * ③ Haiku 실패 → 대·중분류 기반 기본 fallback
 * ④ 전부 실패 → 보편 firstMessage
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FirstMessageService {

    private static final String UNIVERSAL_FALLBACK =
        "어떤 얘기를 나누고 싶으신지 들려주세요. 무엇이 마음에 가장 무거우신가요?";

    private final FirstMessageTemplateLoader templateLoader;
    private final LLMProvider llmBridge;
    private final PromptLoader promptLoader;
    private final CategoryCatalog catalog;
    private final MessageRepository messageRepository;

    /** 세션 생성 응답을 블로킹하지 않도록 비동기로 첫마디 생성·저장 */
    @Async
    public void generateAndSaveAsync(Session session) {
        try {
            String content = generateFirstMessage(session);
            messageRepository.save(Message.builder()
                    .sessionId(session.getId())
                    .sender(MessageSender.MEDIATOR_TO_A)
                    .content(content)
                    .charCount(content.length())
                    .llmModel("template-or-haiku")
                    .build());
            log.debug("First message saved async for session={}", session.getId());
        } catch (Exception e) {
            log.warn("Async first message generation failed for session={}: {}", session.getId(), e.getMessage());
        }
    }

    public String generateFirstMessage(Session session) {
        Session.Category cat = session.getCategory();

        // ① 표준 소분류 → 템플릿
        if (cat != null && cat.majorId != null && cat.middleId != null
                && cat.minorId != null && !"custom".equals(cat.minorId)) {
            Optional<String> template = templateLoader.getTemplate(cat.majorId, cat.middleId, cat.minorId);
            if (template.isPresent()) {
                log.debug("First message from template: {}/{}/{}", cat.majorId, cat.middleId, cat.minorId);
                return template.get();
            }
            log.warn("Template not found for {}/{}/{}, falling back to Haiku", cat.majorId, cat.middleId, cat.minorId);
        }

        // ② custom 자유텍스트 or 템플릿 누락 → Haiku
        if (cat != null && cat.majorId != null) {
            try {
                String haiku = callHaikuFirstMessage(session, cat);
                if (haiku != null && !haiku.isBlank()) {
                    log.debug("First message from Haiku (session={})", session.getId());
                    return haiku.strip();
                }
            } catch (Exception e) {
                log.warn("Haiku first-message call failed: {}", e.getMessage());
            }
        }

        // ③ 대·중분류 기반 단순 fallback
        if (cat != null && cat.majorId != null) {
            String majorLabel = getMajorLabel(cat.majorId);
            String middleLabel = getMiddleLabel(cat.majorId, cat.middleId);
            if (middleLabel != null) {
                return majorLabel + "에서 " + middleLabel + " 문제로 마음이 무거우셨겠어요. 어떤 일이 있었는지 들려주시겠어요?";
            }
            if (majorLabel != null) {
                return majorLabel + " 관계에서 마음이 무거우셨겠어요. 어떤 일이 있었는지 들려주시겠어요?";
            }
        }

        // ④ 보편 fallback
        return UNIVERSAL_FALLBACK;
    }

    private String callHaikuFirstMessage(Session session, Session.Category cat) throws Exception {
        String freeinputPrompt;
        try {
            freeinputPrompt = promptLoader.get("chat/first_message_freeinput.md");
        } catch (Exception e) {
            log.warn("first_message_freeinput.md load failed: {}", e.getMessage());
            return null;
        }

        String systemMd;
        try {
            systemMd = promptLoader.get("system.md");
        } catch (Exception e) {
            systemMd = "";
        }

        StringBuilder prompt = new StringBuilder();
        if (!systemMd.isBlank()) {
            prompt.append(systemMd).append("\n\n");
        }
        prompt.append(freeinputPrompt).append("\n\n");

        // 카테고리 컨텍스트 주입
        prompt.append("<session_context>\n");
        String majorLabel = getMajorLabel(cat.majorId);
        String middleLabel = getMiddleLabel(cat.majorId, cat.middleId);
        String minorLabel = getMinorLabel(cat.majorId, cat.middleId, cat.minorId);

        if (majorLabel != null) prompt.append("대분류: ").append(majorLabel).append("\n");
        if (middleLabel != null) prompt.append("중분류: ").append(middleLabel).append("\n");
        if (minorLabel != null && !"직접 입력".equals(minorLabel)) {
            prompt.append("소분류: ").append(minorLabel).append("\n");
        }
        if (cat.customText != null && !cat.customText.isBlank()) {
            prompt.append("소분류 (사용자 직접 입력): \"").append(cat.customText.strip()).append("\"\n");
        }
        prompt.append("</session_context>\n");

        return llmBridge.invoke(prompt.toString(), MODEL_HAIKU);
    }

    private String getMajorLabel(String majorId) {
        if (majorId == null) return null;
        CategoryCatalog.MajorCategory m = catalog.getMajor(majorId);
        return m != null ? m.getLabel() : null;
    }

    private String getMiddleLabel(String majorId, String middleId) {
        if (majorId == null || middleId == null) return null;
        CategoryCatalog.MiddleCategory m = catalog.getMiddle(majorId, middleId);
        return m != null ? m.getLabel() : null;
    }

    private String getMinorLabel(String majorId, String middleId, String minorId) {
        if (majorId == null || middleId == null || minorId == null) return null;
        CategoryCatalog.MinorCategory m = catalog.getMinor(majorId, middleId, minorId);
        return m != null ? m.getLabel() : null;
    }
}
