package com.againspring.api;

import com.againspring.api.dto.request.ForgotPasswordRequest;
import com.againspring.api.dto.request.GuestRequest;
import com.againspring.api.dto.request.LoginRequest;
import com.againspring.api.dto.request.ResetPasswordRequest;
import com.againspring.api.dto.request.SendVerificationRequest;
import com.againspring.api.dto.request.SignupRequest;
import com.againspring.api.dto.response.AuthResponse;
import com.againspring.service.AuthService;
import com.againspring.service.EmailVerificationService;
import com.againspring.service.LogoutService;
import com.againspring.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth API controller.
 * Handles signup, login, guest token issuance, and logout.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication endpoints")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final LogoutService logoutService;

    /**
     * Send email verification code.
     *
     * @param request send verification request
     * @return 200 OK
     */
    @PostMapping("/send-verification")
    @Operation(summary = "Send email verification code", description = "Send a 6-digit code to the email address")
    @ApiResponse(responseCode = "200", description = "Code sent")
    @ApiResponse(responseCode = "400", description = "Invalid email")
    public ResponseEntity<Void> sendVerification(@Valid @RequestBody SendVerificationRequest request) {
        emailVerificationService.sendCode(request.getEmail());
        return ResponseEntity.ok().build();
    }

    /**
     * User signup.
     *
     * @param request signup request
     * @return auth response with user info and token
     */
    @PostMapping("/signup")
    @Operation(summary = "User signup", description = "Register a new user account")
    @ApiResponse(responseCode = "201", description = "Signup successful",
            content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "409", description = "Email already exists")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        AuthResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * User login.
     *
     * @param request login request
     * @return auth response with user info and token
     */
    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user with email and password")
    @ApiResponse(responseCode = "200", description = "Login successful",
            content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Generate guest token (stateless).
     *
     * @param request guest request
     * @return auth response with guest user info and token
     */
    @PostMapping("/guest")
    @Operation(summary = "Guest token issuance", description = "Generate a short-lived guest token (2 hours)")
    @ApiResponse(responseCode = "200", description = "Guest token issued",
            content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    public ResponseEntity<AuthResponse> guest(@RequestBody GuestRequest request) {
        AuthResponse response = authService.guest(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Logout user by revoking JWT token.
     *
     * @param authHeader Authorization header containing Bearer token
     * @return 204 No Content
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Logout user and revoke JWT token")
    @ApiResponse(responseCode = "204", description = "Logout successful")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        logoutService.revokeToken(authHeader);
        return ResponseEntity.noContent().build();
    }

    /**
     * Request password reset.
     *
     * @param request forgot password request
     * @return 200 OK (always, to prevent account enumeration)
     */
    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset", description = "Send password reset link to email")
    @ApiResponse(responseCode = "200", description = "Reset email sent (or account not found)")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.getEmail());
        return ResponseEntity.ok().build();
    }

    /**
     * Reset password with token.
     *
     * @param request reset password request
     * @return 200 OK
     */
    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Reset password using reset token")
    @ApiResponse(responseCode = "200", description = "Password reset successful")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "400", description = "Invalid or expired token")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }
}
