# backend/scripts/test-automation/scenarios/cancellation/sc24_duo_isolation.py

SCENARIO_SC24 = {
    "id": "SC24",
    "title": "Duo 양쪽 응답 격리 (A 메시지 B에게 노출 안 함)",
    "category": "cancellation",
    "is_duo": True,
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼", "subCategory": "부부"},
    "messages_by_persona": {
        "test1@again.com": [
            {"action": "invite_partner", "delay_before": 0},
            {"action": "send", "content": "남편이 자꾸 친구들만 만나러 가요", "delay_before": 0.5},
            {"action": "wait", "duration": 20},
        ],
        "test2@again.com": [
            {"action": "join_via_invite_token", "delay_before": 1.0},
            {"action": "send", "content": "남편 일정 때문에 외출이 많아요", "delay_before": 0.5},
            {"action": "wait", "duration": 20},
        ],
    },
    "verification_rules": [
        # A와 B가 거의 동시에 보내므로 취소 메커니즘에 의해 B에게 최종 응답
        {"type": "mediator_response_count", "expected_min": 1},
        {"type": "response_to_user_b"},
    ]
}
