# backend/scripts/test-automation/scenarios/cancellation/sc23_context_integration.py

SCENARIO_SC23 = {
    "id": "SC23",
    "title": "취소 후 통합된 컨텍스트 응답",
    "category": "cancellation",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼", "subCategory": "부부갈등"},
    "messages_by_persona": {
        "test1@again.com": [
            {"action": "send", "content": "점심 문제로 싸웠어요", "delay_before": 0},
            {"action": "send", "content": "그리고 저녁 문제도 있었어요", "delay_before": 0.5},
            {"action": "wait", "duration": 15},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected": 1,
         "comment": "두 메시지 -> 통합 응답 1개"},
        {
            "type": "response_contains_context_from_both",
            "keywords_from_msg1": ["점심"],
            "keywords_from_msg2": ["저녁"],
        },
    ]
}
