package com.documentai.platform.infrastructure.processing;

import com.documentai.platform.config.ProcessingProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingServiceTest {

    private final ProcessingProperties properties =
            new ProcessingProperties(400, 700, 50, "unused", "eng", "simple");
    private final ChunkingService chunkingService = new ChunkingService(properties);

    @Test
    void emptyTextProducesNoChunks() {
        assertThat(chunkingService.chunk("   ", null)).isEmpty();
    }

    @Test
    void textShorterThanMaxWordsProducesOneChunk() {
        String text = words(50);

        List<ChunkDraft> chunks = chunkingService.chunk(text, null);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).wordCount()).isEqualTo(50);
        assertThat(chunks.get(0).chunkIndex()).isZero();
    }

    @Test
    void longTextIsSplitWithConfiguredOverlap() {
        String text = words(1500);

        List<ChunkDraft> chunks = chunkingService.chunk(text, null);

        assertThat(chunks.size()).isGreaterThan(1);
        // Every non-last chunk must hit the configured max size exactly.
        for (int i = 0; i < chunks.size() - 1; i++) {
            assertThat(chunks.get(i).wordCount()).isEqualTo(700);
        }
        // Chunk indices are sequential starting at 0.
        assertThat(chunks.stream().map(ChunkDraft::chunkIndex).toList())
                .isEqualTo(IntStream.range(0, chunks.size()).boxed().toList());

        // Consecutive chunks overlap by exactly the configured overlap word count.
        String firstChunkLast50 = lastWords(chunks.get(0).content(), 50);
        String secondChunkFirst50 = firstWords(chunks.get(1).content(), 50);
        assertThat(secondChunkFirst50).isEqualTo(firstChunkLast50);
    }

    private String words(int count) {
        return IntStream.range(0, count).mapToObj(i -> "w" + i).collect(Collectors.joining(" "));
    }

    private String firstWords(String text, int count) {
        return String.join(" ", List.of(text.split(" ")).subList(0, count));
    }

    private String lastWords(String text, int count) {
        String[] all = text.split(" ");
        return String.join(" ", List.of(all).subList(all.length - count, all.length));
    }
}
