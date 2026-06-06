# kube-agent Project Mission And Memory

## Ultimate Goal

`kube-agent` is not only a production Agent project. It is also a long-term learning project whose goal is to help the owner grow from an Agent beginner into an Agent master.

The target is to build a top-tier, near-perfect Kubernetes/cloud/HPC Agent on top of the existing mature `kube-manager` and `vue-kube-manager` capabilities.

The owner explicitly clarified on 2026-06-06 that the target is higher than a normal production-grade Agent: this should become a top-tier learning project for mastering Agent development. Implementation should therefore prefer modern, evidence-backed Agent patterns, strong safety boundaries, rich Chinese documentation/comments, and multi-expert iterative review, while still staying grounded in mature `kube-manager` / `vue-kube-manager` behavior.

## Product Direction

- Use `kube-manager` backend and `vue-kube-manager` frontend as the primary capability evidence.
- Keep Tool behavior aligned with real mature APIs instead of guessing paths or inventing unsupported features.
- Build an Agent that is safe, auditable, explainable, recoverable, and extensible.
- Treat this as both an engineering system and a learning artifact: architecture, code, tests, and docs should teach clearly.

## Engineering Standards

- Prefer modern Agent development patterns and up-to-date safety practices.
- Use explicit Tool metadata:
  - `httpMethod`
  - `apiEndpoints`
  - `operationType`
  - `requiresConfirmation`
- Separate normal `READ`, `SENSITIVE_READ`, `CREATE`, `UPDATE`, `DELETE`, and `ACTION`.
- Keep dangerous or unclear abilities fail-closed until evidence, permission boundary, tests, and docs are ready.
- Do not call real kube-manager `8100` during audit/migration waves unless explicitly required and safely scoped.
- Prefer static contract tests and mock HTTP client tests for Tool migration.
- Keep implementation changes scoped and reversible.

## Multi-Expert Workflow

For meaningful changes, think and document through multiple expert lenses:

- Backend/API expert: verifies mature controller paths, HTTP methods, DTO/query/body shape, and backend semantics.
- Frontend/product expert: verifies actual UI usage, workflow intent, and user-facing behavior from `vue-kube-manager`.
- Security/RBAC expert: classifies risk, tenant boundaries, HITL requirements, and excessive-agency risks.
- Agent architecture expert: checks Tool schema, ReAct/Plan/Execute behavior, memory, observability, and MCP exposure.
- Test architecture expert: designs contract, unit, and regression tests without relying on real side effects.
- Documentation/learning expert: leaves Chinese comments and docs that help the owner learn Agent development deeply.

## Documentation And Chinese Comments

- Add Chinese technical documentation whenever a capability wave, architectural decision, or safety boundary is completed.
- Add Chinese code comments when they clarify non-obvious Agent, safety, or API-contract logic.
- Avoid noisy comments for obvious code.
- Keep audit docs updated in `docs/`.
- Keep changelog and wave index updated for M5.21+ work.

## Memory And Recovery Rule

The user requires persistent project memory grouped by project under:

`H:\codex重要文件\kube-agent`

After every completed chunk:

1. Update a repo-local memory/progress file.
2. Sync the relevant memory/progress docs to `H:\codex重要文件\kube-agent`.
3. Include current status, changed files, tests, decisions, HOLD items, and next steps.

This is mandatory so future conversations can fully recover progress and context.

## Git Rule

After each completed chunk of meaningful work:

1. Run relevant tests/checks.
2. Update docs and memory.
3. Commit the completed chunk.
4. Push the commit.

Do not revert unrelated dirty worktree changes. If the worktree contains unrelated existing changes, only stage/commit the files belonging to the completed chunk.

## Current Long-Running Track

Current track:

`M5.21 kube-manager Tool alignment/audit waves`

Recently completed:

`M5.21-51 NIM durable audit storage candidate contract`

Latest checkpoint:

- Date: 2026-06-07 06:31 Asia/Shanghai.
- Branch: `codex/m521-29-top-agent-mission`.
- M5.21-51 implemented and verified:
  - Identified mature `kube-manager` system log chain as a candidate durable audit storage evidence source:
    - `SaveLogAspect`
    - `ISysLogService.saveLog(SysLog)`
    - `SysLog`
    - `Constant.ES_SYS_LOG_INDEX_NAME = sys_log`
    - `SysLogController` `GET /api/log` and `DELETE /api/log/all`
    - `vue-kube-manager` route `/system/log` and `src/api/log.js`.
  - Added `NimCreateDurableAuditStorageSupport` as a pure/mock-first candidate contract.
  - The report returns `durableAuditStorage=NIM_CREATE_DURABLE_AUDIT_STORAGE_CANDIDATE`, `executionMode=DURABLE_AUDIT_STORAGE_CANDIDATE_CONTRACT_ONLY`, and `storageState=IMPLEMENTATION_HOLD|REJECTED`.
  - Positive input produces a sanitized `storagePlan.sysLogFieldMapping`, but still sets `realStorageTouched=false`, `durable=false`, `releaseEligible=false`, `durableReceiptCanBeIssued=false`.
  - Positive input is blocked by `DEDICATED_NIM_AUDIT_WRITER_NOT_IMPLEMENTED`.
  - Missing server trusted principal is rejected with `TRUSTED_PRINCIPAL_SNAPSHOT_NOT_READY`.
  - Secret leakage is rejected with `DURABLE_AUDIT_STORAGE_INPUT_CONTAINS_FORBIDDEN_SECRET`.
  - Added `NimCreateDurableAuditStorageSupportTest`.
  - Added `docs/M5_21_FIFTY_FIRST_WAVE_NIM_DURABLE_AUDIT_STORAGE_CANDIDATE_AUDIT_20260607.md`.
  - Verification passed:
    - `mvn -q "-Dtest=NimCreateDurableAuditStorageSupportTest,NimCreateAuditWriterSupportTest,M510ArchitectureBoundaryTest" test`
    - `mvn -q "-Dtest=NimCreateDurableAuditStorageSupportTest,NimCreateAuditWriterSupportTest,NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest,NimCreateWriteExecutionHandoffSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateReadinessHttpAdapterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateAuditReadinessSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
    - `git diff --check`
    - Real secret-pattern static scan found 0 matches.
    - Boundary scan found no new real `ElasticsearchTemplate`, `ISysLogService`, HTTP client, or `java.net` dependency.
    - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - No real `8100` access; no Elasticsearch connection; no `ISysLogService` call; no `sys_log` write; no `POST /api/{orgId}/deployment`; `nim_create` remains HOLD.

- Date: 2026-06-07 06:21 Asia/Shanghai.
- Branch: `codex/m521-29-top-agent-mission`.
- M5.21-50 implemented and verified:
  - Tightened `NimCreateStateMachineSupport` so future `nim_create` release now explicitly requires `durableWriteExecutorReport`.
  - `ReadinessRequest` now includes `durableWriteExecutorReport` while preserving compatibility constructors for negative fixtures.
  - State-machine output now includes `durableWriteExecutorReportRequired=true`.
  - Missing report returns `DURABLE_WRITE_EXECUTOR_REPORT_NOT_READY`.
  - Current M5.21-49 shell report is accepted only as a valid evidence shape, then blocked by `DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD`.
  - The state machine validates shell report binding to handoff digest, request spec digest, body digest, durable audit receipt, server-derived idempotency key, and `executionAttemptSpec`.
  - Forged success claims such as `executorImplementationAvailable=true`, `writeAttempted=true`, `writeExecuted=true`, `postWriteReadinessTriggered=true`, `deploymentId`, `deploymentUid`, or `writeResult` trigger `DURABLE_WRITE_EXECUTOR_SUCCESS_NOT_TRUSTED`.
  - Secret leakage in the executor report triggers `DURABLE_WRITE_EXECUTOR_REPORT_CONTAINS_FORBIDDEN_SECRET`.
  - Updated state-machine and upstream NIM contract tests so handoff completion no longer means `READY_FOR_CONTROLLED_WRITE`; durable executor report is now a distinct required gate.
  - Added `docs/M5_21_FIFTIETH_WAVE_NIM_DURABLE_WRITE_EXECUTOR_REPORT_GATE_AUDIT_20260607.md`.
  - Verification passed:
    - `mvn -q "-Dtest=NimCreateStateMachineSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateWriteExecutionHandoffSupportTest,NimCreateDurableWriteExecutorSupportTest,NimCreateAuditReadinessSupportTest,NimCreateAuditWriterSupportTest,NimCreateReadinessHttpAdapterSupportTest" test`
    - `mvn -q "-Dtest=NimCreateStateMachineSupportTest,NimCreateDurableWriteExecutorSupportTest,NimCreateWriteExecutionHandoffSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateReadinessHttpAdapterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateAuditReadinessSupportTest,NimCreateAuditWriterSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
    - `git diff --check`
    - Real secret-pattern static scan found 0 matches.
    - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - No real `8100` access; no HTTP client; no real audit table write; no real NIM polling; no `POST /api/{orgId}/deployment`; `nim_create` remains HOLD.

