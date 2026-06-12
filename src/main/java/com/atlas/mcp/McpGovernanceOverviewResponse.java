package com.atlas.mcp;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only governance overview for future MCP interoperability.
 *
 * <p>中文说明：这是给学习和前端治理页面使用的 MCP 安全总览契约。
 * 它只解释当前 manifest 能暴露什么、哪些能力被阻断、未来打开执行层需要哪些证据；
 * 它不是 MCP Server，也不是 Tool 调用入口。</p>
 *
 * <p>The response is intentionally a contract and teaching surface. It does not
 * expose a live MCP server, accept {@code tools/call}, or execute Atlas Tools.</p>
 */
public record McpGovernanceOverviewResponse(
    String schemaVersion,
    Instant generatedAt,
    String governanceStatus,
    String manifestMode,
    boolean manifestEndpointExists,
    boolean toolSchemaAdapterExists,
    boolean mcpServerRuntimeEnabled,
    boolean toolsCallEnabled,
    boolean externalToolExecutionEnabled,
    boolean callerProvidedToolCallAccepted,
    int exportedToolCount,
    int blockedToolCount,
    int totalToolCount,
    List<Map<String, Object>> governanceCards,
    List<String> recommendedWorkflow,
    List<String> blockedCapabilities,
    Map<String, Object> manifestStats,
    Map<String, Object> futureEnablementProtocol,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-mcp-governance-overview.v1";

    public static McpGovernanceOverviewResponse of(Instant generatedAt, Map<String, Object> manifest) {
        Map<String, Object> stats = objectMap(manifest.get("stats"));
        Map<String, Object> policy = objectMap(manifest.get("policy"));
        int totalTools = intValue(stats.get("totalTools"));
        int exportedTools = intValue(stats.get("exportedTools"));
        int blockedTools = intValue(stats.get("blockedTools"));
        return new McpGovernanceOverviewResponse(
            SCHEMA_VERSION,
            generatedAt,
            "MANIFEST_ONLY_NOT_CALLABLE",
            text(policy.get("mode"), "safe-readonly-manifest"),
            true,
            true,
            false,
            false,
            false,
            false,
            exportedTools,
            blockedTools,
            totalTools,
            governanceCards(totalTools, exportedTools, blockedTools, policy),
            buildRecommendedWorkflow(),
            buildBlockedCapabilities(),
            Map.copyOf(stats),
            buildFutureEnablementProtocol(),
            buildSafety(),
            buildPrivacy()
        );
    }

    private static List<Map<String, Object>> governanceCards(int totalTools,
                                                            int exportedTools,
                                                            int blockedTools,
                                                            Map<String, Object> policy) {
        return List.of(
            card(
                "manifest-export-policy",
                "Read-only MCP manifest export policy",
                "READY",
                "INFO",
                Map.of(
                    "manifestMode", text(policy.get("mode"), "safe-readonly-manifest"),
                    "failClosed", Boolean.TRUE.equals(policy.get("failClosed")),
                    "exportRule", text(policy.get("exportRule"), "permission=PUBLIC && operationType=READ"),
                    "phase2DomainsBlocked", policy.getOrDefault("phase2DomainsBlocked", List.of())
                )
            ),
            card(
                "tool-schema-adapter",
                "Tool schema adapter contract",
                "DEFINED_NOT_RUNTIME_CALLABLE",
                "BLOCKING",
                Map.of(
                    "toolSchemaAdapterExists", true,
                    "structuredToolCallAccepted", false,
                    "callerProvidedToolCallAccepted", false
                )
            ),
            card(
                "mcp-runtime-server",
                "External MCP server/runtime call plane",
                "NOT_ENABLED",
                "BLOCKING",
                Map.of(
                    "mcpServerRuntimeEnabled", false,
                    "toolsListRuntimeEnabled", false,
                    "toolsCallRuntimeEnabled", false
                )
            ),
            card(
                "safe-tool-executor-binding",
                "Future SafeToolExecutor binding gate",
                "REQUIRED_BEFORE_CALLS",
                "BLOCKING",
                Map.of(
                    "safeToolExecutorRequired", true,
                    "hitlRequiredForRiskyTools", true,
                    "durableAuditRequiredForHighRiskTools", true
                )
            ),
            card(
                "manifest-coverage",
                "Current manifest coverage",
                exportedTools > 0 ? "PARTIAL_EXPORT" : "EMPTY_EXPORT",
                "INFO",
                Map.of(
                    "totalTools", totalTools,
                    "exportedTools", exportedTools,
                    "blockedTools", blockedTools
                )
            )
        );
    }

    private static Map<String, Object> card(String id,
                                            String title,
                                            String status,
                                            String severity,
                                            Map<String, Object> evidence) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", id);
        card.put("title", title);
        card.put("status", status);
        card.put("severity", severity);
        card.put("evidence", Map.copyOf(evidence));
        card.put("readOnly", true);
        card.put("runtimeMutationAllowed", false);
        card.put("toolExecution", false);
        card.put("externalCalls", false);
        card.put("mcpToolsCall", false);
        return Map.copyOf(card);
    }

    private static List<String> buildRecommendedWorkflow() {
        return List.of(
            "read-mcp-governance-overview",
            "read-safe-manifest",
            "review-exported-read-only-tools",
            "bind-future-tools-call-through-safe-tool-executor",
            "add-hitl-audit-eval-consent-before-runtime-mcp-server"
        );
    }

    private static List<String> buildBlockedCapabilities() {
        return List.of(
            "mcp-runtime-server",
            "mcp-tools-call",
            "mcp-tools-call-streaming",
            "external-agent-tool-execution",
            "caller-provided-tool-call-arguments",
            "write-tool-export",
            "sensitive-read-tool-export",
            "phase2-domain-tool-export",
            "runtime-tool-registry-mutation"
        );
    }

    private static Map<String, Object> buildFutureEnablementProtocol() {
        Map<String, Object> protocol = new LinkedHashMap<>();
        protocol.put("enablementMode", "future-code-release-only");
        protocol.put("runtimeToggleAllowed", false);
        protocol.put("callerCanEnableToolsCall", false);
        protocol.put("minimumRequiredChecks", List.of(
            "schema-versioned-tool-descriptors",
            "per-tool-export-allowlist",
            "per-tool-consent-policy",
            "SafeToolExecutor-only-runtime-binding",
            "principal-and-tenant-binding",
            "HITL-for-risky-tools",
            "durable-audit-prewrite-for-high-risk-tools",
            "redacted-replay-and-eval-gate-evidence",
            "rate-limit-and-timeout-policy",
            "frontend-operator-observability"
        ));
        protocol.put("defaultIfAnyCheckMissing", "fail-closed-manifest-only");
        return Map.copyOf(protocol);
    }

    private static Map<String, Object> buildSafety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("authenticatedEndpoint", true);
        safety.put("readOnly", true);
        safety.put("manifestOnly", true);
        safety.put("mcpServerRuntimeEnabled", false);
        safety.put("toolsListRuntimeEnabled", false);
        safety.put("toolsCallRuntimeEnabled", false);
        safety.put("toolExecution", false);
        safety.put("safeToolExecutorInvoked", false);
        safety.put("hitlInvocation", false);
        safety.put("auditWrite", false);
        safety.put("durableReceiptIssued", false);
        safety.put("externalCalls", false);
        safety.put("llmUsed", false);
        safety.put("runtimeToolRegistryMutation", false);
        safety.put("writeToolExportAllowed", false);
        safety.put("sensitiveReadToolExportAllowed", false);
        safety.put("phase2DomainToolExportAllowed", false);
        return Map.copyOf(safety);
    }

    private static Map<String, Object> buildPrivacy() {
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", true);
        privacy.put("containsRawEndpoint", false);
        privacy.put("containsRawBackendPath", false);
        privacy.put("containsAuthorizationHeader", false);
        privacy.put("containsToken", false);
        privacy.put("containsLoginPassword", false);
        privacy.put("containsRawPrincipal", false);
        privacy.put("containsRawOrganization", false);
        privacy.put("containsRawRequestBody", false);
        privacy.put("containsRawResponseBody", false);
        return Map.copyOf(privacy);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return Map.copyOf(result);
        }
        return Map.of();
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private static String text(Object value, String fallback) {
        return value != null && !String.valueOf(value).isBlank()
            ? String.valueOf(value)
            : fallback;
    }
}
