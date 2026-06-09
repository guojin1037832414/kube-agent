# M5.67 Release-Blocking Eval Gate Contract

Date: 2026-06-09

## Purpose

M5.67 advances the third M5.64 roadmap step: `release-blocking-eval-gates`.

The new endpoint is:

```text
GET /api/agent/observability/eval/release-blocking-gate-contract
```

It composes M5.66 reviewed trace evidence and the eval gate bundle summary into one release-readiness contract. The contract answers: "Can eval evidence safely become a release blocker now?"

## Current State

The answer is still no:

```text
contractStatus=BLOCKED_BY_REVIEWED_TRACE_EVIDENCE
releaseBlockingGateDefined=true
releaseBlockingEnabled=false
ciBlockingEnabled=false
releaseGateCanOpenNow=false
```

This is the correct Phase 1 state because the built-in trace sets still have no reviewed redacted anchors.

## Release-Gate Checks

M5.67 publishes six checks:

1. reviewed trace evidence;
2. gate bundle release eligibility;
3. no empty trace sets;
4. human Git review complete;
5. CI blocking switch intentionally absent;
6. runtime authority unchanged.

Even if the first three checks pass in the future, this endpoint still does not enable CI. A later explicit slice must wire CI to consume the compact artifact.

## Teaching Point

发布阻断不是一个简单的 `true/false` 开关。顶级 Agent 的 release gate 至少需要：

- reviewed redacted trace evidence;
- deterministic eval gate bundle;
- no empty curated trace sets;
- human Git review;
- explicit CI wiring;
- unchanged runtime authority.

M5.67 把这些条件写成后端契约。这样你学习 Agent 工程时能看到：真正成熟的系统不会因为“有 eval 分数”就直接阻断发布，也不会因为“当前阻断关闭”就没有质量门禁。它会把每个条件、阻塞原因和升级路径都暴露出来。

## Security Boundary

This endpoint is admin-only, read-only, contract-only, and summary-only.

It does not:

- mutate GitHub Actions or CI workflow files;
- enable CI blocking;
- run eval suites;
- mutate trace-set catalogs;
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
mvn -q "-Dtest=AgentReleaseBlockingEvalGateContractServiceTest,AgentReviewedEvalTraceEvidenceServiceTest,AgentEvalWorkbenchCapabilitiesServiceTest,AgentEvalWorkbenchOverviewServiceTest,AgentPhase1ExecutionRoadmapServiceTest,AgentVueReadinessControlPlaneServiceTest,AgentAdvancedTechnologyAdoptionContractServiceTest,AgentTopTierReadinessOverviewServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```