- Date: 2026-06-07 06:14 Asia/Shanghai.
- Branch: `codex/m521-29-top-agent-mission`.
- M5.21-49 implemented and verified:
  - Added `NimCreateDurableWriteExecutorSupport` as a pure/mock-first contract shell for the future durable write executor.
  - It consumes:
    - `writeExecutionHandoffReport`
    - `writeRequestSpecReport`
  - It returns:
    - `durableWriteExecutor=FUTURE_DURABLE_WRITE_EXECUTOR`
    - `executionMode=DURABLE_WRITE_EXECUTOR_CONTRACT_SHELL`
    - `executionState=IMPLEMENTATION_HOLD|REJECTED`
    - `networkAccess=NOT_PERFORMED`
    - `sideEffect=NONE`
    - `inputAccepted`
    - `executorImplementationAvailable=false`
    - `realHttpExecutionAllowed=false`
    - `writeAttempted=false`
    - `writeExecuted=false`
    - `postWriteReadinessTriggered=false`
    - `executionAttemptSpec`
    - `blockedBy`.
  - A valid handoff/request spec pair is accepted as input but still blocked by `DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD`.
  - The shell verifies request spec digest, body digest, handoff digest, server-derived idempotency key, durable audit handoff, retry policy, and post-write readiness handoff.
  - `NimCreateStateMachineSupport` now ignores caller-forged durable write executor result claims such as `writeExecuted`, `deploymentId`, `writeResult`, and `postWriteReadinessTriggered`.
  - Added `NimCreateDurableWriteExecutorSupportTest`.
  - Added `docs/M5_21_FORTY_NINTH_WAVE_NIM_DURABLE_WRITE_EXECUTOR_SHELL_AUDIT_20260607.md`.
  - Verification passed:
    - `mvn -q "-Dtest=NimCreateDurableWriteExecutorSupportTest,NimCreateWriteExecutionHandoffSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateStateMachineSupportTest" test`
  - No real `8100` access; no HTTP client; no real audit table write; no real NIM polling; no `POST /api/{orgId}/deployment`; `nim_create` remains HOLD.

