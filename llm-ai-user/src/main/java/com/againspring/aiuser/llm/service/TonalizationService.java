package com.againspring.aiuser.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Post 톤 정규화 서비스 (2026-06-04)
 *
 * 목적: 사용자가 입력한 제목/본문을 한국 갈등 커뮤니티 톤에 맞게 정규화
 * 사용: JuryService.generateJuryAsync() 호출 전
 *
 * 규칙:
 * 1. 존댓말 → 반말
 * 2. 온점/쌍따옴표 제거
 * 3. 배경 축소 (1-2줄)
 * 4. 감정 강화 (1인칭 반복)
 * 5. 미완성감 유지 (결론/해결책 제거)
 *
 * Graceful fallback: 정규화 실패 시 원본 반환
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TonalizationService {

    private final ClaudeCliInvoker llmInvoker;
    private final ObjectMapper objectMapper;

    @Value("${tonalization.enabled:true}")
    private boolean enabled;

    @Value("${tonalization.timeout-ms:30000}")
    private long timeoutMs;

    public record TonalizationResult(String titleNormalized, String bodyNormalized, boolean success) {}

    /**
     * 제목 + 본문을 한국 커뮤니티 톤으로 정규화
     * @param title 원본 제목 (존댓말 가능)
     * @param body 원본 본문 (존댓말 가능)
     * @return 정규화된 제목/본문
     */
    public TonalizationResult normalize(String title, String body) {
        if (!enabled || (title == null || title.isBlank()) && (body == null || body.isBlank())) {
            return new TonalizationResult(title, body, false);
        }

        try {
            String prompt = buildPrompt(title, body);
            String result = llmInvoker.invoke(prompt, timeoutMs);
            return parseResult(title, body, result);
        } catch (Exception e) {
            log.warn("Tonalization failed, using original text: {}", e.getMessage());
            return new TonalizationResult(title, body, false);
        }
    }

    private String buildPrompt(String title, String body) {
        return """
            당신은 한국 온라인 커뮤니티의 사연을 정규화하는 전문가입니다.

            ## 정규화 규칙 (순서대로 적용)

            1. **문체 변환**: 존댓말 → 반말
               - "~습니다" → "~임"
               - "~어요/~해요" → "~아/~해"
               - "~더라고요" → "~더라"
               - "~나요?" → "~나?"
               - "~을/를 것 같습니다" → "~을/를 것 같아"

            2. **온점·쌍따옴표 제거**
               - 문장 끝 온점(.) 모두 제거
               - 쌍따옴표("") → 일반 인용으로 변경

            3. **배경 축소**
               - 최대 1-2줄만 유지 (언제, 누구인지)
               - 상세한 배경 설명 제거

            4. **감정 강화**
               - "나는", "내가" 1인칭 반복
               - 약한 표현 → 강한 표현으로 변경
               - 예: "불편해요" → "진짜 답답해"

            5. **미완성감 유지**
               - 결론/해결책 제거
               - 질문이나 감정 상태로 마무리
               - "어떻게 해야 할까?" 형태 유지

            6. **금지어 제거**
               - 판결, 유죄, 무죄, 가해자, 피해자
               - 나르시시스트, 경계성, 가스라이팅
               - 극단적 권고 (이혼, 헤어짐, 손절)

            ## 입력

            제목: %s

            본문: %s

            ## 출력 (JSON)

            정규화된 제목과 본문을 JSON으로 반환하세요:
            {
              "title_normalized": "정규화된 제목",
              "body_normalized": "정규화된 본문"
            }

            JSON만 반환 (설명 없음).
            """.formatted(title != null ? title : "", body != null ? body : "");
    }

    private TonalizationResult parseResult(String originalTitle, String originalBody, String jsonResult) {
        try {
            JsonNode root = objectMapper.readTree(jsonResult);
            String titleNorm = root.get("title_normalized").asText(originalTitle);
            String bodyNorm = root.get("body_normalized").asText(originalBody);

            log.info("Tonalization success: title={}c → {}c, body={}c → {}c",
                    originalTitle != null ? originalTitle.length() : 0, titleNorm.length(),
                    originalBody != null ? originalBody.length() : 0, bodyNorm.length());

            return new TonalizationResult(titleNorm, bodyNorm, true);
        } catch (Exception e) {
            log.warn("Failed to parse tonalization result, using originals: {}", e.getMessage());
            return new TonalizationResult(originalTitle, originalBody, false);
        }
    }
}
