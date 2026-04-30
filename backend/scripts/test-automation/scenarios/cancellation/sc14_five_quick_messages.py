# backend/scripts/test-automation/scenarios/cancellation/sc14_five_quick_messages.py

SCENARIO_SC14 = {
    "id": "SC14",
    "title": "5개 빠른 연속 메시지 (4회 취소 예상)",
    "category": "cancellation",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼", "subCategory": "시댁·처가"},
    "messages_by_persona": {
        "test10@again.com": [
            {"action": "send", "content": "남편이랑 또 싸웠어요", "delay_before": 0},
            {"action": "send", "content": "이번엔 정말 너무했어요", "delay_before": 0.5},
            {"action": "send", "content": "어제 친구 결혼식 갔는데", "delay_before": 0.5},
            {"action": "send", "content": "거기서 또 시댁 식구들 만났어요", "delay_before": 0.5},
            {"action": "send", "content": "남편이 자기 어머니 편만 들더라고요", "delay_before": 0.5},
            {"action": "wait", "duration": 15},
        ],
        "test2@again.com": [
            {"action": "send", "content": "또 싸웠다.", "delay_before": 0},
            {"action": "send", "content": "너무함.", "delay_before": 0.5},
            {"action": "send", "content": "결혼식 갔음.", "delay_before": 0.5},
            {"action": "send", "content": "시댁 만남.", "delay_before": 0.5},
            {"action": "send", "content": "어머니 편만 듦.", "delay_before": 0.5},
            {"action": "wait", "duration": 15},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected": 1,
         "comment": "5개 메시지 -> 통합 응답 1개"},
        {
            "type": "response_contains_context_from_both",
            "keywords_from_msg1": ["싸웠", "남편"],
            "keywords_from_msg2": ["시댁", "어머니"],
        },
        {"type": "cancellation_log_present"},
    ]
}
