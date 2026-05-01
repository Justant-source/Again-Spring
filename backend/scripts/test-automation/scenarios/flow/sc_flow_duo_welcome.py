# backend/scripts/test-automation/scenarios/flow/sc_flow_duo_welcome.py
# 정상 흐름: A 세션 생성 → B join → A·B 모두 환영 메시지 수신 검증

SCENARIO_SC_FLOW_DUO_WELCOME = {
    "id": "SC-FLOW-DUO-WELCOME",
    "title": "Duo B 진입 환영 메시지 수신 검증",
    "category": "flow",
    "is_duo": True,
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼", "subCategory": "부부"},
    "messages_by_persona": {
        "test1@again.com": [
            {"action": "invite_partner", "delay_before": 0},
            {"action": "wait", "duration": 10},
        ],
        "test2@again.com": [
            {"action": "join_via_invite_token", "delay_before": 0},
            {"action": "wait", "duration": 10},
        ],
    },
    "verification_rules": [
        {
            "type": "mediator_response_count",
            "expected_min": 1,
            "comment": "B join 후 환영 메시지 ≥1개 (welcomeMessageGenerator)",
        },
    ],
}
