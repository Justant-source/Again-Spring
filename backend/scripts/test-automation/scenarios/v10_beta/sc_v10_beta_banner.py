# SC-V10-BETA-BANNER: 베타 배너 모든 페이지 노출 + 의견 모달 트리거
# 검증: BetaBanner 상단 고정 + "의견 보내주세요" → FeedbackModal 오픈

SCENARIO_SC_V10_BETA_BANNER = {
    "id": "SC-V10-BETA-BANNER",
    "title": "베타 배너 모든 페이지 노출",
    "category": "v10_beta",
    "type": "frontend_manual",
    "steps": [
        "1. 랜딩 / 로그인 / 회원가입 / 채팅 / 결과 페이지 진입",
        "2. 각 페이지 상단에 '베타 서비스 — 경험을 개선하고 있어요' 배너 존재 확인",
        "3. 배너 내 '의견 보내주세요' 버튼 클릭 → FeedbackModal 오픈 확인",
        "4. FeedbackModal: 카테고리 선택 + 10자 이상 내용 입력 → 제출 → 성공 토스트",
        "5. 배너가 스크롤 시에도 상단 고정(fixed)임을 확인",
    ],
    "verification_rules": [
        {
            "type": "ui_element_present",
            "selector": "[data-testid='beta-banner']",
            "comment": "BetaBanner가 모든 페이지에 존재",
        },
        {
            "type": "modal_open_on_click",
            "trigger": "의견 보내주세요",
            "modal": "FeedbackModal",
            "comment": "배너 버튼 클릭 시 FeedbackModal 오픈",
        },
    ],
}