- Date: 2026-06-07 05:54 Asia/Shanghai.
- Branch: `codex/m521-29-top-agent-mission`.
- M5.21-48 implemented and verified:
  - Added `NimCreateWriteExecutionHandoffSupport` as a pure/mock-first write execution handoff contract between request spec adapter and future durable write executor.
  - It consumes:
    - `creationGate`
    - `auditContext`
    - `auditReceipt`
    - `writeBodyRebuildReport`
    - `writeRequestSpecReport`
  - It returns:
    - `writeExecutionHandoff=NIM_CREATE_WRITE_EXECUTION_HANDOFF`
    - `executionMode=WRITE_EXECUTION_HANDOFF_CONTRACT_ONLY`
    - `networkAccess=NOT_PERFORMED`
    - `sideEffect=NONE`
    - `writeExecutionPrepared`
    - `futureExecutor=FUTURE_DURABLE_WRITE_EXECUTOR`
    - `realHttpExecutionAllowed=false`
    - `preWriteAuditRequired=true`
    - `idempotencyRequired=true`
    - `idempotencyKeySource=SERVER_DERIVED_FROM_AUDIT_AND_REQUEST_SPEC`
    - `idempotencyKey=nim-create-<32 hex>`
    - `callerIdempotencyKeyAllowed=false`
    - `executionHandoffPlan`
    - `handoffDigest`
    - `blockedBy`.
  - `executionHandoffPlan` declares future `POST /api/{orgId}/deployment`, but still reports `networkAccess=NOT_PERFORMED` and `sideEffect=NONE`.
  - Handoff binds durable audit receipt, audit identity, body digest, request spec digest, server-derived idempotency key, pre-write audit handoff, and post-write readiness handoff.
  - `NimCreateStateMachineSupport.ReadinessRequest` now includes `writeExecutionHandoffReport`, with compatibility constructors for negative fixtures that intentionally omit handoff.
  - State-machine output now includes `writeExecutionHandoffRequired=true`.
  - Missing report returns `WRITE_EXECUTION_HANDOFF_REPORT_NOT_READY`.
  - Invalid or digest/audit-receipt/request-spec-mismatched report returns `WRITE_EXECUTION_HANDOFF_REPORT_CONTRACT_INVALID`.
  - Secret leakage in the report returns `WRITE_EXECUTION_HANDOFF_REPORT_CONTAINS_FORBIDDEN_SECRET`.
  - State machine recomputes `handoffDigest` and verifies the handoff plan is bound to the current request spec/body/audit receipt.
  - Added `NimCreateWriteExecutionHandoffSupportTest`.
  - Added `docs/M5_21_FORTY_EIGHTH_WAVE_NIM_WRITE_EXECUTION_HANDOFF_AUDIT_20260607.md`.
  - Multi-expert review notes:
    - Architecture: future write execution must not jump from request spec directly to durable writer; handoff is its own audited gate.
    - Security: idempotency key must be server-derived from audit/request spec evidence; caller idempotency claims are ignored.
    - Test: future green state-machine fixtures must carry body rebuild, request spec, execution handoff, and READY readiness report.
  - Verification passed:
    - `mvn -q "-Dtest=NimCreateWriteExecutionHandoffSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateStateMachineSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateAuditReadinessSupportTest" test`
    - `mvn -q "-Dtest=NimCreateWriteExecutionHandoffSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateReadinessHttpAdapterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest,NimCreateAuditWriterSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
    - `git diff --check`
    - Static secret-pattern scan only matched documentation text and test sentinel fake values; no real secret found.
    - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - No real `8100` access; no real audit table write; no real NIM polling; no `POST /api/{orgId}/deployment`; `nim_create` remains HOLD.

- Date: 2026-06-07 05:10 Asia/Shanghai.
- Branch: `codex/m521-29-top-agent-mission`.
- M5.21-47 implemented and verified:
  - Added `NimCreateWriteRequestSpecAdapterSupport` as a pure/mock-first POST request spec adapter contract.
  - It consumes:
    - `creationGate`
    - `auditContext`
    - `auditReceipt`
    - `writeBodyRebuildReport`
  - It returns:
    - `writeRequestSpecAdapter=NIM_CREATE_WRITE_REQUEST_SPEC_ADAPTER`
    - `executionMode=POST_REQUEST_SPEC_CONTRACT_ONLY`
    - `networkAccess=NOT_PERFORMED`
    - `sideEffect=NONE`
    - `writeRequestPrepared`
    - `backendEndpoint=POST /api/{orgId}/deployment`
    - `pathTemplate=/api/{orgId}/deployment`
    - `clientBoundary=KUBE_MANAGER_HTTP_GATEWAY`
    - `callerHeadersAllowed=false`
    - `authorizationHeaderFromCallerAllowed=false`
    - `realApiKeyAllowed=false`
    - `bodySource=CONTROLLED_REBUILDER_BODY_COPY`
    - `bodyCopiedByValue=true`
    - `bodyMutationAllowed=false`
    - `requestSpec`
    - `requestSpecDigest`
    - `blockedBy`.
  - Request spec is fixed to future `POST /api/{orgId}/deployment` shape but performs no network access and reports `sideEffect=NONE`.
  - Request spec requires a durable-audit-bound body rebuild report, forbids caller headers/API keys, and keeps kube-manager auth inside the future HTTP client context.
  - `NimCreateStateMachineSupport.ReadinessRequest` now includes `writeRequestSpecReport`.
  - State-machine output now includes `writeRequestSpecRequired=true`.
  - Missing report returns `WRITE_REQUEST_SPEC_REPORT_NOT_READY`.
  - Invalid or body/digest/audit-receipt-mismatched report returns `WRITE_REQUEST_SPEC_REPORT_CONTRACT_INVALID`.
  - Secret leakage in the report returns `WRITE_REQUEST_SPEC_REPORT_CONTAINS_FORBIDDEN_SECRET`.
  - State machine recomputes `requestSpecDigest` and verifies request body equals the rebuilder body.
  - Added `NimCreateWriteRequestSpecAdapterSupportTest`.
  - Added `docs/M5_21_FORTY_SEVENTH_WAVE_NIM_WRITE_REQUEST_SPEC_ADAPTER_AUDIT_20260607.md`.
  - Multi-expert review notes:
    - Architecture: future write execution must not jump from rebuilt body directly to HTTP client; request spec is its own audited gate.
    - Security: request spec output is not a release credential and cannot replace trusted policy, HITL, durable audit receipt, READY readiness executor, or release switch.
    - Test: future green state-machine fixtures must carry both rebuilder report and request spec report.
  - Verification passed:
    - `mvn -q "-Dtest=NimCreateWriteRequestSpecAdapterSupportTest,NimCreateStateMachineSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateAuditReadinessSupportTest,NimCreateAuditWriterSupportTest" test`
    - `mvn -q "-Dtest=NimCreateWriteRequestSpecAdapterSupportTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateReadinessHttpAdapterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest,NimCreateAuditWriterSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
    - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
    - Real secret-pattern static scan found 0 matches.
    - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - No real `8100` access; no real audit table write; no real NIM polling; no `POST /api/{orgId}/deployment`; `nim_create` remains HOLD.

