package com.againspring.api;

import com.againspring.api.dto.request.OAuthCallbackRequest;
import com.againspring.api.dto.response.AuthResponse;
import com.againspring.service.AuthService;
import com.againspring.service.oauth.OAuthProviderService;
import com.againspring.service.oauth.OAuthUserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/oauth2")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Auth", description = "Authentication endpoints")
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
    @Operation(summary = "소셜 로그인 (OAuth2 code 교환)", description = "FE에서 받은 OAuth2 code를 provider API로 교환해 JWT 발급 (google | kakao | naver)")
    @ApiResponse(responseCode = "200", description = "로그인 성공, JWT 발급")
    @ApiResponse(responseCode = "400", description = "유효성 검사 실패 또는 provider 오류")
    @ApiResponse(responseCode = "401", description = "OAuth2 code 무효 또는 만료")
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
