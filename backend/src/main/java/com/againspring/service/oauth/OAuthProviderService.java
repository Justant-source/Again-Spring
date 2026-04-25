package com.againspring.service.oauth;

import com.againspring.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Google / Kakao / Naver OAuth code→token 교환 및 사용자 프로필 조회.
 * Spring Security OAuth2 Client를 사용하지 않고 직접 HTTP 호출.
 * (현재 아키텍처가 Stateless JWT이므로 서버 사이드 세션 없이 처리)
 */
@Service
@Slf4j
public class OAuthProviderService {

    @Value("${oauth2.google.client-id:}")
    private String googleClientId;
    @Value("${oauth2.google.client-secret:}")
    private String googleClientSecret;

    @Value("${oauth2.kakao.client-id:}")
    private String kakaoClientId;
    @Value("${oauth2.kakao.client-secret:}")
    private String kakaoClientSecret;

    @Value("${oauth2.naver.client-id:}")
    private String naverClientId;
    @Value("${oauth2.naver.client-secret:}")
    private String naverClientSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    public OAuthUserInfo fetchUserInfo(String provider, String code, String redirectUri) {
        return switch (provider.toLowerCase()) {
            case "google" -> fetchGoogleUserInfo(code, redirectUri);
            case "kakao" -> fetchKakaoUserInfo(code, redirectUri);
            case "naver" -> fetchNaverUserInfo(code, redirectUri);
            default -> throw new BusinessException("OAUTH_INVALID_PROVIDER", "지원하지 않는 provider: " + provider);
        };
    }

    @SuppressWarnings("unchecked")
    private OAuthUserInfo fetchGoogleUserInfo(String code, String redirectUri) {
        // 1. code → access_token
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", googleClientId);
        params.add("client_secret", googleClientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("grant_type", "authorization_code");

        Map<String, Object> tokenRes = exchange(
                "https://oauth2.googleapis.com/token",
                HttpMethod.POST, null, params);
        String accessToken = (String) tokenRes.get("access_token");

        // 2. access_token → user info
        Map<String, Object> userInfo = exchange(
                "https://www.googleapis.com/oauth2/v3/userinfo",
                HttpMethod.GET, accessToken, null);

        return OAuthUserInfo.builder()
                .providerId((String) userInfo.get("sub"))
                .email((String) userInfo.get("email"))
                .nickname((String) userInfo.getOrDefault("name", "Google사용자"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private OAuthUserInfo fetchKakaoUserInfo(String code, String redirectUri) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakaoClientId);
        params.add("client_secret", kakaoClientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);

        Map<String, Object> tokenRes = exchange(
                "https://kauth.kakao.com/oauth/token",
                HttpMethod.POST, null, params);
        String accessToken = (String) tokenRes.get("access_token");

        Map<String, Object> userRes = exchange(
                "https://kapi.kakao.com/v2/user/me",
                HttpMethod.GET, accessToken, null);

        String providerId = String.valueOf(userRes.get("id"));
        Map<String, Object> kakaoAccount = (Map<String, Object>) userRes.get("kakao_account");
        String email = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;
        Map<String, Object> profile = kakaoAccount != null
                ? (Map<String, Object>) kakaoAccount.get("profile") : null;
        String nickname = profile != null ? (String) profile.get("nickname") : "카카오사용자";

        return OAuthUserInfo.builder()
                .providerId(providerId)
                .email(email)
                .nickname(nickname)
                .build();
    }

    @SuppressWarnings("unchecked")
    private OAuthUserInfo fetchNaverUserInfo(String code, String redirectUri) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", naverClientId);
        params.add("client_secret", naverClientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);
        params.add("state", "naver_state");

        Map<String, Object> tokenRes = exchange(
                "https://nid.naver.com/oauth2.0/token",
                HttpMethod.POST, null, params);
        String accessToken = (String) tokenRes.get("access_token");

        Map<String, Object> userRes = exchange(
                "https://openapi.naver.com/v1/nid/me",
                HttpMethod.GET, accessToken, null);

        Map<String, Object> response = (Map<String, Object>) userRes.get("response");
        return OAuthUserInfo.builder()
                .providerId((String) response.get("id"))
                .email((String) response.get("email"))
                .nickname((String) response.getOrDefault("name", "네이버사용자"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchange(String url, HttpMethod method,
                                         String bearerToken,
                                         MultiValueMap<String, String> formParams) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        HttpEntity<?> entity;
        if (formParams != null) {
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            entity = new HttpEntity<>(formParams, headers);
        } else {
            entity = new HttpEntity<>(headers);
        }

        try {
            ResponseEntity<Map> res = restTemplate.exchange(url, method, entity, Map.class);
            if (res.getBody() == null) {
                throw new BusinessException("OAUTH_FAILED", "OAuth 응답이 비어있습니다");
            }
            return res.getBody();
        } catch (Exception e) {
            log.error("OAuth API 호출 실패: {} {}", method, url, e);
            throw new BusinessException("OAUTH_FAILED", "소셜 로그인 처리 중 오류가 발생했습니다");
        }
    }
}
