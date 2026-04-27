package com.againspring.service.context;

import static org.junit.jupiter.api.Assertions.*;

import com.againspring.domain.Session;
import org.junit.jupiter.api.Test;

class UserStateAppenderTest {

    private final UserStateAppender appender = new UserStateAppender();

    @Test
    void append_addsEntry_whenStateValid() {
        Session session = new Session();
        Session.UserStateEntry entry = new Session.UserStateEntry();
        entry.state = Session.UserState.VENTING;
        entry.sender = "USER_A";
        entry.turn = 1;

        appender.append(session, entry);

        assertEquals(1, session.getUserStateHistory().size());
        assertEquals(Session.UserState.VENTING, session.getUserStateHistory().get(0).state);
    }

    @Test
    void append_doesNothing_whenEntryNull() {
        Session session = new Session();
        appender.append(session, null);
        assertNull(session.getUserStateHistory());
    }

    @Test
    void append_doesNothing_whenStateNull() {
        Session session = new Session();
        Session.UserStateEntry entry = new Session.UserStateEntry();
        entry.sender = "USER_A";
        // state is null

        appender.append(session, entry);

        assertNull(session.getUserStateHistory());
    }

    @Test
    void append_accumulatesMultipleEntries() {
        Session session = new Session();

        Session.UserStateEntry e1 = new Session.UserStateEntry();
        e1.state = Session.UserState.VENTING;
        e1.sender = "USER_A";
        e1.turn = 1;

        Session.UserStateEntry e2 = new Session.UserStateEntry();
        e2.state = Session.UserState.REFLECTING;
        e2.sender = "USER_A";
        e2.turn = 3;

        appender.append(session, e1);
        appender.append(session, e2);

        assertEquals(2, session.getUserStateHistory().size());
        assertEquals(Session.UserState.REFLECTING, session.getUserStateHistory().get(1).state);
    }
}
