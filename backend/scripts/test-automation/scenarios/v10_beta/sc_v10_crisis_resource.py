# SC-V10-CRISIS-RESOURCE: 위기 자원 모든 페이지 노출 + tel: 링크
# 검증: LegalFooter / ChatHeader 🆘 아이콘 / CrisisResourceModal 정상 동작

SCENARIO_SC_V10_CRISIS_RESOURCE = {
    "id": "SC-V10-CRISIS-RESOURCE",
    "title": "위기 자원 모든 페이지 노출 + tel: 링크",
    "category": "v10_beta",
    "type": "frontend_manual",
    "steps": [
        "1. 랜딩 / 로그인 / 회원가입 / 채팅 / 결과 페이지 진입 확인",
        "2. 각 페이지 하단 LegalFooter → '위기 상황이라면 1393' 링크 존재 확인",
        "3. 모바일 뷰에서 '위기 상황이라면 1393' 클릭 → tel:1393 다이얼 팝업",
        "4. 채팅 페이지 헤더 🆘 버튼 클릭 → CrisisResourceModal 오픈",
        "5. CrisisResourceModal: ESC / 바깥 클릭으로 닫히지 않는 것 확인 (HAX 안전 규칙)",
        "6. CrisisResourceModal 내 핫라인 링크(1393, 1366, 132) 정상 노출 확인",
    ],
    "verification_rules": [
        {
            "type": "ui_element_present",
            "selector": "a[href='tel:1393']",
            "comment": "tel:1393 링크가 페이지에 존재",
        },
        {
            "type": "modal_no_dismiss_on_backdrop",
            "comment": "CrisisResourceModal은 바깥 클릭으로 닫히지 않아야 함 (HAX G8)",
        },
    ],
}
