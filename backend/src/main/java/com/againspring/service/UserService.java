package com.againspring.service;

import com.againspring.api.dto.request.DeleteAccountRequest;
import com.againspring.api.dto.request.OnboardingRequest;
import com.againspring.api.dto.request.UpdateUserRequest;
import com.againspring.api.dto.response.OnboardingResponse;
import com.againspring.api.dto.response.UserResponse;
import com.againspring.common.exception.BusinessException;
import com.againspring.domain.Session;
import com.againspring.domain.User;
import com.againspring.domain.enums.SessionStatus;
import com.againspring.repository.EmailVerificationRepository;
import com.againspring.repository.GuestSessionRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.UserRepository;
import com.againspring.service.StyleCalculator.CommunicationStyle;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User service for profile management and onboarding.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final StyleCalculator styleCalculator;
    private final PasswordEncoder passwordEncoder;
    private final SessionRepository sessionRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final GuestSessionRepository guestSessionRepository;

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
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));

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
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));

        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }

        if (request.getCommunicationStyle() != null) {
            user.setCommunicationStyle(request.getCommunicationStyle());
        }

        user.setUpdatedAt(Instant.now());
        User updated = userRepository.save(user);
        log.info("User profile updated: {}", userId);

        return mapToUserResponse(updated);
    }

    /**
     * Complete onboarding: save 10 answers and calculate communication style.
     *
     * @param userId the user ID
     * @param request onboarding request
     * @return onboarding response with style info
     * @throws BusinessException if user not found or invalid answers
     */
    public OnboardingResponse completeOnboarding(String userId, OnboardingRequest request) {
        User user = userRepository
                .findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));

        // MBTI path: communicationStyle provided directly (no answers required)
        if (request.getAnswers() == null && request.getCommunicationStyle() != null) {
            user.setCommunicationStyle(request.getCommunicationStyle());
            if (request.getMbtiType() != null) user.setMbtiType(request.getMbtiType());
            if (request.getMbtiProfile() != null) user.setMbtiProfile(request.getMbtiProfile());
            user.setOnboardingCompletedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);
            log.info("Onboarding completed via MBTI for user: {}", userId);

            CommunicationStyle style;
            try {
                style = CommunicationStyle.valueOf(request.getCommunicationStyle().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("ONBOARDING_INVALID_STYLE", "Invalid communication style: " + request.getCommunicationStyle());
            }
            return OnboardingResponse.builder()
                    .communicationStyle(style.getValue())
                    .styleInfo(OnboardingResponse.StyleInfo.builder()
                            .emoji(style.getEmoji())
                            .label(style.getLabel())
                            .description(style.getDescription())
                            .strengths(style.getStrengths())
                            .caution(style.getCaution())
                            .build())
                    .build();
        }

        // 10-question path
        if (request.getAnswers() == null) {
            throw new BusinessException("ONBOARDING_INVALID_ANSWERS", "Either answers or communicationStyle is required");
        }

        try {
            CommunicationStyle style = styleCalculator.calculateStyle(request.getAnswers());

            user.setOnboardingAnswers(request.getAnswers());
            user.setCommunicationStyle(style.getValue());
            if (request.getMbtiType() != null) user.setMbtiType(request.getMbtiType());
            if (request.getMbtiProfile() != null) user.setMbtiProfile(request.getMbtiProfile());
            user.setOnboardingCompletedAt(Instant.now());
            user.setUpdatedAt(Instant.now());

            userRepository.save(user);
            log.info("Onboarding completed for user: {}", userId);

            return OnboardingResponse.builder()
                    .communicationStyle(style.getValue())
                    .styleInfo(OnboardingResponse.StyleInfo.builder()
                            .emoji(style.getEmoji())
                            .label(style.getLabel())
                            .description(style.getDescription())
                            .strengths(style.getStrengths())
                            .caution(style.getCaution())
                            .build())
                    .build();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "ONBOARDING_INVALID_ANSWERS", "Invalid onboarding answers: " + e.getMessage());
        }
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
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));

        // Password verification for email/password sign-up users
        if (user.getPasswordHash() != null) {
            if (req == null || req.getPassword() == null
                    || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
                throw new BusinessException("AUTH_INVALID_CREDENTIALS", "Password does not match", 401);
            }
        }

        Instant now = Instant.now();
        String originalEmail = user.getEmail();

        // Cancel active sessions so the partner is not left waiting
        List<SessionStatus> activeStatuses = List.of(
                SessionStatus.CHATTING_SOLO, SessionStatus.CHATTING_DUO,
                SessionStatus.AWAITING_FINALIZATION, SessionStatus.WAITING_B,
                SessionStatus.B_JOINED, SessionStatus.IN_MEDIATION, SessionStatus.SOLO_MODE);
        sessionRepository.findByCreatedByUserIdAndStatusIn(userId, activeStatuses)
                .forEach(s -> { s.setStatus(SessionStatus.TERMINATED); s.setCompletedAt(now); });
        sessionRepository.findByInviteeUserIdAndStatusIn(userId, activeStatuses)
                .forEach(s -> { s.setStatus(SessionStatus.TERMINATED); s.setCompletedAt(now); });

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

        // Remove guest session mapping (guest users only; noop for regular users)
        guestSessionRepository.deleteByGuestId(userId);

        log.info("User account anonymized: {}", userId);
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .communicationStyle(user.getCommunicationStyle())
                .isGuest(user.isGuest())
                .onboardingCompleted(user.getOnboardingCompletedAt() != null)
                .mbtiType(user.getMbtiType())
                .mbtiProfile(user.getMbtiProfile())
                .provider(user.getProvider())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
