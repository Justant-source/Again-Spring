package com.againspring.aiuser.orchestrator.domain.enums;

public enum ActionType {
    LIKE, VOTE, COMMENT, REPLY, POST, INVITE_ANSWER, VIEW,
    /** 댓글/대댓글 좋아요 — 피기백 반응 디스패치용. plan()에서 생성되지 않음(인라인 디스패치). */
    COMMENT_LIKE
}
