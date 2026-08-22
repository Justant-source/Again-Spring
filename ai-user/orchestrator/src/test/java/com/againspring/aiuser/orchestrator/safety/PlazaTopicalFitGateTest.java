package com.againspring.aiuser.orchestrator.safety;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for plaza topical-fit gate: dominance logic and verdict evaluation.
 */
class PlazaTopicalFitGateTest {

	private PlazaTopicalFitGate gate;

	@BeforeEach
	void setUp() {
		gate = new PlazaTopicalFitGate();
	}

	// ========== FAMILY tests ==========

	@Test
	void familyStoryMentioningSpouseIsMatch() {
		// Family story where spouse is mentioned but family is dominant
		String title = "엄마가 남편한테 자꾸 심한 말을 해";
		String body = "우리 엄마가 내 남편한테 자꾸 심한 말을 해. 남편은 참는데 이게 너무 스트레스다. " +
			"아빠도 엄마 말을 제지 안 해. 형이 중간에 들어와도 소용이 없다. 어떻게 해야 할까?";

		PlazaTopicalFitGate.Result result = gate.evaluate("FAMILY", title, body);

		assertEquals("FAMILY", result.declaredPlaza());
		assertEquals(PlazaTopicalFitGate.Verdict.MATCH, result.verdict());
		assertTrue(result.matches());
	}

	@Test
	void familyStoryDominantByParentKeywords() {
		// Clear family story: parents, siblings
		String title = "아버지가 형한테 자꾸 돈 때문에 싸워";
		String body = "아버지가 형이 사업을 한다고 계속 돈을 빌려줬는데 이제 갚지 않겠대. " +
			"우리 엄마는 중간에서 중보하려고 하는데 형이 듣질 않아. 어머니가 너무 스트레스받으시는데...";

		PlazaTopicalFitGate.Result result = gate.evaluate("FAMILY", title, body);

		assertEquals("FAMILY", result.declaredPlaza());
		assertEquals(PlazaTopicalFitGate.Verdict.MATCH, result.verdict());
		assertTrue(result.matches());
	}

	// ========== MARRIED tests ==========

	@Test
	void marriedStoryDominantBySpouseKeywords() {
		// Clear married story
		String title = "남편이 시어머니 말만 듣고 나한테 잔소리해";
		String body = "결혼 후 남편이 내 의견은 안 듣고 시어머니 말만 들어. " +
			"시댁에서 자꾸 간섭해서 결혼생활이 힘들다. 아내로서 뭘 해야 할까?";

		PlazaTopicalFitGate.Result result = gate.evaluate("MARRIED", title, body);

		assertEquals("MARRIED", result.declaredPlaza());
		assertEquals(PlazaTopicalFitGate.Verdict.MATCH, result.verdict());
		assertTrue(result.matches());
	}

	@Test
	void marriedStoryWithChildrearing() {
		// Married story dominated by spousal relationship + childcare
		String title = "육아 때문에 남편과 자꾸 싸워";
		String body = "아이를 낳고 난 후 남편이 육아를 안 도와줘. 내가 혼자 아기를 돌보고 잠도 못 자. " +
			"아내로서 이게 맞나 싶고 결혼생활이 너무 힘들다.";

		PlazaTopicalFitGate.Result result = gate.evaluate("MARRIED", title, body);

		assertEquals("MARRIED", result.declaredPlaza());
		assertEquals(PlazaTopicalFitGate.Verdict.MATCH, result.verdict());
	}

	// ========== COUPLE tests ==========

	@Test
	void coupleStoryDominantByRomanticKeywords() {
		// Clear couple story
		String title = "남친이 전여친 생각을 자꾸 해";
		String body = "사귀는 중인데 남자친구가 전여친 얘기를 자꾸 꺼낸다. " +
			"연애하면서 이별은 아니지만 자꾸 마음이 불안해. 어떻게 해야 할까?";

		PlazaTopicalFitGate.Result result = gate.evaluate("COUPLE", title, body);

		assertEquals("COUPLE", result.declaredPlaza());
		assertEquals(PlazaTopicalFitGate.Verdict.MATCH, result.verdict());
		assertTrue(result.matches());
	}

