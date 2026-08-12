package com.againspring.service.community;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SibomCandidateService — keyword shortlist")
class SibomCandidateServiceTest {

    private final SibomCandidateService service = new SibomCandidateService();

    @Test
    @DisplayName("loads real classpath catalog (≥1 image)")
    void catalogLoads() {
        assertThat(SibomCatalog.entries()).isNotEmpty();
        assertThat(SibomCatalog.isKnown("waiting-reply")).isTrue();
        assertThat(SibomCatalog.isKnown("not-a-real-id")).isFalse();
    }

    @Test
    @DisplayName("empty / blank body → empty list")
    void emptyBodyReturnsEmpty() {
        assertThat(service.shortlist(null, "제목")).isEmpty();
        assertThat(service.shortlist("", "제목")).isEmpty();
        assertThat(service.shortlist("   ", "제목")).isEmpty();
        assertThat(service.shortlist(null)).isEmpty();
    }

    @Test
    @DisplayName("keyword hits rank higher than trigger-only / no-hit")
    void keywordHitsRankHigher() {
        String body = "카톡을 보냈는데 읽씹만 당했다. 읽씹이 계속됐다.";
        List<String> ids = service.shortlist(body, null);

        assertThat(ids).isNotEmpty();
        assertThat(ids.get(0)).isEqualTo("waiting-reply");
        assertThat(ids).doesNotContain("not-a-real-id");
        assertThat(ids.size()).isLessThanOrEqualTo(SibomCandidateService.MAX_CANDIDATES);
    }

    @Test
    @DisplayName("more keyword hits beat fewer (same catalog)")
    void moreHitsRankFirst() {
        String body = "우리는 냉전 상태였다. 서로 피하고 눈도 안 마주치고 지냈다.";
        List<String> ids = service.shortlist(body);

        assertThat(ids).isNotEmpty();
        assertThat(ids.get(0)).isEqualTo("two-cold-backs");
    }

    @Test
    @DisplayName("title contributes to scoring when body present")
    void titleHelpsScore() {
        String body = "그날 이후로 관계가 어색해졌다.";
        String title = "읽씹만 반복되는 사이";
        List<String> withTitle = service.shortlist(body, title);
        List<String> bodyOnly = service.shortlist(body, null);

        assertThat(withTitle).contains("waiting-reply");
        if (!bodyOnly.contains("waiting-reply")) {
            assertThat(withTitle.indexOf("waiting-reply")).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("no keyword match → empty or short list (no soft-fill padding)")
    void noMatchNoPadding() {
        String body = "오늘은 날씨가 맑아서 산책을 다녀왔다. 커피도 마셨다.";
        List<String> ids = service.shortlist(body);
        assertThat(ids.size()).isLessThanOrEqualTo(SibomCandidateService.MAX_CANDIDATES);
        for (String id : ids) {
            assertThat(SibomCatalog.isKnown(id)).isTrue();
        }
    }

    @Test
    @DisplayName("cap at 12 even if many images score")
    void capsAtTwelve() {
        String body = String.join("\n",
                "말을 안 한 지 며칠, 냉전, 서로 피하고",
                "언성이 높아졌다, 그 자리에서 다퉜다, 소리를 질렀다",
                "먼저 사과했는데, 손을 내밀었는데, 받아주지 않았다",
                "읽씹, 답장이 없다, 카톡을 읽고도, 답이 없었다",
                "말을 삼켰다, 결국 아무 말도, 속으로만",
                "말문이 막혔다, 어이가 없었다, 황당했다, 순간 멍해졌다",
                "눈치를 봤다, 분위기를 살폈다, 눈치가 보였다",
                "이제 지쳤다, 더는 못하겠다",
                "억울했다, 분했다",
                "울컥했다, 눈물이 났다",
                "한숨만 나왔다",
                "가슴이 답답했다",
                "혼자 남았다",
                "연락이 끊겼다",
                "무시당했다");
        List<String> ids = service.shortlist(body);
        assertThat(ids).hasSizeLessThanOrEqualTo(12);
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("keyword weight beats trigger-only soft hit")
    void keywordBeatsTriggerOnly() {
        SibomCatalog.Entry keywordHit = new SibomCatalog.Entry(
                "kw-img", "reaction", 1, "m", "c", "g", null, 16,
                List.of("읽씹"), "unused", List.of("unused"));
        SibomCatalog.Entry triggerOnly = new SibomCatalog.Entry(
                "tr-img", "reaction", 1, "m", "c", "g", null, 16,
                List.of("전혀다른키워드"), "읽씹 관련", List.of("읽씹"));
        String text = "결국 읽씹만 당했다.";
        assertThat(SibomCandidateService.score(keywordHit, text))
                .isGreaterThan(SibomCandidateService.score(triggerOnly, text));
    }
}
