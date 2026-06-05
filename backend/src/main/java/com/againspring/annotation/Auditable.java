package com.againspring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 관리자 감사 로깅 애노테이션
 * @Auditable(action="POST_UPDATE", targetId="#postId")를 메서드에 붙이면
 * AuditAspect가 자동으로 AdminAuditLog를 기록한다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * 액션명 (예: POST_UPDATE, POST_DELETE, COMMENT_BLOCK 등)
     */
    String action();

    /**
     * 대상 타입 (예: POST, COMMENT, USER 등)
     * 기본값: 빈 문자열
     */
    String targetType() default "";

    /**
     * 대상 ID (SpEL 표현식, 예: "#postId", "#id", "#userId")
     * 기본값: 빈 문자열
     */
    String targetId() default "";
}