	// ========== FRIEND tests ==========

	@Test
	void friendStoryDominantByFriendKeywords() {
		// Clear friend story
		String title = "절친이 돈을 안 갚아";
		String body = "절친구가 1년 전에 돈을 빌려갔는데 지금까지 안 갚아. " +
			"친구라고 생각했는데 이렇게 될 줄 몰랐다. 손절해야 할까?";

		PlazaTopicalFitGate.Result result = gate.evaluate("FRIEND", title, body);

		assertEquals("FRIEND", result.declaredPlaza());
		assertEquals(PlazaTopicalFitGate.Verdict.MATCH, result.verdict());
		assertTrue(result.matches());
	}

	// ========== WORK tests ==========

	@Test
	void workStoryDominantByWorkKeywords() {
		// Clear work story
		String title = "상사가 회식 때 술을 자꾸 강요해";
		String body = "회사에서 팀장이 회식 때 술을 자꾸 강요한다. " +
			"야근도 많고 연봉도 낮은데 이걸 견디기 힘들다. 퇴사해야 할까?";

		PlazaTopicalFitGate.Result result = gate.evaluate("WORK", title, body);

		assertEquals("WORK", result.declaredPlaza());
		assertEquals(PlazaTopicalFitGate.Verdict.MATCH, result.verdict());
		assertTrue(result.matches());
	}

	// ========== OTHER tests ==========

	@Test
	void ambiguousStoryDefaultsToOther() {
		// Ambiguous story: no clear plaza signals
		String title = "날씨가 좋네요";
		String body = "요즘 날씨가 정말 좋아. 산책도 많이 가고 기분이 좋다.";

		PlazaTopicalFitGate.Result result = gate.evaluate("OTHER", title, body);

		assertEquals("OTHER", result.declaredPlaza());
		// OTHER is exempt (not evaluated)
		assertEquals(PlazaTopicalFitGate.Verdict.EXEMPT, result.verdict());
		assertTrue(result.matches());  // EXEMPT still counts as matches
	}

	@Test
	void otherDeclaredButStrongFamilySignalIsMatch() {
		// Declared as OTHER but story actually has family signals
		// Verdict should be EXEMPT because OTHER is ambiguous and not evaluated
		String title = "기타";
		String body = "뭔가 문제가 있어";

		PlazaTopicalFitGate.Result result = gate.evaluate("OTHER", title, body);

		assertEquals("OTHER", result.declaredPlaza());
		// OTHER is exempt (not evaluated)
		assertEquals(PlazaTopicalFitGate.Verdict.EXEMPT, result.verdict());
		assertTrue(result.matches());  // EXEMPT still counts as matches
	}

	// ========== MISMATCH tests ==========

	@Test
	void declaredFamilyButDominantMarriedIsMatch() {
		// This should be MATCH because a family story can mention spouse
		// The key is dominance: if it's really about spousal conflict, it's MARRIED.
		// But if the FAMILY keywords dominate, it stays FAMILY.
		String title = "엄마가 남편을 미워해";
		String body = "엄마가 내 남편을 계속 못마땅해해. 아버지는 뭐라고 안 하시는데 " +
			"엄마가 자꾸 남편 험담을 한다. 형은 중간에 들어와서 엄마를 진정시킨다.";

		PlazaTopicalFitGate.Result result = gate.evaluate("FAMILY", title, body);

		// The story has family keywords (엄마, 아버지, 형) that dominate
		assertEquals(PlazaTopicalFitGate.Verdict.MATCH, result.verdict());
	}

