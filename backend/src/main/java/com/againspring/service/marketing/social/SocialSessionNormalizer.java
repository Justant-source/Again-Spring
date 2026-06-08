package com.againspring.service.marketing.social;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 소셜 세션(storageState) 입력 정규화 + 검증.
 *
 * <p>핵심 문제: 브라우저 콘솔에서 {@code document.cookie} 로 추출한 세션은
 * httpOnly 인증 쿠키(X={@code auth_token}, Instagram={@code sessionid})를 캡처하지 못해
 * "로그아웃 상태" 세션이 저장된다. 발행 시 compose/create 화면 대신 로그인 화면으로
 * 리다이렉트되어 실패한다.
 *
 * <p>이 정규화기는:
 * <ol>
 *   <li>Playwright storageState JSON 또는 Cookie-Editor 확장 export(JSON 배열) 를 모두 수용
 *   <li>Cookie-Editor 배열은 Playwright storageState 형태로 변환
 *   <li>플랫폼별 필수 httpOnly 인증 쿠키가 없으면 거부(fail-fast) → 죽은 세션 저장 방지
 * </ol>
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class SocialSessionNormalizer {

    private final ObjectMapper mapper = new ObjectMapper();

    /** 플랫폼별 필수 인증 쿠키 (httpOnly). 없으면 로그인 안 된 세션. */
    private static String requiredAuthCookie(String platform) {
        return switch (platform == null ? "" : platform.toUpperCase()) {
            case "X" -> "auth_token";
            case "INSTAGRAM" -> "sessionid";
            case "NAVER_BLOG" -> "NID_AUT";
            default -> null;
        };
    }

    /**
     * 입력을 Playwright storageState JSON 문자열로 정규화하고 필수 인증 쿠키를 검증한다.
     *
     * @param platform "X" 또는 "INSTAGRAM"
     * @param rawInput admin UI 에 붙여넣은 원본 문자열 (storageState 또는 Cookie-Editor JSON)
     * @return 정규화된 storageState JSON
     * @throws IllegalArgumentException 형식 오류 또는 필수 인증 쿠키 누락 시 (안내 메시지 포함)
     */
    @SuppressWarnings("unchecked")
    public String normalizeAndValidate(String platform, String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            throw new IllegalArgumentException("세션 입력이 비어 있습니다.");
        }

        String trimmed = rawInput.trim();
        Map<String, Object> storageState;

        try {
            if (trimmed.startsWith("[")) {
                // Cookie-Editor export (쿠키 배열) → storageState 로 변환
                List<Map<String, Object>> cookieArray =
                        mapper.readValue(trimmed, new TypeReference<List<Map<String, Object>>>() {});
                storageState = fromCookieEditor(cookieArray);
            } else {
                // Playwright storageState 형태로 가정
                storageState = mapper.readValue(trimmed, new TypeReference<Map<String, Object>>() {});
                if (!storageState.containsKey("cookies")) {
                    throw new IllegalArgumentException(
                            "storageState JSON 에 'cookies' 필드가 없습니다. "
                            + "Playwright storageState 또는 Cookie-Editor export 형식이어야 합니다.");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("세션 JSON 파싱 실패: " + e.getMessage(), e);
        }

        List<Map<String, Object>> cookies = (List<Map<String, Object>>) storageState
                .getOrDefault("cookies", new ArrayList<>());

        // 필수 인증 쿠키 검증
        String required = requiredAuthCookie(platform);
        if (required != null) {
            boolean hasAuth = cookies.stream().anyMatch(c -> {
                Object name = c.get("name");
                Object value = c.get("value");
                return required.equals(name) && value != null && !value.toString().isBlank();
            });
            if (!hasAuth) {
                throw new IllegalArgumentException(
                        "필수 로그인 쿠키 '" + required + "' 가 없습니다 (플랫폼 " + platform + "). "
                        + "이는 보통 브라우저 콘솔의 document.cookie 방식으로 세션을 추출했기 때문입니다 — "
                        + "이 방식은 httpOnly 인증 쿠키('" + required + "')를 읽지 못합니다. "
                        + "Cookie-Editor 확장 프로그램으로 'Export → JSON' 한 결과를 붙여넣거나, "
                        + "seed-cli.js(헤드풀 브라우저 로그인)로 시드하세요.");
            }
        }

        try {
            return mapper.writeValueAsString(storageState);
        } catch (Exception e) {
            throw new IllegalArgumentException("정규화된 세션 직렬화 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Cookie-Editor 확장 export(쿠키 객체 배열) → Playwright storageState 변환.
     * Cookie-Editor 필드: name, value, domain, path, expirationDate, httpOnly, secure, sameSite, session
     */
    private Map<String, Object> fromCookieEditor(List<Map<String, Object>> cookieArray) {
        List<Map<String, Object>> cookies = new ArrayList<>();
        for (Map<String, Object> c : cookieArray) {
            if (c.get("name") == null) continue;
            Map<String, Object> pw = new java.util.HashMap<>();
            pw.put("name", c.get("name"));
            pw.put("value", c.getOrDefault("value", ""));

            String domain = str(c.get("domain"));
            pw.put("domain", domain);
            pw.put("path", c.getOrDefault("path", "/"));

            // expires: session 쿠키면 -1, 아니면 epoch seconds (정수)
            boolean isSession = Boolean.TRUE.equals(c.get("session")) || c.get("expirationDate") == null;
            if (isSession) {
                pw.put("expires", -1);
            } else {
                double exp = Double.parseDouble(c.get("expirationDate").toString());
                pw.put("expires", Math.round(exp));
            }

            pw.put("httpOnly", Boolean.TRUE.equals(c.get("httpOnly")));
            pw.put("secure", Boolean.TRUE.equals(c.get("secure")));
            pw.put("sameSite", mapSameSite(str(c.get("sameSite"))));
            cookies.add(pw);
        }

        Map<String, Object> storageState = new java.util.HashMap<>();
        storageState.put("cookies", cookies);
        storageState.put("origins", new ArrayList<>());
        return storageState;
    }

    /** Cookie-Editor sameSite → Playwright sameSite (Strict|Lax|None) */
    private String mapSameSite(String raw) {
        if (raw == null) return "Lax";
        return switch (raw.toLowerCase()) {
            case "no_restriction", "none" -> "None";
            case "strict" -> "Strict";
            case "lax" -> "Lax";
            default -> "Lax";
        };
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }
}
