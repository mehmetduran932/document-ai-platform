package com.documentai.platform.infrastructure.processing;

import com.documentai.platform.config.ProcessingProperties;
import lombok.RequiredArgsConstructor;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Handles scanned images via Tesseract OCR (native binary, wrapped by tess4j). */
@Component
@RequiredArgsConstructor
public class TesseractOcrTextExtractor implements TextExtractor {

    private final ProcessingProperties processingProperties;

    @Override
    public boolean supports(String extension) {
        return SupportedExtensions.IMAGE.contains(extension.toLowerCase());
    }

    @Override
    public ExtractedText extract(InputStream content) throws Exception {
        Path tempFile = Files.createTempFile("ocr-", ".tmp");
        try {
            Files.copy(content, tempFile, StandardCopyOption.REPLACE_EXISTING);

            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(processingProperties.tesseractDataPath());
            tesseract.setLanguage(processingProperties.tesseractLanguage());

            String text = tesseract.doOCR(tempFile.toFile());
            return new ExtractedText(text, 1);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
