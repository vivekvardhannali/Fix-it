package com.fixit.embedding;

/**
 * Why a text is being embedded. Retrieval models embed the two differently (an asymmetric model such as Jina v4 has a
 * separate "query" and "passage" mode), so each caller says which one it is.
 */
public enum EmbeddingPurpose {
    /** A stored question (title + body) that will be searched. */
    DOCUMENT,
    /** What a user typed into the search box. */
    QUERY
}
