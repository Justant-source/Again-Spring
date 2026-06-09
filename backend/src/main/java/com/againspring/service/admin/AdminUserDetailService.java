package com.againspring.service.admin;

import com.againspring.api.dto.response.AdminUserDetailResponse;
import com.againspring.common.exception.BusinessException;
import com.againspring.domain.User;
import com.againspring.repository.UserRepository;
import com.againspring.repository.community.PostCommentRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.community.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin 사용자 상세 조회 — 사용자 기본 정보 + 세션/피드백 집계를 한 번에 묶어 반환.
 */
@Service
@RequiredArgsConstructor
public class AdminUserDetailService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
    private final VoteRepository voteRepository;

    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없어요.", 404));

        long postCount = postRepository.countByAuthorIdAndDeletedAtIsNull(userId);
        long commentCount = postCommentRepository.countByAuthorIdAndDeletedAtIsNull(userId);
        long voteCount = voteRepository.countByVoterUserId(userId);

        return AdminUserDetailResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .isGuest(user.isGuest())
                .mbtiType(user.getMbtiType())
                .communicationStyle(user.getCommunicationStyle())
                .provider(user.getProvider())
                .roles(user.getRoles())
                .status(user.getStatus())
                .suspendedUntil(user.getSuspendedUntil())
                .suspendedReason(user.getSuspendedReason())
                .createdAt(user.getCreatedAt())
                .deletedAt(user.getDeletedAt())
                .onboardingCompletedAt(user.getOnboardingCompletedAt())
                .termsAgreedAt(user.getTermsAgreedAt())
                .privacyAgreedAt(user.getPrivacyAgreedAt())
                .disclaimerAgreedAt(user.getDisclaimerAgreedAt())
                .marketingAgreedAt(user.getMarketingAgreedAt())
                .postCount(postCount)
                .commentCount(commentCount)
                .voteCount(voteCount)
                .build();
    }
}
