package com.againspring.service.marketing.content;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.llm.LLMProvider;
import com.againspring.safety.MarketingCopyGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates Naver Blog marketing content from a real community post.
 * Produces 800-1200 character SEO-friendly blog post in markdown.
 */
@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class NaverBlogContentGenerator implements ContentGenerator {

    private final LLMProvider llmProvider;
    private final MarketingCopyGuard copyGuard;

    @Override
    public MarketingContent.Platform supports() {
        return MarketingContent.Platform.NAVER_BLOG;
    }

    @Override
    public GenerationOutput generate(GenerationContext ctx) throws Exception {
        String prompt = buildPrompt(ctx.sourceContent(), ctx.relationType(), ctx.templateBody());
        String rawResponse = llmProvider.invoke(prompt, "claude-sonnet-4-6");
        String sanitizedRaw = copyGuard.sanitize(rawResponse);
        log.info("Generated Naver Blog content for relation type: {}", ctx.relationType());
        return GenerationOutput.fromLlmJson(sanitizedRaw);
    }

    private String buildPrompt(String sourceContent, String relationType, String templateBody) {
        String templateSection = (templateBody != null && !templateBody.isBlank())
                ? "\n\nUse this content template as the base structure:\n" + templateBody
                : "";
        return """
                당신은 "다시봄"의 마케팅 카피라이터입니다.
                다시봄 = "갈등을 커뮤니티로 풀다" — 사연을 올리면 AI 배심원 9인과 커뮤니티가
                양쪽 입장을 분석하고 '공감 비율'을 보여주는 광장형 서비스.
                철학: "공감이지 판결이 아니다." 누가 잘못했는지 판결하지 않고 여러 시선을 빌려줍니다.
                톤: 존댓말, 따뜻하고 차분하게. SEO 친화적. 이모지 금지.
                금지어: 판결/유죄/가해자/과실비율, 진단명/상담/치료/정신과, 확실/보장/100%%.

                아래 다시봄 커뮤니티 사연을 네이버 블로그 포스트로 만드세요.
                궁금증을 자극하면서도 명확하게 소개해, 독자가 다시봄 광장에 접속해
                양쪽 입장을 읽고 공감 투표에 참여하고 싶게 만드세요.
                반드시:
                - 작성자 vs 상대방 양쪽 입장의 긴장/대비를 소개
                - 현재 공감 비율을 흥미 유발에 활용
                - 마지막에 CTA + 사연 링크로 "다시봄 광장에서 직접 보고 투표하기" 유도
                - 스포일러 금지 (결론 단정 금지)

                다음 JSON만 출력하세요. 마크다운·코드펜스 없이 순수 JSON:
                {
                  "markdown": "네이버 블로그 포스트 (한국어 마크다운, 600~1200자). 이미지 슬롯 마커 3개를 순서대로 포함: <!-- IMG:chat-preview -->, <!-- IMG:report-needs-map -->, <!-- IMG:quote-card -->. 각 마커는 빈 줄로 감쌈. 구조: # 제목 → 도입(2-3문장, 공감비율/양쪽입장 요약) → <!-- IMG:chat-preview --> → 작성자 측 상황 설명(2문장) → <!-- IMG:report-needs-map --> → 상대방 측 상황 설명(1문장) + 다시봄 소개(1문장) → <!-- IMG:quote-card --> → CTA(1문장, 링크 포함) → 면책고지(1줄). 각 섹션 짧게.",
                  "imageSlots": [
                    {"slot": "<!-- IMG:chat-preview -->",     "kind": "chat",         "quoteText": null},
                    {"slot": "<!-- IMG:report-needs-map -->", "kind": "report-needs", "quoteText": null},
                    {"slot": "<!-- IMG:quote-card -->",       "kind": "quote",        "quoteText": "사연 핵심 문장 (25자 이내)"}
                  ],
                  "hashtags": ["#다시봄", "#갈등해결", "#관계회복", "#공감비율", "#관계갈등"]
                }

                규칙:
                - markdown 총 길이: 600~1200자.
                - 이모지·코드펜스 금지.
                - SEO 검색어를 자연스럽게 포함.

                [사연 정보]
                %s

                [관계 유형] %s
                %s
                """.formatted(sourceContent, relationType, templateSection);
    }
}
