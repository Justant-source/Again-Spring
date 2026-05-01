# backend/scripts/test-automation/scenarios/flow/sc_flow_finalize.py
# 정상 흐름: A 종료 요청 → B 합의 → 세션 COMPLETED + 리포트 생성

SCENARIO_SC_FLOW_FINALIZE = {
    "id": "SC-FLOW-FINALIZE",
    "title": "Duo 종료 합의 — COMPLETED + 리포트",
    "category": "flow",
    "is_duo": True,
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼", "subCategory": "부부"},
    "messages_by_persona": {
        "test1@again.com": [
            {"action": "invite_partner", "delay_before": 0},
            {"action": "send",
             "content": "이번 대화를 통해 제 마음을 정리할 수 있었어요",
             "delay_before": 0.5},
            {"action": "wait", "duration": 8},
            # A가 먼저 종료 요청 (B가 agree_finalize를 하기 전)
            {"action": "finalize", "delay_before": 0},
            # B가 agree_finalize할 충분한 시간 대기 후 상태 확인
            {"action": "assert_status", "expected": "COMPLETED", "delay_before": 12},
        ],
        "test2@again.com": [
            {"action": "join_via_invite_token", "delay_before": 1.0},
            {"action": "send",
             "content": "저도 상대방 입장을 이해하게 됐어요",
             "delay_before": 0.5},
            # A의 finalize 요청(~8.5s 후)보다 늦게 agree_finalize 실행
            {"action": "agree_finalize", "delay_before": 10},
        ],
    },
    "verification_rules": [
        {
            "type": "session_status",
            "expected": "COMPLETED",
            "comment": "A finalize → B agree → 세션 COMPLETED",
        },
    ],
}
