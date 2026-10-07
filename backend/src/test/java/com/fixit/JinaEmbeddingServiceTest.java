package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixit.embedding.EmbeddingException;
import com.fixit.embedding.EmbeddingNotConfiguredException;
import com.fixit.embedding.EmbeddingProperties;
import com.fixit.embedding.EmbeddingPurpose;
import com.sun.net.httpserver.HttpServer;

/**
 * JinaEmbeddingService against a local stub of the Jina API (no network, no real key): request shape, response
 * parsing, and every failure mode.
 */
class JinaEmbeddingServiceTest {

    static final String KEY = "jina_test_key_must_never_leak";
    static final ObjectMapper MAPPER = new ObjectMapper();

    record Received(String method, String path, String authorization, String contentType, JsonNode body) {
    }

    HttpServer server;
    final List<Received> received = new CopyOnWriteArrayList<>();
    volatile Function<Received, Reply> handler;

    record Reply(int status, String body, long delayMillis) {
        Reply(int status, String body) { this(status, body, 0); }
    }

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Received r = new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"), exchange.getRequestHeaders().getFirst("Content-Type"),
                    raw.isEmpty() ? null : MAPPER.readTree(raw));
            received.add(r);
            Reply reply = handler.apply(r);
            if (reply.delayMillis() > 0) {
                try { Thread.sleep(reply.delayMillis()); } catch (InterruptedException ignored) { }
            }
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/embeddings";
    }

    private com.fixit.embedding.JinaEmbeddingService service(int dimension) {
        return service(dimension, url(), Duration.ofSeconds(5));
    }

    private com.fixit.embedding.JinaEmbeddingService service(int dimension, String apiUrl, Duration requestTimeout) {
        var props = new EmbeddingProperties("jina", "jina-embeddings-v4", apiUrl, KEY, dimension, null);
        try {   // package-private test constructor
            var ctor = com.fixit.embedding.JinaEmbeddingService.class.getDeclaredConstructor(
                    EmbeddingProperties.class, ObjectMapper.class, Duration.class, Duration.class);
            ctor.setAccessible(true);
            return ctor.newInstance(props, MAPPER, Duration.ofSeconds(2), requestTimeout);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String okBody(int dimension) {
        List<String> numbers = new ArrayList<>();
        for (int i = 0; i < dimension; i++) numbers.add(Float.toString((i + 1) / 1000f));
        return "{\"model\":\"jina-embeddings-v4\",\"object\":\"list\",\"usage\":{\"total_tokens\":8},\"data\":"
                + "[{\"object\":\"embedding\",\"index\":0,\"embedding\":[" + String.join(",", numbers) + "]}]}";
    }

    @Test
    void sendsTheRequestJinaExpects_andParsesTheVector() {
        handler = r -> new Reply(200, okBody(4));
        float[] vector = service(4).generateEmbedding("wifi keeps disconnecting");

        assertThat(vector).containsExactly(0.001f, 0.002f, 0.003f, 0.004f);
        Received r = received.get(0);
        assertThat(r.method()).isEqualTo("POST");
        assertThat(r.path()).isEqualTo("/v1/embeddings");
        assertThat(r.authorization()).isEqualTo("Bearer " + KEY);
        assertThat(r.contentType()).isEqualTo("application/json");
        assertThat(r.body().get("model").asText()).isEqualTo("jina-embeddings-v4");
        assertThat(r.body().get("dimensions").asInt()).isEqualTo(4);                 // dimension setting is requested from the model
        assertThat(r.body().get("input")).hasSize(1);
        assertThat(r.body().get("input").get(0).asText()).isEqualTo("wifi keeps disconnecting");
    }

    @Test
    void storedQuestionsUsePassageTask_searchTextUsesQueryTask() {
        handler = r -> new Reply(200, okBody(2));
        var service = service(2);
        service.generateEmbedding("a stored question");
        service.generateEmbedding("a stored question too", EmbeddingPurpose.DOCUMENT);
        service.generateEmbedding("what the user searched", EmbeddingPurpose.QUERY);
        assertThat(received).extracting(r -> r.body().get("task").asText())
                .containsExactly("retrieval.passage", "retrieval.passage", "retrieval.query");
    }

    @Test
    void unicodeAndLongTextAreSentIntact() {
        handler = r -> new Reply(200, okBody(2));
        String text = "तमिल வணக்கம் 日本語 emoji 🙂 \"quotes\" \\ backslash\nnew line " + "x".repeat(20000);
        service(2).generateEmbedding(text);
        assertThat(received.get(0).body().get("input").get(0).asText()).isEqualTo(text);
    }

    @Test
    void rejectedKeyRateLimitAndServerErrorsBecomeCleanExceptionsWithoutLeakingAnything() {
        for (int status : new int[] { 401, 403, 429, 500, 503 }) {
            handler = r -> new Reply(status, "{\"detail\":\"secret detail " + KEY + "\",\"code\":\"X\"}");
            assertThatThrownBy(() -> service(4).generateEmbedding("x"))
                    .isInstanceOf(EmbeddingException.class)
                    .hasMessageContaining("HTTP " + status)
                    .hasMessageNotContaining(KEY).hasMessageNotContaining("secret detail");
        }
        handler = r -> new Reply(401, "{}");
        assertThatThrownBy(() -> service(4).generateEmbedding("x")).hasMessageContaining("API key rejected");
    }

    @Test
    void malformedOrUnexpectedResponsesAreRejected() {
        for (String body : new String[] { "not json", "{}", "{\"data\":[]}", "{\"data\":[{\"embedding\":\"x\"}]}",
                "{\"data\":[{\"embedding\":[1,\"a\"]}]}", "{\"data\":[{\"embedding\":[1]},{\"embedding\":[2]}]}" }) {
            handler = r -> new Reply(200, body);
            assertThatThrownBy(() -> service(1).generateEmbedding("x")).as(body).isInstanceOf(EmbeddingException.class);
        }
    }

    @Test
    void aVectorOfTheWrongSizeIsRejected() {
        handler = r -> new Reply(200, okBody(3));
        assertThatThrownBy(() -> service(2048).generateEmbedding("x"))
                .isInstanceOf(EmbeddingException.class).hasMessageContaining("dimension 3").hasMessageContaining("2048");
    }

    @Test
    void aSlowProviderTimesOutInsteadOfHanging() {
        handler = r -> new Reply(200, okBody(2), 1500);
        long start = System.nanoTime();
        assertThatThrownBy(() -> service(2, url(), Duration.ofMillis(300)).generateEmbedding("x"))
                .isInstanceOf(EmbeddingException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void anUnreachableProviderIsACleanException() {
        server.stop(0);   // nothing listens on the port any more
        assertThatThrownBy(() -> service(2).generateEmbedding("x"))
                .isInstanceOf(EmbeddingException.class).hasMessageContaining("Could not reach");
    }

    @Test
    void theKeyIsNeverSentOverPlainHttpToARemoteHost() {
        handler = r -> new Reply(200, okBody(2));
        assertThatThrownBy(() -> service(2, "http://api.jina.ai/v1/embeddings", Duration.ofSeconds(1)).generateEmbedding("x"))
                .isInstanceOf(EmbeddingException.class).hasMessageContaining("https");
        assertThatThrownBy(() -> service(2, "ftp://example.com/x", Duration.ofSeconds(1)).generateEmbedding("x"))
                .isInstanceOf(EmbeddingException.class);
        assertThat(received).isEmpty();                        // nothing was even attempted
    }

    @Test
    void unconfiguredServiceNeverCallsOut() {
        var unset = new EmbeddingProperties("jina", "jina-embeddings-v4", url(), "PLACEHOLDER_TO_BE_PROVIDED_SECURELY", 4, null);
        var service = new com.fixit.embedding.JinaEmbeddingService(unset, MAPPER);
        assertThatThrownBy(() -> service.generateEmbedding("x"))
                .isInstanceOf(EmbeddingNotConfiguredException.class).hasMessageContaining("EMBEDDING_API_KEY");
        assertThat(received).isEmpty();
    }
}