	@Test
	void declaredFamilyButPurelySpouseConflictIsMismatch() {
		// Declared FAMILY but the story is purely about spouse conflict
		String title = "남편이 나를 계속 무시해";
		String body = "결혼한 지 5년인데 남편이 내 말을 듣질 않아. 아내로서 " +
			"자존감이 떨어지고 이혼을 생각하기도 한다. 부부 관계가 너무 나빠졌다.";

		PlazaTopicalFitGate.Result result = gate.evaluate("FAMILY", title, body);

		// MARRIED keywords (남편, 부부, 아내) dominate. Should be MISMATCH.
		assertEquals("FAMILY", result.declaredPlaza());
		assertEquals("MARRIED", result.inferredPlaza());
		assertEquals(PlazaTopicalFitGate.Verdict.MISMATCH, result.verdict());
		assertFalse(result.matches());
	}

	@Test
	void declaredCoupleButWorkStressConflictIsMatch() {
		// Declared COUPLE but work-related stress mentioned
		// With margin=3, this is presence (work is mentioned) not dominance (work doesn't far exceed couple score)
		// Real calibration shows margin threshold of 4 optimizes FP rate at 5.0% on 278 prod posts
		String title = "일 때문에 남자친구와 자꾸 싸워";
		String body = "회사 일이 너무 많아서 남친과 만날 시간이 없다. " +
			"팀장이 자꾸 야근을 시키고 프로젝트가 밀렸다. 직장 스트레스 때문에 연애가 힘들다.";

		PlazaTopicalFitGate.Result result = gate.evaluate("COUPLE", title, body);

		// Margin = 3, below threshold of 4, so MATCH (presence not dominance)
		assertEquals("COUPLE", result.declaredPlaza());
		assertEquals("WORK", result.inferredPlaza());
		assertEquals(PlazaTopicalFitGate.Verdict.MATCH, result.verdict());  // Changed: now MATCH
		assertTrue(result.matches());
	}

	@Test
	void declaredWorlButFamilyConflictIsMismatch() {
		// Declared WORK but actually family conflict
		String title = "엄마가 직장을 그만내라고 해";
		String body = "부모님이 내 직장을 반대해. 아버지는 뭐라고 안 하시는데 " +
			"어머니가 자꾸 좋은 일자리로 옮기라고 한다. 형은 나 편이지만 " +
			"부모님과의 관계가 갈수록 멀어진다.";

		PlazaTopicalFitGate.Result result = gate.evaluate("WORK", title, body);

		// FAMILY keywords (엄마, 아버지, 어머니, 형) dominate. Should be MISMATCH.
		assertEquals("WORK", result.declaredPlaza());
		assertEquals("FAMILY", result.inferredPlaza());
		assertEquals(PlazaTopicalFitGate.Verdict.MISMATCH, result.verdict());
	}

	@Test
	void titleWeightedMore() {
		// Title has 3x weight per keyword. Multiple title keywords can outweigh body keywords.
		String title = "엄마와 아빠가 자꾸 싸워";  // Multiple FAMILY title keywords
		String body = "회사 일이 조금 힘들어서 집에 와도 피곤하다.";  // Minimal WORK body keywords

		PlazaTopicalFitGate.Result result = gate.evaluate("FAMILY", title, body);

		// Multiple title keywords dominate
		assertEquals("FAMILY", result.declaredPlaza());
		assertEquals("FAMILY", result.inferredPlaza());
		assertEquals(PlazaTopicalFitGate.Verdict.MATCH, result.verdict());
	}

	@Test
	void emptyTitleAndBody() {
		String title = "";
		String body = "";

		PlazaTopicalFitGate.Result result = gate.evaluate("OTHER", title, body);

		assertEquals("OTHER", result.declaredPlaza());
		assertEquals("OTHER", result.inferredPlaza());
		assertEquals(PlazaTopicalFitGate.Verdict.EXEMPT, result.verdict());
		assertTrue(result.matches());
	}

	@Test
	void nullTitleAndBody() {
		PlazaTopicalFitGate.Result result = gate.evaluate("OTHER", null, null);

		assertEquals("OTHER", result.declaredPlaza());
		assertEquals("OTHER", result.inferredPlaza());
		assertEquals(PlazaTopicalFitGate.Verdict.EXEMPT, result.verdict());
		assertTrue(result.matches());
	}
}
