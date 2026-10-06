package com.againspring.service;

import com.againspring.api.dto.request.DeleteAccountRequest;
import com.againspring.api.dto.request.UpdateUserRequest;
import com.againspring.api.dto.response.UserResponse;
import com.againspring.common.exception.BusinessException;
import com.againspring.domain.User;
import com.againspring.repository.EmailVerificationRepository;
import com.againspring.repository.UserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User service for profile management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationRepository emailVerificationRepository;

    /**
     * Get user profile.
     *
     * @param userId the user ID
     * @return user response
     * @throws BusinessException if user not found
     */
    public UserResponse getUserProfile(String userId) {
        User user = userRepository
                .findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없어요."));

        return mapToUserResponse(user);
    }

    /**
     * Update user profile (nickname, communication style).
     *
     * @param userId the user ID
     * @param request update request
     * @return updated user response
     * @throws BusinessException if user not found
     */
    public UserResponse updateUserProfile(String userId, UpdateUserRequest request) {
        User user = userRepository
                .findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없어요."));

        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }

        if (request.getCommunicationStyle() != null) {
            user.setCommunicationStyle(request.getCommunicationStyle());
        }

        // V22: 중재자 톤 기본값 — null = 미변경, -1 = NULL로 reset, 0~100 = 값 저장
        if (request.getMediatorDefaultX() != null) {
            int v = request.getMediatorDefaultX();
            if (v == -1) {
                user.setMediatorDefaultX(null);
            } else if (v >= 0 && v <= 100) {
                user.setMediatorDefaultX(v);
            } else {
                throw new BusinessException("INVALID_MEDIATOR_X",
                        "중재자 톤 값은 0~100 사이여야 해요.", 400);
            }
        }

        user.setUpdatedAt(Instant.now());
        User updated = userRepository.save(user);
        log.info("User profile updated: {}", userId);

        return mapToUserResponse(updated);
    }

    /**
     * Anonymize user account (soft-delete + PII masking).
     * Password-based users must supply their password for verification.
     * OAuth/guest users skip password check.
     *
     * @param userId the user ID
     * @param req    delete request (password optional for non-password users)
     */
    @Transactional
    public void deleteUserAccount(String userId, DeleteAccountRequest req) {
        User user = userRepository
                .findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없어요."));

        // Password verification for email/password sign-up users
        if (user.getPasswordHash() != null) {
            if (req == null || req.getPassword() == null
                    || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
                throw new BusinessException("AUTH_INVALID_CREDENTIALS", "비밀번호가 올바르지 않아요.", 401);
            }
        }

        Instant now = Instant.now();
        String originalEmail = user.getEmail();

        // Anonymize PII — keep the row for statistics/audit
        String shortId = userId.length() >= 8 ? userId.substring(0, 8) : userId;
        user.setEmail("deleted-" + shortId + "@deleted.local");
        user.setNickname("탈퇴한 사용자");
        user.setPasswordHash(null);
        user.setProvider(null);
        user.setProviderId(null);
        user.setCommunicationStyle(null);
        user.setMbtiType(null);
        user.setMbtiProfile(null);
        user.setOnboardingAnswers(null);
        user.setOnboardingCompletedAt(null);
        user.setDeletedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        // Remove leftover email verification codes (contains raw email)
        if (originalEmail != null) {
            emailVerificationRepository.deleteByEmail(originalEmail);
        }

        log.info("User account anonymized: {}", userId);
    }

    /**
     * 비밀번호 변경.
     * - 일반 변경: currentPassword가 현재 BCrypt 해시와 일치해야 함.
     * - 임시 비번 첫 변경: 동일 로직 (mustChangePassword=true 사용자도 currentPassword에 임시 비번 입력).
     * - 변경 성공 시 mustChangePassword=false 자동 해제.
     */
    @Transactional
    public UserResponse changePassword(String userId,
            com.againspring.api.dto.request.ChangePasswordRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없어요.", 404));

        if (user.isGuest()) {
            throw new BusinessException("GUEST_NO_PASSWORD", "게스트는 비밀번호를 변경할 수 없어요.", 400);
        }
        if (user.getProvider() != null && !user.getProvider().isBlank()) {
            throw new BusinessException("OAUTH_NO_PASSWORD",
                    "소셜 로그인 계정은 비밀번호를 변경할 수 없어요.", 400);
        }
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()
                || !passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException("PASSWORD_MISMATCH",
                    "현재 비밀번호가 일치하지 않아요.", 401);
        }
        if (request.getNewPassword().equals(request.getCurrentPassword())) {
            throw new BusinessException("SAME_PASSWORD",
                    "새 비밀번호는 현재 비밀번호와 달라야 해요.", 400);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        User saved = userRepository.save(user);
        log.info("Password changed for user {}", saved.getId());
        return mapToUserResponse(saved);
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .communicationStyle(user.getCommunicationStyle())
                .isGuest(user.isGuest())
                .mustChangePassword(user.isMustChangePassword())
                .onboardingCompleted(user.getOnboardingCompletedAt() != null)
                .onboardingCompletedAt(user.getOnboardingCompletedAt())
                .tutorialCompleted(user.getTutorialCompletedAt() != null)
                .mediatorDefaultX(user.getMediatorDefaultX())
                .mbtiType(user.getMbtiType())
                .mbtiProfile(user.getMbtiProfile())
                .provider(user.getProvider())
                .roles(user.getRoles())
                .termsAgreedAt(user.getTermsAgreedAt())
                .privacyAgreedAt(user.getPrivacyAgreedAt())
                .disclaimerAgreedAt(user.getDisclaimerAgreedAt())
                .marketingAgreedAt(user.getMarketingAgreedAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
