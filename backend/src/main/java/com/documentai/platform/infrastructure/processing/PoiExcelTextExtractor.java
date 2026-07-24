package com.documentai.platform.infrastructure.processing;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/** Handles Excel spreadsheets (.xls / .xlsx) via Apache POI. */
@Component
public class PoiExcelTextExtractor implements TextExtractor {

    @Override
    public boolean supports(String extension) {
        return SupportedExtensions.EXCEL.contains(extension.toLowerCase());
    }

    @Override
    public ExtractedText extract(InputStream content) throws Exception {
        StringBuilder text = new StringBuilder();
        DataFormatter formatter = new DataFormatter();

        try (Workbook workbook = WorkbookFactory.create(content)) {
            int sheetCount = workbook.getNumberOfSheets();
            for (int s = 0; s < sheetCount; s++) {
                Sheet sheet = workbook.getSheetAt(s);
                text.append("# ").append(sheet.getSheetName()).append('\n');
                for (Row row : sheet) {
                    boolean firstCell = true;
                    for (Cell cell : row) {
                        String value = formatter.formatCellValue(cell);
                        if (value == null || value.isBlank()) {
                            continue;
                        }
                        if (!firstCell) {
                            text.append('\t');
                        }
                        text.append(value);
                        firstCell = false;
                    }
                    text.append('\n');
                }
                text.append('\n');
            }
            return new ExtractedText(text.toString(), sheetCount);
        }
    }
}
