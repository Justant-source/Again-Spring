package com.againspring.service.marketing;

import com.againspring.domain.marketing.MarketingSourceStory;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.bridge.PromptSanitizer;
import com.againspring.repository.marketing.MarketingSourceStoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Anonymizes raw user stories by replacing proper nouns, locations, and workplace references.
 * V15.2: Story anonymization for marketing module.
 * anonymizeAndUpdate() runs async (marketingExecutor) after story is persisted.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class StoryAnonymizationService {

    private final LLMProvider llmProvider;
    private final PromptSanitizer sanitizer;
    private final RewriteRatioCalculator ratioCalc;
    private final MarketingSourceStoryRepository storyRepo;

    private static final String MODEL = "claude-sonnet-4-6";

    public StoryAnonymizationService(LLMProvider llmProvider, PromptSanitizer sanitizer,
                                     RewriteRatioCalculator ratioCalc,
                                     MarketingSourceStoryRepository storyRepo) {
        this.llmProvider = llmProvider;
        this.sanitizer = sanitizer;
        this.ratioCalc = ratioCalc;
        this.storyRepo = storyRepo;
    }

    @Async("marketingExecutor")
    @Transactional
    public void anonymizeAndUpdate(Long storyId, String rawText) {
        MarketingSourceStory story = storyRepo.findById(storyId).orElse(null);
        if (story == null) {
            log.warn("Story {} not found for async anonymization", storyId);
            return;
        }
        try {
            String anonymizedText = anonymize(rawText);
            double ratio = ratioCalc.calculate(rawText, anonymizedText);

            if (ratio < 0.5) {
                log.info("Rewrite ratio {} < 0.5, retrying anonymization for story {}", ratio, storyId);
                try {
                    anonymizedText = anonymize(rawText);
                    ratio = ratioCalc.calculate(rawText, anonymizedText);
                } catch (Exception e) {
                    log.warn("Retry anonymization failed for story {}", storyId);
                }
            }

            story.setAnonymizedText(anonymizedText);
            story.setRewriteRatio(BigDecimal.valueOf(ratio));

            if (ratio < 0.5) {
                story.setStatus(MarketingSourceStory.Status.REJECTED);
                story.setBlockedReason("재작성률 50% 미달");
            }
            storyRepo.save(story);
            log.info("Async anonymization completed for story {}: ratio={}, status={}", storyId, ratio, story.getStatus());
        } catch (Exception e) {
            log.error("Async anonymization failed for story {}", storyId, e);
            story.setStatus(MarketingSourceStory.Status.REJECTED);
            story.setBlockedReason("익명화 실패");
            story.setAnonymizedText("");
            storyRepo.save(story);
        }
    }

    String anonymize(String rawText) throws Exception {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        String sanitized = sanitizer.sanitize(rawText, "story-anonymization");
        String prompt = buildAnonymizationPrompt(sanitized);
        String anonymized = llmProvider.invoke(prompt, MODEL);
        return anonymized != null ? anonymized.trim() : rawText;
    }

    private String buildAnonymizationPrompt(String story) {
        return "다음 이야기를 익명화하세요:\n\n" +
                "1. 고유명사(인명, 가족관계명)를 100% 치환 (예: 김철수 → A, 어머니 → B)\n" +
                "2. 지역명을 광역 단위로 변경 (예: 서초구 → 서울, 강남동 → 경기)\n" +
                "3. 직장명·회사명을 업종으로 교체 (예: 삼성전자 → IT회사, 대형마트 → 소매업체)\n" +
                "4. 전화번호, 주소 등 개인정보 제거\n" +
                "5. 의미와 문맥은 유지\n\n" +
                "예시:\n" +
                "원문: \"서울 강남구의 박민지가 삼성에서 일하고 있어요. 남편 이순신과 싸웠어요.\"\n" +
                "익명화: \"서울의 A가 IT회사에서 일하고 있어요. 남편 B와 싸웠어요.\"\n\n" +
                "이제 다음 이야기를 익명화하세요:\n" +
                story + "\n\n" +
                "익명화된 이야기만 출력하세요. 추가 설명은 하지 않습니다.";
    }
}
