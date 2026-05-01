# backend/scripts/test-automation/scenarios/mechanism/sc_cancel_fast.py
# 취소 메커니즘 검증: 1초 간격 2개 메시지 → 통합 응답 1개

SCENARIO_SC_CANCEL_FAST = {
    "id": "SC-CANCEL-FAST",
    "title": "연속 메시지 취소 (1초 간격 2개) — 통합 응답 1개",
    "category": "mechanism",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼", "subCategory": "시댁·처가"},
    "messages_by_persona": {
        "test1@again.com": [
            {"action": "send", "content": "어제 시댁에 갔다가", "delay_before": 0},
            {"action": "send", "content": "남편이 또 이상하게 행동했어요", "delay_before": 1.0},
            {"action": "wait", "duration": 20},
        ],
    },
    "verification_rules": [
        {
            "type": "mediator_response_count",
            "expected": 1,
            "comment": "1초 간격 2개 → LLM 취소 후 통합 응답 1개",
        },
        {
            "type": "response_contains_context_from_both",
            "keywords_from_msg1": ["시댁", "어제"],
            "keywords_from_msg2": ["남편", "행동"],
        },
        {"type": "cancellation_log_present"},
    ],
}
