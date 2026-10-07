package com.fixit.search;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.fixit.embedding.EmbeddingException;
import com.fixit.embedding.EmbeddingNotConfiguredException;
import com.fixit.embedding.EmbeddingProperties;
import com.fixit.embedding.EmbeddingPurpose;
import com.fixit.embedding.EmbeddingService;
import com.fixit.embedding.EmbeddingTextBuilder;
import com.fixit.repository.QuestionRepository;

/**
 * Keeps question_embeddings in step with question text. Embeddings are DERIVED data, so this class never throws
 * for provider problems: a question is saved even if embedding fails (it can be re-embedded later with
 * {@link #reindexAll()}).
 */
@Service
public class QuestionEmbeddingService {

    public enum Outcome { EMBEDDED, UP_TO_DATE, FAILED }

    private static final Logger log = LoggerFactory.getLogger(QuestionEmbeddingService.class);

    private final EmbeddingService embeddings;
    private final EmbeddingProperties properties;
    private final EmbeddingTextBuilder textBuilder;
    private final QuestionEmbeddingRepository repository;
    private final QuestionRepository questions;
    private final TransactionTemplate readInNewTransaction;

    public QuestionEmbeddingService(EmbeddingService embeddings, EmbeddingProperties properties,
            EmbeddingTextBuilder textBuilder, QuestionEmbeddingRepository repository, QuestionRepository questions,
            PlatformTransactionManager transactionManager) {
        this.embeddings = embeddings;
        this.properties = properties;
        this.textBuilder = textBuilder;
        this.repository = repository;
        this.questions = questions;
        this.readInNewTransaction = new TransactionTemplate(transactionManager);
        this.readInNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readInNewTransaction.setReadOnly(true);
    }

    /** After the question's transaction has committed (so the provider call never holds a DB transaction open). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onQuestionChanged(QuestionContentChanged event) {
        try {
            refreshById(event.questionId());
        } catch (RuntimeException e) {
            log.warn("Could not refresh embedding of question {}: {}", event.questionId(), e.toString());
        }
    }

    /** Loads the question's current text, then refreshes. */
    public Outcome refreshById(long questionId) {
        String[] text = readInNewTransaction.execute(status -> questions.findById(questionId)
                .map(q -> new String[] { q.getTitle(), q.getBody() }).orElse(null));
        if (text == null) {
            return Outcome.FAILED;
        }
        return refresh(questionId, text[0], text[1]);
    }

    /** Embeds (or skips if already current) the given text for the question. Never throws for provider problems. */
    public Outcome refresh(long questionId, String title, String body) {
        if (!properties.isConfigured()) {
            log.debug("Embedding provider not configured; question {} not embedded", questionId);
            return Outcome.FAILED;
        }
        String text = textBuilder.build(title, body);
        String hash = sha256(text);
        int dimension = properties.dimension();

        var stored = repository.find(questionId);
        if (stored.isPresent() && stored.get().textHash().equals(hash)
                && stored.get().model().equals(properties.model()) && stored.get().dimension() == dimension) {
            return Outcome.UP_TO_DATE;
        }

        float[] vector;
        try {
            vector = embeddings.generateEmbedding(text, EmbeddingPurpose.DOCUMENT);
        } catch (EmbeddingNotConfiguredException e) {
            log.debug("Embedding not available for question {}: {}", questionId, e.getMessage());
            return Outcome.FAILED;
        } catch (EmbeddingException e) {
            log.warn("Embedding failed for question {}: {}", questionId, e.getMessage());
            return Outcome.FAILED;
        }
        if (vector == null || vector.length != dimension) {   // also guards providers that skip the base-class checks
            log.warn("Embedding for question {} has wrong size (expected {})", questionId, dimension);
            return Outcome.FAILED;
        }
        repository.upsert(questionId, vector, properties.model(), hash);
        return Outcome.EMBEDDED;
    }

    /**
     * Embeds every question that has no current embedding (missing, changed text, other model or dimension).
     * Safe to run repeatedly. Not exposed over HTTP (there are no admin roles): see app.embedding.reindex-on-startup.
     */
    public ReindexReport reindexAll() {
        var ids = questions.findAllIds();
        if (!properties.isConfigured()) {
            log.warn("Reindex skipped: embedding provider not configured (missing {})", properties.missing());
            return new ReindexReport(0, 0, ids.size());
        }
        int embedded = 0, upToDate = 0, failed = 0;
        for (Long id : ids) {
            switch (refreshById(id)) {
                case EMBEDDED -> embedded++;
                case UP_TO_DATE -> upToDate++;
                case FAILED -> failed++;
            }
        }
        log.info("Reindex finished: {} embedded, {} already current, {} failed", embedded, upToDate, failed);
        return new ReindexReport(embedded, upToDate, failed);
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
