package com.againspring.service.prompt;

import static org.junit.jupiter.api.Assertions.*;

import com.againspring.domain.User;
import com.againspring.domain.enums.MessageSender;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserProfileFragmentTest {

    private final UserProfileFragment fragment = new UserProfileFragment();

    @Test
    void render_returnsEmpty_whenUserIsNull() {
        assertEquals("", fragment.render(null));
    }

    @Test
    void render_returnsEmpty_whenStyleMissing() {
        User u = User.builder().id("u1").nickname("n").build();
        assertEquals("", fragment.render(u));
    }

    @Test
    void render_returnsEmpty_whenStyleBlank() {
        User u = User.builder().id("u1").nickname("n").communicationStyle("  ").build();
        assertEquals("", fragment.render(u));
    }

    @Test
    void render_returnsEmpty_whenStyleUnknown() {
        User u = User.builder().id("u1").nickname("n").communicationStyle("rainbow").build();
        assertEquals("", fragment.render(u));
    }

    @Test
    void render_includesLabelStrengthsCaution_forKnownStyle() {
        User u = User.builder()
            .id("u1").nickname("n")
            .communicationStyle("wave")
            .onboardingAnswers(List.of(3, 4, 3, 4, 3, 3, 3, 4, 3, 3))
            .build();

        String out = fragment.render(u);

        assertTrue(out.contains("<user_profile"));
        assertTrue(out.contains("참고용"));
        assertTrue(out.contains("파도형"));
        assertTrue(out.contains("진솔한 감정 표현"));
        assertTrue(out.contains("감정 격앙 시 휴식 필요"));
        assertTrue(out.endsWith("</user_profile>\n"));
    }

    @Test
    void render_includesSenderTag_whenProvided() {
        User u = User.builder()
            .id("u1").nickname("n").communicationStyle("flame").build();
        String out = fragment.render(u, MessageSender.USER_B);
        assertTrue(out.contains("sender=\"USER_B\""));
        assertTrue(out.contains("불꽃형"));
    }

    @Test
    void render_omitsSenderTag_whenNull() {
        User u = User.builder()
            .id("u1").nickname("n").communicationStyle("mountain").build();
        String out = fragment.render(u, null);
        assertFalse(out.contains("sender="));
        assertTrue(out.contains("산형"));
    }

    @Test
    void render_handlesAllSixStyles() {
        for (String code : List.of("wave", "mountain", "flame", "leaf", "moon", "star")) {
            User u = User.builder().id("u-" + code).nickname("n").communicationStyle(code).build();
            String out = fragment.render(u);
            assertFalse(out.isEmpty(), "style " + code + " must render non-empty");
            assertTrue(out.contains("<user_profile"), "style " + code + " missing tag");
        }
    }

    @Test
    void render_isCaseInsensitiveForStyleCode() {
        User u = User.builder().id("u1").nickname("n").communicationStyle("WAVE").build();
        String out = fragment.render(u);
        assertTrue(out.contains("파도형"));
    }

    @Test
    void render_includesMbti_whenStyleAndMbtiSet() {
        User u = User.builder().id("u1").nickname("n")
                .communicationStyle("wave").mbtiType("INFP").build();
        String out = fragment.render(u);
        assertTrue(out.contains("MBTI: INFP"));
        assertTrue(out.contains("보강 정보"));
        assertTrue(out.contains("단독 결정 변수 아님"));
    }

    @Test
    void render_omitsMbti_whenNull() {
        User u = User.builder().id("u1").nickname("n").communicationStyle("wave").build();
        String out = fragment.render(u);
        assertFalse(out.contains("MBTI"));
    }

    @Test
    void render_omitsMbti_whenBlank() {
        User u = User.builder().id("u1").nickname("n")
                .communicationStyle("wave").mbtiType("  ").build();
        String out = fragment.render(u);
        assertFalse(out.contains("MBTI"));
    }

    @Test
    void render_returnsEmpty_whenStyleNullEvenIfMbtiSet() {
        User u = User.builder().id("u1").nickname("n").mbtiType("INFP").build();
        assertEquals("", fragment.render(u));
    }
}
