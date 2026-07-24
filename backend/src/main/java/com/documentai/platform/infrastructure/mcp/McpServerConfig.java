package com.documentai.platform.infrastructure.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the MCP server into the same Spring Boot app, mounted at /mcp. Workspace scoping is
 * resolved per-request by {@link WorkspaceTransportContextExtractor} from the X-API-Key header;
 * the /mcp/** path itself is additionally gated by Spring Security (see SecurityConfig) so a
 * request with no valid key never reaches the servlet at all.
 */
@Configuration
@RequiredArgsConstructor
public class McpServerConfig {

    private static final String MCP_ENDPOINT = "/mcp";

    private final WorkspaceTransportContextExtractor workspaceTransportContextExtractor;
    private final DocumentMcpTools documentMcpTools;

    @Bean
    public HttpServletStreamableServerTransportProvider mcpTransportProvider(ObjectMapper objectMapper) {
        McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint(MCP_ENDPOINT)
                .contextExtractor(workspaceTransportContextExtractor)
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider transportProvider) {
        return new ServletRegistrationBean<>(transportProvider, MCP_ENDPOINT, MCP_ENDPOINT + "/*");
    }

    @Bean(destroyMethod = "closeGracefully")
    public McpSyncServer mcpSyncServer(HttpServletStreamableServerTransportProvider transportProvider) {
        McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo("document-ai-platform-mcp", "0.1.0")
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .instructions("Read-only document access for this workspace. Use search_documents to find "
                        + "relevant chunks before reading whole documents. This server never generates answers.")
                .build();
        documentMcpTools.toolSpecifications().forEach(server::addTool);
        return server;
    }
}
