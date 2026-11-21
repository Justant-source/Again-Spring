package com.againspring.marketing;

import com.againspring.repository.ai.SystemSettingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboundDraftGuardTest {

    @Mock
    private SystemSettingRepository systemSettingRepository;

    private OutboundDraftGuard guard;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(systemSettingRepository.findById(anyString())).thenReturn(Optional.empty());
        guard = new OutboundDraftGuard(systemSettingRepository, new ObjectMapper());
    }

    @Test
    void tooLong_nonWhitespaceOver40() {
        String body = "가".repeat(41);
        assertThat(guard.firstViolation(body, List.of())).contains("TOO_LONG");
    }

    @Test
    void tooLong_threeLines() {
        assertThat(guard.firstViolation("한줄\n두줄\n세줄", List.of())).contains("TOO_LONG");
    }

    @Test
    void tooLong_shortOneLinerPasses() {
        assertThat(guard.firstViolation("귀엽네", List.of())).isEmpty();
    }

    @Test
    void laughSpam_fourConsecutive() {
        assertThat(guard.firstViolation("진짜ㅋㅋㅋㅋ", List.of())).contains("LAUGH_SPAM");
        assertThat(guard.firstViolation("헐ㅎㅎㅎㅎ", List.of())).contains("LAUGH_SPAM");
    }

    @Test
    void laughSpam_ratioHalfOrMore() {
        assertThat(guard.firstViolation("ㅋㅋㅋ", List.of())).contains("LAUGH_SPAM");
    }

    @Test
    void echo_nearlyEqualToPeer() {
        assertThat(guard.firstViolation("너무 귀여움", List.of("너무귀여움"))).contains("ECHO");
        assertThat(guard.firstViolation("너무귀여움", List.of("다른 말"))).isEmpty();
    }

    @Test
    void langMismatch_englishPostKoreanReply() {
        assertThat(guard.firstViolation("귀엽네ㅋㅋ", "this apple peeler is wild", List.of()))
            .contains("LANG_MISMATCH");
    }

    @Test
    void langMismatch_matchingEnglishPassesLength() {
        assertThat(guard.firstViolation("lmao that peeler", "this apple peeler is wild", List.of()))
            .isEmpty();
    }

    @Test
    void habitEcho_generalizedIginHaldutBlocked() {
        assertThat(guard.firstViolation("9번 당한다니 미친놈이긴 할듯 ㅠㅠ", List.of()))
            .contains("HABIT_ECHO");
        assertThat(guard.firstViolation("30년을 모르고 산다니 미친놈이긴 할듯 ㅋㅋ", List.of()))
            .contains("HABIT_ECHO");
        assertThat(guard.firstViolation("미친놈이긴 할듯 ㅋㅋ", List.of()))
            .contains("HABIT_ECHO");
    }

    @Test
    void habitEcho_operatorTiredPhraseStillAllowed() {
        assertThat(guard.firstViolation("퇴근하고와서 힘빠지긴 할듯", List.of())).isEmpty();
    }

    @Test
    void isKoreanPost_hangulMajority() {
        assertThat(OutboundDraftGuard.isKoreanPost("오늘 퇴근길에 남편이 또 말도 없이 약속을 잡았다")).isTrue();
        assertThat(OutboundDraftGuard.isKoreanPost("친구한테 서운해서 그런데 이게 맞나 싶어요 ㅠㅠ https://t.co/abc @friend")).isTrue();
    }

    @Test
    void isKoreanPost_foreignOrEmptyIsNot() {
        assertThat(OutboundDraftGuard.isKoreanPost(null)).isFalse();
        assertThat(OutboundDraftGuard.isKoreanPost("   ")).isFalse();
        assertThat(OutboundDraftGuard.isKoreanPost("Good morning everyone, have a nice day")).isFalse();
        assertThat(OutboundDraftGuard.isKoreanPost("今日はいい天気ですね")).isFalse();
        assertThat(OutboundDraftGuard.isKoreanPost("صباح الخير للجميع")).isFalse();
        assertThat(OutboundDraftGuard.isKoreanPost("😀😀 https://t.co/xyz @someone 123456")).isFalse();
    }

    @Test
    void isKoreanPost_needsFiveSyllablesAndRatio() {
        assertThat(OutboundDraftGuard.isKoreanPost("최고다")).isFalse();
        assertThat(OutboundDraftGuard.isKoreanPost("최고다 진짜")).isTrue();
        // hangul only a small tag inside a long English post
        assertThat(OutboundDraftGuard.isKoreanPost(
            "New album out now, stream it everywhere you listen to music tonight 방탄 팬 여러분 안녕하세요")).isFalse();
        // ignored noise must not dilute the ratio
        assertThat(OutboundDraftGuard.isKoreanPost("오늘 너무 힘들었다 https://example.com/very/long/path/abcdef @abc_def_ghi 😭😭")).isTrue();
    }

    @Test
    void langMismatch_unchanged() {
        assertThat(OutboundDraftGuard.langMismatch("너무 귀엽다", "so cute omg this is great")).isEqualTo("LANG_MISMATCH");
    }
}
