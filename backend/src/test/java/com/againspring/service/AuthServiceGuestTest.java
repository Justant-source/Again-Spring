package com.againspring.service;

import com.againspring.api.dto.request.GuestRequest;
import com.againspring.api.dto.response.AuthResponse;
import com.againspring.domain.User;
import com.againspring.repository.UserRepository;
import com.againspring.security.JwtService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 게스트 발급 — 소프트 삭제된 행 재활성화 회귀 테스트.
 *
 * 버그: 마이그레이션/탈퇴로 soft-delete된 게스트가 같은 deviceId로 재방문하면
 * guest()가 findById로 삭제 행을 찾아 토큰만 재발급 → UserDetailsService가
 * 삭제 행을 못 찾아 모든 인증 요청 403 (투표 불가). 행을 반드시 재활성화해야 한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService.guest() — soft-delete 재활성화")
class AuthServiceGuestTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtService jwtService;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private AdminRoleAssigner adminRoleAssigner;
    @Mock private com.againspring.service.acquisition.AcquisitionAttribution acquisitionAttribution;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, jwtService, passwordEncoder,
                emailVerificationService, adminRoleAssigner, acquisitionAttribution);
        lenient().when(jwtService.generateGuestToken(anyString())).thenReturn("guest.jwt.token");
    }

    @Test
    @DisplayName("소프트 삭제된 게스트가 같은 deviceId로 재방문 → 행 재활성화(deletedAt=null) 후 저장")
    void guest_reactivatesSoftDeletedRow() {
        // given: 같은 deviceId → 같은 guestId의 soft-deleted 행
        String deviceId = "c5ee9cbde2284d-device-suffix";
        User softDeleted = User.builder()
                .id("d-c5ee9cbde2284d")
                .nickname("게스트 4179")
                .isGuest(true)
                .roles(new ArrayList<>(List.of("USER")))
                .build();
        softDeleted.setDeletedAt(Instant.now()); // 삭제 상태
        when(userRepository.findById("d-c5ee9cbde2284d")).thenReturn(Optional.of(softDeleted));
        when(userRepository.existsByNicknameAndDeletedAtIsNull(anyString())).thenReturn(false);

        GuestRequest req = new GuestRequest();
        req.setDeviceId(deviceId);

        // when
        AuthResponse resp = authService.guest(req);

        // then: 같은 행을 deletedAt=null 로 되살려 저장
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("d-c5ee9cbde2284d", saved.getId());
        assertNull(saved.getDeletedAt(), "재활성화되어 deletedAt이 null이어야 한다");
        assertTrue(saved.isGuest());
        assertNotNull(resp.getToken().getAccessToken());
        // 닉네임은 새로 부여됨 (stale '게스트 4179' 가 아님)
        assertNotEquals("게스트 4179", saved.getNickname());
    }

    @Test
    @DisplayName("활성 게스트 재방문 → 닉네임 유지, 저장하지 않음")
    void guest_activeReturning_keepsNickname() {
        // deviceId "abcdefghijklmn"(14자) → guestId "d-abcdefghijklmn"
        User active = User.builder()
                .id("d-abcdefghijklmn")
                .nickname("활기찬 토끼")
                .isGuest(true)
                .roles(new ArrayList<>(List.of("USER")))
                .build(); // deletedAt = null
        when(userRepository.findById("d-abcdefghijklmn")).thenReturn(Optional.of(active));

        GuestRequest req = new GuestRequest();
        req.setDeviceId("abcdefghijklmn");

        AuthResponse resp = authService.guest(req);

        assertEquals("활기찬 토끼", resp.getUser().getNickname());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("신규 게스트 → 새 행 생성")
    void guest_brandNew_createsRow() {
        when(userRepository.findById(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByNicknameAndDeletedAtIsNull(anyString())).thenReturn(false);

        GuestRequest req = new GuestRequest();
        req.setDeviceId("brandnew1234567-device");

        AuthResponse resp = authService.guest(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertTrue(captor.getValue().isGuest());
        assertNull(captor.getValue().getDeletedAt());
        assertTrue(resp.getUser().isGuest());
    }
}
