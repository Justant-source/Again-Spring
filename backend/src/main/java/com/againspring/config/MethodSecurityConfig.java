package com.againspring.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * 메서드 수준 보안 설정
 * @PreAuthorize, @PostAuthorize, @Secured 등의 메서드 보안 애노테이션 활성화
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class MethodSecurityConfig {
}
