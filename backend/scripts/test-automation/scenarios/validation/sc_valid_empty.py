# backend/scripts/test-automation/scenarios/validation/sc_valid_empty.py
# 검증: 빈 메시지 → 400 응답, mediator 응답 0개 (@NotBlank Fix 검증)

SCENARIO_SC_VALID_EMPTY = {
    "id": "SC-VALID-EMPTY",
    "title": "빈 메시지 → 400 응답 (@NotBlank 검증)",
    "category": "validation",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼"},
    "messages_by_persona": {
        "test1@again.com": [
            {"action": "send", "content": "", "delay_before": 0},
            {"action": "wait", "duration": 3},
        ],
    },
    "verification_rules": [
        {
            "type": "mediator_response_count",
            "expected": 0,
            "comment": "빈 메시지는 400으로 거부됨 — LLM 호출 없음",
        },
    ],
}
