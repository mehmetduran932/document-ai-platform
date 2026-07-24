package com.documentai.platform.infrastructure.mcp;

import com.documentai.platform.dto.response.DocumentChunkResponse;
import com.documentai.platform.dto.response.DocumentResponse;
import com.documentai.platform.dto.response.PageResponse;
import com.documentai.platform.dto.response.SearchResultResponse;
import com.documentai.platform.exception.ResourceNotFoundException;
import com.documentai.platform.service.DocumentService;
import com.documentai.platform.service.SearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * The five MCP tools external agents (Claude Code, Codex, Gemini CLI, ...) use to read a
 * workspace's documents. This layer is deliberately read/access-only: it never calls an LLM and
 * never generates an answer, it only fetches metadata and chunks that were already produced by
 * the search/processing pipeline.
 */
@Component
@RequiredArgsConstructor
public class DocumentMcpTools {

    private static final Logger log = LoggerFactory.getLogger(DocumentMcpTools.class);
    private static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(15);

    private final DocumentService documentService;
    private final SearchService searchService;
    private final ObjectMapper objectMapper;

    public List<SyncToolSpecification> toolSpecifications() {
        return List.of(listDocuments(), searchDocuments(), readDocumentChunk(), getDocumentMetadata(), downloadDocument());
    }

    private SyncToolSpecification listDocuments() {
        Tool tool = Tool.builder()
                .name("list_documents")
                .description("List documents in the caller's workspace, paginated.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "page", Map.of("type", "integer", "description", "Zero-based page index (default 0)"),
                                "size", Map.of("type", "integer", "description", "Page size, max 100 (default 20)")
                        )))
                .build();

        return withAuth(tool, (workspaceId, request) -> {
            int page = intArg(request, "page", 0);
            int size = Math.min(intArg(request, "size", 20), 100);
            PageResponse<DocumentResponse> result = documentService.listDocuments(workspaceId, page, size);
            return jsonResult(result);
        });
    }

    private SyncToolSpecification searchDocuments() {
        Tool tool = Tool.builder()
                .name("search_documents")
                .description("Search the caller's workspace for the chunks most relevant to a natural-language "
                        + "question. Returns at most 8 ranked chunks - never whole documents.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "The question or search phrase")
                        ),
                        "required", List.of("query")))
                .build();

        return withAuth(tool, (workspaceId, request) -> {
            String query = stringArg(request, "query", null);
            if (query == null || query.isBlank()) {
                return errorResult("query is required");
            }
            SearchResultResponse.SearchResponseWrapper result = searchService.search(workspaceId, query);
            return jsonResult(result);
        });
    }

    private SyncToolSpecification readDocumentChunk() {
        Tool tool = Tool.builder()
                .name("read_document_chunk")
                .description("Read a single document chunk (a few hundred words) by its id.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "chunkId", Map.of("type", "string", "description", "UUID of the chunk")
                        ),
                        "required", List.of("chunkId")))
                .build();

        return withAuth(tool, (workspaceId, request) -> {
            UUID chunkId = uuidArg(request, "chunkId");
            if (chunkId == null) {
                return errorResult("chunkId must be a valid UUID");
            }
            DocumentChunkResponse chunk = documentService.getChunk(workspaceId, chunkId);
            return jsonResult(chunk);
        });
    }

    private SyncToolSpecification getDocumentMetadata() {
        Tool tool = Tool.builder()
                .name("get_document_metadata")
                .description("Get metadata (filename, size, processing status, page count, ...) for a document.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "documentId", Map.of("type", "string", "description", "UUID of the document")
                        ),
                        "required", List.of("documentId")))
                .build();

        return withAuth(tool, (workspaceId, request) -> {
            UUID documentId = uuidArg(request, "documentId");
            if (documentId == null) {
                return errorResult("documentId must be a valid UUID");
            }
            DocumentResponse document = documentService.getDocumentMetadata(workspaceId, documentId);
            return jsonResult(document);
        });
    }

    private SyncToolSpecification downloadDocument() {
        Tool tool = Tool.builder()
                .name("download_document")
                .description("Get a short-lived, direct download URL for a document's original file. "
                        + "The MCP server does not stream file bytes itself.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "documentId", Map.of("type", "string", "description", "UUID of the document")
                        ),
                        "required", List.of("documentId")))
                .build();

        return withAuth(tool, (workspaceId, request) -> {
            UUID documentId = uuidArg(request, "documentId");
            if (documentId == null) {
                return errorResult("documentId must be a valid UUID");
            }
            DocumentResponse metadata = documentService.getDocumentMetadata(workspaceId, documentId);
            String url = documentService.getPresignedDownloadUrl(workspaceId, documentId);
            return jsonResult(Map.of(
                    "documentId", documentId.toString(),
                    "filename", metadata.filename(),
                    "downloadUrl", url,
                    "expiresInMinutes", DOWNLOAD_URL_TTL.toMinutes()
            ));
        });
    }

    /** Wraps a handler with workspace-from-API-key resolution and uniform error handling. */
    private SyncToolSpecification withAuth(Tool tool, BiFunction<UUID, CallToolRequest, CallToolResult> handler) {
        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    UUID workspaceId = extractWorkspaceId(exchange);
                    if (workspaceId == null) {
                        return errorResult("Unauthorized: missing or invalid workspace API key (X-API-Key header)");
                    }
                    try {
                        return handler.apply(workspaceId, request);
                    } catch (ResourceNotFoundException e) {
                        return errorResult(e.getMessage());
                    } catch (Exception e) {
                        log.error("MCP tool '{}' failed", tool.name(), e);
                        return errorResult("Tool execution failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private UUID extractWorkspaceId(McpSyncServerExchange exchange) {
        Object value = exchange.transportContext().get(McpAuthContext.WORKSPACE_ID_KEY);
        return value instanceof UUID uuid ? uuid : null;
    }

    private CallToolResult jsonResult(Object body) {
        try {
            return CallToolResult.builder().addTextContent(objectMapper.writeValueAsString(body)).build();
        } catch (Exception e) {
            return errorResult("Failed to serialize tool result: " + e.getMessage());
        }
    }

    private CallToolResult errorResult(String message) {
        return CallToolResult.builder().isError(true).addTextContent(message).build();
    }

    @SuppressWarnings("unchecked")
    private String stringArg(CallToolRequest request, String name, String defaultValue) {
        Object value = request.arguments().get(name);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private int intArg(CallToolRequest request, String name, int defaultValue) {
        Object value = request.arguments().get(name);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return defaultValue;
    }

    private UUID uuidArg(CallToolRequest request, String name) {
        String raw = stringArg(request, name, null);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
