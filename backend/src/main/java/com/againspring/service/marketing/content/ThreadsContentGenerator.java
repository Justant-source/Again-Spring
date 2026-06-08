package com.againspring.service.marketing.content;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.llm.LLMProvider;
import com.againspring.safety.MarketingCopyGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Generates Threads marketing content from a real community post.
 */
@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ThreadsContentGenerator implements ContentGenerator {

    private final LLMProvider llmProvider;
    private final MarketingCopyGuard copyGuard;

    @Override
    public MarketingContent.Platform supports() {
        return MarketingContent.Platform.THREADS;
    }

    @Override
    public GenerationOutput generate(GenerationContext ctx) throws Exception {
        String prompt = buildPrompt(ctx.sourceContent(), ctx.relationType(), ctx.templateBody());
        String rawResponse = llmProvider.invoke(prompt, "claude-sonnet-4-6");
        String sanitized = copyGuard.sanitize(rawResponse);
        log.info("Generated Threads content for relation type: {}", ctx.relationType());
        return GenerationOutput.textOnly(sanitized);
    }

    private String buildPrompt(String sourceContent, String relationType, String templateBody) {
        String templateSection = (templateBody != null && !templateBody.isBlank())
                ? "\n\nUse this content template as the base structure:\n" + templateBody
                : "";
        return """
                당신은 "다시봄"의 마케팅 카피라이터입니다.
                다시봄 = "갈등을 커뮤니티로 풀다" — 갈등 사연을 올리면 AI 배심원 9인과 커뮤니티가
                양쪽 입장을 분석하고 공감 비율을 보여주는 광장형 서비스.
                철학: "공감이지 판결이 아니다."
                톤: 존댓말, 따뜻하고 차분하게. 이모지 금지.
                금지어: 판결/상담/치료/정신과/확실/보장/100%%.

                아래 다시봄 커뮤니티 사연을 Threads 포스트(300자 이내)로 만드세요.
                작성자 vs 상대방 대비 + 공감 비율 훅 + 사연 링크 CTA 포함.
                스포일러 금지. 결론 단정 금지. 이모지 금지.
                끝에 해시태그 3개 포함.

                [사연 정보]
                %s

                [관계 유형] %s
                %s
                """.formatted(sourceContent, relationType, templateSection);
    }
}
