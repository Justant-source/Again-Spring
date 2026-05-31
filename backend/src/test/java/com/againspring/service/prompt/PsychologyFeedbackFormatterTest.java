package com.againspring.service.prompt;

import static org.junit.jupiter.api.Assertions.*;

import com.againspring.domain.Session;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PsychologyFeedbackFormatterTest {

    private final PsychologyFeedbackFormatter fmt = new PsychologyFeedbackFormatter();

    // ── 기존 기본 케이스 ──────────────────────────────────────────

    @Test
    void render_returnsEmpty_whenSessionNull() {
        assertEquals("", fmt.render(null));
    }

    @Test
    void render_returnsEmpty_whenNoHistory() {
        Session s = Session.builder().id("s").build();
        assertEquals("", fmt.render(s));
    }

    @Test
    void render_returnsEmpty_whenAllSignalsBelowThreshold() {
        Session s = Session.builder().id("s").horsemenHistory(List.of(
            entry(1, "USER_A", 0.1, 0.0, 0.1, 0.1)
        )).build();
        assertEquals("", fmt.render(s));
    }

    // ── criticism ────────────────────────────────────────────────

    @Test
    void render_emitsCriticismDirective_aboveThreshold_shortWindow() {
        // 2턴 (WINDOW=3 미만) → 정상 지시 (persistent 분기 아님)
        Session s = Session.builder().id("s").horsemenHistory(List.of(
            entry(1, "USER_A", 0.5, 0.0, 0.0, 0.0),
            entry(2, "USER_A", 0.6, 0.0, 0.0, 0.0)
        )).build();
        String out = fmt.render(s);
        assertTrue(out.contains("psychology_feedback"));
        assertTrue(out.contains("비난"));
        // 2턴은 RECENT_WINDOW 미만 → "전환하세요" 에스컬레이션 아님, 일반 느낌·욕구 지시
        assertTrue(out.contains("느낌·욕구"));
        assertFalse(out.contains("전환하세요"));
    }

    @Test
    void render_emitsCriticismEscalation_whenPersistentAcrossWindow() {
        // RECENT_WINDOW=3 전체가 고비난 → 에스컬레이션
        Session s = Session.builder().id("s").horsemenHistory(List.of(
            entry(1, "USER_A", 0.7, 0.0, 0.0, 0.0),
            entry(2, "USER_A", 0.8, 0.0, 0.0, 0.0),
            entry(3, "USER_A", 0.6, 0.0, 0.0, 0.0)
        )).build();
        String out = fmt.render(s);
        assertTrue(out.contains("비난"));
        assertTrue(out.contains("전환하세요"));
        assertFalse(out.contains("느낌·욕구")); // 에스컬레이션 시 일반 지시 없음
    }

    @Test
    void render_useOnlyRecentWindow_ignoresOldHighCriticism() {
        // 턴 1~7은 고비난, 최근 3턴은 낮음 → 발동 안 됨
        List<Session.HorsemenTurnEntry> hist = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            hist.add(entry(i, "USER_A", 0.8, 0.0, 0.0, 0.0));
        }
        // 최근 3턴 낮게 덮어쓰기
        hist.set(4, entry(5, "USER_A", 0.1, 0.0, 0.0, 0.0));
        hist.set(5, entry(6, "USER_A", 0.1, 0.0, 0.0, 0.0));
        hist.set(6, entry(7, "USER_A", 0.2, 0.0, 0.0, 0.0));
        Session s = Session.builder().id("s").horsemenHistory(hist).build();
        String out = fmt.render(s);
        assertFalse(out.contains("비난"), "최근 윈도우가 낮으면 과거 고비난이 있어도 지시 미발동");
    }

    // ── contempt ─────────────────────────────────────────────────

    @Test
    void render_emitsContemptDirective_onceDetected() {
        Session s = Session.builder().id("s").horsemenHistory(List.of(
            entry(1, "USER_A", 0.0, 0.3, 0.0, 0.0)
        )).build();
        String out = fmt.render(s);
        assertTrue(out.contains("경멸"));
        assertTrue(out.contains("EFT 환기"));
    }

    // ── NVC need-miss ─────────────────────────────────────────────

    @Test
    void render_emitsNeedDirective_normalWhenPartialMiss() {
        // 3턴 중 2턴 need=false (비율 67% ≥ 50%) → 일반 지시 (전체 miss 아님)
        List<Session.NvcTurnEntry> nvc = new ArrayList<>();
        nvc.add(nvcEntry(1, false));
        nvc.add(nvcEntry(2, false));
        nvc.add(nvcEntry(3, true)); // 3번째는 need=true
        Session s = Session.builder().id("s").nvcCompletionHistory(nvc).build();
        String out = fmt.render(s);
        assertTrue(out.contains("욕구"));
        // needMissPersistent=false → 일반 지시, 에스컬레이션 아님
        assertFalse(out.contains("전환하세요"));
        assertTrue(out.contains("다른 각도")); // 일반 지시에 반복 금지 힌트 포함
    }

    @Test
    void render_emitsNeedEscalation_whenAllWindowNeedMissing() {
        // RECENT_WINDOW=3 전체 need=false → 에스컬레이션
        List<Session.NvcTurnEntry> nvc = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            nvc.add(nvcEntry(i, false));
        }
        Session s = Session.builder().id("s").nvcCompletionHistory(nvc).build();
        String out = fmt.render(s);
        assertTrue(out.contains("욕구"));
        assertTrue(out.contains("전환하세요")); // 에스컬레이션 메시지
        assertFalse(out.contains("다른 각도")); // 일반 지시 문구 없음
    }

    @Test
    void render_emitsNeedMissingDirective_whenNvcNeedRarelyComplete_shortWindow() {
        // 기존 테스트 호환: 3턴 전부 need=false → 이제 에스컬레이션으로 처리
        // (shortWindow=false 케이스 — 3턴이므로 persistent 조건 충족)
        List<Session.NvcTurnEntry> nvc = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Session.NvcTurnEntry e = new Session.NvcTurnEntry();
            e.turn = i; e.sender = "USER_A";
            e.observation = true; e.feeling = true;
            e.need = false; e.request = false;
            nvc.add(e);
        }
        Session s = Session.builder().id("s").nvcCompletionHistory(nvc).build();
        String out = fmt.render(s);
        assertTrue(out.contains("욕구")); // 여전히 "욕구" 포함 (에스컬레이션 메시지에도 있음)
    }

    @Test
    void render_useOnlyRecentWindow_ignoresOldNeedMiss() {
        // 턴 1~7 need=false, 최근 3턴 need=true → need-miss 미발동
        List<Session.NvcTurnEntry> nvc = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            nvc.add(nvcEntry(i, false));
        }
        nvc.set(4, nvcEntry(5, true));
        nvc.set(5, nvcEntry(6, true));
        nvc.set(6, nvcEntry(7, true));
        Session s = Session.builder().id("s").nvcCompletionHistory(nvc).build();
        assertEquals("", fmt.render(s), "최근 윈도우 need=true면 과거 miss가 있어도 미발동");
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────

    private Session.HorsemenTurnEntry entry(int turn, String sender,
            double criticism, double contempt, double defensiveness, double stonewalling) {
        Session.HorsemenTurnEntry e = new Session.HorsemenTurnEntry();
        e.turn = turn; e.sender = sender;
        e.criticism = criticism; e.contempt = contempt;
        e.defensiveness = defensiveness; e.stonewalling = stonewalling;
        return e;
    }

    private Session.NvcTurnEntry nvcEntry(int turn, boolean need) {
        Session.NvcTurnEntry e = new Session.NvcTurnEntry();
        e.turn = turn; e.sender = "USER_A";
        e.observation = true; e.feeling = true;
        e.need = need; e.request = false;
        return e;
    }
}
