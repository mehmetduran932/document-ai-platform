package com.documentai.platform.infrastructure.processing;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SupportedExtensions {

    public static final Set<String> PDF = Set.of("pdf");
    public static final Set<String> WORD = Set.of("doc", "docx");
    public static final Set<String> EXCEL = Set.of("xls", "xlsx");
    public static final Set<String> IMAGE = Set.of("png", "jpg", "jpeg", "tiff", "tif", "bmp", "gif");
    public static final Set<String> MARKDOWN = Set.of("md");

    public static final Set<String> ALL = Stream.of(PDF, WORD, EXCEL, IMAGE, MARKDOWN)
            .flatMap(Set::stream)
            .collect(Collectors.toUnmodifiableSet());

    private SupportedExtensions() {
    }

    public static boolean isSupported(String extension) {
        return extension != null && ALL.contains(extension.toLowerCase());
    }
}
