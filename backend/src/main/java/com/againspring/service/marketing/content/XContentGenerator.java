package com.againspring.service.marketing.content;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.llm.LLMProvider;
import com.againspring.safety.MarketingCopyGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates X (Twitter) marketing content from a real community post.
 * Produces 3-5 tweets, each under 270 characters.
 */
@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class XContentGenerator implements ContentGenerator {

    private final LLMProvider llmProvider;
    private final MarketingCopyGuard copyGuard;

    @Override
    public MarketingContent.Platform supports() {
        return MarketingContent.Platform.X;
    }

    @Override
    public GenerationOutput generate(GenerationContext ctx) throws Exception {
        String prompt = buildPrompt(ctx.sourceContent(), ctx.relationType(), ctx.templateBody());
        String rawResponse = llmProvider.invoke(prompt, "claude-sonnet-4-6");
        String sanitizedRaw = copyGuard.sanitize(rawResponse);
        log.info("Generated X content for relation type: {}", ctx.relationType());
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
                톤: 존댓말, 따뜻하고 차분하게, 시적이고 짧게. 단정형('~입니다')·명령형('하세요') 대신
                '~일 수 있어요','~처럼 보여요'. 이모지 금지.
                금지어: 판결/유죄/가해자/과실비율, 진단명/상담/치료/정신과, 확실/보장/100%.

                아래는 다시봄 커뮤니티에 실제 올라온 사연입니다. X(트위터)용 스레드를 만드세요.
                궁금증을 자극하면서도 명확하게 소개해, 사람들이 다시봄 광장에 접속해
                양쪽 입장을 읽고 공감 투표에 참여하고 싶게 만드세요.
                반드시:
                - 작성자 vs 상대방 양쪽 입장의 긴장/대비를 후킹 요소로
                - 현재 공감 비율을 흥미 유발에 활용
                - 마지막에 CTA + 사연 링크로 "다시봄 광장에서 직접 보고 투표하기" 유도
                - 스포일러 금지 (결론 단정 금지)

                다음 JSON만 출력하세요. 마크다운·코드펜스 없이 순수 JSON:
                {
                  "tweets": [
                    "트윗1 (270자 이내, 한국어) — 사연 제목/상황으로 강렬하게 시작",
                    "트윗2 (270자 이내) — 작성자 vs 상대방 대비 + 공감 비율 훅",
                    "트윗3 (270자 이내) — CTA + 링크. 면책고지 포함: AI 기반 공감 분석 도구로, 전문 법률·의료 조언을 대신하지 않습니다."
                  ],
                  "quoteCard": {
                    "line1": "사연에서 뽑은 메타포·핵심 문구 (30자 이내)",
                    "line2": "공감이나 시선 한 줄 (40자 이내)",
                    "attribution": "다시봄"
                  }
                }

                규칙:
                - tweets: 3~5개. 각 270자 이내.
                - 마지막 트윗에만 #다시봄 해시태그.
                - quoteCard.line1은 반드시 사연 내용 기반.
                - 이모지·마크다운·코드펜스 금지.

                [사연 정보]
                %s

                [관계 유형] %s
                %s
                """.formatted(sourceContent, relationType, templateSection);
    }
}
