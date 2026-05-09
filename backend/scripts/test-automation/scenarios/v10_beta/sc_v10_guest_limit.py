# SC-V10-GUEST-LIMIT: 게스트 3턴 → 4턴 차단 → 회원가입 흐름
# 검증: 게스트 4번째 메시지는 402 GUEST_LIMIT_REACHED

SCENARIO_SC_V10_GUEST_LIMIT = {
    "id": "SC-V10-GUEST-LIMIT",
    "title": "게스트 3턴 한도 → 4턴 차단",
    "category": "v10_beta",
    "auth_type": "guest",
    "relation_type": "friend",
    "category_data": {"mainCategory": "친구·지인"},
    "messages_by_persona": {
        "guest": [
            {"action": "send", "content": "첫 번째 메시지입니다", "delay_before": 0},
            {"action": "wait", "duration": 5},
            {"action": "send", "content": "두 번째 메시지입니다", "delay_before": 0},
            {"action": "wait", "duration": 5},
            {"action": "send", "content": "세 번째 메시지입니다", "delay_before": 0},
            {"action": "wait", "duration": 5},
            # 4번째 → 402 GUEST_LIMIT_REACHED 예상
            {"action": "send", "content": "네 번째 메시지 — 차단 예상", "delay_before": 0,
             "expect_error": {"status": 402, "code": "GUEST_LIMIT_REACHED"}},
        ],
    },
    "verification_rules": [
        {
            "type": "http_error",
            "expected_status": 402,
            "expected_code": "GUEST_LIMIT_REACHED",
            "comment": "게스트 3턴 초과 → 402 GUEST_LIMIT_REACHED",
        },
    ],
}
