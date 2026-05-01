# backend/scripts/test-automation/scenarios/mechanism/sc_cancel_burst.py
# 취소 메커니즘 검증: 0.5초 간격 3개 메시지 → 2회 취소 예상, 통합 응답 1개

SCENARIO_SC_CANCEL_BURST = {
    "id": "SC-CANCEL-BURST",
    "title": "빠른 연속 메시지 (0.5초 간격 3개) — 2회 취소 예상",
    "category": "mechanism",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼", "subCategory": "부부"},
    "messages_by_persona": {
        "test1@again.com": [
            {"action": "send", "content": "남편이랑 또 싸웠어요", "delay_before": 0},
            {"action": "send", "content": "이번엔 진짜 너무했어요", "delay_before": 0.5},
            {"action": "send", "content": "어제 친구 결혼식에서 시댁 식구들 만났는데", "delay_before": 0.5},
            {"action": "wait", "duration": 20},
        ],
    },
    "verification_rules": [
        {
            "type": "mediator_response_count",
            "expected": 1,
            "comment": "3개 폭주 → 취소 2회, 통합 응답 1개",
        },
        {
            "type": "response_contains_context_from_both",
            "keywords_from_msg1": ["남편", "싸웠"],
            "keywords_from_msg2": ["결혼식", "시댁"],
        },
        {"type": "cancellation_log_present"},
    ],
}
