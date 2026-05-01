# backend/scripts/test-automation/scenarios/validation/sc_valid_limit.py
# 검증: 세션 4번째 생성 → 429 응답 (MAX_ACTIVE_SESSIONS=3 검증)
# 주의: --reset 플래그로 test1의 기존 세션을 모두 삭제한 뒤 실행해야 함

SCENARIO_SC_VALID_LIMIT = {
    "id": "SC-VALID-LIMIT",
    "title": "세션 한도 초과 — 4번째 생성 시 429",
    "category": "validation",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼"},
    "messages_by_persona": {
        "test1@again.com": [
            # run_scenario()가 세션 1개 자동 생성 → 이후 2개 더 생성 (한도 = 3)
            {"action": "create_session",
             "relation_type": "couple",
             "category_data": {"mainCategory": "가족·결혼"},
             "delay_before": 0},
            {"action": "create_session",
             "relation_type": "couple",
             "category_data": {"mainCategory": "가족·결혼"},
             "delay_before": 0},
            # 4번째 생성 → 429 기대
            {"action": "create_session",
             "relation_type": "couple",
             "category_data": {"mainCategory": "가족·결혼"},
             "delay_before": 0},
        ],
    },
    "verification_rules": [
        {
            "type": "session_limit_429",
            "comment": "4번째 세션 생성 시 429 응답 (Bug A 수정 검증)",
        },
    ],
}
