# backend/scripts/test-automation/scenarios/flow/sc_flow_solo.py
# 정상 흐름: Solo 3턴 대화 — 중재자 응답 ≥2개, 회피 패턴 없음

SCENARIO_SC_FLOW_SOLO = {
    "id": "SC-FLOW-SOLO",
    "title": "Solo 정상 3턴 대화",
    "category": "flow",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼", "subCategory": "시댁·처가"},
    "messages_by_persona": {
        "test1@again.com": [
            {"action": "send",
             "content": "명절마다 시댁 부엌에서 혼자 일하는 게 너무 지쳐요",
             "delay_before": 0},
            {"action": "wait", "duration": 20},
            {"action": "send",
             "content": "남편은 거실에서 TV만 봐요. 한 번도 도와준 적이 없어요",
             "delay_before": 0},
            {"action": "wait", "duration": 20},
            {"action": "send",
             "content": "시어머니도 당연하다는 듯 시키기만 해요. 전 며느리 로봇이 아닌데",
             "delay_before": 0},
            {"action": "wait", "duration": 20},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected_min": 2},
        {"type": "no_avoidance_pattern"},
    ],
}
