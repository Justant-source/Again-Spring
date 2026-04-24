package com.againspring.service;

import com.againspring.api.dto.request.OnboardingRequest;
import com.againspring.api.dto.request.UpdateUserRequest;
import com.againspring.api.dto.response.OnboardingResponse;
import com.againspring.api.dto.response.UserResponse;
import com.againspring.common.exception.BusinessException;
import com.againspring.domain.User;
import com.againspring.repository.UserRepository;
import com.againspring.service.StyleCalculator.CommunicationStyle;
import java.time.Instant;
import java.util.ArrayList;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * User service for profile management and onboarding.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final StyleCalculator styleCalculator;

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

        // Validate and calculate style
        try {
            CommunicationStyle style = styleCalculator.calculateStyle(request.getAnswers());

            user.setOnboardingAnswers(request.getAnswers());
            user.setCommunicationStyle(style.getValue());
            user.setOnboardingCompletedAt(Instant.now());
            user.setUpdatedAt(Instant.now());

            User updated = userRepository.save(user);
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
     * Soft-delete user account.
     *
     * @param userId the user ID
     * @throws BusinessException if user not found or already deleted
     */
    public void deleteUserAccount(String userId) {
        User user = userRepository
                .findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));

        user.setDeletedAt(Instant.now());
        userRepository.save(user);

        log.info("User account soft-deleted: {}", userId);
        // TODO Phase 10: Implement Neo4j cleanup event and cascade cancel pending sessions
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .communicationStyle(user.getCommunicationStyle())
                .isGuest(user.isGuest())
                .onboardingCompleted(user.getOnboardingCompletedAt() != null)
                .temperatureHistory(new ArrayList<>())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
