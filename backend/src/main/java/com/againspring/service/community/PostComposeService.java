package com.againspring.service.community;

import com.againspring.domain.community.Post;
import com.againspring.domain.community.VoteOption;
import com.againspring.domain.enums.PostCategory;
import com.againspring.domain.enums.PostStatus;
import com.againspring.domain.enums.PostVisibility;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.bridge.PromptSanitizer;
import com.againspring.llm.prompt.PromptLoader;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.community.VoteOptionRepository;
import com.againspring.safety.KeywordGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PostComposeService - 커뮤니티 사연 중립화 서비스
 * 사용자의 원본 사연을 LLM으로 중립화하고 투표 옵션을 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PostComposeService {

    private final PostRepository postRepository;
    private final VoteOptionRepository voteOptionRepository;
    private final PromptLoader promptLoader;
    private final PromptSanitizer promptSanitizer;
    private final KeywordGuard keywordGuard;
    private final ObjectMapper objectMapper;

    @Qualifier("chatLlmProvider")
    private final LLMProvider composeLlmProvider;

    @Value("${llm.compose.model:claude-haiku-4-5-20251001}")
    private String composeModel;

    /**
     * 사용자 사연을 중립화하고 Post + VoteOption을 생성
     * 위기 감지 시 IllegalArgumentException 발생
     *
     * @param authorId 작성자 ID
     * @param userTitle 사용자가 입력한 제목
     * @param bodyRaw 원본 사연 텍스트
     * @param category 관계 카테고리
     * @param visibility 공개/비공개 설정
     * @param jurorCount AI 배심원 인원 (0-9)
     * @param sessionId 관련 세션 ID (nullable)
     * @return 중립화된 Post 객체 (status=VOTING)
     */
    public Post composeAndNeutralize(String authorId, String userTitle, String bodyRaw, PostCategory category, String visibility, int jurorCount, String sessionId) {
        log.info("Starting compose for author {} with category {}", authorId, category);

        // 1) 위기 감지 (이중방어 — FE에서도 감지)
        var scanResult = keywordGuard.scanUserInput(bodyRaw, authorId);
        if (scanResult.isCrisis() || scanResult.isBlocked()) {
            throw new IllegalArgumentException("CRISIS_DETECTED");
        }

        // 2) Post 생성 (초기 상태: NEUTRALIZING)
        String postId = "post_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Post post = Post.builder()
                .id(postId)
                .authorId(authorId)
                .sessionId(sessionId)
                .bodyRaw(bodyRaw)
                .userTitle(userTitle)
                .category(category)
                .visibility(PostVisibility.valueOf(visibility.toUpperCase()))
                .status(PostStatus.NEUTRALIZING)
                .neutralizationPassed(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        postRepository.save(post);
        log.info("Post created: {} with status NEUTRALIZING", postId);

        try {
            // 3) 프롬프트 로드 및 LLM 호출
            String neutralizePrompt = promptLoader.get("community/neutralize.md");
            String sanitizedBody = promptSanitizer.sanitize(bodyRaw, postId);

            String prompt = neutralizePrompt + "\n\n사용자 사연:\n" + sanitizedBody;
            String llmResponse = composeLlmProvider.invoke(prompt, composeModel);

            log.debug("LLM response received, length: {}", llmResponse.length());

            // 4) JSON 파싱
            JsonNode responseJson = parseJsonFromLlm(llmResponse);
            String title = responseJson.get("title").asText();
            String bodyPublished = responseJson.get("bodyPublished").asText();

            // 5) Post 업데이트
            post.setTitle(title);
            post.setBodyPublished(bodyPublished);
            post.setNeutralizationPassed(true);
            post.setStatus(PostStatus.VOTING);
            post.setVoteCloseAt(Instant.now().plusSeconds(7 * 24 * 3600)); // 7일
            postRepository.save(post);

            // 6) VoteOption 저장 — 정확히 2개 고정: "작성자" (0) vs "상대방" (1)
            List<VoteOption> options = new ArrayList<>();
            VoteOption opt1 = VoteOption.builder()
                    .postId(postId)
                    .label("작성자")
                    .orderIdx(0)
                    .build();
            VoteOption opt2 = VoteOption.builder()
                    .postId(postId)
                    .label("상대방")
                    .orderIdx(1)
                    .build();
            options.add(opt1);
            options.add(opt2);
            voteOptionRepository.saveAll(options);
            log.info("VoteOptions saved: 2 fixed options for post {}", postId);

            return post;

        } catch (NoSuchFileException e) {
            log.error("Prompt file not found: {}", e.getMessage());
            post.setStatus(PostStatus.BLOCKED);
            postRepository.save(post);
            throw new IllegalArgumentException("Neutralize prompt not found", e);

        } catch (Exception e) {
            log.error("LLM invocation or parsing failed for post {}: {}", postId, e.getMessage(), e);
            post.setStatus(PostStatus.BLOCKED);
            postRepository.save(post);
            throw new IllegalArgumentException("Failed to neutralize post: " + e.getMessage(), e);
        }
    }

    /**
     * LLM 응답에서 JSON 추출 및 파싱
     * ```json ... ``` 마커를 제거하고 ObjectMapper로 파싱
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
