# M5.56 MCP Governance Overview

Date: 2026-06-09

## Summary

M5.56 adds a read-only governance overview for MCP interoperability:

```text
McpToolManifestService
        |
        | safe-readonly-manifest
        v
McpGovernanceOverviewService
        |
        v
GET /api/agent/mcp/governance/overview
```

The endpoint explains the current MCP posture: Atlas can publish a safe manifest of eligible read-only tools, but it does not expose a live MCP server or accept runtime `tools/call`.

## Delivered

- Added `McpGovernanceOverviewResponse`.
- Added `McpGovernanceOverviewService`.
- Added authenticated endpoint:

```text
GET /api/agent/mcp/governance/overview
```

- Added governance cards:
  - `manifest-export-policy`
  - `tool-schema-adapter`
  - `mcp-runtime-server`
  - `safe-tool-executor-binding`
  - `manifest-coverage`

## Current State

- `governanceStatus=MANIFEST_ONLY_NOT_CALLABLE`
- `manifestMode=safe-readonly-manifest`
- `manifestEndpointExists=true`
- `toolSchemaAdapterExists=true`
- `mcpServerRuntimeEnabled=false`
- `toolsCallEnabled=false`
- `externalToolExecutionEnabled=false`
- `callerProvidedToolCallAccepted=false`

## Security Boundary

M5.56 does not add:

- MCP runtime server
- `tools/call` handler
- streaming tool call plane
- external Agent tool execution
- caller-provided tool-call argument acceptance
- Tool execution
- `SafeToolExecutor` invocation
- HITL invocation
- audit write
- durable receipt issuance
- external calls
- LLM calls
- runtime Tool registry mutation
- write-tool export
- sensitive-read Tool export
- kube-manager calls
- `RestClient`
- `WebClient`

The endpoint is authenticated, read-only, and manifest-only.

## Future Enablement Protocol

Future MCP `tools/call` support must be a separate code release and must prove:

- schema-versioned tool descriptors
- per-tool export allowlist
- per-tool consent policy
- SafeToolExecutor-only runtime binding
- principal and tenant binding
- HITL for risky tools
- durable audit prewrite for high-risk tools
- redacted replay and eval gate evidence
- rate-limit and timeout policy
- frontend operator observability

Default if any check is missing:

```text
fail-closed-manifest-only
```

## Learning Note

MCP is not “把工具直接开放给外部 Agent”。成熟做法是先把 Tool 暴露面拆成三层：

1. `manifest/list`：只读发现层，可以先安全落地。
2. `governance overview`：说明哪些能力被阻断、为什么阻断、未来如何审查。
3. `tools/call`：真正执行层，必须经过身份、租户、HITL、审计、eval、限流和 `SafeToolExecutor`。

M5.56 只完成前两层，不打开第三层。

## Verification

Passed:

```powershell
mvn -q "-Dtest=McpGovernanceOverviewServiceTest,M520McpManifestSafetyContractTest,AgentSecurityConfigWebMvcTest" test
```
