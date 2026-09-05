package com.againspring.aiuser.orchestrator.safety;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * persona-diversity-v4 WP2 item5 — 12-gram 겹침 가드.
 * {@code ai-user/learning/app/services/ngram_guard.py}와 동일 정의(임계 0.20, min_gram 12).
 */
class SourceOverlapGuardTest {

    private final SourceOverlapGuard guard = new SourceOverlapGuard();

    @Test
    void verbatimCopyIsRejected() {
        String original = "시어머니가 매주 반찬을 만들어서 냉장고에 쌓아두시는데 정작 우리는 입에 안 맞아서 버리는 일이 반복되고 있어요";
        String generated = original; // 원문 그대로 복붙

        SourceOverlapGuard.GuardResult result = guard.check(generated, original);

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).isEqualTo("SOURCE_NGRAM_OVERLAP");
        assertThat(result.overlapRatio()).isGreaterThan(SourceOverlapGuard.THRESHOLD);
    }

    @Test
    void fullyRewrittenTextPasses() {
        String original = "시어머니가 매주 반찬을 만들어서 냉장고에 쌓아두시는데 정작 우리는 입에 안 맞아서 버리는 일이 반복되고 있어요";
        String generated = "장모님이 명절마다 김치를 몇 통씩 담가서 보내주시는데 우리 집 냉장고엔 자리가 없어서 결국 상해서 버린 적이 많아요";

        SourceOverlapGuard.GuardResult result = guard.check(generated, original);

        assertThat(result.passed()).isTrue();
        assertThat(result.overlapRatio()).isLessThan(SourceOverlapGuard.THRESHOLD);
    }

    @Test
    void shortSharedPhraseBelowThresholdPasses() {
        String original = "회사 팀장이 내가 낸 기획안을 자기 이름으로 임원 보고에 올렸다";
        // 자연스러운 재구성 픽스처 — 관계·표현은 다르지만 짧은 관용구 몇 개만 우연히 겹칠 수 있는 수준.
        String generated = "구매팀 대리로 일하는 나는 지난달 새로운 제안서를 준비해서 부서 회의에 냈는데 팀장이 그걸 그대로 가져가 자기 성과처럼 발표해버렸다 억울해서 잠이 안 온다";

        SourceOverlapGuard.GuardResult result = guard.check(generated, original);

        assertThat(result.passed()).isTrue();
    }

    @Test
    void nullOrBlankInputsAlwaysPass() {
        assertThat(guard.check(null, "원문").passed()).isTrue();
        assertThat(guard.check("생성문", null).passed()).isTrue();
        assertThat(guard.check("", "").passed()).isTrue();
    }

    @Test
    void normalizeCollapsesWhitespaceAndNewlines() {
        String withNewlines = "가나다\n라마바\t사아자";
        assertThat(SourceOverlapGuard.normalize(withNewlines)).isEqualTo("가나다 라마바 사아자");
    }

    @Test
    void overlapRatioIsOneWhenIdentical() {
        String text = "열두글자이상되는테스트문자열입니다반복해서써봅니다";
        assertThat(SourceOverlapGuard.overlapRatio(text, text, SourceOverlapGuard.MIN_GRAM)).isEqualTo(1.0);
    }

    @Test
    void overlapRatioIsZeroWhenNoSharedTwelveGram() {
        String generated = "오늘 점심은 김치찌개를 먹었고 날씨가 정말 좋아서 산책도 다녀왔다";
        String original = "주식 시장이 급락해서 포트폴리오 손실이 커졌고 다음 달 예산 계획을 다시 세워야 한다";
        assertThat(SourceOverlapGuard.overlapRatio(generated, original, SourceOverlapGuard.MIN_GRAM)).isEqualTo(0.0);
    }
}
