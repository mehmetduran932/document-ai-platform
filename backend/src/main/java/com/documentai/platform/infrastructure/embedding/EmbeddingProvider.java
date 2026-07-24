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

    /** One embedding per input, same order as {@code texts}. Batched in one call where the vendor API allows it. */
    List<float[]> embedBatch(List<String> texts);

    float[] embed(String text);
}
