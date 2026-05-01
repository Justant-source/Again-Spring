# backend/scripts/test-automation/scenarios/context/sc_ctx_duo_turns.py
# 멀티턴 Duo 3+3턴 — A·B 교차 대화 후 중재자가 양쪽 시각을 통합하는지 검증

SCENARIO_SC_CTX_DUO_TURNS = {
    "id": "SC-CTX-DUO-TURNS",
    "title": "Duo 3+3턴 교차 컨텍스트 통합",
    "category": "context",
    "is_duo": True,
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼", "subCategory": "부부"},
    "messages_by_persona": {
        "test1@again.com": [
            {"action": "invite_partner", "delay_before": 0},
            # A 턴 1: 핵심 불만
            {"action": "send",
             "content": "남편이 퇴근 후에 말 한마디도 안 해요. 집에 오면 바로 소파에 누워요. 무시당하는 느낌이에요",
             "delay_before": 0.5},
            {"action": "wait", "duration": 40},
            # A 턴 2: 반박 및 자신의 고됨 설명
            {"action": "send",
             "content": "저도 하루 종일 일하고 집에 와요. 그래도 남편 밥 챙기고 집 정리해요. 그게 당연한 건 아니잖아요",
             "delay_before": 0},
            {"action": "wait", "duration": 40},
            # A 턴 3: 바라는 것 표현
            {"action": "send",
             "content": "그냥 '오늘 힘들었겠다' 한마디만 해줘도 좋겠어요. 그게 그렇게 어려운 건가요",
             "delay_before": 0},
            {"action": "wait", "duration": 40},
        ],
        "test2@again.com": [
            {"action": "join_via_invite_token", "delay_before": 1.0},
            # B 턴 1: 방어적 설명
            {"action": "send",
             "content": "집에 오면 진짜 아무 말도 하기 싫을 때가 있어요. 말하라고 하면 더 힘들어요",
             "delay_before": 1.0},
            {"action": "wait", "duration": 40},
            # B 턴 2: 상대 행동에 대한 반응
            {"action": "send",
             "content": "아내도 집 정리하는 거 알아요. 근데 제가 뭘 해도 부족하다는 말만 들어요. 그러니까 말하기가 싫어져요",
             "delay_before": 0},
            {"action": "wait", "duration": 40},
            # B 턴 3: 원하는 것 표현
            {"action": "send",
             "content": "저는 좀 조용히 쉬고 나서 대화하고 싶어요. 그게 무시하는 게 아닌데 그렇게 받아들이더라고요",
             "delay_before": 0},
            {"action": "wait", "duration": 40},
        ],
    },
    "verification_rules": [
        # 3+3턴 → 중재자 응답 ≥4개
        {"type": "mediator_response_count", "expected_min": 4},
        # 전체 응답에서 회피 패턴 없음
        {"type": "all_mediator_no_avoidance"},
        # 응답들이 반복되지 않음
        {"type": "mediator_responses_distinct", "overlap_threshold": 0.65},
        # 후반 응답이 양쪽 초반 키워드를 통합하는지 확인
        {"type": "mediator_later_response_references",
         "keywords": ["무시", "소파", "말하기", "쉬고"],
         "from_turn": 3},
    ],
}
