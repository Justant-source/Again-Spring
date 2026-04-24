package com.againspring.api;

import com.againspring.api.dto.request.GuestRequest;
import com.againspring.api.dto.request.LoginRequest;
import com.againspring.api.dto.request.SignupRequest;
import com.againspring.api.dto.response.AuthResponse;
import com.againspring.service.AuthService;
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
     * Logout (stateless, no-op).
     *
     * @return 204 No Content
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Logout user (stateless — no-op for JWT)")
    @ApiResponse(responseCode = "204", description = "Logout successful")
    public ResponseEntity<Void> logout() {
        // Stateless JWT logout — token is simply discarded by client
        // TODO Phase 7/8: Implement JWT blacklist for true logout if needed
        return ResponseEntity.noContent().build();
    }
}
