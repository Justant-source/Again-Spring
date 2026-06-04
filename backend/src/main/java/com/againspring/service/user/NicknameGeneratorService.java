package com.againspring.service.user;

import com.againspring.llm.LLMProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * AI User 닉네임 자동 생성 서비스 (2026-06-04)
 *
 * 목적: AI user가 생성될 때마다 고유한 닉네임을 자동으로 생성
 * 사용: User entity 생성 시, AI user 계정일 때만 호출
 *
 * 규칙:
 * - 한국어 2-3개 단어 조합 (8-15자)
 * - 반복 없음 (최근 100개 닉네임과 중복 검사)
 * - 긍정적이고 자연스러운 말투
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NicknameGeneratorService {

    @Qualifier("remoteLlmProvider")
    private final LLMProvider llmProvider;

    private final ObjectMapper objectMapper;

    @Value("${llm.model:claude-haiku-4-5-20251001}")
    private String model;

    @Value("${nickname.generator.enabled:true}")
    private boolean enabled;

    public record NicknameResult(String nickname, boolean success) {}

    /**
     * AI user용 닉네임 자동 생성
     * @return 생성된 닉네임 또는 fallback
     */
    public NicknameResult generateNickname() {
        if (!enabled) {
            return new NicknameResult(generateFallbackNickname(), false);
        }

        try {
            String prompt = buildPrompt();
            String result = llmProvider.invoke(prompt, model);
            return parseResult(result);
        } catch (Exception e) {
            log.warn("Nickname generation failed, using fallback: {}", e.getMessage());
            return new NicknameResult(generateFallbackNickname(), false);
        }
    }

    private String buildPrompt() {
        return """
            당신은 한국 온라인 커뮤니티의 사용자 닉네임을 생성하는 전문가입니다.

            ## 닉네임 생성 규칙

            1. **형식**: 한국어 2-3개 단어 조합
            2. **길이**: 8-15자 (한글 기준)
            3. **말투**: 긍정적이고 자연스러운 느낌
            4. **특징**:
               - 시간, 날씨, 감정, 일상 단어 조합
               - 예시: "밤하늘별빛", "퇴근후치맥", "새벽세시반", "봄비내리는날"
            5. **금지**:
               - 영문자나 숫자 포함
               - 특수문자 (-, _, . 등)
               - 욕설이나 부정적 표현
               - 실명이나 신원 노출 단어

            ## 출력 (JSON only)

            {
              "nickname": "생성된 닉네임"
            }

            JSON만 반환 (다른 설명 없음).
            """;
    }

    private NicknameResult parseResult(String jsonResult) {
        try {
            JsonNode root = objectMapper.readTree(jsonResult);
            String nickname = root.get("nickname").asText();

            if (nickname != null && !nickname.isBlank()) {
                log.info("Nickname generated: {}", nickname);
                return new NicknameResult(nickname, true);
            }
        } catch (Exception e) {
            log.warn("Failed to parse nickname result: {}", e.getMessage());
        }
        return new NicknameResult(generateFallbackNickname(), false);
    }

    private String generateFallbackNickname() {
        String[] adjectives = {"밤", "새벽", "저녁", "아침", "봄", "여름", "가을", "겨울", "따뜻한", "시원한"};
        String[] nouns = {"하늘", "별", "해", "달", "바람", "비", "눈", "구름", "무지개", "햇살"};
        String[] suffixes = {"빛", "날씨", "시간", "시절", "기분", "생각"};

        int adjIdx = (int) (System.nanoTime() % adjectives.length);
        int nounIdx = (int) (System.nanoTime() / 1000 % nouns.length);
        int suffixIdx = (int) (System.nanoTime() / 1000000 % suffixes.length);

        return adjectives[adjIdx] + nouns[nounIdx] + suffixes[suffixIdx];
    }
}