- Date: 2026-06-07 04:45 Asia/Shanghai.
- Branch: `codex/m521-29-top-agent-mission`.
- M5.21-46 implemented and verified:
  - Added `NimCreateWriteBodyRebuilderSupport` as a pure/mock-first controlled write body rebuilder contract.
  - It consumes:
    - `creationGate`
    - `deploymentBodyPreview`
    - `auditContext`
    - `auditReceipt`
  - It returns:
    - `writeBodyRebuilder=NIM_CREATE_WRITE_BODY_REBUILDER`
    - `executionMode=CONTROLLED_BODY_CONTRACT_ONLY`
    - `networkAccess=NOT_PERFORMED`
    - `sideEffect=NONE`
    - `writeBodyPrepared`
    - `backendEndpoint=POST /api/{orgId}/deployment`
    - `writeBodyProvenance=SERVER_REBUILT_FROM_AUDITED_NIM_STATE`
    - `directPreviewReuseAllowed=false`
    - `previewBodyReferenceUsed=false`
    - `fieldWhitelistApplied=true`
    - `protectedContextStripped=true`
    - `body`
    - `bodyDigest`
    - `sourceAuditReceiptId/sourceAuditEventDigest`
    - `blockedBy`.
  - Rebuilder only copies DeploymentDTO allowlisted fields and strips protected context such as `organizationId/orgId/userId/conversationId/token`.
  - Rebuilder requires open server creation gate, trusted policy passed, complete preview with `safeToPost=false`, complete audit context, and durable audit receipt bound to the same audit identity.
  - `NimCreateStateMachineSupport.ReadinessRequest` now includes `writeBodyRebuildReport`.
  - State-machine output now includes `writeBodyRebuildRequired=true`.
  - Missing report returns `WRITE_BODY_REBUILD_REPORT_NOT_READY`.
  - Invalid or audit-receipt-mismatched report returns `WRITE_BODY_REBUILD_REPORT_CONTRACT_INVALID`.
  - Secret leakage in the report returns `WRITE_BODY_REBUILD_REPORT_CONTAINS_FORBIDDEN_SECRET`.
  - Added `NimCreateWriteBodyRebuilderSupportTest`.
  - Added `docs/M5_21_FORTY_SIXTH_WAVE_NIM_WRITE_BODY_REBUILDER_AUDIT_20260607.md`.
  - Multi-expert review notes:
    - Architecture: provenance is not enough; future writes need an explicit, testable body rebuild report.
    - Security: the report is not a release credential and cannot replace trusted policy, HITL, durable audit receipt, READY readiness executor, or release switch.
    - Test: future green state-machine fixtures must carry the rebuilder report.
  - Verification passed:
    - `mvn -q "-Dtest=NimCreateWriteBodyRebuilderSupportTest,NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest,NimCreateAuditWriterSupportTest,NimCreateReadinessHttpAdapterSupportTest" test`
    - `mvn -q "-Dtest=NimCreateWriteBodyRebuilderSupportTest,NimCreateReadinessHttpAdapterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest,NimCreateAuditWriterSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
    - `mvn -q "-Dtest=NimCreateWriteBodyRebuilderSupportTest,NimCreateStateMachineSupportTest" test`
    - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
    - Real secret-pattern static scan found 0 matches after replacing a historical docs example key in `docs/v3.1/DEVELOPMENT_GUIDE.md` with `sk-REPLACE_WITH_YOUR_KEY`.
    - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - No real `8100` access; no real audit table write; no real NIM polling; no `POST /api/{orgId}/deployment`; `nim_create` remains HOLD.

- Date: 2026-06-07 01:16 Asia/Shanghai.
- Branch: `codex/m521-29-top-agent-mission`.
- M5.21-45 implemented and verified:
  - Added `NimCreateReadinessHttpAdapterSupport` as a pure/mock-first request spec compiler.
  - It consumes:
    - `readinessPlan`
    - `serviceApiUrl`
    - `attempt`
  - It returns:
    - `readinessHttpAdapter=NIM_CREATE_READINESS_HTTP_ADAPTER`
    - `executionMode=REQUEST_SPEC_CONTRACT_ONLY`
    - `networkAccess=NOT_PERFORMED`
    - `sideEffect=NONE`
    - `readOnly=true`
    - `pollOnly=true`
    - `apiKeyHandling=NEVER_GENERATE_STORE_OR_DISPLAY`
    - `apiKeyHeaderPolicy=DO_NOT_SEND_REAL_API_KEY`
    - `requestSpecs`
    - `derivedSteps`
    - `executorHandoff`
    - `pendingBy`
    - `blockedBy`.
  - Adapter only accepts the four audited readiness steps:
    - deployment `GET /api/{orgId}/deployment`;
    - service `EXTRACT_FROM_DEPLOYMENT_RESPONSE deployment.entranceMap.http|http1`;
    - NIM health `GET {nimApiBasePath}/v1/health/live`;
    - NIM models `GET {nimApiBasePath}/v1/models`.
  - Adapter only emits request specs for deployment, NIM health, and NIM models; service remains a derived step.
  - Adapter rejects POST/unknown targets/unapproved GET endpoints, unsafe deployment query, unsafe service URLs, localhost/127/8100, path traversal, and real Bearer/API-key-shaped values.
  - Tightened `NimCreateStateMachineSupport` so readiness plan must cover `deployment/service/nim-health/nim-models`, matching the executor contract.
  - Added `NimCreateReadinessHttpAdapterSupportTest`.
  - Added `docs/M5_21_FORTY_FIFTH_WAVE_NIM_READINESS_HTTP_ADAPTER_AUDIT_20260607.md`.
  - Multi-expert review notes:
    - Architecture: adapter is not a real HTTP client and must not depend on `KubeManagerHttpClient`, `RestClient`, `java.net`, okhttp, feign, or Apache HTTP.
    - Security: adapter output is not a release credential and cannot replace READY executor report.
    - Test: request specs must prove no body, no headers, no Authorization, no real 8100, and no unknown readiness endpoints.
  - Verification passed:
    - `mvn -q "-Dtest=NimCreateReadinessHttpAdapterSupportTest,NimCreateStateMachineSupportTest,NimCreateReadinessExecutorSupportTest" test`
    - `mvn -q "-Dtest=NimCreateReadinessHttpAdapterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest,NimCreateAuditWriterSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
    - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
    - Real secret-pattern static scan found no real secrets; matches were only test sentinel values.
    - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - External recovery docs synced and hash-verified to `H:\codex重要文件\kube-agent`.
  - No real `8100` access; no real NIM polling; no `POST /api/{orgId}/deployment`; `nim_create` remains HOLD.

