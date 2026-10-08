package com.fixit.search;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * In-memory Okapi BM25 over every question's title + body. Built lazily and rebuilt automatically when the questions
 * table changes (see {@link LexicalCorpusRepository#fingerprint()}); fine at campus scale. If the corpus ever gets
 * very large, replace this class (e.g. with PostgreSQL full-text search); callers only use {@link #score}.
 *
 * Score of a question for a query:
 *   raw  = sum over query terms t of  idf(t) * tf * (k1 + 1) / (tf + k1 * (1 - b + b * dl / avgdl)),
 *          idf(t) = ln(1 + (N - df + 0.5) / (df + 0.5))                        (never negative)
 *   norm = raw / sum over the query terms that occur in the corpus of idf(t) * (k1 + 1)
 * Each term contributes strictly less than idf*(k1+1), so norm is in [0, 1): "what share of the best-possible
 * evidence for this query the question has". It is comparable across queries, which a threshold and the hybrid
 * mix need (raw BM25 is not). Questions with no query term are omitted (score 0).
 */
@Component
public class Bm25Index {

    private record Snapshot(String fingerprint, Map<String, Map<Long, Integer>> postings,
            Map<Long, Integer> lengths, double averageLength) {
    }

    private final LexicalCorpusRepository corpus;
    private volatile Snapshot snapshot;

    public Bm25Index(LexicalCorpusRepository corpus) {
        this.corpus = corpus;
    }

    /** Forces a rebuild on next use (needed only when text is changed behind the application's back). */
    public void invalidate() {
        snapshot = null;
    }

    public Map<Long, Double> score(Collection<String> queryTerms, double k1, double b) {
        Snapshot s = current();
        int n = s.lengths().size();
        Map<Long, Double> raw = new HashMap<>();
        double best = 0;
        for (String term : new LinkedHashSet<>(queryTerms)) {
            Map<Long, Integer> postings = s.postings().get(term);
            if (postings == null) {
                continue;                       // unknown term: nothing can match it, so it does not count
            }
            int df = postings.size();
            double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
            best += idf * (k1 + 1);
            for (var posting : postings.entrySet()) {
                double tf = posting.getValue();
                double dl = s.lengths().get(posting.getKey());
                double contribution = idf * tf * (k1 + 1) / (tf + k1 * (1 - b + b * dl / s.averageLength()));
                raw.merge(posting.getKey(), contribution, Double::sum);
            }
        }
        if (best == 0) {
            return Map.of();
        }
        final double denominator = best;
        raw.replaceAll((id, value) -> value / denominator);
        return raw;
    }

    private Snapshot current() {
        String fingerprint = corpus.fingerprint();
        Snapshot s = snapshot;
        if (s != null && s.fingerprint().equals(fingerprint)) {
            return s;
        }
        synchronized (this) {
            s = snapshot;
            if (s == null || !s.fingerprint().equals(fingerprint)) {
                s = build(fingerprint);
                snapshot = s;
            }
            return s;
        }
    }

    private Snapshot build(String fingerprint) {
        Map<String, Map<Long, Integer>> postings = new HashMap<>();
        Map<Long, Integer> lengths = new HashMap<>();
        long[] total = { 0 };
        corpus.forEachQuestion(doc -> {
            var tokens = Tokenizer.tokens(doc.title() + " " + doc.body());
            lengths.put(doc.questionId(), tokens.size());
            total[0] += tokens.size();
            for (String token : tokens) {
                postings.computeIfAbsent(token, k -> new HashMap<>()).merge(doc.questionId(), 1, Integer::sum);
            }
        });
        double average = lengths.isEmpty() || total[0] == 0 ? 1 : (double) total[0] / lengths.size();
        return new Snapshot(fingerprint, postings, lengths, average);
    }
}
