package com.againspring.service;

import com.againspring.api.dto.request.AgreeReconfirmRequest;
import com.againspring.api.dto.request.GuestRequest;
import com.againspring.api.dto.request.LoginRequest;
import com.againspring.api.dto.request.SignupRequest;
import com.againspring.api.dto.response.AuthResponse;
import com.againspring.common.exception.BusinessException;
import com.againspring.domain.GuestSession;
import com.againspring.domain.User;
import com.againspring.repository.GuestSessionRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.UserRepository;
import com.againspring.security.JwtService;
import com.againspring.util.GuestNicknameGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
    private final SessionRepository sessionRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final AdminRoleAssigner adminRoleAssigner;
    private final Random random = new Random();

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        emailVerificationService.verifyCode(request.getEmail(), request.getVerificationCode());

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BusinessException("USER_ALREADY_EXISTS", "이미 가입된 이메일이에요. 로그인해 주세요.");
        }

        Instant now = Instant.now();
        User user = User.builder()
                .id(generateUserId())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .isGuest(false)
                .roles(new ArrayList<>())
                .termsAgreedAt(now)
                .privacyAgreedAt(now)
                .disclaimerAgreedAt(now)
                .marketingAgreedAt(request.isMarketingAgreed() ? now : null)
                .build();
        user.getRoles().add("USER");

        User saved = userRepository.save(user);
        saved = adminRoleAssigner.ensureAdminIfWhitelisted(saved);
        log.info("User signup successful: {}", saved.getId());

        // 게스트 → 회원 마이그레이션 (요청에 게스트 ID가 있는 경우에만)
        if (request.getMigrateFromGuestId() != null && !request.getMigrateFromGuestId().isBlank()) {
            saved = migrateGuestData(request.getMigrateFromGuestId(), saved);
        }

        String token = jwtService.generateAccessToken(saved.getId(), saved.getEmail());
        return buildAuthResponse(saved, token, 86400, false);
    }

    /**
     * 게스트 user의 온보딩/MBTI/통신스타일을 신규 회원에 복사하고,
     * 게스트가 만든/초대받은 모든 세션을 신규 회원으로 reassign 후 게스트 user를 soft delete.
     * 안전성: 대상 user가 isGuest=true 인지 확인, 아니면 무시 (다른 회원 데이터 탈취 차단).
     */
    @Transactional
    protected User migrateGuestData(String guestId, User newMember) {
        var guestOpt = userRepository.findById(guestId);
        if (guestOpt.isEmpty()) {
            log.warn("Guest migration skipped — guest user not found: {}", guestId);
            return newMember;
        }
        User guest = guestOpt.get();
        if (!guest.isGuest()) {
            log.warn("Guest migration refused — target id is not a guest: {}", guestId);
            return newMember;
        }
        if (guest.getDeletedAt() != null) {
            log.warn("Guest migration skipped — guest already deleted: {}", guestId);
            return newMember;
        }

        // 1) 프로필 복사 (게스트가 채워둔 경우만 — 신규 회원 입력값 덮어쓰기 안 함)
        if (guest.getCommunicationStyle() != null && newMember.getCommunicationStyle() == null) {
            newMember.setCommunicationStyle(guest.getCommunicationStyle());
        }
        if (guest.getOnboardingAnswers() != null && newMember.getOnboardingAnswers() == null) {
            newMember.setOnboardingAnswers(guest.getOnboardingAnswers());
        }
        if (guest.getMbtiType() != null && newMember.getMbtiType() == null) {
            newMember.setMbtiType(guest.getMbtiType());
        }
        if (guest.getMbtiProfile() != null && newMember.getMbtiProfile() == null) {
            newMember.setMbtiProfile(guest.getMbtiProfile());
        }
        if (guest.getOnboardingCompletedAt() != null && newMember.getOnboardingCompletedAt() == null) {
            newMember.setOnboardingCompletedAt(guest.getOnboardingCompletedAt());
        }
        User savedMember = userRepository.save(newMember);

        // 2) 세션 ownership 이전 (createdByUserId / inviteeUserId 모두)
        int reassignedCreated = sessionRepository.reassignCreatedBy(guestId, savedMember.getId());
        int reassignedInvited = sessionRepository.reassignInvitee(guestId, savedMember.getId());
        log.info("Guest sessions migrated: created={}, invited={} (guest {} → member {})",
                reassignedCreated, reassignedInvited, guestId, savedMember.getId());

        // 3) 게스트 user soft delete (재인증 시도 차단)
        guest.setDeletedAt(Instant.now());
        userRepository.save(guest);
        log.info("Guest user soft-deleted after migration: {}", guestId);

        return savedMember;
    }

    public AuthResponse login(LoginRequest request) {
        // 1) 이메일 등록 여부
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(
                        "EMAIL_NOT_REGISTERED",
                        "등록되지 않은 이메일이에요. 회원가입이 필요해요.",
                        404));

        // 2) 탈퇴 계정
        if (user.getDeletedAt() != null) {
            throw new BusinessException(
                    "USER_ALREADY_DELETED",
                    "탈퇴한 계정이에요. 새 이메일로 가입해주세요.",
                    410);
        }

        // 3) OAuth 가입자가 이메일/비밀번호로 로그인 시도
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            String providerLabel = providerLabel(user.getProvider());
            throw new BusinessException(
                    "OAUTH_LOGIN_REQUIRED",
                    providerLabel + " 로그인으로 가입된 계정이에요. " + providerLabel + " 로그인을 사용해주세요.",
                    401);
        }

        // 4) 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(
                    "WRONG_PASSWORD",
                    "비밀번호가 올바르지 않아요. 비밀번호 찾기를 이용해주세요.",
                    401);
        }

        user = adminRoleAssigner.ensureAdminIfWhitelisted(user);
        log.info("User login successful: {}", user.getId());
        String token = jwtService.generateAccessToken(user.getId(), user.getEmail());
        return buildAuthResponse(user, token, 86400, false);
    }

    private String providerLabel(String provider) {
        if (provider == null) return "소셜";
        // 현재 지원: Google만. 그 외는 일반화된 '소셜' 라벨로 노출하지 않음.
        if ("google".equalsIgnoreCase(provider)) return "Google";
        return "소셜";
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
            throw new BusinessException("USER_ALREADY_DELETED", "탈퇴한 계정이에요.");
        }

        user = adminRoleAssigner.ensureAdminIfWhitelisted(user);
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
                : GuestNicknameGenerator.generateUnique(
                        userRepository::existsByNicknameAndDeletedAtIsNull);

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

        // 게스트 유저를 users 테이블에 저장 (없을 때만) — UserDetailsService/SessionService 인증 경로 공유
        if (!userRepository.existsById(guestId)) {
            User guestUser = User.builder()
                    .id(guestId)
                    .nickname(displayNickname)
                    .isGuest(true)
                    .roles(new ArrayList<>(List.of("USER")))
                    .build();
            userRepository.save(guestUser);
            log.info("Guest user row created: {}", guestId);
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
                        .mustChangePassword(user.isMustChangePassword())
                        .communicationStyle(user.getCommunicationStyle())
                        .mbtiType(user.getMbtiType())
                        .mbtiProfile(user.getMbtiProfile())
                        .provider(user.getProvider())
                        .roles(user.getRoles())
                        .onboardingCompletedAt(user.getOnboardingCompletedAt())
                        .termsAgreedAt(user.getTermsAgreedAt())
                        .privacyAgreedAt(user.getPrivacyAgreedAt())
                        .disclaimerAgreedAt(user.getDisclaimerAgreedAt())
                        .marketingAgreedAt(user.getMarketingAgreedAt())
                        .createdAt(user.getCreatedAt())
                        .build())
                .token(AuthResponse.TokenInfo.builder()
                        .accessToken(token)
                        .expiresIn(expiresIn)
                        .build())
                .build();
    }

    @Transactional
    public void reconfirmConsent(String userId, AgreeReconfirmRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없어요.", 404));
        Instant now = Instant.now();
        user.setTermsAgreedAt(now);
        user.setPrivacyAgreedAt(now);
        user.setDisclaimerAgreedAt(now);
        if (request.isMarketingAgreed()) {
            user.setMarketingAgreedAt(now);
        }
        userRepository.save(user);
    }

    private String generateUserId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 26);
    }

    private String generateGuestId() {
        return "Guest-" + String.format("%06d", random.nextInt(1_000_000));
    }
}
