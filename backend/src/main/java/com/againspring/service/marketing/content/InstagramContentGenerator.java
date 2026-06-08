package com.againspring.service.marketing.content;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.llm.LLMProvider;
import com.againspring.safety.MarketingCopyGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates Instagram marketing content from a real community post.
 * Produces a caption with carousel slides.
 */
@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class InstagramContentGenerator implements ContentGenerator {

    private final LLMProvider llmProvider;
    private final MarketingCopyGuard copyGuard;

    @Override
    public MarketingContent.Platform supports() {
        return MarketingContent.Platform.INSTAGRAM;
    }

    @Override
    public GenerationOutput generate(GenerationContext ctx) throws Exception {
        String prompt = buildPrompt(ctx.sourceContent(), ctx.relationType(), ctx.templateBody());
        String rawResponse = llmProvider.invoke(prompt, "claude-sonnet-4-6");
        String sanitizedRaw = copyGuard.sanitize(rawResponse);
        log.info("Generated Instagram content for relation type: {}", ctx.relationType());
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
                톤: 존댓말, 따뜻하고 차분하게. 이모지 금지. 금지어: 판결/유죄/가해자/과실비율,
                진단명/상담/치료/정신과, 확실/보장/100%%.

                아래 다시봄 커뮤니티 사연을 인스타그램 카드뉴스로 만드세요.
                궁금증을 자극하면서도 명확하게 소개해, 사람들이 다시봄 광장에 접속해
                양쪽 입장을 읽고 공감 투표에 참여하고 싶게 만드세요.
                반드시:
                - 작성자 vs 상대방 양쪽 입장의 긴장/대비를 슬라이드로 시각화
                - 현재 공감 비율을 흥미 유발에 활용
                - 마지막에 CTA + 사연 링크 유도
                - 스포일러 금지 (결론 단정 금지)

                다음 JSON만 출력하세요. 마크다운·코드펜스 없이 순수 JSON:
                {
                  "caption": "인스타그램 캡션 (150자 이내, 한국어). 강렬한 훅 + CTA. 이모지 금지.",
                  "hashtags": ["#다시봄", "#갈등해결", "#관계회복", "#관계유형태그", "#감정태그"],
                  "slides": [
                    {"role": "COVER",   "title": "사연 제목·상황 한 줄 (25자 이내)", "body": "", "visualHint": "gradient-warm"},
                    {"role": "SCENE",   "title": "작성자 입장 한 줄", "body": "핵심 발언이나 갈등 장면 (40자 이내)", "visualHint": "chat-bubble"},
                    {"role": "FEELING", "title": "상대방 입장 한 줄", "body": "상대방 관점이나 감정 (40자 이내)", "visualHint": "emotion-palette"},
                    {"role": "NVC",     "title": "공감 비율", "body": "작성자 X%% : 상대방 Y%% — 당신은?", "visualHint": "two-line"},
                    {"role": "CTA",     "title": "다시봄에서 직접 보기", "body": "링크를 눌러 양쪽 입장 읽고 투표하기", "visualHint": "cta-logo"}
                  ]
                }

                규칙:
                - slides: 5~6개 (인사이트가 있으면 CTA 앞에 BONUS 슬라이드 추가 가능).
                - 슬라이드 title: 25자 이내. body: 50자 이내.
                - role 값: COVER | SCENE | FEELING | NVC | CTA | BONUS
                - 이모지·마크다운·코드펜스 금지.

                [사연 정보]
                %s

                [관계 유형] %s
                %s
                """.formatted(sourceContent, relationType, templateSection);
    }
}
