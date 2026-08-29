package com.againspring.marketing.holding;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MarketingHoldingContentGuard}.
 *
 * <p>The two known-bad titles/bodies here are the actual posts that leaked into the
 * marketing holding pool on 2026-08-29 (X 노출 상위 5위 중 2건). The "legit conflict"
 * fixtures are real prod posts that share the same generation pipeline
 * ({@code source_community} corpus-inspired AI-user posts) but ARE proper A-vs-B
 * conflict stories — used here to guard against regressions that would make the rule
 * too broad (오탐 방지).
 */
class MarketingHoldingContentGuardTest {

    @Test
    void flagsHistoricalTriviaByYearPattern() {
        String title = "덕혜옹주가 일본 친구한테 털어놓은 고종 독살 얘기";
        String body = "1919년 총독부 명령으로 안상호라는 사람한테 홍차에 비소를 탔다는 거\n"
            + "그걸 시녀를 통해 마시게 해서 독살했다는 내용이 기록으로 남아있음";

        Optional<String> reason = MarketingHoldingContentGuard.exclusionReason(title, body);

        assertThat(reason).contains(MarketingHoldingContentGuard.REASON_YEAR_TRIVIA_PATTERN);
    }

    @Test
    void flagsProsAndConsListicle() {
        String title = "여초회사 1년 근무자가 쓰는 장단점";
        String body = "장점부터 말하면 첫째는 사무실 냄새가 진짜 달라\n"
            + "단점도 당연히 있는데 생각보다 많지 않았음";

        Optional<String> reason = MarketingHoldingContentGuard.exclusionReason(title, body);

        assertThat(reason).contains(MarketingHoldingContentGuard.REASON_PROS_CONS_LISTICLE);
    }

    @Test
    void doesNotFlagOrdinaryConflictStoryWithoutPartnerReplyYet() {
        String title = "아내가 상의없이 오백만원 빌려준 걸 알았다";
        String body = "남편이랑 정말 잘 지내는 편이라 주변에서도 좋은 커플이란 말을 많이 들었다\n"
            + "근데 요즘 내 경제적 무능력이 너무 미안해진다";

        Optional<String> reason = MarketingHoldingContentGuard.exclusionReason(title, body);

        assertThat(reason).isEmpty();
    }

    @Test
    void doesNotFlagConflictStoryMentioningARelativeYearLikeDurationOfRelationship() {
        String title = "3년째 만난 남자친구가 갑자기 잠수를 탔다";
        String body = "작년부터 조금씩 연락이 뜸해지더니 이번 달엔 아예 답이 없다";

        Optional<String> reason = MarketingHoldingContentGuard.exclusionReason(title, body);

        assertThat(reason).isEmpty();
    }

    @Test
    void handlesNullAndBlankInputSafely() {
        assertThat(MarketingHoldingContentGuard.exclusionReason(null, null)).isEmpty();
        assertThat(MarketingHoldingContentGuard.exclusionReason("", "")).isEmpty();
        assertThat(MarketingHoldingContentGuard.exclusionReason("제목만 있음", null)).isEmpty();
    }
}
