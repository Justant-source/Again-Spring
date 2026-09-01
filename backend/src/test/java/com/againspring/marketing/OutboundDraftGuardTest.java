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
        when(systemSettingRepository.findById(anyString())).thenReturn(Optional.empty());
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
}
