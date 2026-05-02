package com.againspring.service.context;

import com.againspring.domain.Session;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.prompt.PromptLoader;
import com.againspring.service.prompt.IssueContextFragment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.againspring.service.ChatService.MODEL_HAIKU;

/**
 * Phase D PR-5 — B 진입 환영 메시지 생성.
 * welcome_partner.md 프롬프트 + IssueContext + 첫 질문 힌트를 조합해 LLM 호출.
 * 실패 시 fallback 정적 메시지 반환.
 *
 * 권위본: shared/docs/policies/context-algorithm.md §6.5
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WelcomeMessageGenerator {

    private final PromptLoader loader;
    private final LLMProvider llmBridge;
    private final IssueContextFragment issueFragment;

    public String generate(Session session, Session.PendingQuestion welcomeQ) {
        StringBuilder p = new StringBuilder();
        // system.md를 먼저 로드해야 Claude Code CLI가 SW 엔지니어링 역할을 거부하지 않음
        try {
            p.append(loader.get("system.md")).append("\n\n");
        } catch (Exception e) {
            log.warn("system.md load failed: {}", e.getMessage());
        }
        try {
            p.append(loader.get("chat/welcome_partner.md")).append("\n\n");
        } catch (Exception e) {
            log.warn("welcome_partner.md load failed: {}", e.getMessage());
        }
        p.append(issueFragment.render(session)).append("\n");
        p.append("<welcome_question>\n");
        p.append("intent: ").append(welcomeQ.intent == null ? "WELCOME_PARTNER" : welcomeQ.intent.name()).append("\n");
        p.append("hint: ").append(welcomeQ.text).append("\n");
        p.append("category: ").append(
            session.getCategory() == null ? "none" : session.getCategory().majorId
        ).append("\n");
        p.append("</welcome_question>\n");

        try {
            String result = llmBridge.invoke(p.toString(), MODEL_HAIKU);
            if (result != null && !result.isBlank()) return result.strip();
        } catch (Exception e) {
            log.warn("Welcome message LLM failed: {}", e.getMessage());
        }
        return fallback();
    }

    private String fallback() {
        return "함께 정리하러 와주셔서 고마워요. 천천히 마음을 들려주세요. "
             + "상대분이 적으신 내용은 제가 따로 듣고 있어요. 두 분의 이야기는 서로 보이지 않아요. "
             + "당신 입장에서, 최근 두 분 사이에 어떤 마음이 드셨는지 편하게 들려주실 수 있어요?";
    }
}
