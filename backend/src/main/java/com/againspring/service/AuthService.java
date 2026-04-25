package com.againspring.service;

import com.againspring.api.dto.request.GuestRequest;
import com.againspring.api.dto.request.LoginRequest;
import com.againspring.api.dto.request.SignupRequest;
import com.againspring.api.dto.response.AuthResponse;
import com.againspring.common.exception.BusinessException;
import com.againspring.domain.GuestSession;
import com.againspring.domain.User;
import com.againspring.repository.GuestSessionRepository;
import com.againspring.repository.UserRepository;
import com.againspring.security.JwtService;
import com.againspring.util.GuestNicknameGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final GuestSessionRepository guestSessionRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final Random random = new Random();

    public AuthResponse signup(SignupRequest request) {
        emailVerificationService.verifyCode(request.getEmail(), request.getVerificationCode());

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BusinessException("USER_ALREADY_EXISTS", "Email already registered");
        }

        User user = User.builder()
                .id(generateUserId())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .isGuest(false)
                .roles(new ArrayList<>())
                .build();
        user.getRoles().add("USER");

        User saved = userRepository.save(user);
        log.info("User signup successful: {}", saved.getId());

        String token = jwtService.generateAccessToken(saved.getId(), saved.getEmail());
        return buildAuthResponse(saved, token, 86400, false);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException("AUTH_INVALID_CREDENTIALS", "Invalid email or password", 401));

        if (user.getDeletedAt() != null) {
            throw new BusinessException("USER_ALREADY_DELETED", "User account has been deleted");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("AUTH_INVALID_CREDENTIALS", "Invalid email or password", 401);
        }

        log.info("User login successful: {}", user.getId());
        String token = jwtService.generateAccessToken(user.getId(), user.getEmail());
        return buildAuthResponse(user, token, 86400, false);
    }

    /**
     * OAuth 소셜 로그인 — provider 정보로 기존 사용자 조회 또는 신규 생성.
     * code 교환 및 프로필 조회는 OAuth2Service(per-provider)에서 처리 후 이 메서드를 호출.
     */
    @Transactional
    public AuthResponse oauthSignIn(String provider, String providerId, String email, String nickname) {
        User user = userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .id(generateUserId())
                            .email(email)
                            .nickname(nickname != null ? nickname : "사용자")
                            .provider(provider)
                            .providerId(providerId)
                            .isGuest(false)
                            .roles(new ArrayList<>())
                            .build();
                    newUser.getRoles().add("USER");
                    User saved = userRepository.save(newUser);
                    log.info("OAuth user created: {} via {}", saved.getId(), provider);
                    return saved;
                });

        if (user.getDeletedAt() != null) {
            throw new BusinessException("USER_ALREADY_DELETED", "User account has been deleted");
        }

        log.info("OAuth login successful: {} via {}", user.getId(), provider);
        String token = jwtService.generateAccessToken(user.getId(), user.getEmail());
        return buildAuthResponse(user, token, 86400, false);
    }

    /**
     * 게스트 토큰 발급.
     * inviteToken이 있으면 같은 URL 재방문 시 동일 Guest ID를 반환한다.
     */
    @Transactional
    public AuthResponse guest(GuestRequest request) {
        String guestId;
        String displayNickname = (request.getNickname() != null && !request.getNickname().isBlank())
                ? request.getNickname()
                : GuestNicknameGenerator.generate();

        if (request.getInviteToken() != null && !request.getInviteToken().isBlank()) {
            guestId = guestSessionRepository.findByInviteToken(request.getInviteToken())
                    .map(GuestSession::getGuestId)
                    .orElseGet(() -> {
                        String newId = generateGuestId();
                        GuestSession gs = GuestSession.builder()
                                .inviteToken(request.getInviteToken())
                                .guestId(newId)
                                .guestNickname(displayNickname)
                                .expiresAt(Instant.now().plusSeconds(86400 * 30)) // 30일
                                .build();
                        guestSessionRepository.save(gs);
                        log.info("Guest session created: {} for token {}", newId, request.getInviteToken());
                        return newId;
                    });
        } else {
            guestId = generateGuestId();
            log.info("Guest token issued (no invite): {}", guestId);
        }

        String token = jwtService.generateGuestToken(guestId);

        return AuthResponse.builder()
                .user(AuthResponse.UserInfo.builder()
                        .id(guestId)
                        .nickname(displayNickname)
                        .isGuest(true)
                        .build())
                .token(AuthResponse.TokenInfo.builder()
                        .accessToken(token)
                        .expiresIn(7200)
                        .build())
                .build();
    }

    private AuthResponse buildAuthResponse(User user, String token, int expiresIn, boolean isGuest) {
        return AuthResponse.builder()
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .nickname(user.getNickname())
                        .isGuest(isGuest)
                        .onboardingCompletedAt(user.getOnboardingCompletedAt())
                        .createdAt(user.getCreatedAt())
                        .build())
                .token(AuthResponse.TokenInfo.builder()
                        .accessToken(token)
                        .expiresIn(expiresIn)
                        .build())
                .build();
    }

    private String generateUserId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 26);
    }

    private String generateGuestId() {
        return "Guest-" + String.format("%06d", random.nextInt(1_000_000));
    }
}
