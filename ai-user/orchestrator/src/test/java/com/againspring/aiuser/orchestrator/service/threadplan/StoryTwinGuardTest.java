package com.againspring.aiuser.orchestrator.service.threadplan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoryTwinGuardTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private StoryTwinGuard guard;

    @BeforeEach
    void setUp() {
        guard = new StoryTwinGuard(jdbcTemplate);
    }

    @Test
    void exactNormalizedTitleIsTwin() {
        List<StoryTwinGuard.RecentAiPost> recent = List.of(
                new StoryTwinGuard.RecentAiPost("직장 엄마  conf 육아 갈등", "전혀 다른 본문입니다만 제목만 같으면 twin"));
        Optional<String> reason = guard.twinReason(
                "직장 엄마  conf 육아 갈등",
                "오늘은 팀장이 일을 넘겼다 완전 다른 이야기",
                recent);
        assertThat(reason).contains("exact-title");
    }

    @Test
    void whitespaceNormalizedTitleStillMatches() {
        List<StoryTwinGuard.RecentAiPost> recent = List.of(
                new StoryTwinGuard.RecentAiPost("직장   엄마   퇴근   육아",
                        "어제 회식에서 팀장이 한 말은 잊히지 않는다"));
        assertThat(guard.twinReason("직장 엄마 퇴근 육아",
                "오늘은 어린이집 픽업 문제로 남편과 다퉜다", recent))
                .contains("exact-title");
    }

    @Test
    void nearDuplicateBodyTriggersTwin() {
        String body = "근데 직장 엄마라서 야근하면 어린이집 픽업을 남편이 안 해줘서 매번 싸운다 진짜 지친다";
        String almost = "근데 직장 엄마라서 야근하면 어린이집 픽업을 남편이 안 해줘서 매번 싸운다 너무 지친다";
        List<StoryTwinGuard.RecentAiPost> recent = List.of(
                new StoryTwinGuard.RecentAiPost("다른 제목입니다요", body));
        Optional<String> reason = guard.twinReason("완전 다른 제목으로", almost, recent);
        assertThat(reason).isPresent();
        assertThat(reason.get()).startsWith("body-jaccard");
        assertThat(StoryTwinGuard.maxBigramJaccard(almost, List.of(body)))
                .isGreaterThanOrEqualTo(StoryTwinGuard.BODY_JACCARD_THRESHOLD);
    }

    @Test
    void differentWorkPostsAreNotTwins() {
        String a = "팀장이 금요일에 신규 프로젝트를 나한테만 떠넘겨서 주말 내내 야근했다";
        String b = "동료가 실적 가로채서 상사가 나한테만 잔소리하는데 증거는 메일에 다 있다";
        List<StoryTwinGuard.RecentAiPost> recent = List.of(
                new StoryTwinGuard.RecentAiPost("팀장 떠넘김", a));
        assertThat(guard.twinReason("실적 가로채기", b, recent)).isEmpty();
        assertThat(StoryTwinGuard.maxBigramJaccard(b, List.of(a)))
                .isLessThan(StoryTwinGuard.BODY_JACCARD_THRESHOLD);
    }

    @Test
    void emptyRecentsNeverTwins() {
        assertThat(guard.twinReason("아무 제목", "아무 본문 충분히 긴 내용", List.of())).isEmpty();
        assertThat(guard.twinReason("아무 제목", "아무 본문", null)).isEmpty();
    }

    @Test
    void shortTextsExemptFromJaccardRelyOnExactTitle() {
        // Under 12 chars (no spaces) → empty bigrams; only exact title can match.
        assertThat(StoryTwinGuard.charBigrams("짧은제목")).isEmpty();
        List<StoryTwinGuard.RecentAiPost> recent = List.of(
                new StoryTwinGuard.RecentAiPost("짧은제목", "짧은본문"));
        assertThat(guard.twinReason("짧은제목", "다른짧은", recent)).contains("exact-title");
        assertThat(guard.twinReason("다른짧은제", "다른짧은", recent)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void isObviousTwinLoadsFromJdbc() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(List.of(
                        new StoryTwinGuard.RecentAiPost(
                                "같은 제목 충분히 김",
                                "본문도 충분히 길어야 한다 이 정도면 충분")));
        assertThat(guard.isObviousTwin("같은 제목 충분히 김", "전혀 다른 본문 내용으로 씁니다")).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadFailureFailsOpen() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenThrow(new RuntimeException("db down"));
        assertThat(guard.isObviousTwin("제목", "본문")).isFalse();
    }

    @Test
    void thresholdsDocumentedInConstants() {
        assertThat(StoryTwinGuard.TITLE_JACCARD_THRESHOLD).isEqualTo(0.45);
        assertThat(StoryTwinGuard.BODY_JACCARD_THRESHOLD).isEqualTo(0.35);
        assertThat(StoryTwinGuard.WINDOW_DAYS).isEqualTo(14);
        assertThat(StoryTwinGuard.RECENT_LIMIT).isEqualTo(30);
    }
}
