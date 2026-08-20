"""
6-Plaza Keyword Classifier for Again-Spring AI Learning Service

Classifies Korean community posts into the 6 plazas:
  COUPLE  - 연인·연애 갈등
  MARRIED - 부부·기혼 갈등
  FRIEND  - 친구·지인 갈등
  FAMILY  - 본가(natal family) 갈등 — 부모·형제·조부모
  WORK    - 직장·직업 갈등
  OTHER   - 기타 갈등 (default fallback)

Scoring (do not flatten primary+supporting):
  title keyword hits * 3
  primary keyword hits in body * 2
  supporting keyword hits in body * 1
Optional channel_hint adds a small bonus to that plaza only (never a veto).

Pure Python, no external deps (stdlib re only).
"""

import re
from typing import Dict, Optional, Tuple

PLAZAS = ("COUPLE", "MARRIED", "FRIEND", "FAMILY", "WORK")
CHANNEL_HINT_BONUS = 2

# Disambiguate MARRIED vs FAMILY. Scored with the same title/body weights as a bonus
# (not flattened). Concat must parenthesize content/title — `content or "" + title` drops title.
SPOUSE_KEYWORDS = [
    "남편", "아내", "와이프", "신랑", "시어머니", "시어머", "시아버", "시댁",
]

PLAZA_KEYWORDS: Dict[str, Dict[str, list]] = {
    "COUPLE": {
        "primary": [
            "남친", "여친", "남자친구", "여자친구", "연인", "사귀", "데이트",
            "썸", "전남친", "전여친", "배우자", "헤어", "이별", "환승",
            "바람", "프로포즈", "결혼약속", "러브홀릭", "러브", "사랑",
            "연애", "짝사랑", "짝", "애인", "사귄지", "사귀면서",
            "헤어지자", "헤어짐", "위기", "이별통고",
        ],
        "supporting": [
            "헤어지", "나쁜놈", "나쁜년", "착한남자", "착한여자", "외로움",
            "그리움", "그립다", "보고싶다", "떨어져", "이루어질수없는",
            "삼각", "삼각형", "키스", "섹스", "스킨십", "멜론", "스며들",
            "사귀는데", "사귀면서", "사귄", "사귀고",
        ],
    },
    "MARRIED": {
        "primary": [
            "남편", "아내", "와이프", "신랑", "부부", "시어머니", "시댁",
            "시아버지", "시누이", "처가", "장모", "장인", "며느리", "사위",
            "기혼", "이혼", "결혼생활", "신혼", "혼인", "혼외", "외도",
            "시집", "친정", "시집살이", "결혼", "결혼후",
        ],
        "supporting": [
            "육아", "아이", "아기", "아들", "딸", "아이키우", "육아스트레스",
            "아이교육", "자식", "자녀", "양육", "임신", "출산", "산후",
            "산후우울증", "모유수유", "분유", "기저귀", "야식", "수면",
        ],
    },
    "FRIEND": {
        "primary": [
            "친구", "절친", "베프", "동창", "동기", "지인", "무리", "절교",
            "손절", "친구사이", "친구와", "친구가", "친구때문에",
            "학교", "반", "같은반", "같은학교", "동창회", "후배",
            "선배", "단짝", "우리반", "우리학교",
        ],
        "supporting": [
            "빌려준돈", "돈빌려", "돈빌", "빌렸", "갚아", "이자", "빌려줬",
            "약속어김", "약속지킴",
            "따돌", "따돌림", "괴롭", "괴롭힘",
            "싸운후", "싸운이후",
            "관계단절", "절교선포", "끝장", "끝낸", "이별선포",
        ],
    },
    "FAMILY": {
        "primary": [
            "엄마", "아빠", "어머니", "아버지", "부모", "부모님", "아부지",
            "어무니", "어머님", "아버님", "엄친아", "부친",
            "모친", "동생", "형", "누나", "언니", "오빠", "형님",
            "누님", "우니", "형아", "오빠야", "누나야",
            "조부모", "할머니", "할아버지", "할머님", "할아버님",
            "외할머니", "외할아버지", "증조", "이모",
            "삼촌", "고모", "숙모", "서방", "조카", "조카딸",
            "조카아들",
            "본가", "원가족", "친오빠", "친동생",
            "혼나고", "혼나", "혼내", "폐를", "폐가",
        ],
        "supporting": [
            "가정", "가정불화",
            "가족", "가족관계", "가족싸움", "가족문제", "가족갈등",
            "상속", "유산", "재산", "보험", "저축",
            "용돈", "생활비", "학비", "교육비", "결혼자금",
            "대출", "빚", "빚때문에", "빚독촉", "깡패", "사채",
            "폭행", "학대", "학대받", "맞았", "맞는다",
            "심한말", "욕설", "모욕", "모욕감",
            "독립", "독립하", "나가", "나가고싶",
            "손절", "연락끊", "왕래끊", "관계끊", "게을러", "게으름",
        ],
    },
    "WORK": {
        "primary": [
            "회사", "직장", "팀장", "부장", "과장", "차장", "대리",
            "사수", "상사", "동료", "후배", "선배", "신입", "인턴",
            "퇴사", "이직", "야근", "회식", "연봉", "월급", "급여",
            "승진", "업무", "프로젝트", "회의", "출근", "부서",
            "인수인계", "갑질", "직장인", "사원", "직원", "종업원",
            "일자리", "구직", "취직", "직업", "직군", "직종",
            "업체", "회사일", "일때문에", "일스트레스",
            "직장스트레스", "직장괴롭힘", "직장따돌림", "워라밸",
        ],
        "supporting": [
            "결근", "지각", "외출", "휴가", "연차", "병가", "태만",
            "게으름", "게을러", "성과", "성적", "평가", "평점",
            "보너스", "상여금", "보상", "인상", "인상승진안됨",
            "감봉", "감원", "구조조정", "레이오프", "정리해고",
            "계약직", "기간제", "파견", "용역", "프리랜서",
            "사업", "자영", "가게", "매장", "매출", "손실",
            "고객", "거래처", "거래처담당자", "고객센터", "콜센터",
            "미팅", "발표", "프레젠테이션", "제안", "계약",
            "실수", "실패", "클레임", "민원", "컴플레인",
            "복직", "대기", "휴직", "병석", "개인사정", "가정사정",
            "직급", "신분", "지위", "권력", "관계", "인맥",
            "차별", "불공정", "부정행위", "뇌물", "뇌물요구",
        ],
    },
    "OTHER": {
        "fallback": ["기타", "etc", "기타갈등"],
    },
}


