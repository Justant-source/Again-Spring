package com.againspring.api;

import com.againspring.api.dto.request.OnboardingRequest;
import com.againspring.api.dto.request.UpdateUserRequest;
import com.againspring.api.dto.response.OnboardingResponse;
import com.againspring.api.dto.response.UserResponse;
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

/**
 * User API controller.
 * Handles user profile, updates, and onboarding.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "User", description = "User profile and onboarding endpoints")
public class UserController {

    private final UserService userService;

    /**
     * Get current user profile.
     *
     * @param userDetails authenticated user
     * @return user profile
     */
    @GetMapping("/me")
    @Operation(summary = "Get user profile", description = "Retrieve current user profile")
    @ApiResponse(responseCode = "200", description = "User profile retrieved",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<UserResponse> getUserProfile(@AuthenticationPrincipal UserDetails userDetails) {
        UserResponse response = userService.getUserProfile(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    /**
     * Update user profile (PATCH).
     *
     * @param userDetails authenticated user
     * @param request update request
     * @return updated user profile
     */
    @PatchMapping("/me")
    @Operation(summary = "Update user profile", description = "Update nickname or communication style")
    @ApiResponse(responseCode = "200", description = "Profile updated",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<UserResponse> updateUserProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UpdateUserRequest request) {
        UserResponse response = userService.updateUserProfile(userDetails.getUsername(), request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete user account (soft delete).
     *
     * @param userDetails authenticated user
     * @return 204 No Content
     */
    @DeleteMapping("/me")
    @Operation(summary = "Delete user account", description = "Soft-delete user account")
    @ApiResponse(responseCode = "204", description = "Account deleted")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<Void> deleteUserAccount(@AuthenticationPrincipal UserDetails userDetails) {
        userService.deleteUserAccount(userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    /**
     * Complete onboarding with 10-question answers.
     *
     * @param userDetails authenticated user
     * @param request onboarding request
     * @return onboarding response with communication style
     */
    @PostMapping("/me/onboarding")
    @Operation(summary = "Complete onboarding", description = "Save 10-question onboarding answers and compute communication style")
    @ApiResponse(responseCode = "200", description = "Onboarding completed",
            content = @Content(schema = @Schema(implementation = OnboardingResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<OnboardingResponse> completeOnboarding(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody OnboardingRequest request) {
        OnboardingResponse response = userService.completeOnboarding(userDetails.getUsername(), request);
        return ResponseEntity.ok(response);
    }
}
