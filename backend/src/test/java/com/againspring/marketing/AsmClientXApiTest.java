package com.againspring.marketing;

import com.againspring.marketing.dto.XPublishRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsmClientXApiTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void publishX_postsJsonAndMapsResult() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        start((path, method, req) -> {
            body.set(req);
            return "{\"ok\":true,\"tweetId\":\"tw-1\",\"url\":\"https://x.com/i/tw-1\"}";
        });
        AsmClient client = client();

        AsmClient.XPublishResult r = client.publishX("hello", "parent-9", null, null);

        assertThat(r.ok()).isTrue();
        assertThat(r.tweetId()).isEqualTo("tw-1");
        assertThat(r.url()).isEqualTo("https://x.com/i/tw-1");
        XPublishRequest parsed = new ObjectMapper().readValue(body.get(), XPublishRequest.class);
        assertThat(parsed.text()).isEqualTo("hello");
        assertThat(parsed.replyToTweetId()).isEqualTo("parent-9");
    }

    @Test
    void publishRitual_mapsPhoto() throws Exception {
        start((path, method, req) ->
            "{\"ok\":true,\"tweetId\":\"r1\",\"url\":\"https://x.com/i/r1\",\"photo\":\"dawn.jpg\"}");
        AsmClient client = client();

        AsmClient.XPublishResult r = client.publishRitual("morning", "좋은 아침");

        assertThat(r.ok()).isTrue();
        assertThat(r.photo()).isEqualTo("dawn.jpg");
        assertThat(r.tweetId()).isEqualTo("r1");
    }

    @Test
    void listXInbox_mapsIsoCreatedAt() throws Exception {
        start((path, method, req) -> {
            assertThat(path).contains("sinceMinutes=90");
            return "{\"items\":[{\"tweetId\":\"in-1\",\"parentTweetId\":\"p1\","
                + "\"ourPostTweetId\":\"ours\",\"authorHandle\":\"bob\","
                + "\"text\":\"hi\",\"createdAt\":\"2026-08-31T00:10:00Z\"}]}";
        });
        AsmClient client = client();

        List<AsmClient.XInboxItem> items = client.listXInbox(90);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).tweetId()).isEqualTo("in-1");
        assertThat(items.get(0).ourPostTweetId()).isEqualTo("ours");
        assertThat(items.get(0).createdAt()).isEqualTo(Instant.parse("2026-08-31T00:10:00Z"));
    }

    @Test
    void listXOutboundCandidates_mapsQueryAndFlags() throws Exception {
        start((path, method, req) -> {
            assertThat(path).contains("minReplies=3");
            assertThat(path).contains("maxAgeHours=6");
            return "{\"items\":[{\"tweetId\":\"hot-1\",\"authorHandle\":\"m\","
                + "\"text\":\"글\",\"replyCount\":4,\"ageHours\":1.5,"
                + "\"alreadyRepliedByUs\":true,\"ourReplyTweetId\":\"ours-r\"}]}";
        });
        AsmClient client = client();

        List<AsmClient.XOutboundCandidate> items = client.listXOutboundCandidates(3, 6);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).alreadyRepliedByUs()).isTrue();
        assertThat(items.get(0).ourReplyTweetId()).isEqualTo("ours-r");
        assertThat(items.get(0).replyCount()).isEqualTo(4);
        assertThat(items.get(0).ageHours()).isEqualTo(1.5);
    }

    @Test
    void listXOutboundCandidates_survivesReadsLongerThanDefaultTimeout() throws Exception {
        start((path, method, req) -> {
            Thread.sleep(180);
            return "{\"items\":[]}";
        });
        AsmProperties props = new AsmProperties();
        props.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        props.setApiToken("tok");
        props.setRequestTimeoutMs(50);
        props.setStatsRequestTimeoutMs(5_000);
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
        RestClient.Builder builder = RestClient.builder()
            .messageConverters(converters -> {
                converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                converters.add(new MappingJackson2HttpMessageConverter(mapper));
            });
        AsmClient client = new AsmClient(props, builder, mapper);

        assertThat(client.listXOutboundCandidates(3, 6)).isEmpty();
    }

    @Test
    void publishX_4xx_throwsWithoutTreatingAsSuccess() throws Exception {
        start((path, method, req) -> {
            throw new StatusException(400, "{\"detail\":\"bad\"}");
        });
        AsmClient client = client();

        assertThatThrownBy(() -> client.publishX("x", null, null, null))
            .isInstanceOf(AsmUnavailableException.class);
    }

    private AsmClient client() {
        AsmProperties props = new AsmProperties();
        props.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        props.setApiToken("tok");
        props.setRequestTimeoutMs(5_000);
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
        RestClient.Builder builder = RestClient.builder()
            .messageConverters(converters -> {
                converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                converters.add(new MappingJackson2HttpMessageConverter(mapper));
            });
        return new AsmClient(props, builder, mapper);
    }

    private void start(Handler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath();
                String q = exchange.getRequestURI().getQuery();
                String full = q == null ? path : path + "?" + q;
                byte[] req = exchange.getRequestBody().readAllBytes();
                String json;
                int status = 200;
                try {
                    json = handler.handle(full, exchange.getRequestMethod(),
                        new String(req, StandardCharsets.UTF_8));
                } catch (StatusException se) {
                    status = se.status;
                    json = se.body;
                } catch (Exception e) {
                    status = 500;
                    json = "{\"error\":\"" + e.getMessage() + "\"}";
                }
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @FunctionalInterface
    private interface Handler {
        String handle(String path, String method, String body) throws Exception;
    }

    private static final class StatusException extends RuntimeException {
        final int status;
        final String body;

        StatusException(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
