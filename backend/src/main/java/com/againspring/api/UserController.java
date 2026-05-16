package com.againspring.api;

import com.againspring.api.dto.request.DeleteAccountRequest;
import com.againspring.api.dto.request.OnboardingRequest;
import com.againspring.api.dto.request.UpdateUserRequest;
import com.againspring.api.dto.response.OnboardingResponse;
import com.againspring.api.dto.response.SessionHistoryResponse;
import com.againspring.api.dto.response.UserResponse;
import com.againspring.domain.Session;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.UserRepository;
import com.againspring.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * User API controller.
 * Handles user profile, updates, onboarding, and session history.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "User", description = "User profile and onboarding endpoints")
public class UserController {

    private final UserService userService;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;

    @GetMapping("/me")
    @Operation(summary = "Get user profile")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<UserResponse> getUserProfile(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userService.getUserProfile(userDetails.getUsername()));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update user profile")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = UserResponse.class)))
    public ResponseEntity<UserResponse> updateUserProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUserProfile(userDetails.getUsername(), request));
    }

    @PostMapping("/me/password")
    @Operation(summary = "Change password (current → new). For temp password first change, currentPassword is the temp.")
    @ApiResponse(responseCode = "200", description = "Password changed")
    @ApiResponse(responseCode = "401", description = "Current password mismatch")
    public ResponseEntity<UserResponse> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @jakarta.validation.Valid @RequestBody com.againspring.api.dto.request.ChangePasswordRequest request) {
        return ResponseEntity.ok(userService.changePassword(userDetails.getUsername(), request));
    }

    @DeleteMapping("/me")
    @Operation(summary = "Delete user account (anonymize PII)")
    @ApiResponse(responseCode = "204", description = "Account anonymized")
    @ApiResponse(responseCode = "401", description = "Invalid password")
    public ResponseEntity<Void> deleteUserAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody(required = false) DeleteAccountRequest request) {
        userService.deleteUserAccount(userDetails.getUsername(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/tutorial/complete")
    @Operation(summary = "Mark tutorial as completed (V24)")
    @ApiResponse(responseCode = "204", description = "Tutorial marked complete")
    @SecurityRequirement(name = "bearer-jwt")
    public ResponseEntity<Void> completeTutorial(
            @AuthenticationPrincipal UserDetails userDetails) {
        userService.completeTutorial(userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/onboarding")
    @Operation(summary = "Complete onboarding")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = OnboardingResponse.class)))
    public ResponseEntity<OnboardingResponse> completeOnboarding(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody OnboardingRequest request) {
        return ResponseEntity.ok(userService.completeOnboarding(userDetails.getUsername(), request));
    }

    /**
     * 내 세션 목록 조회 (완료 + 진행 중 모두 포함, 최신순).
     * GET /api/users/me/history
     */
    @GetMapping("/me/history")
    @Operation(summary = "Get my session history", description = "Returns all sessions (completed and active) for the current user")
    @ApiResponse(responseCode = "200", description = "History retrieved")
    public ResponseEntity<List<SessionHistoryResponse>> getMyHistory(
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userDetails.getUsername();
        List<Session> sessions = sessionRepository
                .findByCreatedByUserIdOrInviteeUserIdOrderByCreatedAtDesc(userId, userId);

        List<SessionHistoryResponse> items = sessions.stream()
                .map(s -> {
                    // 상대방 닉네임: 내가 A면 B(초대 수신자), 내가 B면 A(초대 발신자)
                    String partnerNickname;
                    if (userId.equals(s.getCreatedByUserId())) {
                        // 내가 A — 상대는 B
                        if (s.getInviteeGuestName() != null) {
                            partnerNickname = s.getInviteeGuestName();
                        } else if (s.getInviteeUserId() != null) {
                            partnerNickname = userRepository
                                    .findByIdAndDeletedAtIsNull(s.getInviteeUserId())
                                    .map(u -> u.getNickname())
                                    .orElse("상대방");
                        } else {
                            partnerNickname = null;
                        }
                    } else {
                        // 내가 B — 상대는 A(creator)
                        partnerNickname = userRepository
                                .findByIdAndDeletedAtIsNull(s.getCreatedByUserId())
                                .map(u -> u.getNickname())
                                .orElse("상대방");
                    }

                    Session.Category cat = s.getCategory();
                    return SessionHistoryResponse.builder()
                            .id(s.getId())
                            .status(s.getStatus() != null ? s.getStatus().getValue() : "unknown")
                            .relationType(s.getRelationType() != null ? s.getRelationType().getValue() : null)
                            .conflictType(s.getConflictType() != null ? s.getConflictType().getValue() : null)
                            .partnerNickname(partnerNickname)
                            .soloMode(Boolean.TRUE.equals(s.getSoloMode()))
                            .completedAt(s.getCompletedAt())
                            .createdAt(s.getCreatedAt())
                            .majorCategoryId(cat != null ? cat.majorId : null)
                            .middleCategoryId(cat != null ? cat.middleId : null)
                            .minorCategoryId(cat != null ? cat.minorId : null)
                            .customCategoryText(cat != null ? cat.customText : null)
                            .reportId(s.getReportId())
                            .build();
                })
                .toList();

        return ResponseEntity.ok(items);
    }
}
