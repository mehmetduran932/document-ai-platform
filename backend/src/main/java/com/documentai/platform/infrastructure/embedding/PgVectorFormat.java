package com.documentai.platform.infrastructure.embedding;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** pgvector accepts/returns its column type as text in "[v1,v2,...]" form over JDBC. */
public final class PgVectorFormat {

    private PgVectorFormat() {
    }

    public static String toLiteral(float[] vector) {
        return IntStream.range(0, vector.length)
                .mapToObj(i -> Float.toString(vector[i]))
                .collect(Collectors.joining(",", "[", "]"));
    }
}
