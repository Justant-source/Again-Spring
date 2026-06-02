package com.againspring.api;

import com.againspring.api.dto.request.DeleteAccountRequest;
import com.againspring.api.dto.request.OnboardingRequest;
import com.againspring.api.dto.request.UpdateUserRequest;
import com.againspring.api.dto.response.OnboardingResponse;
import com.againspring.api.dto.response.UserResponse;
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
    // private final SessionRepository sessionRepository; (removed)
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

    /**
     * V47: 중재자 성향 기본값 설정 (회원 프로필 저장).
     * 세션 생성 시 mediatorStyleX/Y 미입력이면 이 값으로 프리필.
     */
    @PatchMapping("/me/mediator-style")
    @Operation(summary = "Set default mediator style", description = "회원 프로필에 중재자 성향 기본값(X/Y 0~100) 저장")
    @ApiResponse(responseCode = "204", description = "Saved")
    @ApiResponse(responseCode = "400", description = "Invalid range")
    public ResponseEntity<Void> updateMediatorStyle(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody java.util.Map<String, Integer> body) {
        Integer x = body.get("mediatorStyleX");
        Integer y = body.get("mediatorStyleY");
        if ((x != null && (x < 0 || x > 100)) || (y != null && (y < 0 || y > 100))) {
            return ResponseEntity.badRequest().build();
        }
        userService.updateMediatorStyle(userDetails.getUsername(), x, y);
        return ResponseEntity.noContent().build();
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

    /** @deprecated 세션 히스토리 — V18에서 제거됨 (광장형으로 전환). 빈 응답 반환. */
    @GetMapping("/me/history")
    @Operation(summary = "내 세션 히스토리 (V18 이후 비사용)")
    @ApiResponse(responseCode = "200", description = "빈 목록 반환")
    public ResponseEntity<List<Object>> getMyHistory(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(java.util.Collections.emptyList());
    }
}