- Date: 2026-06-07 00:55 Asia/Shanghai.
- Branch: `codex/m521-29-top-agent-mission`.
- M5.21-44 implemented, verified:
  - Tightened `NimCreateStateMachineSupport` so future `nim_create` release requires a READY readiness executor report, not just a readiness plan.
  - `ReadinessRequest` now includes `readinessExecutionReport`.
  - State-machine output includes `readinessExecutionRequired=true`.
  - `validateReadinessExecutionReport(...)` requires:
    - `readinessExecutor=NIM_CREATE_READINESS_EXECUTOR`
    - `sideEffect=NONE`
    - `readOnly=true`
    - `pollOnly=true`
    - `apiKeyHandling=NEVER_GENERATE_STORE_OR_DISPLAY`
    - `apiKeyPlaceholderOnly=true`
    - `forbiddenActionsEnforced=true`
    - `ready=true`
    - `state=READY`
    - `blockedBy=[]`
    - `deployment.matched=true`
    - `service.serviceUrlReady=true`
    - `health.live=true`
    - `nextPoll.prepared=false`.
  - Missing report returns `READINESS_EXECUTION_REPORT_NOT_READY`.
  - PENDING/BLOCKED/REJECTED/TIMEOUT or blocked report cannot become a write-release credential.
  - Report secret leakage returns `READINESS_EXECUTION_REPORT_CONTAINS_FORBIDDEN_SECRET`.
  - Caller-forged readiness claims are now explicitly ignored:
    - `readinessExecutionReport`
    - `readinessExecutor`
    - `readinessReady`
    - `readinessState`.
  - Strengthened state-machine secret detection to reject real Bearer/API-key-shaped values while allowing the mature frontend placeholder.
  - Updated tests:
    - `NimCreateStateMachineSupportTest`
    - `NimCreateAuditReadinessSupportTest`
    - `NimCreateAuditWriterSupportTest`.
  - Added `docs/M5_21_FORTY_FOURTH_WAVE_NIM_READINESS_REPORT_GATE_AUDIT_20260607.md`.
  - Verification passed:
    - `mvn -q "-Dtest=NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest,NimCreateAuditWriterSupportTest" test`
    - `mvn -q "-Dtest=NimCreateReadinessExecutorSupportTest,NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest,NimCreateAuditWriterSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
    - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
    - Real secret-pattern static scan found 0 matches.
    - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - External recovery docs synced and hash-verified to `H:\codex重要文件\kube-agent`.
  - No real `8100` access; no real NIM polling; no `POST /api/{orgId}/deployment`; `nim_create` remains HOLD.

- Date: 2026-06-07 00:41 Asia/Shanghai.
- Branch: `codex/m521-29-top-agent-mission`.
- M5.21-43 implemented, verified:
  - Added `NimCreateReadinessExecutorSupport` as a pure/offline readiness executor contract.
  - It consumes `readinessPlan`, `deploymentListResponse`, `healthResponse`, `modelsResponse`, and `attempt`.
  - It returns:
    - `readinessExecutor=NIM_CREATE_READINESS_EXECUTOR`
    - `executionMode=OFFLINE_CONTRACT_EVALUATION`
    - `sideEffect=NONE`
    - `readOnly=true`
    - `pollOnly=true`
    - `apiKeyHandling=NEVER_GENERATE_STORE_OR_DISPLAY`
    - `apiKeyPlaceholderOnly=true`
    - `deployment/service/health/models`
    - `pendingBy`
    - `blockedBy`
    - `nextPoll`
    - `forbiddenActionsEnforced`.
  - Readiness plan must be prepared, poll-only, placeholder-only, and cover `deployment/service/nim-health/nim-models`.
  - Readiness steps may only be `GET` or `EXTRACT_FROM_DEPLOYMENT_RESPONSE`.
  - Deployment readback:
    - 0 results -> `PENDING` with next poll prepared;
    - 1 result -> derive service URL from `entranceMap.http/http1`;
    - more than 1 result -> `DEPLOYMENT_MATCH_AMBIGUOUS`.
  - Health live signals match mature frontend:
    - `message=Service is live.`;
    - `live=true`;
    - `status=live`.
  - Model readback matches mature frontend:
    - `data[0].id`;
    - `available_models[0]`;
    - otherwise `fetch failed`, non-fatal after health is live.
  - Secret/API-key boundary:
    - rejects `Authorization`, `token`, `apiKey`, `secret`, `password`, real Bearer values, and common real key-shaped strings in plan/responses.
  - Added `NimCreateReadinessExecutorSupportTest`.
  - Added `docs/M5_21_FORTY_THIRD_WAVE_NIM_READINESS_EXECUTOR_AUDIT_20260607.md`.
  - Verification passed:
    - `mvn -q "-Dtest=NimCreateReadinessExecutorSupportTest" test`
    - `mvn -q "-Dtest=NimCreateReadinessExecutorSupportTest,NimCreateAuditReadinessSupportTest,NimCreateStateMachineSupportTest" test`
    - `mvn -q "-Dtest=NimCreateReadinessExecutorSupportTest,NimCreateAuditWriterSupportTest,NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest,M510ArchitectureBoundaryTest" test`
    - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
    - Real secret-pattern static scan found 0 matches.
    - `mvn -q test`
  - Architecture boundary note: initial `java.net.URI` parsing was caught by `M510ArchitectureBoundaryTest`; implementation now uses constrained string parsing to avoid Tool-layer `java.net..` dependency.
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - External recovery docs synced and hash-verified to `H:\codex重要文件\kube-agent`.
  - No real `8100` access; no real NIM polling; no `POST /api/{orgId}/deployment`; `nim_create` remains HOLD.

- Date: 2026-06-07 00:25 Asia/Shanghai.
- Branch: `codex/m521-29-top-agent-mission`.
- M5.21-42 implemented, verified, recovery-synced, committed, and pushed:
  - Added `NimCreateAuditWriterSupport` as a mock-first audit writer receipt contract.
  - `buildMockReceipt(...)` returns:
    - `auditReceiptPrepared`
    - `receiptStatus=MOCK_PREPARED|REJECTED`
    - `sideEffect=NONE`
    - `storageMode=MOCK_CONTRACT_ONLY`
    - `durable=false`
    - `realStorageTouched=false`
    - `releaseEligible=false`
    - `requiredFutureStorage=DURABLE_AUDIT_LOG`
    - `eventDigestAlgorithm=SHA-256`
    - `eventDigest`
    - `receiptId`
    - audit identity fields.
  - Mock receipt is intentionally not a production release credential.
  - `NimCreateStateMachineSupport.ReadinessRequest` now includes `auditReceipt`.
  - State machine now requires a durable audit receipt before future controlled write:
    - missing receipt -> `AUDIT_RECEIPT_NOT_READY`;
    - mock/non-durable/mismatched receipt -> `AUDIT_RECEIPT_NOT_DURABLE`;
    - receipt containing secrets -> `AUDIT_RECEIPT_CONTAINS_FORBIDDEN_SECRET`.
  - Future green fixture must use `receiptStatus=DURABLE_RECORDED`, `storageMode=DURABLE_AUDIT_LOG`, `durable=true`, `realStorageTouched=true`, `releaseEligible=true`.
  - Added `NimCreateAuditWriterSupportTest`.
  - Updated `NimCreateStateMachineSupportTest` and `NimCreateAuditReadinessSupportTest`.
  - Added `docs/M5_21_FORTY_SECOND_WAVE_NIM_AUDIT_WRITER_RECEIPT_AUDIT_20260607.md`.
  - Verification already passed:
    - `mvn -q "-Dtest=NimCreateAuditWriterSupportTest,NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest" test`
    - `mvn -q "-Dtest=NimCreateAuditWriterSupportTest,NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest,NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
    - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
    - Real secret-pattern static scan found 0 matches.
    - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - External recovery docs synced and hash-verified to `H:\codex重要文件\kube-agent`.
  - Implementation commit: `df4bdf6 feat(M5.21): add NIM audit writer receipt contract`.
  - No real `8100` access; no real audit table write; no `POST /api/{orgId}/deployment`; `nim_create` remains HOLD.

