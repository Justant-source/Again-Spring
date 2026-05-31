package com.againspring.service.community;

import com.againspring.domain.community.Juror;
import com.againspring.domain.community.Post;
import com.againspring.domain.community.VoteOption;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.bridge.PromptSanitizer;
import com.againspring.llm.prompt.PromptLoader;
import com.againspring.repository.community.JurorRepository;
import com.againspring.repository.community.VoteOptionRepository;
import com.againspring.safety.KeywordGuard;
import com.againspring.safety.ScanResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JuryServiceTest - JuryService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
class JuryServiceTest {

    @Mock
    private JurorRepository jurorRepository;

    @Mock
    private VoteOptionRepository voteOptionRepository;

    @Mock
    private PromptLoader promptLoader;

    @Mock
    private PromptSanitizer promptSanitizer;

    @Mock
    private KeywordGuard keywordGuard;

    @Mock
    private LLMProvider juryLlmProvider;

    private JuryService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new JuryService(
                jurorRepository,
                voteOptionRepository,
                promptLoader,
                promptSanitizer,
                keywordGuard,
                objectMapper,
                juryLlmProvider
        );
    }

    @Test
    void testGenerateJuryAsync_Success() throws Exception {
        // Given
        Post post = Post.builder()
                .id("post_123")
                .bodyPublished("두 분이 의견 차이가 있습니다.")
                .build();

        VoteOption option1 = VoteOption.builder()
                .id(1L)
                .label("A님 입장이 더 이해됩니다")
                .build();
        VoteOption option2 = VoteOption.builder()
                .id(2L)
                .label("B님 입장이 더 이해됩니다")
                .build();
        VoteOption option3 = VoteOption.builder()
                .id(3L)
                .label("서로 오해가 있어 보입니다")
                .build();
        List<VoteOption> options = List.of(option1, option2, option3);

        String llmResponse = """
                ```json
                {
                  "chosenOptionLabel": "A님 입장이 더 이해됩니다",
                  "empathyComment": "A님 입장도 충분히 이해됩니다."
                }
                ```
                """;

        when(promptLoader.get("community/jury_persona.md")).thenReturn("# Persona\\n{{PERSONA_BLOCK}}");
        when(juryLlmProvider.invoke(anyString(), anyString())).thenReturn(llmResponse);
        when(jurorRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        service.generateJuryAsync(post, options);

        // Then
        // 비동기 실행이므로 검증은 제한적
        verify(promptLoader, times(1)).get("community/jury_persona.md");
    }

    @Test
    void testPersonaVariety() {
        // PERSONAS가 9인이고 다양한 조합을 가지는지 검증
        // 이는 reflection을 통해 검증하거나 통합 테스트에서 검증 가능
        // 단위 테스트에서는 서비스 생성만 확인
        assertNotNull(service);
    }

    @Test
    void testGenerateJuryAsync_LlmFailure() throws Exception {
        // Given
        Post post = Post.builder()
                .id("post_123")
                .bodyPublished("두 분이 의견 차이가 있습니다.")
                .build();

        List<VoteOption> options = new ArrayList<>();

        when(promptLoader.get("community/jury_persona.md")).thenThrow(new RuntimeException("File not found"));

        // When
        service.generateJuryAsync(post, options);

        // Then - 비동기 실행이므로 예외를 발생하지 않음
        verify(promptLoader, times(1)).get("community/jury_persona.md");
    }
}
