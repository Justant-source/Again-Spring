# backend/scripts/test-automation/scenarios/cancellation/sc13_two_messages_cancel.py

SCENARIO_SC13 = {
    "id": "SC13",
    "title": "연속 메시지 취소 검증 (1초 간격 2개)",
    "category": "cancellation",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼", "subCategory": "시댁·처가"},
    "messages_by_persona": {
        "test1@again.com": [
            {"action": "send", "content": "어제 시댁에 갔는데", "delay_before": 0},
            {"action": "send", "content": "남편이 또 이상하게 행동했어", "delay_before": 1.0},
            {"action": "wait", "duration": 15},
        ],
        "test2@again.com": [
            {"action": "send", "content": "시댁 갔다.", "delay_before": 0},
            {"action": "send", "content": "남편 또 이상함.", "delay_before": 1.0},
            {"action": "wait", "duration": 15},
        ],
        "test10@again.com": [
            {"action": "send",
             "content": "어제 처가에 가서 장모님이랑 대화하다가 또 결혼생활 얘기로 빠졌는데요.",
             "delay_before": 0},
            {"action": "send",
             "content": "그 와중에 아내가 옆에서 한숨만 쉬더라고요. 그게 진짜 답답했어요.",
             "delay_before": 1.0},
            {"action": "wait", "duration": 15},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected": 1,
         "comment": "두 메시지에 대해 통합 응답 1개"},
        {
            "type": "response_contains_context_from_both",
            "keywords_from_msg1": ["시댁", "처가", "장모"],
            "keywords_from_msg2": ["남편", "아내", "한숨", "행동"],
        },
        {"type": "cancellation_log_present"},
    ]
}