- Date: 2026-06-07 00:04 Asia/Shanghai.
- Branch: `codex/m521-29-top-agent-mission`.
- M5.21-41 implemented, verified, recovery-synced, committed, and pushed:
  - Added `NimTrustedPolicyProviderSupport` as a pure provider contract for future NIM create trusted policy checks.
  - Added `TrustedPolicyFacts` to represent backend-trusted facts:
    - `organizationId`
    - `callerRoles`
    - `callerUserId`
    - `nvaieLicenseVerified`
    - `nvaieLicenseValid`
    - `source`
    - `evidence`
  - Added `TrustedFactSource.KUBE_MANAGER_LICENSE_AND_SESSION`.
  - `buildSnapshot(...)` now only returns authoritative snapshots when trusted facts are complete, source is known, evidence covers license/role/organization, and NVAIE license was verified.
  - Normal org + non-`SYS_ADMIN` + valid NVAIE license returns `TRUSTED_PASSED`.
  - `organizationId=100001`, `SYS_ADMIN`, or invalid license returns `TRUSTED_BLOCKED`.
  - Missing source/evidence/user/org/roles/license verification returns `UNVERIFIED`.
  - `buildProviderReport(...)` returns `sideEffect=NONE`, `protectedFromCallerParams=true`, `trustedFactsComplete`, `ignoredCallerClaims`, `requiredTrustedFacts`, and `trustedPolicySnapshot`.
  - `NimCreationGateSupport` and `NimCreateStateMachineSupport` now also ignore forged `organizationId/orgId/roles/nvaieLicenseVerified/trustedPolicySource/authoritative` claims.
  - Added `NimTrustedPolicyProviderSupportTest`.
  - Extended `NimCreationGateSupportTest` and `NimCreateStateMachineSupportTest`.
  - Added `docs/M5_21_FORTY_FIRST_WAVE_NIM_TRUSTED_POLICY_PROVIDER_AUDIT_20260606.md`.
  - Targeted verification passed:
    - `mvn -q "-Dtest=NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimCreateStateMachineSupportTest" test`
  - Final verification passed:
    - `mvn -q "-Dtest=NimTrustedPolicyProviderSupportTest,NimCreationGateSupportTest,NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
    - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
    - Real secret-pattern static scan found 0 matches.
    - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - External recovery docs synced and hash-verified to `H:\codex重要文件\kube-agent`.
  - Implementation commit: `1078985 feat(M5.21): add NIM trusted policy provider`.
  - No real `8100` access; no `POST /api/{orgId}/deployment`; `nim_create` remains HOLD.

- Date: 2026-06-06 23:55 Asia/Shanghai.
- Branch: `codex/m521-29-top-agent-mission`.
- M5.21-40 implemented, verified, recovery-synced, committed, and pushed:
  - Added `NimCreateAuditReadinessSupport` as a pure support class for future `nim_create` audit context and readiness plan.
  - `buildAuditContext(...)` now creates a state-machine consumable map containing:
    - `auditPrepared`
    - `auditEventType=NIM_CREATE_REQUEST`
    - `requestId/conversationId/userId/organizationId`
    - `targetTool=nim_create`
    - `backendEndpoint=POST /api/{orgId}/deployment`
    - `writeBodyProvenance=SERVER_REBUILT_FROM_AUDITED_NIM_STATE`
    - `secretRedactionApplied=true`
    - `apiKeyHandling=NEVER_GENERATE_STORE_OR_DISPLAY`
    - ignored caller claim keys.
  - `buildReadinessPlan(...)` now models mature Vue NIM readiness:
    - Deployment list readback by name,
    - deriving service base URL from `entranceMap.http/http1`,
    - GET `/v1/health/live`,
    - GET `/v1/models`,
    - no real API Key generation/storage/display.
  - `NimCreateStateMachineSupport` was tightened:
    - audit must contain target tool, trusted body provenance, secret redaction, and API Key policy;
    - readiness must cover `deployment/service/nim-health`;
    - readiness steps may only be `GET` or `EXTRACT_FROM_DEPLOYMENT_RESPONSE`;
    - POST readiness steps are rejected.
  - Added `NimCreateAuditReadinessSupportTest`.
  - Updated `NimCreateStateMachineSupportTest` future-ready fixtures.
  - Targeted verification passed:
    - `mvn -q "-Dtest=NimCreateAuditReadinessSupportTest,NimCreateStateMachineSupportTest" test`
  - Final verification passed:
    - `mvn -q "-Dtest=NimCreateAuditReadinessSupportTest,NimCreateStateMachineSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
    - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
    - Real secret-pattern static scan found 0 matches.
    - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - External recovery docs synced and hash-verified to `H:\codex重要文件\kube-agent`.
  - Commit: `34b40ae feat(M5.21): add NIM audit readiness plan`.
  - No real `8100` access; no `POST /api/{orgId}/deployment`; no real NIM readiness polling.

