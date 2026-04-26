package com.againspring.service.prompt;

import static org.junit.jupiter.api.Assertions.*;

import com.againspring.domain.Session;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PsychologyFeedbackFormatterTest {

    private final PsychologyFeedbackFormatter fmt = new PsychologyFeedbackFormatter();

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
    void render_emitsCriticismDirective_aboveThreshold() {
        Session s = Session.builder().id("s").horsemenHistory(List.of(
            entry(1, "USER_A", 0.5, 0.0, 0.0, 0.0),
            entry(2, "USER_A", 0.6, 0.0, 0.0, 0.0)
        )).build();
        String out = fmt.render(s);
        assertTrue(out.contains("psychology_feedback"));
        assertTrue(out.contains("비난"));
        assertTrue(out.contains("느낌·욕구"));
    }

    @Test
    void render_emitsContemptDirective_onceDetected() {
        Session s = Session.builder().id("s").horsemenHistory(List.of(
            entry(1, "USER_A", 0.0, 0.3, 0.0, 0.0)
        )).build();
        String out = fmt.render(s);
        assertTrue(out.contains("경멸"));
        assertTrue(out.contains("EFT 환기"));
    }

    @Test
    void render_emitsNeedMissingDirective_whenNvcNeedRarelyComplete() {
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
        assertTrue(out.contains("욕구"));
    }

    @Test
    void render_returnsEmpty_whenAllSignalsBelowThreshold() {
        Session s = Session.builder().id("s").horsemenHistory(List.of(
            entry(1, "USER_A", 0.1, 0.0, 0.1, 0.1)
        )).build();
        assertEquals("", fmt.render(s));
    }

    private Session.HorsemenTurnEntry entry(int turn, String sender,
            double criticism, double contempt, double defensiveness, double stonewalling) {
        Session.HorsemenTurnEntry e = new Session.HorsemenTurnEntry();
        e.turn = turn; e.sender = sender;
        e.criticism = criticism; e.contempt = contempt;
        e.defensiveness = defensiveness; e.stonewalling = stonewalling;
        return e;
    }
}
