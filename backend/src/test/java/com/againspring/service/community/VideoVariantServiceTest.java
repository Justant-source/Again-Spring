package com.againspring.service.community;

import com.againspring.llm.LLMProvider;
import com.againspring.llm.PromptSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoVariantServiceTest {

    @Mock
    LLMProvider llmProvider;

    @Mock
    PromptSanitizer promptSanitizer;

    VideoVariantService service;

    @BeforeEach
    void setUp() {
        service = new VideoVariantService(llmProvider, promptSanitizer, new ObjectMapper());
        ReflectionTestUtils.setField(service, "model", "test-model");
        ReflectionTestUtils.setField(service, "enabled", true);
        lenient().when(promptSanitizer.sanitize(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void generate_parsesLlmJson_forBothPlatforms() throws Exception {
        when(llmProvider.invoke(anyString(), anyString())).thenReturn("""
            {"hook_reels":"릴스만의훅","script_reels":"짧은요약. 공감 비율은?","hook_shorts":"쇼츠훅","script_shorts":"조금긴요약. 댓글로"}
            """);

        VideoVariantService.Variants v = service.generate(
            "마스터훅", "shock", "제목", "본문이 길어요 갈등 이야기", true, true);

        assertThat(v.hookReels()).isEqualTo("릴스만의훅");
        assertThat(v.hookShorts()).isEqualTo("쇼츠훅");
        assertThat(v.scriptReels()).contains("공감");
        assertThat(v.scriptShorts()).contains("댓글");
        assertThat(v.maxDurationReelsSec()).isEqualTo(30);
        assertThat(v.maxDurationShortsSec()).isEqualTo(45);
    }

    @Test
    void generate_llmFail_fallsBackToHeuristic() throws Exception {
        when(llmProvider.invoke(anyString(), anyString())).thenThrow(new RuntimeException("down"));

        VideoVariantService.Variants v = service.generate(
            "마스터", "anger", "제목", "본문요약용텍스트입니다", true, false);

        assertThat(v.hookReels()).isEqualTo("마스터");
        assertThat(v.scriptReels()).contains("공감");
        assertThat(v.hookShorts()).isNull();
        assertThat(v.maxDurationReelsSec()).isEqualTo(30);
        assertThat(v.maxDurationShortsSec()).isNull();
    }

    @Test
    void generate_nonVideo_returnsEmpty() {
        VideoVariantService.Variants v = service.generate("h", "sad", "t", "b", false, false);
        assertThat(v.hookReels()).isNull();
        assertThat(v.scriptShorts()).isNull();
    }

    @Test
    void generate_stripsForbiddenTerms() throws Exception {
        when(llmProvider.invoke(anyString(), anyString())).thenReturn("""
            {"hook_reels":"이건판결이다","script_reels":"배심원이 처방한다 공감","hook_shorts":"ok","script_shorts":"요약 댓글"}
            """);

        VideoVariantService.Variants v = service.generate(
            "마스터", "tension", "제목", "본문", true, true);

        assertThat(v.hookReels()).doesNotContain("판결");
        assertThat(v.scriptReels()).doesNotContain("배심원");
        assertThat(v.scriptReels()).doesNotContain("처방");
    }
}
