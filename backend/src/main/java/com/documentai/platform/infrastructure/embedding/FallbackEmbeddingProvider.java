package com.documentai.platform.infrastructure.embedding;

import com.documentai.platform.config.EmbeddingProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The only bean the rest of the app sees as {@link EmbeddingProvider} when app.embedding-provider is
 * unset or 'fallback' (see {@link E5EmbeddingProvider} for the mutually-exclusive alternative).
 *
 * Two modes, controlled by {@code app.embedding.chain-enabled}:
 * <ul>
 *   <li><b>chain disabled</b> (deterministic): calls exactly {@code app.embedding.primary-vendor}
 *       every time, with no substitution - if it fails, the exception propagates. For deployments
 *       that want "always OpenAI" (or Gemini, or Voyage) and nothing else.</li>
 *   <li><b>chain enabled</b> (default): tries gemini, then openai, then voyage, so ingestion/search
 *       doesn't go down just because one vendor is unavailable or rate-limited. Whichever vendor
 *       succeeds is remembered ({@code stickyVendor}) and tried first on the next call - this
 *       matters because vectors from different vendors are not comparable via cosine similarity
 *       even at the same width: without stickiness, a burst of calls served by OpenAI (because
 *       Gemini was rate-limited) followed by a single later call that happens to catch Gemini
 *       recovered would silently mix two incompatible vector spaces in the same search. Stickiness
 *       is in-memory only (resets on restart) - {@link EmbeddingBackfillRunner} plus the
 *       {@code document_chunks.embedding_model} column handle correcting any drift that does occur,
 *       this just makes it much less likely to happen during normal operation.</li>
 * </ul>
 *
 * embed() cannot simply delegate to embedBatch(List.of(text)): Voyage's API distinguishes "query"
 * text from "document" text via an explicit input_type parameter (VoyageEmbeddingClient.embedQuery
 * vs embedBatch), so a single query must be routed through the query-specific method at every step,
 * not just wrapped in a one-element batch.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.search-provider", havingValue = "embedding")
@ConditionalOnProperty(name = "app.embedding-provider", havingValue = "fallback", matchIfMissing = true)
public class FallbackEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(FallbackEmbeddingProvider.class);
    private static final List<String> DEFAULT_CHAIN_ORDER = List.of("gemini", "openai", "voyage");

    private final EmbeddingProperties properties;
    private final GeminiEmbeddingClient geminiEmbeddingClient;
    private final OpenAiEmbeddingClient openAiEmbeddingClient;
    private final VoyageEmbeddingClient voyageEmbeddingClient;

    private final AtomicReference<String> stickyVendor = new AtomicReference<>();

    @Override
    public List<EmbeddingResult> embedBatch(List<String> texts) {
        if (!properties.chainEnabled()) {
            String vendor = properties.primaryVendor();
            String identity = identityFor(vendor);
            return callVendorBatch(vendor, texts).stream().map(v -> new EmbeddingResult(v, identity)).toList();
        }

        EmbeddingGenerationException lastError = null;
        for (String vendor : chainOrder()) {
            try {
                List<float[]> vectors = callVendorBatch(vendor, texts);
                stickyVendor.set(vendor);
                String identity = identityFor(vendor);
                return vectors.stream().map(v -> new EmbeddingResult(v, identity)).toList();
            } catch (EmbeddingGenerationException e) {
                log.warn("{} embeddings failed, trying next vendor: {}", vendor, e.getMessage());
                lastError = e;
            }
        }
        throw lastError;
    }

    @Override
    public EmbeddingResult embed(String text) {
        if (!properties.chainEnabled()) {
            String vendor = properties.primaryVendor();
            return new EmbeddingResult(callVendorQuery(vendor, text), identityFor(vendor));
        }

        EmbeddingGenerationException lastError = null;
        for (String vendor : chainOrder()) {
            try {
                float[] vector = callVendorQuery(vendor, text);
                stickyVendor.set(vendor);
                return new EmbeddingResult(vector, identityFor(vendor));
            } catch (EmbeddingGenerationException e) {
                log.warn("{} embeddings failed, trying next vendor: {}", vendor, e.getMessage());
                lastError = e;
            }
        }
        throw lastError;
    }

    /** Sticky vendor first (if one has succeeded before), then the remaining vendors in default order. */
    private List<String> chainOrder() {
        String sticky = stickyVendor.get();
        if (sticky == null) {
            return DEFAULT_CHAIN_ORDER;
        }
        List<String> order = new ArrayList<>(DEFAULT_CHAIN_ORDER.size());
        order.add(sticky);
        for (String vendor : DEFAULT_CHAIN_ORDER) {
            if (!vendor.equals(sticky)) {
                order.add(vendor);
            }
        }
        return order;
    }

    private List<float[]> callVendorBatch(String vendor, List<String> texts) {
        return switch (vendor) {
            case "gemini" -> geminiEmbeddingClient.embedBatch(texts);
            case "openai" -> openAiEmbeddingClient.embedBatch(texts);
            case "voyage" -> voyageEmbeddingClient.embedBatch(texts);
            default -> throw new IllegalStateException("Unknown embedding vendor: " + vendor);
        };
    }

    private float[] callVendorQuery(String vendor, String text) {
        return switch (vendor) {
            case "gemini" -> geminiEmbeddingClient.embedBatch(List.of(text)).get(0);
            case "openai" -> openAiEmbeddingClient.embedBatch(List.of(text)).get(0);
            case "voyage" -> voyageEmbeddingClient.embedQuery(text);
            default -> throw new IllegalStateException("Unknown embedding vendor: " + vendor);
        };
    }

    private String identityFor(String vendor) {
        return switch (vendor) {
            case "gemini" -> "gemini:" + properties.gemini().model();
            case "openai" -> "openai:" + properties.openai().model();
            case "voyage" -> "voyage:" + properties.voyage().model();
            default -> throw new IllegalStateException("Unknown embedding vendor: " + vendor);
        };
    }
}