def _normalize_text(text: str) -> str:
    """Normalize Korean text for keyword matching."""
    if not text:
        return ""
    text = re.sub(r"\s+", " ", text.strip())
    return text.lower()


def _count_keywords_in_text(text: str, keywords: list) -> int:
    """Count keyword hits in text (substring match, case-insensitive)."""
    if not text:
        return 0
    text_lower = text.lower()
    count = 0
    for kw in keywords:
        if kw.lower() in text_lower:
            count += 1
    return count


def _score_plaza(content: str, title: str, plaza_name: str) -> int:
    """Score one plaza: title hits * 3, body primary * 2, body supporting * 1."""
    if plaza_name not in PLAZA_KEYWORDS:
        return 0

    keywords_dict = PLAZA_KEYWORDS[plaza_name]
    primary = keywords_dict.get("primary") or []
    supporting = keywords_dict.get("supporting") or []

    title_hits = (
        _count_keywords_in_text(title or "", primary)
        + _count_keywords_in_text(title or "", supporting)
    )
    primary_body = _count_keywords_in_text(content or "", primary)
    supporting_body = _count_keywords_in_text(content or "", supporting)
    score = title_hits * 3 + primary_body * 2 + supporting_body * 1

    if plaza_name == "MARRIED":
        # Bonus uses the same weights; title must be included (parens, not `or`+concat).
        score += (
            _count_keywords_in_text(title or "", SPOUSE_KEYWORDS) * 3
            + _count_keywords_in_text(content or "", SPOUSE_KEYWORDS) * 2
        )

    return score


def score_all_plazas(
    content: str,
    title: str = "",
    channel_hint: Optional[str] = None,
) -> Dict[str, int]:
    """Return scores for the five real plazas. channel_hint adds CHANNEL_HINT_BONUS only."""
    content_norm = _normalize_text(content or "")
    title_norm = _normalize_text(title or "")
    scores = {
        plaza: _score_plaza(content_norm, title_norm, plaza) for plaza in PLAZAS
    }
    if channel_hint in scores:
        scores[channel_hint] += CHANNEL_HINT_BONUS
    return scores


def _pick_winner(scores: Dict[str, int], content_norm: str, title_norm: str) -> str:
    max_score = max(scores.values()) if scores else 0
    if max_score == 0:
        return "OTHER"

    tied_plazas = [p for p in PLAZAS if scores[p] == max_score]
    if len(tied_plazas) == 1:
        return tied_plazas[0]

    combined = (content_norm or "") + " " + (title_norm or "")
    married_keywords = ["남편", "아내", "와이프", "신랑", "부부", "시어머니", "시댁"]
    if any(kw in combined for kw in married_keywords):
        if "MARRIED" in tied_plazas:
            return "MARRIED"

    precedence_order = ["MARRIED", "FAMILY", "WORK", "COUPLE", "FRIEND"]
    for plaza in precedence_order:
        if plaza in tied_plazas:
            return plaza
    return "COUPLE"


