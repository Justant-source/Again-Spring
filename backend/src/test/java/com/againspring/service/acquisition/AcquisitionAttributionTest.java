package com.againspring.service.acquisition;

import com.againspring.domain.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AcquisitionAttributionTest {

    private final AcquisitionAttribution attribution = new AcquisitionAttribution(new ObjectMapper());

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void bindRequestWithUtmCookie(String json) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (json != null) {
            request.setCookies(new Cookie(AcquisitionAttribution.COOKIE_NAME,
                URLEncoder.encode(json, StandardCharsets.UTF_8)));
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("as_utm 쿠키의 채널·캠페인을 신규 사용자에 귀속한다")
    void attributesFromCookie() {
        bindRequestWithUtmCookie("{\"source\":\"youtube\",\"medium\":\"organic\",\"campaign\":\"story_675\"}");
        User user = User.builder().id("u1").nickname("n").build();

        attribution.applyTo(user);

        assertThat(user.getAcquisitionSource()).isEqualTo("youtube");
        assertThat(user.getAcquisitionCampaign()).isEqualTo("story_675");
    }

    @Test
    @DisplayName("이미 귀속된 값은 덮어쓰지 않는다 (first-touch 보존)")
    void keepsFirstTouch() {
        bindRequestWithUtmCookie("{\"source\":\"x\",\"campaign\":\"story_999\"}");
        User user = User.builder().id("u1").nickname("n").build();
        user.setAcquisitionSource("youtube");
        user.setAcquisitionCampaign("story_675");

        attribution.applyTo(user);

        assertThat(user.getAcquisitionSource()).isEqualTo("youtube");
        assertThat(user.getAcquisitionCampaign()).isEqualTo("story_675");
    }

    @Test
    @DisplayName("쿠키가 깨졌거나 없어도 가입을 막지 않는다")
    void survivesBadCookie() {
        bindRequestWithUtmCookie("not-json{{{");
        User user = User.builder().id("u1").nickname("n").build();
        attribution.applyTo(user);
        assertThat(user.getAcquisitionSource()).isNull();

        bindRequestWithUtmCookie(null);
        attribution.applyTo(user);
        assertThat(user.getAcquisitionSource()).isNull();
    }

    @Test
    @DisplayName("요청 컨텍스트가 없는 경로(스케줄러)에서도 안전하게 no-op")
    void noRequestContextIsSafe() {
        RequestContextHolder.resetRequestAttributes();
        User user = User.builder().id("u1").nickname("n").build();
        attribution.applyTo(user);
        assertThat(user.getAcquisitionSource()).isNull();
    }

    @Test
    @DisplayName("게스트가 가진 채널을 회원 계정이 승계한다 — 쿠키 만료 후 가입 대비")
    void memberInheritsFromGuest() {
        User guest = User.builder().id("g1").nickname("게스트").isGuest(true).build();
        guest.setAcquisitionSource("instagram");
        guest.setAcquisitionCampaign("story_42");
        User member = User.builder().id("m1").nickname("회원").build();

        attribution.inherit(guest, member);

        assertThat(member.getAcquisitionSource()).isEqualTo("instagram");
        assertThat(member.getAcquisitionCampaign()).isEqualTo("story_42");
    }

    @Test
    @DisplayName("회원이 이미 채널을 갖고 있으면 게스트 값으로 덮지 않는다")
    void inheritDoesNotOverwrite() {
        User guest = User.builder().id("g1").nickname("게스트").isGuest(true).build();
        guest.setAcquisitionSource("instagram");
        User member = User.builder().id("m1").nickname("회원").build();
        member.setAcquisitionSource("youtube");

        attribution.inherit(guest, member);

        assertThat(member.getAcquisitionSource()).isEqualTo("youtube");
    }
}
