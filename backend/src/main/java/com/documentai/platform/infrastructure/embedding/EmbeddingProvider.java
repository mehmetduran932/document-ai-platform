package com.documentai.platform.infrastructure.embedding;

import java.util.List;

/**
 * Turns text into a dense vector for semantic (embedding-based) search. Kept separate from
 * {@link com.documentai.platform.infrastructure.answer.AnswerProvider} even though both may call
 * the same vendor API - embeddings and chat completions are different capabilities with
 * different failure modes and lifecycles (embeddings are computed once at ingest time and
 * re-used; answers are generated per-question).
 */
public interface EmbeddingProvider {

    /**
     * {@code modelIdentity} names exactly which vendor/model produced {@code vector} (e.g.
     * "openai:text-embedding-3-small"), persisted alongside it in
     * {@code document_chunks.embedding_model} so a later query only ever compares vectors from
     * the same vendor - vectors from different vendors are not comparable via cosine similarity
     * even at the same dimensionality. Returned rather than fixed per-provider because
     * FallbackEmbeddingProvider can only know which vendor actually served a given call after it
     * succeeds.
     */
    record EmbeddingResult(float[] vector, String modelIdentity) {
    }

    /** One embedding per input, same order as {@code texts}. Batched in one call where the vendor API allows it. */
    List<EmbeddingResult> embedBatch(List<String> texts);

    EmbeddingResult embed(String text);
}
