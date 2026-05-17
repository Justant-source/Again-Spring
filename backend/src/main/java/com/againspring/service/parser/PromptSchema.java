package com.againspring.service.parser;

/**
 * P1-2: 프롬프트 ↔ 파서 단일 계약.
 *
 * shared/docs/prompts/chat/_response_instructions.md 의 구조 태그와
 * JSON 필드명을 이곳에 집중 정의한다.
 *
 * 태그명/필드명 변경 시 이 파일 한 곳만 수정하면
 * ChatTurnMetaParser 가 자동으로 따라온다.
 */
public final class PromptSchema {

    private PromptSchema() {}

    // ── XML 래퍼 태그 ─────────────────────────────────────────────────────────

    /** LLM 응답 전체를 감싸는 선택적 래퍼. 있으면 내부 텍스트만 추출. */
    public static final String TAG_MEDIATOR_RESPONSE = "mediator_response";

    /** 분석 메타 블록 (horsemen·nvc·user_state·issue_delta·question_queue_delta). */
    public static final String TAG_TURN_META = "turn_meta";

    /** AI가 turn_meta 대신 독립 최상위 태그로 출력하는 경우를 위한 fallback. */
    public static final String TAG_ISSUE_DELTA = "issue_delta";

    /** AI가 turn_meta 대신 독립 최상위 태그로 출력하는 경우를 위한 fallback. */
    public static final String TAG_QUEUE_DELTA = "question_queue_delta";

    // ── turn_meta 내부 JSON 최상위 필드 ──────────────────────────────────────

    public static final String FIELD_HORSEMEN         = "horsemen";
    public static final String FIELD_NVC_COMPLETION   = "nvc_completion";
    public static final String FIELD_USER_STATE       = "user_state";
    public static final String FIELD_ISSUE_DELTA      = "issue_delta";
    public static final String FIELD_QUEUE_DELTA      = "question_queue_delta";

    // ── horsemen 서브 필드 ────────────────────────────────────────────────────

    public static final String H_CRITICISM    = "criticism";
    public static final String H_CONTEMPT     = "contempt";
    public static final String H_DEFENSIVENESS = "defensiveness";
    public static final String H_STONEWALLING  = "stonewalling";

    // ── nvc_completion 서브 필드 ──────────────────────────────────────────────

    public static final String NVC_OBSERVATION = "observation";
    public static final String NVC_FEELING     = "feeling";
    public static final String NVC_NEED        = "need";
    public static final String NVC_REQUEST     = "request";

    // ── user_state 서브 필드 ──────────────────────────────────────────────────

    public static final String US_STATE       = "state";
    public static final String US_EVIDENCE    = "evidence";
    public static final String US_CONFIDENCE  = "confidence";
    public static final String US_DERIVED_FROM = "derived_from";

    // ── issue_delta 서브 필드 ─────────────────────────────────────────────────

    public static final String ID_HEADLINE          = "headline";
    public static final String ID_FACTS_ADDED       = "facts_added";
    public static final String ID_FACTS_CONFIRMED   = "facts_confirmed";
    public static final String ID_NEEDS_ADDED       = "needs_added";
    public static final String ID_THREADS_ADDED     = "threads_added";
    public static final String ID_THREADS_RESOLVED  = "threads_resolved";

    // ── question_queue_delta 서브 필드 ────────────────────────────────────────

    public static final String QD_ASKED            = "asked";
    public static final String QD_NEW              = "new";
    public static final String QD_INTENT           = "intent";
    public static final String QD_TARGET           = "target";
    public static final String QD_TEXT             = "text";
    public static final String QD_HOOK_FROM_ISSUE  = "hookFromIssue";
    public static final String QD_ANTIDOTE_FOR     = "antidoteFor";

    // ── 방어적 스트립 대상 태그 목록 (파서 마지막 패스용) ─────────────────────

    /** UNKNOWN_STRUCTURED_BLOCK 정규식에서 사용하는 태그 목록 (| 구분). */
    public static final String STRIP_TAG_ALTERNATION =
        TAG_TURN_META + "|" + TAG_ISSUE_DELTA + "|" + TAG_QUEUE_DELTA +
        "|user_state|horsemen|nvc_completion";
}