def classify_plaza(
    content: str,
    title: str = "",
    channel_hint: Optional[str] = None,
) -> str:
    """
    Classify a Korean community post into one of 6 plazas.

    channel_hint (COUPLE/MARRIED/FRIEND/FAMILY/WORK) is a small score bonus only.
    It never forces the plaza and never dumps a mismatch to OTHER.
    """
    content_norm = _normalize_text(content or "")
    title_norm = _normalize_text(title or "")

    if not content_norm and not title_norm:
        return "OTHER"

    scores = score_all_plazas(content, title, channel_hint=channel_hint)
    return _pick_winner(scores, content_norm, title_norm)


def confident_plaza(
    content: str,
    title: str = "",
    channel_hint: Optional[str] = None,
) -> Optional[Tuple[str, dict]]:
    """
    Predicted plaza only if the winner is confident.

    Confident when winner score >= 6 and either:
      - only one plaza scored > 0, or
      - winner >= 2 * runner-up
    Otherwise None (keep OTHER / uncertain).
    """
    content_norm = _normalize_text(content or "")
    title_norm = _normalize_text(title or "")
    if not content_norm and not title_norm:
        return None

    scores = score_all_plazas(content, title, channel_hint=channel_hint)
    winner = _pick_winner(scores, content_norm, title_norm)
    if winner == "OTHER":
        return None

    winner_score = scores[winner]
    if winner_score < 6:
        return None

    positive = [p for p in PLAZAS if scores[p] > 0]
    if len(positive) == 1:
        return (winner, scores)

    runner_up = max(scores[p] for p in PLAZAS if p != winner)
    if winner_score >= 2 * runner_up:
        return (winner, scores)
    return None


if __name__ == "__main__":
    test_cases = [
        (
            "남자친구가 전여친 얘기를 자꾸 꺼냐. 처음엔 괜찮았는데 이제 진짜 답답함.",
            "연인·연애 갈등",
            "COUPLE",
        ),
        (
            "결혼 약속하고 사귀는데 자꾸 헤어지자고 해. 왜 이러는 거야?",
            "헤어짐 위기",
            "COUPLE",
        ),
        (
            "남편이 시어머니 말만 듣고 나한테 잔소리해. 시댁에서 자꾸 간섭해.",
            "부부·기혼 갈등",
            "MARRIED",
        ),
        (
            "아내가 친정엄마랑 자꾸 전화하면서 내 얘기를 해. 프라이버시가 없어.",
            "결혼생활",
            "MARRIED",
        ),
        (
            "시어머니가 자꾸 참견해",
            "",
            "MARRIED",
        ),
        (
            "육아 너무 힘든데 남편이 전혀 안 도와줘.",
            "육아 스트레스",
            "MARRIED",
        ),
        (
            "절친이 돈을 빌려갔는데 1년이 넘게 안 갚아. 너무 화난다.",
            "친구 배신",
            "FRIEND",
        ),
        (
            "동기들이 자꾸 나를 무시하고 따돌려. 학교 때부터 친한 사이였는데.",
            "친구 관계",
            "FRIEND",
        ),
        (
            "엄마가 자꾸 나한테 심한 말을 해. 아빠는 뭘 해? 싸움만 해.",
            "가족·부모 갈등",
            "FAMILY",
        ),
        (
            "동생이 게을러서 엄마한테 혼나고 나한테도 자꾸 폐를 끼쳐.",
            "형제자매",
            "FAMILY",
        ),
        (
            "아빠가 자꾸 술을 마셔",
            "부모님 걱정",
            "FAMILY",
        ),
        (
            "상사가 회식 때 자꾸 술을 강요해. 야근도 많고 연봉도 낮아.",
            "직장·직업 갈등",
            "WORK",
        ),
        (
            "팀장이 내 아이디어를 자기 아이디어라고 발표했다. 너무 화나.",
            "직장 갑질",
            "WORK",
        ),
        (
            "날씨가 요즘 정말 좋네. 산책도 많이 가고.",
            "일상 잡담",
            "OTHER",
        ),
        (
            "영화 추천해줄 수 있는 사람 있을까? 액션물 좋아합니다.",
            "영화 추천",
            "OTHER",
        ),
    ]

    print("=" * 80)
    print("6-Plaza Classifier Dry-Run Test")
    print("=" * 80)
    print()

    passed = 0
    failed = 0

    for content, title, expected in test_cases:
        predicted = classify_plaza(content, title)
        status = "✓ PASS" if predicted == expected else "✗ FAIL"
        if predicted == expected:
            passed += 1
        else:
            failed += 1

        print(f"{status}")
        print(f"  Title: {title}")
        print(f"  Content: {content[:60]}...")
        print(f"  Expected: {expected}, Got: {predicted}")
        print()

    print("=" * 80)
    print(f"Results: {passed}/{len(test_cases)} PASSED, {failed}/{len(test_cases)} FAILED")
    print("=" * 80)
