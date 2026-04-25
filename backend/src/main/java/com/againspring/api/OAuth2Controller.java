package com.againspring.api;

import com.againspring.api.dto.request.OAuthCallbackRequest;
import com.againspring.api.dto.response.AuthResponse;
import com.againspring.service.AuthService;
import com.againspring.service.oauth.OAuthProviderService;
import com.againspring.service.oauth.OAuthUserInfo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/oauth2")
@RequiredArgsConstructor
@Slf4j
public class OAuth2Controller {

    private final OAuthProviderService oAuthProviderService;
    private final AuthService authService;

    /**
     * 소셜 로그인 콜백 처리.
     * FE에서 OAuth code를 전달하면 provider API를 통해 사용자 정보를 조회하고 JWT를 발급한다.
     *
     * @param provider google | kakao | naver
     * @param request  { code, redirectUri }
     */
    @PostMapping("/{provider}")
    public ResponseEntity<AuthResponse> oauthCallback(
            @PathVariable String provider,
            @Valid @RequestBody OAuthCallbackRequest request) {

        log.info("OAuth callback: provider={}", provider);

        OAuthUserInfo userInfo = oAuthProviderService.fetchUserInfo(
                provider, request.getCode(), request.getRedirectUri());

        AuthResponse response = authService.oauthSignIn(
                provider, userInfo.getProviderId(), userInfo.getEmail(), userInfo.getNickname());

        return ResponseEntity.ok(response);
    }
}
