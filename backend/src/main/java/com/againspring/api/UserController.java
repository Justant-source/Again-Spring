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



}
