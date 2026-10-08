package com.fixit.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lexical tokenisation: lower-case, split on anything that is not a letter or digit, drop 1-character tokens and
 * a small English stop-word list. No stemming (so "disconnect" and "disconnecting" are different terms - the
 * semantic part of hybrid search is what bridges such differences).
 */
public final class Tokenizer {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "are", "but", "not", "you", "all", "any", "can", "had", "her", "was", "one", "our",
            "out", "has", "have", "his", "how", "its", "may", "who", "why", "with", "this", "that", "from", "they",
            "will", "what", "when", "where", "which", "been", "were", "into", "than", "then", "them", "there",
            "about", "would", "could", "should", "does", "did", "is", "it", "in", "of", "to", "on", "at", "by", "an",
            "as", "be", "or", "if", "my", "me", "we", "so", "do", "am", "no", "up");

    private Tokenizer() {
    }

    public static List<String> tokens(String text) {
        List<String> tokens = new ArrayList<>();
        for (String t : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (t.length() >= 2 && !STOP_WORDS.contains(t)) {
                tokens.add(t);
            }
        }
        return tokens;
    }
}
