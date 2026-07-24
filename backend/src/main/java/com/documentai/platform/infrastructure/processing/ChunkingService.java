package com.documentai.platform.infrastructure.processing;

import com.documentai.platform.config.ProcessingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits extracted plain text into overlapping word-count-bounded chunks
 * (default: up to 700 words per chunk, 50-word overlap between consecutive chunks;
 * see app.processing.chunk-* properties).
 *
 * Page association is best-effort: extractors that cannot reliably attribute text to a page
 * (PDF/Word via Tika, OCR output) leave chunk.page() null rather than guess.
 */
@Component
@RequiredArgsConstructor
public class ChunkingService {

    private final ProcessingProperties properties;

    public List<ChunkDraft> chunk(String text, Integer page) {
        List<String> words = List.of(text.trim().split("\\s+")).stream()
                .filter(w -> !w.isBlank())
                .toList();

        List<ChunkDraft> chunks = new ArrayList<>();
        if (words.isEmpty()) {
            return chunks;
        }

        int maxWords = properties.chunkMaxWords();
        int overlap = properties.chunkOverlapWords();
        int step = Math.max(1, maxWords - overlap);

        int index = 0;
        int start = 0;
        while (start < words.size()) {
            int end = Math.min(start + maxWords, words.size());
            String content = String.join(" ", words.subList(start, end));
            chunks.add(new ChunkDraft(page, index++, content, end - start));
            if (end == words.size()) {
                break;
            }
            start += step;
        }
        return chunks;
    }
}
