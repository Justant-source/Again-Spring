package com.againspring.security;

import com.againspring.domain.User;
import com.againspring.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security UserDetailsService implementation.
 * Loads user by ID and maps roles to granted authorities.
 * Excludes soft-deleted users.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        // Try to load user by ID (excludes soft-deleted)
        User user = userRepository
                .findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> {
                    log.debug("User not found with ID: {}", userId);
                    return new UsernameNotFoundException("User not found: " + userId);
                });

        // Convert roles to Spring Security GrantedAuthority
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (user.getRoles() != null) {
            for (String role : user.getRoles()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
        }
        if (authorities.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }

        // Check if user is suspended
        boolean accountEnabled = !isSuspended(user);

        // Return Spring Security User
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getId())
                .password(user.getPasswordHash() != null ? user.getPasswordHash() : "")
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(!accountEnabled)  // disabled가 true면 계정 비활성화
                .build();
    }

    /**
     * 사용자가 정지 상태인지 확인
     * status='SUSPENDED'이고 suspendedUntil이 NULL이거나 미래인 경우 정지
     */
    private boolean isSuspended(User user) {
        if (!"SUSPENDED".equals(user.getStatus())) {
            return false;
        }

        // suspendedUntil이 NULL이거나 현재 시각 이후이면 정지 상태
        if (user.getSuspendedUntil() == null) {
            return true;
        }

        return user.getSuspendedUntil().isAfter(Instant.now());
    }
}
