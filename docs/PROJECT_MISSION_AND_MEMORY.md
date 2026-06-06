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

`M5.21-43 NIM readiness read-only executor contract`

Latest checkpoint:

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

Recommended next work:

- Continue NIM orchestration through safe slices:
  - design `NimTrustedPolicyProvider` to fill `NimTrustedPolicySnapshot` from real backend license/user/org evidence,
  - add future `nim_create` state-machine contract tests requiring server-generated `HitlConfirmation`, audit context, and an open trusted gate before any write,
  - design creation-aftercare readiness polling as a separate sensitive read path that never generates, stores, or displays real API keys,
  - or pick another mature GET area with clean backend/frontend evidence.
