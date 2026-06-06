package com.againspring.aiuser.orchestrator.seed;

/**
 * AI 유저(봇) 식별에 사용되는 단일 SQL 술어 상수 모음.
 *
 * <p>봇 식별의 유일한 기준: {@code users.synthetic = 1} (backend V59에서 추가된 컬럼).
 * 기존의 {@code email LIKE 'ai-user%@againspring.com'} 방식은 도메인 불일치로 0행만 매칭되어 폐기.
 *
 * <p>모든 봇 식별 SQL은 반드시 이 클래스의 상수를 사용할 것.
 */
public final class AiUserIdentity {

    private AiUserIdentity() {}

    /** WHERE 절: 봇 계정 (synthetic=1) */
    public static final String SYNTHETIC_PREDICATE = "synthetic = 1";

    /** WHERE 절: 실유저 계정 (synthetic=0 또는 NULL — V59 이전 row 포함) */
    public static final String REAL_USER_PREDICATE = "(synthetic = 0 OR synthetic IS NULL)";

    /**
     * {@code NOT IN} 서브쿼리: 실유저 저자만 포함 (봇 제외).
     * InteractionScanner 등에서 봇 댓글을 스캔 대상에서 제외할 때 사용.
     * {@code OR synthetic IS NULL} 포함 — V59 마이그레이션 이전에 생성된 실유저 계정 보호.
     */
    public static final String REAL_USER_AUTHOR_CONDITION =
        "(synthetic = 0 OR synthetic IS NULL)";
}
