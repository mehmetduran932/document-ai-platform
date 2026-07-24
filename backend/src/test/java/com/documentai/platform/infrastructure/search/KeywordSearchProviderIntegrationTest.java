package com.documentai.platform.infrastructure.search;

import com.documentai.platform.AbstractIntegrationTest;
import com.documentai.platform.domain.entity.Document;
import com.documentai.platform.domain.entity.DocumentChunk;
import com.documentai.platform.domain.entity.Workspace;
import com.documentai.platform.domain.enums.ProcessingStatus;
import com.documentai.platform.repository.DocumentChunkRepository;
import com.documentai.platform.repository.DocumentRepository;
import com.documentai.platform.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the whole FTS round-trip: the DB trigger populates search_vector on insert, and
 * KeywordSearchProvider's plainto_tsquery/ts_rank query finds and ranks it correctly - and that
 * results never cross workspace boundaries. This is the seam the whole SearchProvider
 * abstraction depends on being correct.
 */
class KeywordSearchProviderIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KeywordSearchProvider keywordSearchProvider;
    @Autowired
    private WorkspaceRepository workspaceRepository;
    @Autowired
    private DocumentRepository documentRepository;
    @Autowired
    private DocumentChunkRepository chunkRepository;

    private UUID workspaceAId;
    private UUID workspaceBId;

    @BeforeEach
    void setUp() {
        Workspace workspaceA = workspaceRepository.save(Workspace.builder().name("Workspace A").build());
        Workspace workspaceB = workspaceRepository.save(Workspace.builder().name("Workspace B").build());
        workspaceAId = workspaceA.getId();
        workspaceBId = workspaceB.getId();

        Document docA = documentRepository.save(Document.builder()
                .workspace(workspaceA)
                .filename("contract.pdf")
                .extension("pdf")
                .size(1024)
                .storageKey("a/contract.pdf")
                .processingStatus(ProcessingStatus.COMPLETED)
                .build());

        chunkRepository.save(DocumentChunk.builder()
                .document(docA)
                .workspace(workspaceA)
                .chunkIndex(0)
                .content("This contract includes a termination clause and detailed payment terms for both parties.")
                .wordCount(14)
                .build());

        Document docB = documentRepository.save(Document.builder()
                .workspace(workspaceB)
                .filename("recipe.pdf")
                .extension("pdf")
                .size(512)
                .storageKey("b/recipe.pdf")
                .processingStatus(ProcessingStatus.COMPLETED)
                .build());

        chunkRepository.save(DocumentChunk.builder()
                .document(docB)
                .workspace(workspaceB)
                .chunkIndex(0)
                .content("Mix the flour with sugar and bake at 180 degrees for termination of hunger.")
                .wordCount(13)
                .build());
    }

    @Test
    void findsRelevantChunkByKeyword() {
        List<SearchResultChunk> results = keywordSearchProvider.search(
                new SearchQuery(workspaceAId, "payment termination", List.of("payment", "termination"), 8));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).documentFilename()).isEqualTo("contract.pdf");
        assertThat(results.get(0).relevanceScore()).isGreaterThan(0);
    }

    @Test
    void neverReturnsChunksFromAnotherWorkspace() {
        List<SearchResultChunk> results = keywordSearchProvider.search(
                new SearchQuery(workspaceAId, "termination", List.of("termination"), 8));

        assertThat(results).extracting(SearchResultChunk::documentFilename).containsOnly("contract.pdf");
    }

    @Test
    void returnsEmptyWhenNoTermsMatch() {
        List<SearchResultChunk> results = keywordSearchProvider.search(
                new SearchQuery(workspaceAId, "spaceship galaxy", List.of("spaceship", "galaxy"), 8));

        assertThat(results).isEmpty();
    }

    @Test
    void respectsMaxResultsLimit() {
        List<SearchResultChunk> results = keywordSearchProvider.search(
                new SearchQuery(workspaceAId, "contract", List.of("contract"), 0));

        assertThat(results).isEmpty();
    }
}
