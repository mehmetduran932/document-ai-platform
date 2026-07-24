package com.documentai.platform.infrastructure.keyword;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TermFrequencyKeywordExtractionServiceTest {

    private final TermFrequencyKeywordExtractionService service = new TermFrequencyKeywordExtractionService();

    @Test
    void blankTextProducesNoKeywords() {
        assertThat(service.extract("   ", 8)).isEmpty();
    }

    @Test
    void ranksMoreFrequentTermsHigher() {
        String text = "contract contract contract payment payment termination the and for with";

        List<ExtractedKeyword> keywords = service.extract(text, 8);

        assertThat(keywords).isNotEmpty();
        assertThat(keywords.get(0).keyword()).isEqualTo("contract");
        assertThat(keywords.stream().map(ExtractedKeyword::keyword))
                .doesNotContain("the", "and", "for", "with");
    }

    @Test
    void respectsMaxKeywordsLimit() {
        String text = "alpha beta gamma delta epsilon zeta eta theta iota kappa";

        List<ExtractedKeyword> keywords = service.extract(text, 3);

        assertThat(keywords).hasSize(3);
    }
}
