package com.documentai.platform.infrastructure.processing;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/** Handles PDF and Word documents via Apache Tika's auto-detecting parser. */
@Component
public class TikaTextExtractor implements TextExtractor {

    @Override
    public boolean supports(String extension) {
        return SupportedExtensions.PDF.contains(extension.toLowerCase())
                || SupportedExtensions.WORD.contains(extension.toLowerCase());
    }

    @Override
    public ExtractedText extract(InputStream content) throws Exception {
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        new AutoDetectParser().parse(content, handler, metadata, new ParseContext());

        Integer pageCount = null;
        String pages = metadata.get("xmpTPg:NPages");
        if (pages != null) {
            try {
                pageCount = Integer.parseInt(pages);
            } catch (NumberFormatException ignored) {
                // metadata not numeric; leave pageCount unknown
            }
        }
        return new ExtractedText(handler.toString(), pageCount);
    }
}
