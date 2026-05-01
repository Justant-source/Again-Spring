# backend/scripts/test-automation/scenarios/context/sc_ctx_recall.py
# 멀티턴 Solo 5턴 — 초반에 언급한 구체적 키워드를 중재자가 후반에도 기억하는지 검증

SCENARIO_SC_CTX_RECALL = {
    "id": "SC-CTX-RECALL",
    "title": "Solo 5턴 초반 컨텍스트 회상 검증",
    "category": "context",
    "relation_type": "couple",
    "category_data": {"mainCategory": "가족·결혼", "subCategory": "시댁·처가"},
    "messages_by_persona": {
        "test1@again.com": [
            # 턴 1: 구체적 키워드 포함한 초기 상황 (시어머니, 비교)
            {"action": "send",
             "content": "시어머니가 첫날부터 다른 며느리와 비교해요. '저 집 며느리는 이것도 잘 하더라' 이런 말을 자주 해요",
             "delay_before": 0},
            {"action": "wait", "duration": 40},
            # 턴 2: 남편의 태도
            {"action": "send",
             "content": "남편에게 말했더니 '엄마는 원래 그래, 신경 쓰지 마'라고 해요. 편을 들어주지 않아요",
             "delay_before": 0},
            {"action": "wait", "duration": 40},
            # 턴 3: 앞으로의 걱정
            {"action": "send",
             "content": "이번 추석에 또 가야 하는데 벌써부터 무서워요. 명절이 너무 싫어졌어요",
             "delay_before": 0},
            {"action": "wait", "duration": 40},
            # 턴 4: 자기 의심
            {"action": "send",
             "content": "제가 너무 예민한 건지, 며느리로서 당연히 감수해야 하는 건지 모르겠어요",
             "delay_before": 0},
            {"action": "wait", "duration": 40},
            # 턴 5: 남편과의 관계 개선 요청 (초반 키워드 없이 다른 주제 전환 후 연결)
            {"action": "send",
             "content": "남편이 시어머니와 저 사이에서 어떻게 행동하면 좋을지 같이 생각해볼 수 있을까요?",
             "delay_before": 0},
            {"action": "wait", "duration": 40},
        ],
    },
    "verification_rules": [
        # 5턴 → 중재자 응답 ≥4개
        {"type": "mediator_response_count", "expected_min": 4},
        # 전체 응답에서 회피 패턴 없음
        {"type": "all_mediator_no_avoidance"},
        # 응답들이 반복되지 않음
        {"type": "mediator_responses_distinct", "overlap_threshold": 0.65},
        # 후반(3번째 이후) 응답이 초반에 등장한 구체적 키워드를 기억하는지
        {"type": "mediator_later_response_references",
         "keywords": ["비교", "시어머니", "추석", "명절", "며느리"],
         "from_turn": 3},
    ],
}
