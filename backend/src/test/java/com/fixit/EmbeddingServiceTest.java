package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.fixit.embedding.*;

/** Phase 14 unit tests: no Spring, no provider. The "provider" is a stub we control. */
class EmbeddingServiceTest {

    private static EmbeddingProperties configured(int dim) {
        return new EmbeddingProperties("stub", "stub-model", "http://stub", "secret-key", dim, null);
    }

    private static class StubService extends AbstractEmbeddingService {
        final AtomicInteger calls = new AtomicInteger();
        final Supplier<float[]> result;

        StubService(EmbeddingProperties p, Supplier<float[]> result) {
            super(p);
            this.result = result;
        }

        volatile EmbeddingPurpose lastPurpose;

        @Override
        protected float[] callProvider(String text, EmbeddingPurpose purpose) {
            calls.incrementAndGet();
            lastPurpose = purpose;
            return result.get();
        }
    }

    @Test
    void validVectorIsReturned() {
        var service = new StubService(configured(3), () -> new float[] { 0.1f, 0.2f, 0.3f });
        assertThat(service.generateEmbedding("hello")).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void purposeDefaultsToDocumentAndIsForwardedToTheProvider() {
        var service = new StubService(configured(1), () -> new float[] { 1f });
        service.generateEmbedding("stored question");
        assertThat(service.lastPurpose).isEqualTo(EmbeddingPurpose.DOCUMENT);
        service.generateEmbedding("what the user typed", EmbeddingPurpose.QUERY);
        assertThat(service.lastPurpose).isEqualTo(EmbeddingPurpose.QUERY);
    }

    @Test
    void wrongDimensionIsRejected() {
        var service = new StubService(configured(4), () -> new float[] { 1f, 2f, 3f });
        assertThatThrownBy(() -> service.generateEmbedding("hello"))
                .isInstanceOf(EmbeddingException.class).hasMessageContaining("dimension 3").hasMessageContaining("4");
    }

    @Test
    void emptyNullAndNonFiniteVectorsAreRejected() {
        for (float[] bad : new float[][] { null, new float[0], { 1f, Float.NaN }, { Float.POSITIVE_INFINITY, 1f } }) {
            var service = new StubService(configured(2), () -> bad);
            assertThatThrownBy(() -> service.generateEmbedding("x")).isInstanceOf(EmbeddingException.class);
        }
    }

    @Test
    void providerFailureBecomesCleanEmbeddingException() {
        var boom = new IllegalStateException("connection reset, key=secret-key");
        var service = new StubService(configured(2), () -> { throw boom; });
        assertThatThrownBy(() -> service.generateEmbedding("x"))
                .isInstanceOf(EmbeddingException.class).hasCause(boom)
                .hasMessage("Embedding provider call failed");     // message itself carries no provider detail
    }

    @Test
    void unconfiguredServiceNeverCallsTheProvider() {
        var placeholders = new EmbeddingProperties("PLACEHOLDER_TO_BE_DECIDED", "PLACEHOLDER_TO_BE_DECIDED",
                "PLACEHOLDER_TO_BE_PROVIDED", "PLACEHOLDER_TO_BE_PROVIDED_SECURELY", null, null);
        var service = new StubService(placeholders, () -> new float[] { 1f });
        assertThatThrownBy(() -> service.generateEmbedding("x"))
                .isInstanceOf(EmbeddingNotConfiguredException.class)
                .hasMessageContaining("EMBEDDING_PROVIDER").hasMessageContaining("EMBEDDING_DIMENSION");
        assertThat(service.calls).hasValue(0);
    }

    @Test
    void blankInputIsACallerBug() {
        var service = new StubService(configured(1), () -> new float[] { 1f });
        assertThatThrownBy(() -> service.generateEmbedding("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.generateEmbedding(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingSettingsAreListedByEnvName() {
        assertThat(configured(3).missing()).isEmpty();
        var partial = new EmbeddingProperties("openai-like", "m", "PLACEHOLDER_TO_BE_PROVIDED", "", 0, null);
        assertThat(partial.missing()).containsExactly("EMBEDDING_API_URL", "EMBEDDING_API_KEY", "EMBEDDING_DIMENSION");
        assertThat(partial.isConfigured()).isFalse();
    }

    @Test
    void apiKeyNeverAppearsInToString() {
        assertThat(configured(3).toString()).doesNotContain("secret-key").contains("<hidden>");
    }

    @Test
    void textBuilderFollowsTheConfiguredSource() {
        var both = new EmbeddingTextBuilder(configured(1));    // default = TITLE_AND_BODY (proposed)
        assertThat(both.build("Title", "Body")).isEqualTo("Title\n\nBody");
        var titleOnly = new EmbeddingTextBuilder(new EmbeddingProperties("p", "m", "u", "k", 1, EmbeddingTextSource.TITLE_ONLY));
        assertThat(titleOnly.build("Title", "Body")).isEqualTo("Title");
        var bodyOnly = new EmbeddingTextBuilder(new EmbeddingProperties("p", "m", "u", "k", 1, EmbeddingTextSource.BODY_ONLY));
        assertThat(bodyOnly.build("Title", "Body")).isEqualTo("Body");
    }
}
