package com.documentai.platform.mapper;

import com.documentai.platform.domain.entity.Document;
import com.documentai.platform.domain.entity.DocumentChunk;
import com.documentai.platform.dto.response.DocumentChunkResponse;
import com.documentai.platform.dto.response.DocumentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DocumentMapper {

    DocumentResponse toResponse(Document document);

    @Mapping(target = "documentId", source = "document.id")
    DocumentChunkResponse toChunkResponse(DocumentChunk chunk);
}
