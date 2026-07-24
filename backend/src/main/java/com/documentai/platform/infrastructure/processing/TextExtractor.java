package com.documentai.platform.infrastructure.processing;

import java.io.InputStream;

/** One implementation per document family (PDF/Word via Tika, Excel via POI, images via OCR). */
public interface TextExtractor {

    boolean supports(String extension);

    ExtractedText extract(InputStream content) throws Exception;
}
