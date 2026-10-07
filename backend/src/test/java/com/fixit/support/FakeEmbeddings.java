package com.fixit.support;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.fixit.embedding.EmbeddingException;
import com.fixit.embedding.EmbeddingPurpose;
import com.fixit.embedding.EmbeddingService;

/**
 * TEST-ONLY stand-in for a real embedding provider. It is a deterministic bag-of-words hash (shared words =>
 * similar vectors) and has NO understanding of meaning - it verifies the mechanics (storage, filtering, thresholds,
 * ordering), never search quality. Never use it outside tests.
 */
public class FakeEmbeddings implements EmbeddingService {

    public static final int DIM = 256;
    public static final String MODEL = "fake-model";

    /** Property overrides making the app consider the provider "configured". */
    public static final String P_PROVIDER = "app.embedding.provider=fake";
    public static final String P_MODEL = "app.embedding.model=" + MODEL;
    public static final String P_URL = "app.embedding.api-url=http://fake";
    public static final String P_KEY = "app.embedding.api-key=fake-key";
    public static final String P_DIM = "app.embedding.dimension=" + DIM;
    public static final String P_THRESHOLD = "app.search.semantic-threshold=0.3";
    /** Search tuning values for tests only (the shipped config leaves them unset). */
    public static final String P_LEXICAL_THRESHOLD = "app.search.lexical-threshold=0.2";
    public static final String P_HYBRID_THRESHOLD = "app.search.hybrid-threshold=0.25";
    public static final String P_WEIGHT = "app.search.semantic-weight=0.5";

    public final AtomicInteger calls = new AtomicInteger();
    public volatile boolean failing;
    public volatile int forcedLength = -1;
    /** The purpose of every call, in order (DOCUMENT for stored questions, QUERY for search text). */
    public final List<EmbeddingPurpose> purposes = new CopyOnWriteArrayList<>();

    public void reset() {
        calls.set(0);
        failing = false;
        forcedLength = -1;
        purposes.clear();
    }

    @Override
    public float[] generateEmbedding(String text) {
        return generateEmbedding(text, EmbeddingPurpose.DOCUMENT);
    }

    @Override
    public float[] generateEmbedding(String text, EmbeddingPurpose purpose) {
        purposes.add(purpose);
        calls.incrementAndGet();
        if (failing) {
            throw new EmbeddingException("fake provider is down");
        }
        int len = forcedLength > 0 ? forcedLength : DIM;
        float[] v = new float[len];
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() >= 3) {
                v[(token.hashCode() & 0x7fffffff) % len] += 1f;
            }
        }
        double norm = 0;
        for (float x : v) norm += x * x;
        if (norm == 0) {
            v[0] = 1f;
            return v;
        }
        norm = Math.sqrt(norm);
        for (int i = 0; i < len; i++) v[i] /= (float) norm;
        return v;
    }

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        FakeEmbeddings fakeEmbeddings() {
            return new FakeEmbeddings();
        }
    }
}
