# M5.66 Reviewed Eval Trace Evidence Contract

Date: 2026-06-09

## Purpose

M5.66 advances the second M5.64 roadmap step: `reviewed-eval-trace-evidence`.

The new endpoint is:

```text
GET /api/agent/observability/eval/reviewed-trace-evidence
```

It gives the backend, Vue workbench, and future release process one read-only contract for the question: "Do we have reviewed redacted trace evidence that is strong enough to prepare release-blocking eval gates?"

## Current State

The current built-in trace sets are still empty, so the contract intentionally reports:

```text
evidenceStatus=NEEDS_REVIEWED_REDACTED_TRACE_EVIDENCE
reviewedEvidenceReady=false
releaseBlockingAllowedNow=false
ciBlockingEnabled=false
```

This is a good fail-closed state. The project now knows exactly what evidence is missing without pretending that schema-only eval gates are release evidence.

## Review Pipeline

M5.66 publishes the evidence path:

1. Discover redacted candidate trace anchors.
2. Run curation review through deterministic eval gates.
3. Render sanitized catalog patch review for Vue and Git review.
4. Promote anchors only through human source-control review.
5. Regenerate compact gate-bundle evidence.
6. Promote release-blocking gates only in a later explicit slice.

## Teaching Point

顶级 Agent 不是只看“回答是否看起来正确”。它必须能证明每一次关键行为来自可回放、可脱敏、可评测、可审查的 trace evidence。

M5.66 对应现代 Agent 工程里的几条主线：

- OpenAI Agents-style tracing: agent run、tool call、handoff、guardrail 都要留下可复核证据。
- MCP governance: 外部工具协议可以被发现和展示，但 runtime `tools/call` 不能绕过本地权限、审计和评测。
- OpenTelemetry GenAI: 观测字段要能映射到可携带的 trace/eval 证据，同时内部字段保持稳定。
- OWASP LLM security: prompt injection、sensitive disclosure、excessive agency 不能只靠 prompt 规劝，需要 eval gate 和 release evidence。
- W3C Trace Context: trace anchor 要能和分布式追踪体系对齐。

## Security Boundary

This endpoint is admin-only, read-only, contract-only, and summary-only.

It does not:

- run evals;
- change CI blocking;
- mutate `observability/eval-trace-sets.json`;
- query raw audit evidence;
- embed replay payloads;
- execute Tools;
- invoke `SafeToolExecutor`;
- invoke HITL;
- call kube-manager;
- expose MCP runtime `tools/call`;
- call LLMs or external services;
- write audit or durable receipts;
- write memory or execute retrieval;
- touch NIM / HPC / Slurm / BCM Phase 2 scope.

## Verification

Implemented and verified with:

```powershell
mvn -q "-Dtest=AgentReviewedEvalTraceEvidenceServiceTest,AgentEvalWorkbenchCapabilitiesServiceTest,AgentEvalWorkbenchOverviewServiceTest,AgentPhase1ExecutionRoadmapServiceTest,AgentVueReadinessControlPlaneServiceTest,AgentAdvancedTechnologyAdoptionContractServiceTest,AgentTopTierReadinessOverviewServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```
