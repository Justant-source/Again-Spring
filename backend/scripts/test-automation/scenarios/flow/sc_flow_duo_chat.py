# backend/scripts/test-automation/scenarios/flow/sc_flow_duo_chat.py
# 정상 흐름: Duo 양방향 대화 — A·B 각 2턴, 양방향 mediator 응답 검증

SCENARIO_SC_FLOW_DUO_CHAT = {
    "id": "SC-FLOW-DUO-CHAT",
    "title": "Duo 양방향 정상 대화 (각 2턴)",
    "category": "flow",
    "is_duo": True,
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼", "subCategory": "부부"},
    "messages_by_persona": {
        "test1@again.com": [
            {"action": "invite_partner", "delay_before": 0},
            {"action": "send",
             "content": "남편이 제 말을 들으려 하질 않아요. 말하면 방으로 들어가버려요",
             "delay_before": 0.5},
            {"action": "wait", "duration": 20},
            {"action": "send",
             "content": "대화가 안 되니까 저도 지쳐서 포기하게 돼요",
             "delay_before": 0},
            {"action": "wait", "duration": 20},
        ],
        "test2@again.com": [
            {"action": "join_via_invite_token", "delay_before": 1.0},
            {"action": "send",
             "content": "아내가 말을 꺼내면 항상 감정이 먼저 폭발해서 제가 자꾸 피하게 돼요",
             "delay_before": 1.0},
            {"action": "wait", "duration": 20},
            {"action": "send",
             "content": "저도 대화하고 싶은데 방법을 모르겠어요",
             "delay_before": 0},
            {"action": "wait", "duration": 20},
        ],
    },
    "verification_rules": [
        {"type": "mediator_response_count", "expected_min": 2,
         "comment": "A·B 각 2턴 → mediator 응답 ≥2개"},
        {"type": "no_avoidance_pattern"},
    ],
}
