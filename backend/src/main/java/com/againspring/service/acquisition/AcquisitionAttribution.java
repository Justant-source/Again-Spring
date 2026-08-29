package com.againspring.service.acquisition;

import com.againspring.domain.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 신규 사용자에게 유입 채널(first-touch UTM)을 귀속시킨다.
 *
 * <p>배경(2026-08-29): {@code users.acquisition_source}/{@code acquisition_campaign} 컬럼은
 * 예전부터 있었지만 채우는 코드가 없어 전 행이 NULL이었다. 그래서 "YouTube를 보고 가입한
 * 사람이 몇 명인가"라는 질문에 한 달 내내 답할 수 없었다. 마케팅 개선의 성패를 판정할
 * 유일한 종단 지표라 배선을 복구한다.
 *
 * <p>출처는 프론트엔드 {@code VisitTracker}가 심는 {@code as_utm} 쿠키다. first-touch 정책
 * (이미 있으면 덮어쓰지 않음)이라 "처음 데려온 채널"이 남는다 — 마지막 클릭이 아니라
 * 발견 채널을 알고 싶기 때문이다.
 *
 * <p>요청 컨텍스트가 없는 경로(스케줄러·배치)에서도 안전하게 no-op이 되도록 만들었다.
 * 귀속 실패가 가입 자체를 막아서는 안 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AcquisitionAttribution {

    public static final String COOKIE_NAME = "as_utm";

    private static final int MAX_LEN = 100;

    private final ObjectMapper objectMapper;

    /**
     * 현재 HTTP 요청의 {@code as_utm} 쿠키를 읽어 신규 사용자에 귀속시킨다.
     * 이미 값이 있으면 손대지 않는다(first-touch 보존).
     */
    public void applyTo(User user) {
        if (user == null || user.getAcquisitionSource() != null) {
            return;
        }
        currentRequest()
            .flatMap(this::readCookie)
            .ifPresent(utm -> {
                user.setAcquisitionSource(truncate(text(utm, "source")));
                user.setAcquisitionCampaign(truncate(text(utm, "campaign")));
                log.info("Acquisition attributed: user={} source={} campaign={}",
                    user.getId(), user.getAcquisitionSource(), user.getAcquisitionCampaign());
            });
    }

    /**
     * 게스트가 갖고 있던 귀속을 회원 계정으로 승계한다.
     *
     * <p>마케팅 링크로 들어와 게스트로 둘러본 뒤 며칠 후 가입하는 흐름에서, 가입 시점의
     * 쿠키가 만료됐어도 게스트 행에 남은 채널 정보를 잃지 않기 위한 경로다.
     */
    public void inherit(User guest, User member) {
        if (guest == null || member == null || member.getAcquisitionSource() != null) {
            return;
        }
        if (guest.getAcquisitionSource() == null) {
            return;
        }
        member.setAcquisitionSource(guest.getAcquisitionSource());
        member.setAcquisitionCampaign(guest.getAcquisitionCampaign());
        log.info("Acquisition inherited from guest: member={} source={}",
            member.getId(), member.getAcquisitionSource());
    }

    private Optional<HttpServletRequest> currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return Optional.ofNullable(servletAttrs.getRequest());
        }
        return Optional.empty();
    }

    private Optional<JsonNode> readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (!COOKIE_NAME.equals(cookie.getName())) {
                continue;
            }
            String raw = cookie.getValue();
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            try {
                String decoded = URLDecoder.decode(raw, StandardCharsets.UTF_8);
                return Optional.ofNullable(objectMapper.readTree(decoded));
            } catch (Exception e) {
                // 쿠키는 클라이언트가 조작할 수 있는 값이다. 깨진 값 때문에 가입이
                // 실패하면 안 되므로 조용히 포기한다.
                log.debug("as_utm cookie unparseable: {}", e.getMessage());
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return (s == null || s.isBlank() || "null".equals(s)) ? null : s;
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_LEN ? s : s.substring(0, MAX_LEN);
    }
}
