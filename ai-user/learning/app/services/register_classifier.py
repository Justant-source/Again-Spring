"""
문체 register 분류 — Wave1-D 이후 BLIND·NATEPAN 2문체 세계 기준.

반환값은 기존과 동일하게 'casual' | 'polite' | 'mixed' (example_bank.register /
/examples/similar 필터 계약 유지).

문체 앵커 의도:
  - polite  ≈ BLIND 계열 (직장인 익명, 해요체·습니다체)
  - casual  ≈ NATEPAN 계열 (판 커뮤니티, 음슴체·반말)
  - mixed   = 양쪽이 비슷한 비중일 때만 (필터 무력화 방지 — 과다 mixed 억제)

혼합 임계를 0.70→0.55로 낮추고, BLIND/NATEPAN에서 흔한 종결·조사 패턴을 보강했다.
"""
import re

# BLIND 계열 — 해요체 / 습니다체 / 공손 종결
_POLITE = re.compile(
    r'(?:'
    r'(?:했|했었|았|었|였|웠|이었|이|아|어)어요'
    r'|았어요|었어요|였어요|웠어요'
    r'|에요|예요|해요|했요|습니다|십니다|니다|세요|시죠'
    r'|네요|는데요|거든요|죠|이에요'
    r'|같아요|겠네요|겠는데요|드릴게요|주세요|할까요'
    r'|습니다만'
    r')'
    r'(?:[.!?~…]|\s)*$',
    re.MULTILINE
)

# NATEPAN 계열 — 음슴체 / 반말 / 판 특유 종결
_CASUAL = re.compile(
    r'(?:'
    r'임|함|됨|음|슴|거임|거임\?|거든|더라|더라고'
    r'|했음|었음|있음|없음|임다|함다|됨다'
    r'|잖아|냐|냐\?|노|지|나|구나|네|지뭐|지않|지않음'
    r'|ㅋㅋ+|ㅎㅎ+|ㅠㅠ+|ㄹㅇ|ㅇㅇ|ㄷㄷ'
    r'|인데|인데여?|인데여|인듯|인거임|할듯|할거임'
    r'|뭐임|뭔데|뭐냐|어케|어캄'
    r')'
    r'(?:[.!?~…]|\s)*$',
    re.MULTILINE
)

# 다수결 임계 — 이 비율 이상이면 해당 축으로 확정 (구 0.70은 mixed 과다)
_MAJORITY = 0.55


def _count_markers(text: str) -> tuple[int, int]:
    """줄 단위 + 마지막 문장 조각에서 polite/casual 마커 수."""
    lines = [l.strip() for l in text.splitlines() if len(l.strip()) > 3]
    # 개행이 거의 없는 단락도 문장 단위로 쪼갠다
    if len(lines) <= 1:
        chunks = re.split(r'[.!?~\n]+', text)
        lines = [c.strip() for c in chunks if len(c.strip()) > 3]

    polite_count = sum(1 for l in lines if _POLITE.search(l))
    casual_count = sum(1 for l in lines if _CASUAL.search(l))

    # 줄 매칭이 없으면 본문 끝 80자에 한 번 더 시도 (짧은 댓글·한 문단)
    if polite_count + casual_count == 0 and text.strip():
        tail = text.strip()[-80:]
        if _POLITE.search(tail):
            polite_count = 1
        if _CASUAL.search(tail):
            casual_count = 1

    return polite_count, casual_count


def classify(text: str) -> str:
    """
    텍스트의 종결어미 분포를 보고 register 분류.
    반환: 'casual' | 'polite' | 'mixed'
    """
    if not text:
        return 'mixed'

    polite_count, casual_count = _count_markers(text)
    total = polite_count + casual_count

    if total == 0:
        return 'mixed'

    polite_ratio = polite_count / total
    casual_ratio = casual_count / total

    if polite_ratio >= _MAJORITY:
        return 'polite'
    if casual_ratio >= _MAJORITY:
        return 'casual'
    return 'mixed'
