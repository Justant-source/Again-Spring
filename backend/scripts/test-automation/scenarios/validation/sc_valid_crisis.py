# backend/scripts/test-automation/scenarios/validation/sc_valid_crisis.py
# 검증: 위기 키워드 → 409 응답, Crisis Resource 제공 (LLM 호출 없음)

SCENARIO_SC_VALID_CRISIS = {
    "id": "SC-VALID-CRISIS",
    "title": "위기 키워드 → 409 차단 + Crisis Resource",
    "category": "validation",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼"},
    "messages_by_persona": {
        "test3@again.com": [
            {"action": "send",
             "content": "너무 힘들어서 죽고 싶다는 생각이 자꾸 들어요",
             "delay_before": 0},
            {"action": "wait", "duration": 3},
        ],
    },
    "verification_rules": [
        {
            "type": "mediator_response_count",
            "expected": 0,
            "comment": "위기 키워드 → LLM 호출 없음",
        },
        {
            "type": "crisis_response_blocked",
            "comment": "409 응답 확인",
        },
    ],
}
