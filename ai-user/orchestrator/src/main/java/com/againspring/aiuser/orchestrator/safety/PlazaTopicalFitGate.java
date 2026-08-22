package com.againspring.aiuser.orchestrator.safety;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Topical-fit gate for generated stories: verify that declared plaza matches
 * the inferred plaza based on keyword dominance in title+body.
 *
 * <p>Rule-based, no LLM call. Judges DOMINANCE (what relationship type drives
 * the story), not presence (e.g., a couple story naturally mentions marriage).
 * Logs only — does not block. Config flags govern logging and future blocking.
 *
 * <p>Scoring: title keyword hits * 3, primary body * 2, supporting body * 1.
 *
 * <p>Verdict logic (Phase 4, redesigned 2026-08-22):
 * - EXEMPT: declared plaza is OTHER (doesn't fit any specific category)
 * - MATCH: declared == inferred
 * - MATCH: declared and inferred are adjacent (COUPLE<->MARRIED, FRIEND<->FAMILY)
 * - MATCH: another plaza's margin is <= 4 (presence, not dominance)
 * - MISMATCH: another plaza clearly dominates (margin > 4)
 *
 * <p>Calibrated against 278 real prod posts: 5.0% FP rate (13/258 evaluable).
 * Improvements from previous rule (32.0% FP):
 * - Exempts OTHER (was 100% FP)
 * - Adjacent plaza tolerance fixes COUPLE/MARRIED/FRIEND/FAMILY confusions
 * - Margin-based dominance prevents false positives from natural cross-mentions
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlazaTopicalFitGate {

	public enum Verdict { MATCH, MISMATCH, EXEMPT }

	private static final Map<String, KeywordSet> PLAZA_KEYWORDS = Map.ofEntries(
		Map.entry("COUPLE", new KeywordSet(
			List.of("남친", "여친", "남자친구", "여자친구", "연인", "사귀", "데이트",
				"썸", "전남친", "전여친", "배우자", "헤어", "이별", "환승",
				"바람", "프로포즈", "결혼약속", "러브홀릭", "러브", "사랑",
				"연애", "짝사랑", "짝", "애인", "사귄지", "사귀면서",
				"헤어지자", "헤어짐", "위기", "이별통고"),
			List.of("헤어지", "나쁜놈", "나쁜년", "착한남자", "착한여자", "외로움",
				"그리움", "그립다", "보고싶다", "떨어져", "이루어질수없는",
				"삼각", "삼각형", "키스", "섹스", "스킨십", "멜론", "스며들",
				"사귀는데", "사귀면서", "사귄", "사귀고"))),
		Map.entry("MARRIED", new KeywordSet(
			List.of("남편", "아내", "와이프", "신랑", "부부", "시어머니", "시댁",
				"시아버지", "시누이", "처가", "장모", "장인", "며느리", "사위",
				"기혼", "이혼", "결혼생활", "신혼", "혼인", "혼외", "외도",
				"시집", "친정", "시집살이", "결혼", "결혼후"),
			List.of("육아", "아이", "아기", "아들", "딸", "아이키우", "육아스트레스",
				"아이교육", "자식", "자녀", "양육", "임신", "출산", "산후",
				"산후우울증", "모유수유", "분유", "기저귀", "야식", "수면"))),
		Map.entry("FRIEND", new KeywordSet(
			List.of("친구", "절친", "베프", "동창", "동기", "지인", "무리", "절교",
				"손절", "친구사이", "친구와", "친구가", "친구때문에",
				"학교", "반", "같은반", "같은학교", "동창회", "후배",
				"선배", "단짝", "우리반", "우리학교"),
			List.of("빌려준돈", "돈빌려", "돈빌", "빌렸", "갚아", "이자", "빌려줬",
				"약속어김", "약속지킴",
				"따돌", "따돌림", "괴롭", "괴롭힘",
				"싸운후", "싸운이후",
				"관계단절", "절교선포", "끝장", "끝낸", "이별선포"))),
		Map.entry("FAMILY", new KeywordSet(
			List.of("엄마", "아빠", "어머니", "아버지", "부모", "부모님", "아부지",
				"어무니", "어머님", "아버님", "엄친아", "부친",
				"모친", "동생", "형", "누나", "언니", "오빠", "형님",
				"누님", "우니", "형아", "오빠야", "누나야",
				"조부모", "할머니", "할아버지", "할머님", "할아버님",
				"외할머니", "외할아버지", "증조", "이모",
				"삼촌", "고모", "숙모", "서방", "조카", "조카딸",
				"조카아들",
				"본가", "원가족", "친오빠", "친동생",
				"혼나고", "혼나", "혼내", "폐를", "폐가"),
			List.of("가정", "가정불화",
				"가족", "가족관계", "가족싸움", "가족문제", "가족갈등",
				"상속", "유산", "재산", "보험", "저축",
				"용돈", "생활비", "학비", "교육비", "결혼자금",
				"대출", "빚", "빚때문에", "빚독촉", "깡패", "사채",
				"폭행", "학대", "학대받", "맞았", "맞는다",
				"심한말", "욕설", "모욕", "모욕감",
				"독립", "독립하", "나가", "나가고싶",
				"손절", "연락끊", "왕래끊", "관계끊", "게을러", "게으름"))),
		Map.entry("WORK", new KeywordSet(
			List.of("회사", "직장", "팀장", "부장", "과장", "차장", "대리",
				"사수", "상사", "동료", "후배", "선배", "신입", "인턴",
				"퇴사", "이직", "야근", "회식", "연봉", "월급", "급여",
				"승진", "업무", "프로젝트", "회의", "출근", "부서",
				"인수인계", "갑질", "직장인", "사원", "직원", "종업원",
				"일자리", "구직", "취직", "직업", "직군", "직종",
				"업체", "회사일", "일때문에", "일스트레스",
				"직장스트레스", "직장괴롭힘", "직장따돌림", "워라밸"),
			List.of("결근", "지각", "외출", "휴가", "연차", "병가", "태만",
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
				"차별", "불공정", "부정행위", "뇌물", "뇌물요구"))
		),
		Map.entry("OTHER", new KeywordSet(List.of(), List.of()))
	);

	private static final List<String> PLAZA_ORDER = List.of("MARRIED", "FAMILY", "WORK", "COUPLE", "FRIEND", "OTHER");

	// Adjacent plazas (treated as non-mismatch): COUPLE <-> MARRIED, FRIEND <-> FAMILY
	private static final Map<String, Set<String>> ADJACENT_PLAZAS = Map.ofEntries(
		Map.entry("COUPLE", Set.of("MARRIED")),
		Map.entry("MARRIED", Set.of("COUPLE")),
		Map.entry("FRIEND", Set.of("FAMILY")),
		Map.entry("FAMILY", Set.of("FRIEND"))
	);

	// Margin threshold: dominance must exceed this value to trigger MISMATCH
	// Below this, treat as "presence, not dominance" (e.g., natural cross-mentions)
	private static final int MARGIN_THRESHOLD_FOR_MISMATCH = 4;

	/**
	 * Evaluate the topical fit of a generated story. Logs verdict with scores.
	 * Returns the result for testing/validation.
	 *
	 * @param declaredPlaza the category passed to generation (e.g., "FAMILY")
	 * @param title generated post title
	 * @param body generated post body
	 * @return a Result with verdict and scores
	 */
	public Result evaluate(String declaredPlaza, String title, String body) {
		// Defect fix 1: OTHER is exempt (it means "doesn't fit specific plaza")
		if ("OTHER".equals(declaredPlaza)) {
			Result result = new Result(declaredPlaza, "OTHER", 0, 0, 0, Verdict.EXEMPT);
			log.info("[PLAZA_FIT] declaredPlaza={} verdict=EXEMPT reason='OTHER_exempt'",
				declaredPlaza);
			return result;
		}

		String titleNorm = normalize(title);
		String bodyNorm = normalize(body);

		Map<String, Integer> scores = new HashMap<>();
		for (String plaza : PLAZA_KEYWORDS.keySet()) {
			if (!"OTHER".equals(plaza)) {  // Only score actual plazas, not OTHER
				scores.put(plaza, scorePlaza(titleNorm, bodyNorm, plaza));
			}
		}

		// Find the inferred plaza: winner by score, ties broken by PLAZA_ORDER
		String inferred = findWinner(scores);

		int declaredScore = scores.getOrDefault(declaredPlaza, 0);
		int inferredScore = scores.getOrDefault(inferred, 0);
		int margin = Math.abs(declaredScore - inferredScore);

		// Defect fix 2: Dominance > presence
		Verdict verdict;
		String reason;

		if (declaredPlaza.equals(inferred)) {
			verdict = Verdict.MATCH;
			reason = "exact_match";
		} else if (isAdjacent(declaredPlaza, inferred)) {
			// Adjacent plazas (COUPLE<->MARRIED, FRIEND<->FAMILY) are OK
			verdict = Verdict.MATCH;
			reason = "adjacent_plazas";
		} else if (margin <= MARGIN_THRESHOLD_FOR_MISMATCH) {
			// Margin too small = presence not dominance (natural cross-mentions)
			verdict = Verdict.MATCH;
			reason = "presence_not_dominance";
		} else {
			// Clear dominance by another plaza
			verdict = Verdict.MISMATCH;
			reason = "inferred_dominates";
		}

		Result result = new Result(declaredPlaza, inferred, declaredScore, inferredScore, margin, verdict);

		// Log greppable line
		log.info("[PLAZA_FIT] declaredPlaza={} inferredPlaza={} declaredScore={} inferredScore={} margin={} verdict={} reason={}",
			declaredPlaza, inferred, declaredScore, inferredScore, margin, verdict.name(), reason);

		return result;
	}

	private String normalize(String text) {
		if (text == null) return "";
		return text.toLowerCase()
			.replaceAll("\\s+", " ")
			.trim();
	}

	private int scorePlaza(String titleNorm, String bodyNorm, String plaza) {
		KeywordSet keywords = PLAZA_KEYWORDS.get(plaza);
		if (keywords == null) return 0;

		// Title: hits * 3 (both primary and supporting)
		int titleHits = countKeywords(titleNorm, keywords.primary)
			+ countKeywords(titleNorm, keywords.supporting);

		// Body: primary * 2, supporting * 1
		int primaryHits = countKeywords(bodyNorm, keywords.primary);
		int supportingHits = countKeywords(bodyNorm, keywords.supporting);

		return titleHits * 3 + primaryHits * 2 + supportingHits * 1;
	}

	private int countKeywords(String text, List<String> keywords) {
		if (text == null || text.isEmpty()) return 0;
		int count = 0;
		for (String kw : keywords) {
			if (text.contains(kw.toLowerCase())) {
				count++;
			}
		}
		return count;
	}

	private String findWinner(Map<String, Integer> scores) {
		int max = scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
		if (max == 0) return "OTHER";

		Set<String> tied = scores.entrySet().stream()
			.filter(e -> e.getValue() == max)
			.map(Map.Entry::getKey)
			.toList()
			.stream()
			.collect(java.util.stream.Collectors.toSet());

		if (tied.size() == 1) return tied.iterator().next();

		// Tiebreak by PLAZA_ORDER (excluding OTHER)
		for (String plaza : PLAZA_ORDER) {
			if (!"OTHER".equals(plaza) && tied.contains(plaza)) {
				return plaza;
			}
		}
		return "OTHER";
	}

	private boolean isAdjacent(String declared, String inferred) {
		Set<String> adjacent = ADJACENT_PLAZAS.get(declared);
		return adjacent != null && adjacent.contains(inferred);
	}

	public record Result(
		String declaredPlaza,
		String inferredPlaza,
		int declaredScore,
		int inferredScore,
		int margin,
		Verdict verdict
	) {
		public boolean matches() {
			// EXEMPT (OTHER plaza) and MATCH both return true
			return verdict == Verdict.MATCH || verdict == Verdict.EXEMPT;
		}
	}

	private record KeywordSet(List<String> primary, List<String> supporting) {}
}