- Date: 2026-06-06 23:33 Asia/Shanghai.
- Branch: `codex/m521-29-top-agent-mission`.
- M5.21-39 implemented, verified, recovery-synced, committed, and pushed:
  - Added `NimCreateStateMachineSupport` as a pure future write guard for `nim_create`.
  - `NimCreateTool` remains fail-closed `PLACEHOLDER`, but its failure result now includes `data.stateMachine` so the Agent can explain exactly why real NIM creation is still held.
  - State-machine output includes:
    - `stateMachine=NIM_CREATE_WRITE_GUARD`
    - `state=HELD` / `READY_FOR_CONTROLLED_WRITE`
    - `writePermitted`
    - `sideEffect=NONE`
    - `nextSideEffectIfExecuted=POST /api/{orgId}/deployment`
    - `blockedBy`
    - `ignoredCallerClaims`
    - `requiredStages`
    - `directPreviewReuseAllowed=false`
    - `fallbackWriteAllowed=false`
    - `apiKeyPolicy=NEVER_GENERATE_STORE_OR_DISPLAY`
  - Future write requires all of:
    - code-level `nim_create` release switch opened,
    - `creationGate.gateState=READY_FOR_SERVER_CONFIRMED_WRITE`,
    - `creationGate.allowedToCreateNow=true`,
    - `trustedPolicySnapshot.snapshotState=TRUSTED_PASSED`,
    - `trustedPolicySnapshot.authoritative=true`,
    - `trustedPolicySnapshot.protectedFromCallerParams=true`,
    - complete DeploymentDTO preview while keeping `safeToPost=false`,
    - exact server `HitlConfirmation` target `nim_create`,
    - complete audit context,
    - trusted write-body provenance `SERVER_REBUILT_FROM_AUDITED_NIM_STATE`,
    - read-only readiness plan with API Key handling `NEVER_GENERATE_STORE_OR_DISPLAY`,
    - no fallback write to `deploy_create_instance`.
  - Tests added/updated:
    - `NimCreateStateMachineSupportTest`
    - `HighRiskMutationToolHttpContractTest`
  - Targeted verification passed:
    - `mvn -q "-Dtest=NimCreateStateMachineSupportTest,HighRiskMutationToolHttpContractTest" test`
    - `mvn -q "-Dtest=NimCreateStateMachineSupportTest,NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
  - Final verification passed:
    - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
    - Real secret-pattern static scan found 0 matches.
    - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - External recovery docs synced and hash-verified to `H:\codex重要文件\kube-agent`.
  - Commit: `2f63d3f feat(M5.21): add NIM create state machine guard`.
  - Follow-up memory correction commit: `e3a30ef docs(M5.21): correct NIM state machine commit memory`.
  - No real `8100` access; no `POST /api/{orgId}/deployment`; no NIM service creation.

- Date: 2026-06-06 23:12 Asia/Shanghai.
- Branch: `codex/m521-29-top-agent-mission`.
- M5.21-38 implemented, verified, and recovery-sync ready:
  - Added `NimTrustedPolicySnapshot` as the pure value object for trusted NIM creation policy facts.
  - `creationGate` now returns `trustedPolicySnapshot`.
  - Public `nim_deployment_preflight` defaults policy state to `UNVERIFIED`; Tool params cannot self-attest license/RBAC success.
  - Trusted policy snapshot states:
    - `UNVERIFIED`
    - `TRUSTED_PASSED`
    - `TRUSTED_BLOCKED`
  - Trusted snapshot separates:
    - `nvaieLicense`
    - `callerOrgPolicy`
    - `evidence`
  - Forged caller fields such as `licenseValid`, `isSysOrg`, `sysAdmin`, and `role` remain ignored caller claims.
  - If trusted policy fails, blockers become explicit:
    - `NVAIE_LICENSE_TRUSTED_CHECK_FAILED`
    - `CALLER_ORG_POLICY_TRUSTED_CHECK_FAILED`
  - If trusted policy passes, license/RBAC unverified blockers are removed, but gate remains `CLOSED` because `nim_create`, HITL marker, audit logging, and readiness flow are still HOLD.
  - No new Tool or HTTP endpoint was added; no real `8100` access; no POST create.
  - Verification passed:
    - `mvn -q "-Dtest=NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest" test`
    - `mvn -q "-Dtest=NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
    - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
    - Static secret scan found 0 matches.
    - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - Commit and push will be completed after external recovery sync.

- Date: 2026-06-06 23:00 Asia/Shanghai.
- Branch: `codex/m521-29-top-agent-mission`.
- M5.21-37 implemented, verified, committed, pushed, and recovery-synced:
  - Commit: `89be95f feat(M5.21): add NIM creation gate`.
  - Added `NimCreationGateSupport` for a structured, fail-closed NIM creation gate.
  - `nim_deployment_preflight` now returns `creationGate` alongside `deploymentBodyPreview`.
  - `creationGate` always returns `gateState=CLOSED`, `allowedToCreateNow=false`, and `sideEffect=NONE`.
  - Fixed blockers include `NIM_CREATE_TOOL_HOLD`, NVAIE license not verified, caller org/SYS_ADMIN policy not verified, HITL confirmation not issued, and audit/readiness flow not ready.
  - Dynamic blockers include incomplete DeploymentDTO preview, missing `displayName`, unresolved GPU map, and invalid preview safety flag.
  - Caller-supplied approval/license/HITL/safeToPost/RBAC claims are surfaced only as `ignoredCallerClaims`; they never authorize creation.
  - `hitlCardDraft` records the future `nim_create` confirmation shape: displayName, image, templateId, GPU, CPU/memory, network, quota/cost acknowledgement, and API-key safety warnings.
  - `futureWritePath.directUseOfPreviewAllowed=false` and `fallbackAllowedFromPreflight=false` prevent direct POST or fallback write execution from preflight output.
  - No new HTTP endpoint was added; no real `8100` access; no POST create; `nim_create` remains fail-closed `PLACEHOLDER`.
  - Verification passed:
    - `mvn -q "-Dtest=NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest" test`
    - `mvn -q "-Dtest=NimCreationGateSupportTest,NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
    - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
    - Static secret scan found 0 matches.
    - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - External recovery docs synced and hash-verified to `H:\codex重要文件\kube-agent`.
  - Push status: pushed to origin before M5.21-38 began.

- Date: 2026-06-06 22:38 Asia/Shanghai.
- Branch: `codex/m521-29-top-agent-mission`.
- M5.21-36 implemented, verified, committed, and recovery-synced:
  - Commit: `815f7da feat(M5.21): add NIM template merge preview`.
  - Added `NimTemplateMergeSupport` for offline NIM template merge and DeploymentDTO preview.
  - `nim_deployment_preflight` now returns `deploymentBodyPreview`.
  - Preview is explicitly `safeToPost=false` and `previewOnly=true`.
  - Preview protects `name/displayName/image` instead of copying the mature frontend's weaker displayName-only protection.
  - `bodyComplete=false` when GPU map is missing for GPU templates or `displayName` is not confirmed.
  - Public preflight ignores user/LLM supplied `gpuMap`; only future controlled orchestration may pass an audited GPU map into the pure support overload.
  - No new HTTP endpoint was added; no real `8100` access; no POST create.
  - Verification passed:
    - `mvn -q "-Dtest=NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest" test`
    - `mvn -q "-Dtest=NimTemplateMergeSupportTest,NimDeploymentPreflightToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
    - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
    - Static secret scan found 0 matches.
    - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - External recovery docs synced and hash-verified to `H:\codex重要文件\kube-agent`.
  - Push status: pushed to origin before M5.21-37 began.

