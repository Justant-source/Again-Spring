package com.againspring.aiuser.llm.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 어휘이질(T5) rare-vocab detector 테스트.
 * Python build_common_words.py와 Java tokenizeForRareVocab 패리티 검증.
 */
class SelfCritiqueServiceRareVocabTest {

    // ── 1. tokenizeForRareVocab 골든 벡터 (Python build_common_words.py 패리티 가드) ──

    @Test
    void tokenize_basicJosa() {
        // 기본 조사 제거
        List<String> toks = SelfCritiqueService.tokenizeForRareVocab("회사에서 팀장이 또 보고서를 가로챘다");
        assertTrue(toks.contains("회사"), "에서 제거");
        assertTrue(toks.contains("팀장"), "이 제거");
        assertTrue(toks.contains("보고서"), "를 제거");
        assertFalse(toks.stream().anyMatch(t -> t.endsWith("에서") || t.endsWith("이")));
    }

    @Test
    void tokenize_dropsPunctAndEmoji() {
        List<String> toks = SelfCritiqueService.tokenizeForRareVocab("진짜... 너무 힘들어요ㅠㅠ!!!");
        assertTrue(toks.contains("진짜"), "구두점 제거 후 토큰 보존");
        assertFalse(toks.stream().anyMatch(t -> t.contains(".")), "온점 포함 토큰 없어야");
    }

    @Test
    void tokenize_shortTokenFiltered() {
        List<String> toks = SelfCritiqueService.tokenizeForRareVocab("나 그 거 또");
        // "나", "그", "거", "또" 모두 1자 → 필터 대상 (길이≥2 조건)
        for (String t : toks) assertTrue(t.length() >= 2, "길이<2 토큰이 포함됨: " + t);
    }

    @Test
    void tokenize_mixedHangulDigit() {
        List<String> toks = SelfCritiqueService.tokenizeForRareVocab("사귄 지 1년 됐음");
        assertTrue(toks.stream().anyMatch(t -> t.contains("년")), "숫자+한글 토큰 유지");
    }

    @Test
    void tokenize_pureLatinDropped() {
        List<String> toks = SelfCritiqueService.tokenizeForRareVocab("오늘 SNS 올렸더니");
        // SNS는 순수 라틴 → 한글 없음 → 제거. "오늘"·"올렸"은 유지.
        assertFalse(toks.contains("sns"), "순수 라틴 제거");
        assertFalse(toks.contains("SNS"), "순수 라틴 대소문자 제거");
    }

    // ── 2. detector 발동 / 침묵 (ReflectionTestUtils로 필드 주입) ──

    private SelfCritiqueService makeService(boolean enabled, Set<String> common, double threshold, int minTokens, int penalty) {
        SelfCritiqueService svc = new SelfCritiqueService(null, null, null);
        ReflectionTestUtils.setField(svc, "enabled", true);
        ReflectionTestUtils.setField(svc, "passThreshold", 5);
        ReflectionTestUtils.setField(svc, "extraCliches", "");
        ReflectionTestUtils.setField(svc, "rareVocabEnabled", enabled);
        ReflectionTestUtils.setField(svc, "commonWords", common);
        ReflectionTestUtils.setField(svc, "rareRatioThreshold", threshold);
        ReflectionTestUtils.setField(svc, "rareMinTokens", minTokens);
        ReflectionTestUtils.setField(svc, "rareVocabPenalty", penalty);
        return svc;
    }

    // 인위적으로 common-set을 작게 만들어서 비율 높게 유도
    @Test
    void detector_firesOnHighRareRatio() {
        Set<String> common = Set.of("나", "그", "는"); // 거의 empty → 대부분 rare
        SelfCritiqueService svc = makeService(true, common, 0.10, 5, 1);
        // 25토큰 이상 되도록 긴 텍스트 (POST contentType)
        String longPost = "오늘 회사에서 정말 황당한 일이 있었어요 팀장이 또 갑자기 보고서 가로챘고 " +
                          "저는 너무 억울해서 눈물이 날 것 같았어요 이런 상황에서 어떻게 해야 할지 " +
                          "도저히 모르겠어서 여기에 적어보는 거예요";
        SelfCritiqueService.CritiqueResult result = svc.quickCheck(longPost, "post", "polite");
        assertTrue(result.issues().stream().anyMatch(i -> i.contains("어휘이질")),
            "희귀어휘 비율 높을 때 어휘이질 issue 발동");
    }

    @Test
    void detector_silentOnCommonText() {
        // 모든 단어가 common에 포함된 경우
        Set<String> common = new HashSet<>();
        for (String w : SelfCritiqueService.tokenizeForRareVocab(
                "오늘 회사에서 정말 황당한 일이 있었어요 팀장이 보고서 가로챘어요 억울해서 눈물 났어요")) {
            common.add(w);
        }
        SelfCritiqueService svc = makeService(true, common, 0.10, 5, 1);
        String post = "오늘 회사에서 정말 황당한 일이 있었어요 팀장이 보고서 가로챘어요 억울해서 눈물 났어요";
        SelfCritiqueService.CritiqueResult result = svc.quickCheck(post, "post", "polite");
        assertFalse(result.issues().stream().anyMatch(i -> i.contains("어휘이질")),
            "모든 단어 common일 때 어휘이질 미발동");
    }

    @Test
    void detector_silentOnComment() {
        Set<String> common = Set.of("나","는"); // almost-empty common, should still not fire for comment
        SelfCritiqueService svc = makeService(true, common, 0.01, 5, 1);
        SelfCritiqueService.CritiqueResult result = svc.quickCheck(
            "오늘 회사에서 정말 황당한 일이 있었어요 팀장이 보고서 가로챘어요", "comment", "polite");
        assertFalse(result.issues().stream().anyMatch(i -> i.contains("어휘이질")),
            "comment 타입은 어휘이질 미발동");
    }

    @Test
    void detector_silentOnShortPost() {
        Set<String> common = Set.of("나","는");
        SelfCritiqueService svc = makeService(true, common, 0.01, 25, 1); // min 25 tokens
        // 5토큰짜리 짧은 POST
        SelfCritiqueService.CritiqueResult result = svc.quickCheck(
            "오늘 너무 힘들었어요", "post", "polite");
        assertFalse(result.issues().stream().anyMatch(i -> i.contains("어휘이질")),
            "min-tokens 미달 POST는 어휘이질 미발동");
    }

    @Test
    void detector_disabledWhenFlagFalse() {
        Set<String> common = Set.of("나","는");
        SelfCritiqueService svc = makeService(false, common, 0.01, 5, 1); // disabled
        String longPost = "오늘 회사에서 정말 황당한 일이 있었어요 팀장이 또 갑자기 보고서 가로챘고 " +
                          "저는 너무 억울해서 눈물이 날 것 같았어요 이런 상황에서 어떻게 해야 할지 " +
                          "도저히 모르겠어서 여기에 적어보는 거예요";
        SelfCritiqueService.CritiqueResult result = svc.quickCheck(longPost, "post", "polite");
        assertFalse(result.issues().stream().anyMatch(i -> i.contains("어휘이질")),
            "rareVocabEnabled=false일 때 미발동");
    }
}
