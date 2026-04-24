package com.againspring.service;

import com.againspring.api.dto.request.GuestRequest;
import com.againspring.api.dto.request.LoginRequest;
import com.againspring.api.dto.request.SignupRequest;
import com.againspring.api.dto.response.AuthResponse;
import com.againspring.common.exception.BusinessException;
import com.againspring.domain.User;
import com.againspring.repository.UserRepository;
import com.againspring.security.JwtService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Authentication service.
 * Handles user signup, login, and guest token generation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    /**
     * User signup.
     *
     * @param request signup request
     * @return auth response with token
     * @throws BusinessException if email already exists
     */
    public AuthResponse signup(SignupRequest request) {
        // Validate email uniqueness
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BusinessException("USER_ALREADY_EXISTS", "Email already registered");
        }

        // Create new user
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .isGuest(false)
                .roles(new ArrayList<>())
                .build();

        user.getRoles().add("USER");

        User savedUser = userRepository.save(user);
        log.info("User signup successful: {}", savedUser.getId());

        // Generate JWT
        String accessToken = jwtService.generateAccessToken(savedUser.getId(), savedUser.getEmail());

        return AuthResponse.builder()
                .user(AuthResponse.UserInfo.builder()
                        .id(savedUser.getId())
                        .email(savedUser.getEmail())
                        .nickname(savedUser.getNickname())
                        .isGuest(false)
                        .createdAt(savedUser.getCreatedAt())
                        .build())
                .token(AuthResponse.TokenInfo.builder()
                        .accessToken(accessToken)
                        .expiresIn(86400) // 24 hours in seconds
                        .build())
                .build();
    }

    /**
     * User login.
     *
     * @param request login request
     * @return auth response with token
     * @throws BusinessException if credentials invalid
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(
                        "AUTH_INVALID_CREDENTIALS", "Invalid email or password"));

        // Check soft-delete
        if (user.getDeletedAt() != null) {
            throw new BusinessException(
                    "USER_ALREADY_DELETED", "User account has been deleted");
        }

        // Validate password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(
                    "AUTH_INVALID_CREDENTIALS", "Invalid email or password");
        }

        log.info("User login successful: {}", user.getId());

        // Generate JWT
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());

        return AuthResponse.builder()
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .nickname(user.getNickname())
                        .isGuest(false)
                        .createdAt(user.getCreatedAt())
                        .build())
                .token(AuthResponse.TokenInfo.builder()
                        .accessToken(accessToken)
                        .expiresIn(86400)
                        .build())
                .build();
    }

    /**
     * Generate guest token (stateless, no DB write).
     *
     * @param request guest request
     * @return auth response with guest token (2h expiration)
     */
    public AuthResponse guest(GuestRequest request) {
        String guestId = "gst_" + UUID.randomUUID().toString().substring(0, 8);

        String accessToken = jwtService.generateGuestToken(guestId);

        log.info("Guest token issued: {}", guestId);

        return AuthResponse.builder()
                .user(AuthResponse.UserInfo.builder()
                        .id(guestId)
                        .nickname("게스트")
                        .isGuest(true)
                        .build())
                .token(AuthResponse.TokenInfo.builder()
                        .accessToken(accessToken)
                        .expiresIn(7200) // 2 hours in seconds
                        .build())
                .build();
    }
}
