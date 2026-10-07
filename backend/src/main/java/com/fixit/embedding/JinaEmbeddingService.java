package com.fixit.embedding;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Jina AI embeddings (https://api.jina.ai/v1/embeddings), model jina-embeddings-v4.
 *
 * Request: POST {model, task, dimensions, input:[text]} with "Authorization: Bearer <key>".
 *  - task = retrieval.passage for stored questions and retrieval.query for search text (Jina v4 embeds the two
 *    differently; using the right one is what makes query-to-question similarity work well),
 *  - dimensions = EMBEDDING_DIMENSION (v4's native size is 2048; smaller sizes are supported by the model).
 * The returned vector is validated by {@link AbstractEmbeddingService} (present, right length, finite numbers).
 * The API key is only ever put in the Authorization header: never logged, never in an exception message.
 */
public class JinaEmbeddingService extends AbstractEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(JinaEmbeddingService.class);
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    private final ObjectMapper mapper;
    private final HttpClient client;
    private final Duration requestTimeout;

    public JinaEmbeddingService(EmbeddingProperties properties, ObjectMapper mapper) {
        this(properties, mapper, Duration.ofSeconds(5), Duration.ofSeconds(30));
    }

    JinaEmbeddingService(EmbeddingProperties properties, ObjectMapper mapper, Duration connectTimeout, Duration requestTimeout) {
        super(properties);
        this.mapper = mapper;
        this.requestTimeout = requestTimeout;
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();   // does not follow redirects
    }

    @Override
    protected float[] callProvider(String text, EmbeddingPurpose purpose) {
        URI uri = endpoint();
        String body;
        try {
            body = mapper.writeValueAsString(Map.of(
                    "model", properties.model(),
                    "task", purpose == EmbeddingPurpose.QUERY ? "retrieval.query" : "retrieval.passage",
                    "dimensions", properties.dimension(),
                    "input", List.of(text)));
        } catch (IOException e) {
            throw new EmbeddingException("Could not build the embedding request", e);
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + properties.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException("Embedding request was interrupted", e);
        } catch (IOException e) {
            throw new EmbeddingException("Could not reach the Jina embeddings API (" + e.getClass().getSimpleName() + ")", e);
        }

        if (response.statusCode() != 200) {
            log.debug("Jina error body: {}", abbreviate(response.body()));       // may hold a useful 'detail'; never the key
            String hint = response.statusCode() == 401 || response.statusCode() == 403 ? " (API key rejected)"
                    : response.statusCode() == 429 ? " (rate limited)" : "";
            throw new EmbeddingException("Jina embeddings API returned HTTP " + response.statusCode() + hint);
        }
        return parse(response.body());
    }

    private float[] parse(String json) {
        JsonNode embedding;
        try {
            JsonNode data = mapper.readTree(json).path("data");
            if (!data.isArray() || data.size() != 1) {
                throw new EmbeddingException("Jina response did not contain exactly one embedding");
            }
            embedding = data.get(0).path("embedding");
        } catch (IOException e) {
            throw new EmbeddingException("Jina response was not valid JSON", e);
        }
        if (!embedding.isArray()) {
            throw new EmbeddingException("Jina response had no embedding array");
        }
        float[] vector = new float[embedding.size()];
        for (int i = 0; i < vector.length; i++) {
            JsonNode n = embedding.get(i);
            if (!n.isNumber()) {
                throw new EmbeddingException("Jina embedding contained a non-number");
            }
            vector[i] = (float) n.asDouble();
        }
        return vector;
    }

    /** The API key must never travel over plain http to a remote host. */
    private URI endpoint() {
        URI uri;
        try {
            uri = URI.create(properties.apiUrl());
        } catch (IllegalArgumentException e) {
            throw new EmbeddingException("EMBEDDING_API_URL is not a valid URL");
        }
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        boolean loopback = uri.getHost() != null && LOOPBACK_HOSTS.contains(uri.getHost().toLowerCase());
        if (!https && !(loopback && "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new EmbeddingException("EMBEDDING_API_URL must use https");
        }
        return uri;
    }

    private static String abbreviate(String s) {
        return s == null ? "" : s.length() <= 300 ? s : s.substring(0, 300) + "…";
    }
}
