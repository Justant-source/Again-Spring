# backend/scripts/test-automation/scenarios/exception/sc18_inactive_session.py

SCENARIO_SC18 = {
    "id": "SC18",
    "title": "비활성 세션 메시지 (예외 처리)",
    "category": "exception",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼"},
    "messages_by_persona": {
        "test1@again.com": [
            {"action": "send", "content": "테스트 메시지입니다", "delay_before": 0},
            {"action": "wait", "duration": 3},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected": 0,
         "comment": "비활성 세션에선 응답 없음"},
    ]
}