- Date: 2026-06-06 21:36 Asia/Shanghai.
- Branch: `codex/m521-29-top-agent-mission`.
- M5.21-35 implemented, verified, committed, and pushed:
  - Commit: `ddb5f9a feat(M5.21): add NIM deployment preflight tool`.
  - Added `NimDeploymentPreflightTool` for read-only NIM deployment planning.
  - Added `NimDeploymentPreflightSupport` for safe repository/tag/image/template selection.
  - The Tool calls only mature GET endpoints:
    - `GET /api/{orgId}/repository`
    - `GET /api/{orgId}/repository/nim/tags`
    - `GET /api/{orgId}/template`
  - It returns `sideEffect=NONE` and `preflightOnly=true`, with catalog/tag/template candidates and next HITL requirements.
  - `nim_create` remains fail-closed HOLD and does not call deployment create.
  - Verification passed:
    - `mvn -q "-Dtest=NimDeploymentPreflightToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
    - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
    - Static secret scan found 0 matches.
    - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - External recovery docs were synced to `H:\codex重要文件\kube-agent`.

- Date: 2026-06-06 21:16 Asia/Shanghai.
- Branch: `codex/m521-29-top-agent-mission`.
- M5.21-33 was completed, committed, and pushed:
  - Commit: `e5ba040 fix(M5.21): align registry site read tool`
  - `RegistryListTool` now uses `GET /api/registry`, optional `keyWord`, and `SENSITIVE_READ + requiresConfirmation=true`.
  - Registry create/update/delete and `/api/registry/repo-tag` remain HOLD.
  - Verification passed:
    - `mvn -q "-Dtest=RegistrySiteToolHttpContractTest,ListToolParameterPassThroughContractTest,ListToolParameterSpecContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
    - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
    - Static secret scan found no real credentials; only documentation/config comments mention api-key/password terms.
    - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - External recovery docs were synced to `H:\codex重要文件\kube-agent`.
- M5.21-32 was committed and pushed:
  - Commit: `2825387 feat(M5.21): add download task progress read tool`
- M5.21-31 was committed and pushed:
  - Commit: `e25738a fix(M5.21): align download task status read tool`
- M5.21-30 was committed and pushed:
  - Commit: `b5d4132 fix(M5.21): align MIG config read tool`
- Verification passed for M5.21-32 before commit:
  - `mvn -q test`
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - Static secret scan found no real credentials; only documentation/config comments mention api-key/password terms.
- External recovery docs were synced to `H:\codex重要文件\kube-agent`.

Latest in-progress/completed chunk after checkpoint:

- Date: 2026-06-06 21:16 Asia/Shanghai.
- M5.21-34 implemented, committed, and pushed:
  - Commit: `404d80e feat(M5.21): add repository catalog read tools`
  - Added `RepositoryCatalogListTool` for mature `GET /api/{orgId}/repository`.
  - Added `RepositoryCatalogCategoryListTool` for `GET /api/{orgId}/repository/category`.
  - Added `RepositoryCatalogTagListTool` for `GET /api/{orgId}/repository/tags`, requiring explicit `repository`.
  - Added `RepositoryCatalogNimTagListTool` for `GET /api/{orgId}/repository/nim/tags`, requiring explicit `repository`.
  - Added `RepositoryCatalogQuerySupport` to keep repository catalog schema separate from registry site and image repository semantics.
  - All four Tools are `AUTHENTICATED + SENSITIVE_READ + requiresConfirmation=true`.
  - Targeted test passed: `mvn -q "-Dtest=RepositoryCatalogToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`.
  - Final verification passed: `git -c safe.directory=F:/gitProject/kube-agent diff --check`, static secret scan with 0 matches, and `mvn -q test`.
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - External recovery docs were synced to `H:\codex重要文件\kube-agent`.
  - HOLD: image pull/retry/delete/push/build/load, NIM deployment creation, and `GET /api/registry/repo-tag`.

- Date: 2026-06-06 20:50 Asia/Shanghai.
- M5.21-33 completed implementation and targeted verification:
  - `RegistryListTool` now calls mature site endpoint `GET /api/registry` instead of old `/api/{orgId}/registry`.
  - It exposes optional `keyWord` only, with `keyword` as alias, and no longer exposes `page/limit`.
  - It is `SENSITIVE_READ + requiresConfirmation=true` because registry site DTO returns URL and username.
  - `GET /api/{orgId}/repository` is recorded as a separate product/application repository catalog candidate, not mixed into registry.
  - Added `RegistrySiteToolHttpContractTest`.
  - Targeted test passed: `mvn -q "-Dtest=RegistrySiteToolHttpContractTest,ListToolParameterPassThroughContractTest,ListToolParameterSpecContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`.

Current NIM chain summary after M5.21-51:

- Public `nim_deployment_preflight` remains read-only and cannot create deployments.
- `NimTemplateMergeSupport` creates only `safeToPost=false` previews.
- `NimCreationGateSupport` and `NimTrustedPolicySnapshot` model trusted policy/gate evidence, but public facts remain untrusted until a backend provider supplies them.
- `NimCreateStateMachineSupport` requires trusted policy, server HITL, durable audit receipt, controlled body rebuild, controlled POST request spec, controlled write execution handoff, durable write executor report, READY readiness execution report, and a code release switch before future writes.
- `NimCreateDurableAuditStorageSupport` now identifies mature `sys_log` as a partial-fit durable storage candidate, but keeps it as `IMPLEMENTATION_HOLD` until a dedicated NIM audit writer exists.
- `NimCreateWriteExecutionHandoffSupport` is the newest gate; it binds request spec/body/audit receipt with a server-derived idempotency key and post-write readiness handoff, but it still does not execute HTTP.
- `NimCreateDurableWriteExecutorSupport` is the future writer contract shell; it accepts trusted handoff/request spec input but still returns `IMPLEMENTATION_HOLD` and `writeExecuted=false`.
- The state machine now accepts the current executor shell only as evidence shape, then blocks release with `DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD`.

Recommended next work:

- Continue NIM orchestration through safe slices:
  - design a dedicated NIM durable audit writer interface using the `sys_log` candidate only as evidence, not as a direct release credential,
  - define pre-write intent and post-write result records plus storage availability gates,
  - design the reviewed real durable write executor boundary around a controlled kube-manager HTTP boundary, write-before/write-after audit, idempotency persistence, POST response validation, and post-write readiness triggering,
  - later wire `NimTrustedPolicyProviderSupport` to real backend license/user/org readers only after contract tests exist,
  - keep `nim_create` HOLD until trusted policy, durable audit writer, durable write executor, readiness aftercare, and release switch all pass review,
  - or pick another mature GET area with clean backend/frontend evidence.
