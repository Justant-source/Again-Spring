package com.againspring.marketing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramDrillCommandsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void drillBare_defaultsToOne() throws Exception {
        var p = TelegramDrillCommands.parse(mapper.readTree("""
            {"message":{"message_id":9,"chat":{"id":111},"text":"/drill"}}
            """));
        assertThat(p.kind()).isEqualTo(TelegramDrillCommands.Kind.DRILL);
        assertThat(p.drillCount()).isEqualTo(1);
        assertThat(p.chatId()).isEqualTo(111L);
    }

    @Test
    void drillWithCount_capsAtFive() throws Exception {
        var p = TelegramDrillCommands.parse(mapper.readTree("""
            {"message":{"message_id":9,"chat":{"id":111},"text":"/drill 9"}}
            """));
        assertThat(p.kind()).isEqualTo(TelegramDrillCommands.Kind.DRILL);
        assertThat(p.drillCount()).isEqualTo(5);
    }

    @Test
    void skipAndReplyBindReplyTo() throws Exception {
        var skip = TelegramDrillCommands.parse(mapper.readTree("""
            {"message":{"message_id":10,"chat":{"id":111},"text":"/skip"}}
            """));
        assertThat(skip.kind()).isEqualTo(TelegramDrillCommands.Kind.SKIP);

        var reply = TelegramDrillCommands.parse(mapper.readTree("""
            {"message":{"message_id":11,"chat":{"id":111},"text":"너무귀여움",
              "reply_to_message":{"message_id":99}}}
            """));
        assertThat(reply.kind()).isEqualTo(TelegramDrillCommands.Kind.REPLY);
        assertThat(reply.replyToMessageId()).isEqualTo(99L);
        assertThat(reply.text()).isEqualTo("너무귀여움");
    }

    @Test
    void chatterWithoutReply_isIgnored() throws Exception {
        var p = TelegramDrillCommands.parse(mapper.readTree("""
            {"message":{"message_id":12,"chat":{"id":111},"text":"hello"}}
            """));
        assertThat(p.kind()).isEqualTo(TelegramDrillCommands.Kind.IGNORE);
    }
}
