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

    @Qualifier("remoteLlmProvider")
    private final LLMProvider juryLlmProvider;

    @Value("${llm.jury.model:claude-haiku-4-5-20251001}")
    private String juryModel;

    // 고정 9인 페르소나 — 성향 다양성 설계:
    // 공감형 2명 / 분석형 2명 / 현실주의형 3명 / 논리형 2명
    // → 작성자 편 자동 쏠림 방지, 분포가 사안마다 달라지도록
    private static final List<Juror.JurorPersona> PERSONAS = List.of(
            new Juror.JurorPersona("20대", "여성", "공감형",       "관계 중시"),
            new Juror.JurorPersona("20대", "남성", "논리형",       "공정 중시"),
            new Juror.JurorPersona("30대", "여성", "현실주의형",   "균형 중시"),
            new Juror.JurorPersona("30대", "남성", "분석형",       "개인 중시"),
            new Juror.JurorPersona("30대", "여성", "현실주의형",   "공정 중시"),
            new Juror.JurorPersona("40대", "여성", "논리형",       "공정 중시"),
            new Juror.JurorPersona("40대", "남성", "공감형",       "관계 중시"),
            new Juror.JurorPersona("40대", "남성", "현실주의형",   "경험 중시"),
            new Juror.JurorPersona("50대", "여성", "분석형",       "경험 중시")
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

            // 이미 저장된 배심원 수만큼 건너뛰기 (재시작 복구용)
            long existing = jurorRepository.countByPostId(post.getId());
            int startIdx = (int) Math.min(existing, PERSONAS.size());
            int endIdx = Math.min(jurorCount, PERSONAS.size());
            if (startIdx >= endIdx) {
                log.info("Jury already complete for post {} ({}/{}), skipping", post.getId(), existing, jurorCount);
                return;
            }
            // 지정된 jurorCount만큼만 배심원 생성 (PERSONAS를 부분 취하기)
            List<Juror.JurorPersona> selectedPersonas = PERSONAS.subList(startIdx, endIdx);
            for (Juror.JurorPersona persona : selectedPersonas) {
                try {
                    // 페르소나 블록 생성
                    String personaBlock = buildPersonaBlock(persona);
                    String prompt = juryPersonaPrompt.replace("{{PERSONA_BLOCK}}", personaBlock);

                    // 포스트 본문 및 선택지 추가
                    prompt += "\n\n[작성자 사연]\n" + post.getBodyPublished();
                    if (post.getPartnerBodyPublished() != null && !post.getPartnerBodyPublished().isBlank()) {
                        prompt += "\n\n[상대방 입장]\n" + post.getPartnerBodyPublished();
                    }
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
    private static final java.util.Map<String, String> DISPOSITION_GUIDE = java.util.Map.of(
        "공감형",       "감정과 관계에 민감하게 반응합니다. 단, 한쪽 이야기만 들었을 때 무조건 동조하지 않도록 의식적으로 균형을 잡으려 합니다.",
        "분석형",       "사실관계와 논리적 일관성을 봅니다. 작성자의 서술에서 모순이나 선택적 강조를 찾아냅니다.",
        "현실주의형",   "비슷한 상황을 많이 봐왔습니다. 겉으로 표현된 말보다 실제 행동 패턴을 더 신뢰합니다. 낭만적 해석보다 현실적 가능성을 먼저 떠올립니다.",
        "논리형",       "전제와 결론의 타당성을 점검합니다. '작성자가 이렇게 느꼈다'와 '실제로 그런 상황이었다'를 구분합니다. 빠진 정보가 결론을 바꿀 수 있는지 따집니다."
    );

    private String buildPersonaBlock(Juror.JurorPersona persona) {
        String guide = DISPOSITION_GUIDE.getOrDefault(persona.getDisposition(),
                "솔직하고 균형 잡힌 시각으로 판단합니다.");
        return String.format(
                "나이: %s, 성별: %s, 성향: %s, 가치관: %s\n접근 방식: %s",
                persona.getAgeGroup(),
                persona.getGender(),
                persona.getDisposition(),
                persona.getValueOrientation(),
                guide
        );
    }

    /**
     * LLM 응답에서 JSON 추출 및 파싱
     */
    private JsonNode parseJsonFromLlm(String response) throws Exception {
        String json = response;

        // 1순위: ```json ... ``` 마커
        if (json.contains("```json")) {
            int start = json.indexOf("```json") + 7;
            int end = json.lastIndexOf("```");
            if (end > start) {
                json = json.substring(start, end).trim();
                return objectMapper.readTree(json);
            }
        }

        // 2순위: ``` ... ``` 마커 (json 없는 경우)
        if (json.contains("```")) {
            int start = json.indexOf("```") + 3;
            int end = json.lastIndexOf("```");
            if (end > start) {
                String candidate = json.substring(start, end).trim();
                if (candidate.startsWith("{")) {
                    return objectMapper.readTree(candidate);
                }
            }
        }

        // 3순위: 응답 내 첫 '{' ~ 마지막 '}' 사이 추출
        int braceStart = json.indexOf('{');
        int braceEnd = json.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            json = json.substring(braceStart, braceEnd + 1);
            return objectMapper.readTree(json);
        }

        json = json.trim();
        return objectMapper.readTree(json);
    }
}
