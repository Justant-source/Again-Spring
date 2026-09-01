package com.againspring.marketing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FxTwitterXTimelineClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsePage_readsObjectReplyingTo_quoteText_andMedia() throws Exception {
        String json = """
            {
              "results": [
                {
                  "id": "111",
                  "text": "@foo 너무귀여움",
                  "replying_to_status": null,
                  "replying_to": {
                    "screen_name": "foo",
                    "status": "999",
                    "url": "https://x.com/foo/status/999"
                  },
                  "media": {
                    "photos": [{"type": "photo", "url": "https://pbs.twimg.com/x.jpg"}],
                    "all": [{"type": "photo"}]
                  }
                },
                {
                  "id": "222",
                  "text": "인용 코멘트",
                  "quote": {"text": "원글 내용입니다"}
                },
                {
                  "id": "333",
                  "text": "문자열 답글",
                  "replying_to": "bar",
                  "replying_to_status": "888"
                }
              ]
            }
            """;

        var statuses = FxTwitterXTimelineClient.parsePage(mapper.readTree(json));

        assertThat(statuses).hasSize(3);
        XManualStatusClassifier.Status reply = statuses.get(0);
        assertThat(reply.id()).isEqualTo("111");
        assertThat(reply.replyToHandle()).isEqualTo("foo");
        assertThat(reply.replyToStatusId()).isEqualTo("999");
        assertThat(reply.hasMedia()).isTrue();
        assertThat(reply.quote()).isFalse();

        XManualStatusClassifier.Status quote = statuses.get(1);
        assertThat(quote.quote()).isTrue();
        assertThat(quote.quoteText()).isEqualTo("원글 내용입니다");
        assertThat(quote.hasMedia()).isFalse();

        XManualStatusClassifier.Status stringReply = statuses.get(2);
        assertThat(stringReply.replyToHandle()).isEqualTo("bar");
        assertThat(stringReply.replyToStatusId()).isEqualTo("888");
    }

    @Test
    void parseStatusResponse_readsTweetTextAndPhotos() throws Exception {
        String json = """
            {
              "code": 200,
              "message": "OK",
              "tweet": {
                "text": "부모 트윗 본문",
                "media": {
                  "photos": [{"type": "photo"}]
                }
              }
            }
            """;
        var parent = FxTwitterXTimelineClient.parseStatusResponse(mapper.readTree(json));
        assertThat(parent).isNotNull();
        assertThat(parent.text()).isEqualTo("부모 트윗 본문");
        assertThat(parent.hasPhoto()).isTrue();
    }

    @Test
    void parseStatusResponse_404_returnsNull() throws Exception {
        String json = """
            {"code":404,"message":"NOT_FOUND","tweet":null}
            """;
        assertThat(FxTwitterXTimelineClient.parseStatusResponse(mapper.readTree(json))).isNull();
    }

    @Test
    void hasPhotoMedia_detectsTypePhotoInAll() throws Exception {
        var media = mapper.readTree("""
            {"all":[{"type":"video"},{"type":"photo"}]}
            """);
        assertThat(FxTwitterXTimelineClient.hasPhotoMedia(media)).isTrue();
    }
}
