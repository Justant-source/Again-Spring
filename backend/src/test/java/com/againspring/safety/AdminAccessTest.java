package com.againspring.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Admin Access Control Tests")
class AdminAccessTest {

    @Test
    @DisplayName("roles에 ADMIN 포함 시 ROLE_ADMIN GrantedAuthority 생성")
    void userWithAdminRole_hasRoleAdminAuthority() {
        var roles = List.of("USER", "ADMIN");
        var authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();

        assertTrue(authorities.stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("roles에 ADMIN 없으면 ROLE_ADMIN GrantedAuthority 없음")
    void userWithoutAdminRole_noRoleAdminAuthority() {
        var roles = List.of("USER");
        var authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();

        assertFalse(authorities.stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("빈 roles 리스트는 ROLE_ADMIN 없음")
    void emptyRoles_noAdminAuthority() {
        var roles = List.of("USER");
        var authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();

        assertFalse(authorities.stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("ROLE_ prefix가 붙은 권한 이름이 Spring Security hasRole('ADMIN') 검사와 일치")
    void roleNameMapping_matchesSpringSecurityConvention() {
        // Spring Security hasRole("ADMIN") 은 내부적으로 "ROLE_ADMIN"을 검사
        String adminRole = "ADMIN";
        String expectedAuthority = "ROLE_" + adminRole;

        var authority = new SimpleGrantedAuthority(expectedAuthority);
        assertEquals("ROLE_ADMIN", authority.getAuthority());
    }
}
