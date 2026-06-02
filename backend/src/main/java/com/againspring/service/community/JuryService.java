package com.againspring.service.community;

import com.againspring.domain.community.Juror;
import com.againspring.domain.community.Post;
import com.againspring.domain.community.VoteOption;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.prompt.PromptLoader;
import com.againspring.repository.community.JurorRepository;
import com.againspring.repository.community.VoteOptionRepository;
import com.againspring.safety.KeywordGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.List;

/**
 * JuryService - AI 배심원 생성 서비스
 * 포스트에 대해 9인의 AI 배심원이 투표하고 감정 의견을 남김
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JuryService {

    private final JurorRepository jurorRepository;
    private final VoteOptionRepository voteOptionRepository;
    private final PromptLoader promptLoader;
    // private final PromptSanitizer promptSanitizer; (removed)
    private final KeywordGuard keywordGuard;
    private final ObjectMapper objectMapper;

    @Qualifier("juryLlmProvider")
    private final LLMProvider juryLlmProvider;

    @Value("${llm.jury.model:claude-haiku-4-5-20251001}")
    private String juryModel;

    // 고정 9인 페르소나
    private static final List<Juror.JurorPersona> PERSONAS = List.of(
            new Juror.JurorPersona("20대", "여성", "공감형", "관계 중시"),
            new Juror.JurorPersona("20대", "남성", "분석형", "개인 중시"),
            new Juror.JurorPersona("30대", "여성", "공감형", "공정 중시"),
            new Juror.JurorPersona("30대", "남성", "실용형", "개인 중시"),
            new Juror.JurorPersona("30대", "여성", "분석형", "관계 중시"),
            new Juror.JurorPersona("40대", "여성", "공감형", "공정 중시"),
            new Juror.JurorPersona("40대", "남성", "실용형", "공정 중시"),
            new Juror.JurorPersona("40대", "남성", "분석형", "관계 중시"),
            new Juror.JurorPersona("50대", "여성", "공감형", "관계 중시")
    );

    /**
     * 포스트에 대해 지정된 수의 배심원이 투표하도록 비동기 생성
     * 각 배심원이 독립적으로 생성되며, 실패해도 전체 실패로 이어지지 않음
     * jurorCount=0이면 즉시 반환 (배심원 생성 스킵)
     *
     * @param post 대상 포스트
     * @param options 투표 선택지 목록
     * @param jurorCount 생성할 배심원 수 (0-9, 0이면 스킵)
     */
    @Async
    public void generateJuryAsync(Post post, List<VoteOption> options, int jurorCount) {
        log.info("Starting jury generation for post {} with jurorCount={}", post.getId(), jurorCount);

        // jurorCount=0이면 스킵
        if (jurorCount <= 0) {
            log.info("Jury generation skipped for post {} (jurorCount=0)", post.getId());
            return;
        }

        try {
            String juryPersonaPrompt = promptLoader.get("community/jury_persona.md");

            // 선택지 정보를 프롬프트에 포함
            StringBuilder optionsText = new StringBuilder("투표 선택지:\n");
            for (VoteOption opt : options) {
                optionsText.append(String.format("- %s\n", opt.getLabel()));
            }

            // 지정된 jurorCount만큼만 배심원 생성 (PERSONAS를 부분 취하기)
            List<Juror.JurorPersona> selectedPersonas = PERSONAS.subList(0, Math.min(jurorCount, PERSONAS.size()));
            for (Juror.JurorPersona persona : selectedPersonas) {
                try {
                    // 페르소나 블록 생성
                    String personaBlock = buildPersonaBlock(persona);
                    String prompt = juryPersonaPrompt.replace("{{PERSONA_BLOCK}}", personaBlock);

                    // 포스트 본문 및 선택지 추가
                    prompt += "\n\n중립화된 사연:\n" + post.getBodyPublished();
                    prompt += "\n\n" + optionsText.toString();

                    // LLM 호출
                    String llmResponse = juryLlmProvider.invoke(prompt, juryModel);
                    log.debug("Jury response received for post {}, persona {}", post.getId(), persona.getAgeGroup());

                    // 응답 파싱
                    JsonNode responseJson = parseJsonFromLlm(llmResponse);
                    String chosenOptionLabel = responseJson.get("chosenOptionLabel").asText();
                    String empathyComment = responseJson.get("empathyComment").asText();

                    // 선택지 ID 찾기
                    Long chosenOptionId = options.stream()
                            .filter(opt -> opt.getLabel().equals(chosenOptionLabel))
                            .map(VoteOption::getId)
                            .findFirst()
                            .orElse(null);

                    // 배심원 저장 (독립 트랜잭션 — @Async 내 save는 트랜잭션 없이 직접 호출)
                    Juror juror = Juror.builder()
                            .postId(post.getId())
                            .persona(persona)
                            .chosenOptionId(chosenOptionId)
                            .empathyComment(empathyComment)
                            .build();

                    jurorRepository.save(juror);
                    log.info("Juror saved for post {}, persona: {} / {}", post.getId(), persona.getAgeGroup(), persona.getGender());

                } catch (Exception e) {
                    log.warn("Failed to generate juror for post {} with persona {} / {}: {}",
                            post.getId(), persona.getAgeGroup(), persona.getGender(), e.getMessage());
                    // 개별 배심원 실패는 전체 실패로 이어지지 않음
                }
            }

            log.info("Jury generation completed for post {}", post.getId());

        } catch (NoSuchFileException e) {
            log.error("Jury persona prompt not found: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Jury generation failed for post {}: {}", post.getId(), e.getMessage(), e);
        }
    }

    /**
     * 페르소나를 프롬프트용 텍스트 블록으로 변환
     */
    private String buildPersonaBlock(Juror.JurorPersona persona) {
        return String.format(
                "나이: %s, 성별: %s, 성향: %s, 가치관: %s",
                persona.getAgeGroup(),
                persona.getGender(),
                persona.getDisposition(),
                persona.getValueOrientation()
        );
    }

    /**
     * LLM 응답에서 JSON 추출 및 파싱
     */
    private JsonNode parseJsonFromLlm(String response) throws Exception {
        String json = response;

        // ```json ... ``` 마커 제거
        if (json.contains("```json")) {
            int start = json.indexOf("```json") + 7;
            int end = json.lastIndexOf("```");
            if (end > start) {
                json = json.substring(start, end);
            }
        }

        json = json.trim();
        return objectMapper.readTree(json);
    }
}
