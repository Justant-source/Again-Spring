package com.againspring.service.community;

import com.againspring.domain.community.Post;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.PromptSanitizer;
import com.againspring.repository.community.PostRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마케팅용 홍보 짧은 제목 생성 (IG 훅 등).
 * 사연 생성 시 1회만 호출 — 발행 파이프에서 추가 LLM 없음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromoTitleService {

    public static final int MAX_LEN = 20;

    @Qualifier("remoteLlmProvider")
    private final LLMProvider llmProvider;
    private final PromptSanitizer promptSanitizer;
    private final PostRepository postRepository;
    private final ObjectMapper objectMapper;

    @Value("${llm.model:claude-haiku-4-5-20251001}")
    private String model;

    @Value("${promo-title.enabled:true}")
    private boolean enabled;

    /**
     * 비어 있으면 title/userTitle를 MAX_LEN으로 자른 폴백.
     */
    public static String resolveOrFallback(Post post) {
        if (post == null) return "";
        String promo = post.getPromoTitle();
        if (promo != null && !promo.isBlank()) {
            return truncate(promo.trim(), MAX_LEN);
        }
        String base = post.getTitle() != null && !post.getTitle().isBlank()
                ? post.getTitle()
                : post.getUserTitle();
        return truncate(base != null ? base.trim() : "", MAX_LEN);
    }

    @Async("taskExecutor")
    public void generateAsync(String postId) {
        if (!enabled || postId == null || postId.isBlank()) return;
        try {
            generateAndSave(postId);
        } catch (Exception e) {
            log.warn("PromoTitle generation failed for {}: {}", postId, e.getMessage());
        }
    }

    @Transactional
    public void generateAndSave(String postId) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) return;
        if (post.getPromoTitle() != null && !post.getPromoTitle().isBlank()) {
            return; // already set — one-shot
        }

        String title = post.getTitle() != null ? post.getTitle() : post.getUserTitle();
        String body = post.getBodyPublished() != null ? post.getBodyPublished() : post.getBodyRaw();
        String generated = generate(title, body);
        if (generated == null || generated.isBlank()) {
            generated = resolveOrFallback(post);
        }
        post.setPromoTitle(truncate(generated, MAX_LEN));
        postRepository.save(post);
        log.info("PromoTitle saved for {}: '{}'", postId, post.getPromoTitle());
    }

    /**
     * LLM으로 ≤20자 자극·질문형 훅 생성. 실패 시 null.
     */
    public String generate(String title, String body) {
        if (!enabled) return null;
        try {
            String prompt = buildPrompt(title, body);
            String result = llmProvider.invoke(prompt, model);
            return parseResult(result);
        } catch (Exception e) {
            log.warn("PromoTitle LLM failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildPrompt(String title, String body) {
        String safeTitle = promptSanitizer.sanitize(title != null ? title : "");
        String safeBody = promptSanitizer.sanitize(body != null ? body : "");
        if (safeBody.length() > 800) {
            safeBody = safeBody.substring(0, 800);
        }

        return """
            당신은 한국 인스타그램 커뮤니티 사연 계정의 훅 카피라이터입니다.
            사연을 보고 피드에서 스크롤을 멈추게 하는 **홍보용 짧은 제목**을 만드세요.

            ## 규칙
            - 한글 기준 **최대 20자** (공백 포함). 초과 금지.
            - 자극적이거나 질문형. 예: "장모 때리면 이혼까지?", "연락 한 통이 뭐길래"
            - **금지 표현**: 판결, 처방, 승패, 승자, 패자, 가해자, 피해자, 유죄, 무죄
            - 이모지·해시태그·따옴표·마침표로 끝내지 말 것
            - 사연 내용을 왜곡하지 말 것

            <user_input>
            제목: %s

            본문: %s
            </user_input>

            ## 출력 (JSON only)
            {"promo_title": "20자 이내 훅"}
            """.formatted(safeTitle, safeBody);
    }

    private String parseResult(String jsonResult) {
        try {
            String json = TonalizationService.extractJsonObject(jsonResult);
            JsonNode root = objectMapper.readTree(json);
            String promo = root.path("promo_title").asText(null);
            if (promo == null || promo.isBlank()) return null;
            promo = promo.trim()
                    .replaceAll("^[\"']+|[\"']+$", "")
                    .replaceAll("[.。!?？]+$", "");
            return truncate(promo, MAX_LEN);
        } catch (Exception e) {
            log.debug("PromoTitle parse failed: {}", e.getMessage());
            return null;
        }
    }

    static String truncate(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() <= max) return t;
        return t.substring(0, max);
    }
}
