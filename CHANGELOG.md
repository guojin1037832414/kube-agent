# Atlas Kube-Agent 变更日志

> 按 Milestone 分组，记录所有已实现功能和重大变更。
> 格式：基于 [Keep a Changelog](https://keepachangelog.com/)。
> 里程碑前缀规范：`feat(Mx):`、`fix(Mx):`、`docs(Mx):`。

---

## [M5.21-91] - NIM release/switch secret detector migration

**Delivery**: Migrated release-decision and code-release-switch contract scanners to the shared no-exception forbidden secret material policy.
**Changes**
- `NimCreateDurableAuditReleaseDecisionContractSupport` now uses `NimForbiddenSecretMaterialDetector.nonBooleanNumberValuePolicy()` instead of a local forbidden secret key/value scanner.
- `NimCreateDurableAuditCodeReleaseSwitchContractSupport` now uses the same shared policy.
- Extended `NimForbiddenSecretMaterialDetectorUsageContractTest` so six non-Boolean/Number policy support classes cannot reintroduce local secret detector copies or documented-field exceptions.
- Added `docs/M5_21_NINETY_FIRST_WAVE_NIM_RELEASE_SWITCH_SHARED_SECRET_DETECTOR_MIGRATION_AUDIT_20260608.md`.
**Security**
- Local forged release/switch claim scanners were intentionally preserved because they guard authority/evidence forgery, not credential material.
- No runtime write behavior was opened.
- No real `8100`, deployment POST, release decision signer, code release switch implementation, durable writer/probe/receipt implementation, validation result signer, Elasticsearch, `ISysLogService`, or `sys_log` write was added.
- `nim_create` remains HOLD/mock-first.

## [M5.21-90] - NIM validation/probe-result secret detector migration

**Delivery**: Migrated validation/probe-result evidence scanners to a shared no-exception forbidden secret material policy.
**Changes**
- Added `NimForbiddenSecretMaterialDetector.nonBooleanNumberValuePolicy()` for contracts that allow Boolean/Number state scalars under forbidden keys but reject non-blank secret-bearing values and secret-like strings.
- `NimCreateDurableAuditStorageProbeResultSupport` now uses the shared non-Boolean/Number policy.
- `NimCreateDurableAuditReceiptValidationProbeResultBindingSupport` now uses the same shared policy.
- `NimCreateDurableAuditReceiptValidationResultSupport` now uses the same shared policy.
- `NimCreateDurableAuditValidationResultProbeBindingMigrationSupport` now uses the same shared policy.
- Extended `NimForbiddenSecretMaterialDetectorUsageContractTest` so these four migrated support classes cannot reintroduce local secret detector copies or documented-field exceptions.
- Added direct policy tests for Boolean/Number state scalar allowance versus nested secret object rejection.
- Added `docs/M5_21_NINETIETH_WAVE_NIM_VALIDATION_PROBE_RESULT_SHARED_SECRET_DETECTOR_MIGRATION_AUDIT_20260608.md`.
**Security**
- Local forged-success scanners were intentionally preserved because they guard evidence-source and validation/release forgery, not credential material.
- No runtime write behavior was opened.
- No real `8100`, deployment POST, storage probe, durable writer/probe/receipt implementation, validation result signer, release decision, release switch, Elasticsearch, `ISysLogService`, or `sys_log` write was added.
- `nim_create` remains HOLD/mock-first.

## [M5.21-89] - NIM durable audit storage secret detector migration

**Delivery**: Migrated the durable audit storage/probe-boundary input scanners to the shared forbidden secret material detector.
**Changes**
- `NimCreateDurableAuditStorageSupport` now uses `NimForbiddenSecretMaterialDetector.textValuePolicy()` instead of a local forbidden secret key/value scanner.
- `NimCreateDurableAuditStorageAvailabilityGateSupport` now uses the same shared text-value policy.
- `NimCreateDurableAuditStorageProbeExecutorSupport` now uses the same shared text-value policy while keeping its separate forged-success scanner local.
- `NimCreateDedicatedDurableAuditWriterBoundarySupport` now uses the same shared text-value policy while keeping its separate forged-success scanner local.
- Extended `NimForbiddenSecretMaterialDetectorUsageContractTest` so eleven migrated support classes cannot reintroduce local secret detector copies.
- Added nested/list-carried secret regression tests for all four migrated support classes.
- Added `docs/M5_21_EIGHTY_NINTH_WAVE_NIM_DURABLE_AUDIT_STORAGE_SHARED_SECRET_DETECTOR_MIGRATION_AUDIT_20260608.md`.
**Security**
- No runtime write behavior was opened.
- No real `8100`, deployment POST, storage probe, durable writer/probe/receipt implementation, validation result, release decision, release switch, Elasticsearch, `ISysLogService`, or `sys_log` write was added.
- `nim_create` remains HOLD/mock-first.

## [M5.21-88] - NIM durable write-chain secret detector migration

**Delivery**: Migrated the durable write-chain input scanners to the shared forbidden secret material detector.
**Changes**
- `NimCreateDurableWriteExecutorSupport` now uses `NimForbiddenSecretMaterialDetector.textValuePolicy()` instead of a local forbidden secret key/value scanner.
- `NimCreateDurableAuditWriterPlanSupport` now uses the same shared text-value policy.
- `NimCreateDurableAuditWriterInterfaceSpecSupport` now uses the same shared text-value policy for input scanning while preserving generated `requestContract.forbiddenFields` documentation.
- Extended `NimForbiddenSecretMaterialDetectorUsageContractTest` so the seven migrated support classes cannot reintroduce local secret detector copies.
- Added nested/list-carried secret regression tests and interface-spec policy boundary tests for documented field names, secret-like metadata, and numeric forbidden-key values.
- Added `docs/M5_21_EIGHTY_EIGHTH_WAVE_NIM_DURABLE_WRITE_CHAIN_SHARED_SECRET_DETECTOR_MIGRATION_AUDIT_20260608.md`.
**Security**
- No runtime write behavior was opened.
- No real `8100`, deployment POST, durable writer/probe/receipt implementation, validation result, release decision, release switch, Elasticsearch, `ISysLogService`, or `sys_log` write was added.
- `nim_create` remains HOLD/mock-first.

## [M5.21-87] - NIM write request and handoff secret detector migration

**Delivery**: Migrated the next homogeneous NIM write-chain contract shells to the shared forbidden secret material detector.
**Changes**
- `NimCreateWriteRequestSpecAdapterSupport` now uses `NimForbiddenSecretMaterialDetector.textValuePolicy()` instead of a local forbidden secret key/value scanner.
- `NimCreateWriteExecutionHandoffSupport` now uses the same shared text-value policy.
- Extended `NimForbiddenSecretMaterialDetectorUsageContractTest` so the four migrated support classes cannot reintroduce local `FORBIDDEN_SECRET_KEYS`, `looksLikeSecretValue(...)`, `isForbiddenSecretKey(...)`, or `secretBearingValue(...)` copies.
- Added adapter and handoff nested-secret regression tests for list-carried `ngcApiKey`, `Authorization=Bearer ...`, `token=...`, and `secret=...` material.
- Added `docs/M5_21_EIGHTY_SEVENTH_WAVE_NIM_WRITE_CHAIN_SHARED_SECRET_DETECTOR_MIGRATION_AUDIT_20260608.md`.
**Security**
- No runtime write behavior was opened.
- No real `8100`, deployment POST, durable writer/probe/receipt implementation, validation result, release decision, release switch, Elasticsearch, `ISysLogService`, or `sys_log` write was added.
- `nim_create` remains HOLD/mock-first.

## [M5.21-86] - Shared NIM forbidden secret material detector

**Delivery**: Centralized the first NIM secret-material scanner without changing existing HOLD/write boundaries.
**Changes**
- Added `NimForbiddenSecretMaterialDetector` as a shared detector for forbidden NIM secret keys and secret-looking values.
- Preserved policy differences through explicit detector policies:
  - text-value policy for existing `hasText(...)` style checks.
  - receipt-schema policy that allows documented forbidden field names while rejecting real secret material.
  - strict recursive policy for runtime source guard inputs where non-null secret-key values remain unsafe.
- Migrated `NimCreateDurableAuditReceiptSchemaSupport` and `NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport` to the shared detector.
- Added `NimForbiddenSecretMaterialDetectorTest` and `NimForbiddenSecretMaterialDetectorUsageContractTest`.
- Added `docs/M5_21_EIGHTY_SIXTH_WAVE_SHARED_NIM_FORBIDDEN_SECRET_MATERIAL_DETECTOR_AUDIT_20260608.md`.
**Security**
- No runtime write behavior was opened.
- No real `8100`, deployment POST, durable writer/probe/receipt, validation result, release decision, release switch, Elasticsearch, `ISysLogService`, or `sys_log` write was added.
- `nim_create` remains HOLD/mock-first.

## [M5.21-85] - Shared protected Tool parameter filter

**Delivery**: Centralized protected Tool parameter filtering across ReAct, SafeToolExecutor, and execute_node.
**Changes**
- Added `ProtectedToolParameterFilter` as the shared execution-boundary filter for auth/session/tenant, HITL, audit, release, risk metadata, and write-control fields.
- `ReActEngine` now uses the shared filter instead of its previous local protected parameter list.
- `SafeToolExecutor` now strips forged confirmation, audit, release, and write-control fields in both Graph compatibility and PLAN_EXECUTE_NODE paths.
- `AtlasGraphConfig` execute_node now uses the same shared filter for its recursive Plan parameter fail-closed guard before delegating to SafeToolExecutor.
- Added normalized-key coverage for variants such as `hitl_approved`, `release-approved`, `write_allowed`, `operation_type`, and `api.endpoints`.
- Added `ProtectedToolParameterFilterTest` and `ProtectedToolParameterFilterUsageContractTest`.
- Extended `SafeToolExecutorTest` and updated static execute-node/SafeToolExecutor contract tests.
- Added `docs/M5_21_EIGHTY_FIFTH_WAVE_SHARED_PROTECTED_TOOL_PARAMETER_FILTER_AUDIT_20260608.md`.
**Security**
- No runtime write behavior was opened.
- No real `8100`, deployment POST, durable writer/probe/receipt, validation result, release decision, release switch, Elasticsearch, `ISysLogService`, or `sys_log` write was added.
- `nim_create` remains HOLD/mock-first.

## [M5.21-84] - ReAct HITL execution guard contract

**Delivery**: Hardened ReAct execution-layer HITL behavior and audit timeline visibility.
**Changes**
- `ReActEngine` now emits `tool_done(success=false)` and `observation` events when `HitlGuard` blocks a high-risk Action, before the existing `error` event.
- The blocked Observation remains in `ReActMemory`, allowing the next ReAct turn to summarize the safety block instead of retrying blindly.
- ReAct Action parameter cleanup now strips forged confirmation, audit, release, and write-control fields such as `confirmed`, `hitlConfirmed`, `approval`, `auditReceipt`, `releaseDecision`, `writePermitted`, `writeExecutionAllowed`, `realHttpExecutionAllowed`, and `releaseEligible`.
- Added normalized-key filtering for common variants such as `hitl_approved`, `release-approved`, and `write_allowed`.
- Added `ReActEngineHitlGuardContractTest` to prove a scripted high-risk CREATE Action is blocked before Tool execution and produces a complete risk-tagged event timeline.
- Added `docs/M5_21_EIGHTY_FOURTH_WAVE_REACT_HITL_EXECUTION_GUARD_CONTRACT_AUDIT_20260608.md`.
**Security**
- No runtime write behavior was opened.
- No real `8100`, deployment POST, durable writer/probe/receipt, validation result, release decision, release switch, Elasticsearch, `ISysLogService`, or `sys_log` write was added.
- `nim_create` remains HOLD/mock-first.

## [M5.21-83] - ReAct risk metadata prompt contract

**Delivery**: Aligned ReAct high-risk prompt behavior with ToolRegistry risk metadata.
**Changes**
- `ReActPromptBuilder` now treats ToolRegistry risk labels as authoritative prompt-level risk hints.
- Tools with `operationType=CREATE/UPDATE/DELETE/ACTION/PLACEHOLDER` or `requiresConfirmation=true` must output Mode C/HITL instead of direct Action.
- The prompt states that completed parameters, default value backfill, optional fields, and natural-language "confirmation" do not replace server-side HITL.
- The prompt forbids proactively generating auth, tenant, HITL, audit, release, or write-control fields in `Action.params`.
- `operationType=PLACEHOLDER` or `httpMethod=NONE` is now described as a non-open real backend execution path; ReAct must not claim create/delete/submit/change success for those tools.
- Added `ReActPromptBuilderRiskMetadataContractTest`, covering READ/CREATE/UPDATE/DELETE/ACTION/PLACEHOLDER prompt labels and Mode C rules.
- Added `docs/M5_21_EIGHTY_THIRD_WAVE_REACT_RISK_METADATA_PROMPT_CONTRACT_AUDIT_20260608.md`.
**Security**
- No runtime write behavior was opened.
- No real `8100`, deployment POST, durable writer/probe/receipt, validation result, release decision, release switch, Elasticsearch, `ISysLogService`, or `sys_log` write was added.
- `nim_create` remains HOLD/mock-first.

## [M5.21-82] - Default value prompt authority contract

**Delivery**: Extended default-value safety into the LLM-visible ToolRegistry prompt.
**Changes**
- `ToolRegistry.buildSystemPromptForCurrentUser()` now states that "默认/可选" means form draft/frontend fill only.
- The prompt rule says defaults do not mean user confirmation, HITL pass, release approval, audit success, write authorization, or real HTTP execution permission.
- The prompt rule clarifies `requiresConfirmation=false` as "no extra HITL", not a bypass of login, RBAC, tenant isolation, release gates, or backend authorization.
- The prompt tells the model not to proactively generate auth, tenant, HITL, audit, release, or write-control fields in `Action.params`.
- Extended `ToolRegistryPromptContractTest`.
- Added `M521DefaultValuePromptAuthorityContractTest` to keep ToolRegistry prompt generation from importing/rendering `DefaultValueRegistry`, `DefaultValueApplier`, `IntentDefaults`, or `defaults.yml`.
- Added `docs/M5_21_EIGHTY_SECOND_WAVE_DEFAULT_VALUE_PROMPT_AUTHORITY_CONTRACT_AUDIT_20260608.md`.
**Security**
- No runtime write behavior was opened.
- No real `8100`, deployment POST, durable writer/probe/receipt, validation result, release decision, release switch, Elasticsearch, `ISysLogService`, or `sys_log` write was added.
- `nim_create` remains HOLD/mock-first.

## [M5.21-81] - Default value global safety contract

**Delivery**: Generalized the "defaults are not authorization" rule into the shared default-value infrastructure.
**Changes**
- Added `DefaultValueSafety` as the global protected-default filter.
- `IntentDefaults` now sanitizes default maps at construction time, covering YAML-loaded defaults, test/reflection injection, and future registry construction paths.
- Protected keys are normalized and recursively stripped from nested maps/lists.
- Protected near-synonyms such as `accessToken`, `clientSecret`, `targetOrgId`, `hitlApproved`, `writeAllowed`, `releaseApproved`, `trustedPolicySource`, `writeBodyRebuildReport`, `success`, and `executed` are also blocked.
- `DefaultValueRegistryTest` now proves dangerous injected defaults such as `confirmed`, `writePermitted`, `releaseEligible`, `Authorization`, `organizationId`, and nested `token` are never applied.
- Added `M521DefaultValueSafetyContractTest` to recursively scan `defaults.yml` and lock protected key examples.
- Kept legitimate business form defaults such as `user_create.role=user` allowed.
- Added a Chinese safety note to `defaults.yml`.
- Added `docs/M5_21_EIGHTY_FIRST_WAVE_DEFAULT_VALUE_GLOBAL_SAFETY_CONTRACT_AUDIT_20260608.md`.
**Security**
- No runtime write behavior was opened.
- No real `8100`, deployment POST, durable writer/probe/receipt, validation result, release decision, release switch, Elasticsearch, `ISysLogService`, or `sys_log` write was added.
- `nim_create` remains HOLD/mock-first.

## [M5.21-80] - NIM create defaults / intent HOLD contract

**Delivery**: Hardened the `nim_create` metadata layer so defaults and intent parameters remain UI/form draft hints only.
**Changes**
- Added `M521NimCreateDefaultsIntentHoldContractTest`.
- The contract parses `defaults.yml` and asserts `defaults.nim_create` contains only `gpuPercentLimits`, `replicas`, and `enableWebSsh`.
- The contract parses `intents.yml` and rejects `nim_create` control-plane/release keys such as `safeToPost`, `confirmed`, `writePermitted`, `writeExecutionAllowed`, `releaseEligible`, `releaseDecision`, auth headers, org/user identity, fallback, and deployment success fields.
- The contract proves applying `nim_create` defaults plus forged release claims still leaves `NimCreateTool` fail-closed with `UNSUPPORTED_BACKEND_OPERATION`, `state=HELD`, `writePermitted=false`, and `sideEffect=NONE`.
- `NimCreateStateMachineSupport` now records additional forged caller release/code-switch/source-guard claims as ignored.
- Locked `NimCreateTool` against `@WithDefaults`, `DefaultValueApplier`, and `DefaultValueRegistry` while it remains a placeholder.
- Added `docs/M5_21_EIGHTIETH_WAVE_NIM_CREATE_DEFAULTS_INTENT_HOLD_CONTRACT_AUDIT_20260608.md`.
**Security**
- No runtime write behavior was opened.
- No real `8100`, HTTP client in `NimCreateTool`, `POST /api/{orgId}/deployment`, durable writer, storage probe, durable receipt, validation result, release decision, release switch, Elasticsearch, `ISysLogService`, or `sys_log` write was added.
- `nim_create` remains HOLD/mock-first.

## [M5.21-79] - NIM create Tool entry no-I/O static contract

**Delivery**: Hardened the public `nim_create` Tool entry so the placeholder entry no longer owns an unused runtime HTTP dependency.
**Changes**
- Removed unused `KubeManagerHttpClient` constructor dependency from `NimCreateTool`.
- `NimCreateTool` remains `httpMethod=NONE`, `apiEndpoints={}`, `operationType=PLACEHOLDER`, `requiresConfirmation=true`, and fail-closed.
- Updated `HighRiskMutationToolHttpContractTest` to construct `new NimCreateTool()` and continue verifying no HTTP interaction.
- Added `M521NimCreateToolEntryStaticContractTest` to guard the public Tool entry against HTTP/storage/sys_log/8100/runtime shortcut drift.
- Added `docs/M5_21_SEVENTY_NINTH_WAVE_NIM_CREATE_TOOL_ENTRY_NO_IO_STATIC_CONTRACT_AUDIT_20260608.md`.
**Security**
- No real durable writer, storage probe, HTTP client in `NimCreateTool`, Spring write executor registration, kube-manager `8100`, durable receipt, release decision, release switch, or deployment POST was added.
- `nim_create` remains HOLD/mock-first.

## [M5.21-78] - NIM durable audit writer/probe boundary static contract

**Delivery**: Hardened the future NIM durable audit writer/probe boundary before any real storage or write path exists.
**Changes**
- `NimCreateDedicatedDurableAuditWriterBoundarySupport` now recursively rejects forged success claims hidden in nested maps or list items.
- Added `boundary_shouldRejectNestedForgedStorageAndReceiptClaims` to prove nested `storageAvailable=true` and `receiptStatus=DURABLE_RECORDED` are fail-closed.
- Added `M521NimDurableAuditWriterProbeBoundaryStaticContractTest`.
- The static contract reads the dedicated writer boundary, storage probe executor, and the wider durable audit/release chain to lock digest fields and forged-claim blockers.
- The contract statically rejects Spring/HTTP/storage/sys_log/8100/runtime I/O shortcuts and direct success-state `result.put(..., true)` writes.
**Security**
- No real durable writer, storage probe, HTTP client, Spring registration, Elasticsearch, `ISysLogService`, `sys_log`, kube-manager `8100`, durable receipt, release decision, release switch, or deployment POST was added.
- `nim_create` remains HOLD/mock-first.

## [M5.21-77] - NIM runtime source guard binding static contract

**Delivery**: Added a source-level contract test that locks the M5.21-76 runtime source-guard report binding into the current state-machine and durable-executor shells.
**Changes**
- Added `M521NimRuntimeSourceGuardBindingContractTest`.
- The contract asserts `NimCreateStateMachineSupport` still declares and validates `codeReleaseSwitchRuntimeSourceGuardReport`, emits required/false release fields, and cross-checks durable executor source guard digests.
- The contract asserts `NimCreateDurableWriteExecutorSupport` still reads, validates, secret-scans, and digest-binds the same source guard report before accepting the write shell.
- The contract statically rejects environment/property/Spring/HTTP/storage/sys_log/8100/write-success shortcuts in the two binding shells.
- Added `docs/M5_21_SEVENTY_SEVENTH_WAVE_NIM_RUNTIME_SOURCE_GUARD_BINDING_STATIC_CONTRACT_AUDIT_20260608.md`.
**Security**
- No runtime write behavior changed.
- `nim_create` remains HOLD/mock-first; no real switch, release credential, HTTP client, Spring registration, kube-manager `8100`, `sys_log`, Elasticsearch, or deployment POST was added.

## [M5.21-76] - NIM runtime source guard report binding

**Delivery**: Bound the M5.21-75 `codeReleaseSwitchRuntimeSourceGuardReport` into the current state-machine and durable-executor shells as mandatory fail-closed evidence, while keeping `nim_create` held.
**Changes**
- `NimCreateDurableWriteExecutorSupport` now requires the runtime source-guard report before accepting a controlled handoff/request-spec/switch-contract input.
- `NimCreateStateMachineSupport` now accepts and independently validates the same source-guard report, and cross-checks the durable executor report echoes the same source guard/runtime-binding digests.
- Legal executor shell output remains `IMPLEMENTATION_HOLD` with both `DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD` and `CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_IMPLEMENTATION_HOLD`.
- Legal state-machine full-shell output remains `HELD` with executor HOLD, switch-contract HOLD, and source-guard HOLD.
- Added tests for missing source guard, tampered `sourceGuardMatrixDigest`, forged source release claims, `llmJsonSourceAllowed=true`, backend readback claims, `deploymentId`, and source guard secret leakage.
- Added `docs/M5_21_SEVENTY_SIXTH_WAVE_NIM_CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REPORT_BINDING_AUDIT_20260608.md`.
**Security**
- A valid source-guard report is required evidence only; it is not a release credential.
- No real switch, release credential, HTTP client, Spring Bean, Controller, Tool registration, Elasticsearch, `ISysLogService`, `sys_log` write, kube-manager `8100` call, or real `POST /api/{orgId}/deployment` was added.
- `writePermitted`, `writeExecutionAllowed`, `realHttpExecutionAllowed`, `writeAttempted`, `writeExecuted`, and `postWriteReadinessTriggered` remain false.

## [M5.21-75] - NIM code release switch runtime source guard matrix

**Delivery**: Added a contract-only runtime source-guard matrix after M5.21-73 so future `nim_create` release code cannot confuse planning evidence, runtime flags, readback data, or executor success with a reviewed open switch.
**Changes**
- Added `NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport`.
- Added `NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupportTest`.
- Added `M521NimCodeReleaseSwitchRuntimeSourceGuardContractTest` as a source-level guard against environment/property release-switch shortcuts.
- The matrix marks M5.21-72 and M5.21-73 reports as planning/shape sources only and keeps `acceptedSourcesForCurrentRelease=[]`.
- The matrix explicitly forbids caller/LLM JSON, env/runtime flags, legacy `nimCreateReleased`, state-machine `writePermitted`, durable executor success, backend readback, and `sys_log`/Elasticsearch backfill as release sources.
- Added `docs/M5_21_SEVENTY_FIFTH_WAVE_NIM_CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_AUDIT_20260608.md`.
**Security**
- No real switch, release decision, validation result, release credential, HTTP client, Spring Bean, Controller, Tool registration, Elasticsearch, `ISysLogService`, `sys_log` write, kube-manager `8100` call, or real `POST /api/{orgId}/deployment` was added.
- `nim_create` remains `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`.

## [M5.21-74] - NIM code release switch contract report binding

**Delivery**: Wired the M5.21-72 `codeReleaseSwitchContractReport` into the current state-machine and durable-executor shells as fail-closed release evidence shape, while keeping `nim_create` held.
**Changes**
- `NimCreateStateMachineSupport` now accepts `codeReleaseSwitchContractReport`, recomputes `codeReleaseSwitchContractDigest`, validates the M5.21-72 HOLD contract shape, and rejects missing/tampered/forged-open switch evidence.
- `NimCreateDurableWriteExecutorSupport` now requires the same switch contract report before accepting handoff/request-spec input and exposes `sourceCodeReleaseSwitchContractDigest`.
- Added state-machine and durable-executor tests for missing, tampered, and forged code-switch reports.
- Added `docs/M5_21_SEVENTY_FOURTH_WAVE_NIM_CODE_RELEASE_SWITCH_CONTRACT_REPORT_BINDING_AUDIT_20260607.md`.
**Security**
- A valid switch contract report remains shape evidence only and still adds an implementation HOLD blocker.
- No real switch, release decision, validation result, release credential, HTTP client, Spring Bean, Controller, Tool registration, Elasticsearch, `ISysLogService`, `sys_log` write, kube-manager `8100` call, or real `POST /api/{orgId}/deployment` was added.

## [M5.21-73] - NIM code release switch runtime binding contract

**Delivery**: Added a contract-only runtime binding layer that consumes the M5.21-72 code release switch contract report and requires both the state machine and durable executor to re-check the reviewed switch digest before any future write release.
**Changes**
- Added `NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport`.
- Added `NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest`.
- Requires future `NimCreateStateMachineSupport` to consume `codeReleaseSwitchContractReport`, recompute `codeReleaseSwitchContractDigest`, bind server-issued release/validation digests, and reject `nimCreateReleased=true` as standalone authorization.
- Requires future `NimCreateDurableWriteExecutorSupport` to re-check the same switch digest immediately before any real POST and reject state-machine/write-success shortcuts.
- Updated state-machine and durable-executor shell outputs with `codeReleaseSwitchRuntimeBindingRequired=true` and false verified/bound flags.
- Added `docs/M5_21_SEVENTY_THIRD_WAVE_NIM_CODE_RELEASE_SWITCH_RUNTIME_BINDING_AUDIT_20260607.md`.
**Security**
- This wave does not create a real switch, release decision, validation result, DTO, Spring Bean, storage client, release credential, Tool registration, Controller, state-machine release, or write path.
- `nim_create` remains `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`, with no real `8100`, no real deployment POST execution, no Elasticsearch, no `ISysLogService`, and no `sys_log` write.

## [M5.21-72] - NIM code release switch contract

**Delivery**: Added a contract-only future `NimCreateDurableAuditCodeReleaseSwitch` layer that consumes the M5.21-71 release decision contract and keeps write release held until a reviewed, server-owned code release switch exists.
**Changes**
- Added `NimCreateDurableAuditCodeReleaseSwitchContractSupport`.
- Added `NimCreateDurableAuditCodeReleaseSwitchContractSupportTest`.
- Binds M5.21-71 `releaseDecisionContractDigest`, M5.21-70 `validationResultContractDigest`, future server-issued `validationResultDigest`, future `releaseDecisionDigest`, future `codeReleaseSwitchDigest`, source audit event, trusted principal, upstream migration/probe/schema/writer digests, and future write-chain digest fields.
- Requires future switch evidence to carry `codeReviewDigest`, `testEvidenceDigest`, `securityApprovalDigest`, `rollbackPlanDigest`, and `changeWindowDigest` before the switch can open.
- Rejects missing or tampered M5.21-71 reports, caller-supplied switch/runtime/environment overrides, forged open/write claims, and secret-bearing inputs.
- Keeps `serverOwnedCodeReleaseSwitchRequired=true`, `callerSwitchEvidenceAuthoritative=false`, `realCodeReleaseSwitchCreated=false`, `realCodeReleaseSwitchOpened=false`, `codeReleaseSwitchDigestVerified=false`, `releaseEligible=false`, `writePermitted=false`, and `writeExecutionAllowed=false`.
- Added `docs/M5_21_SEVENTY_SECOND_WAVE_NIM_CODE_RELEASE_SWITCH_CONTRACT_AUDIT_20260607.md`.
**Security**
- This wave does not create a real switch, release decision, validation result, DTO, Spring Bean, storage client, release credential, Tool registration, Controller, state-machine release, or write path.
- `nim_create` remains `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`, with no real `8100`, no real deployment POST execution, no Elasticsearch, no `ISysLogService`, and no `sys_log` write.

## [M5.21-71] - NIM durable audit release decision contract

**Delivery**: Added a contract-only future `NimDurableAuditReleaseDecision` value contract that consumes the M5.21-70 receipt validation result contract and keeps write release held until a reviewed server-issued release decision exists.
**Changes**
- Added `NimCreateDurableAuditReleaseDecisionContractSupport`.
- Added `NimCreateDurableAuditReleaseDecisionContractSupportTest`.
- Binds M5.21-70 `validationResultContractDigest`, future server-issued `validationResultDigest`, future `releaseDecisionDigest`, `codeReleaseSwitchDigest`, source audit event, trusted principal, M5.21-69 enhanced migration, M5.21-68 probe binding/result, M5.21-67 probe executor, M5.21-58 migration, receipt schema, validation plan, writer interface, writer boundary, writer plan, availability plan, and future write-chain digest fields.
- Requires future release evidence to explicitly carry `bodyDigest`, `requestSpecDigest`, `handoffDigest`, `auditReceiptId`, and `serverDerivedIdempotencyKey`.
- Rejects missing or tampered M5.21-70 reports, caller-supplied release/validation/receipt evidence, forged release gate/write claims, and secret-bearing inputs.
- Keeps `serverIssuedReleaseDecisionRequired=true`, `callerReleaseEvidenceAuthoritative=false`, `realReleaseDecisionCreated=false`, `serverIssuedReleaseDecisionAccepted=false`, `releaseDecisionAccepted=false`, `releaseCredentialIssued=false`, `releaseEligible=false`, `writePermitted=false`, and `writeExecutionAllowed=false`.
- Added `docs/M5_21_SEVENTY_FIRST_WAVE_NIM_DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_AUDIT_20260607.md`.
**Security**
- This wave does not create a real release decision, validation result, DTO, Spring Bean, storage client, release credential, Tool registration, Controller, state-machine release, or write path.
- `nim_create` remains `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`, with no real `8100`, no real deployment POST execution, no Elasticsearch, no `ISysLogService`, and no `sys_log` write.

## [M5.21-70] - NIM durable audit receipt validation result contract

**Delivery**: Added a contract-only future `NimDurableAuditReceiptValidationResult` value contract that consumes the M5.21-69 enhanced migration report and keeps NIM write release held until a reviewed server-issued validation result exists.
**Changes**
- Added `NimCreateDurableAuditReceiptValidationResultSupport`.
- Added `NimCreateDurableAuditReceiptValidationResultSupportTest`.
- Binds M5.21-69 `enhancedMigrationPlanDigest`, M5.21-68 `sourceProbeBindingPlanDigest`, M5.21-68 `sourceProbeResultContractDigest`, M5.21-67 `sourceProbeExecutorPlanDigest`, M5.21-58 `sourceMigrationPlanDigest`, source audit event, receipt schema, validation plan, writer interface, writer boundary, writer plan, availability plan, and trusted principal digest.
- Requires future server-issued validation result evidence to explicitly carry `storageProbeReceiptDigest`, `preWriteDurableAckDigest`, `postWriteDurableAckDigest`, and `durableReceiptDigest`.
- Rejects missing or tampered M5.21-69 migration reports, caller-supplied validation/release/receipt evidence, forged PASS/release/write claims, and secret-bearing inputs.
- Keeps `serverIssuedValidationResultRequired=true`, `callerValidationEvidenceAuthoritative=false`, `legacyMigrationReportAloneAllowed=false`, `realStorageTouched=false`, `realValidationResultCreated=false`, `validationPassed=false`, `releaseEligible=false`, and `writeExecutionAllowed=false`.
- Added `docs/M5_21_SEVENTIETH_WAVE_NIM_DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_CONTRACT_AUDIT_20260607.md`.
**Security**
- This wave does not create a real validator, validation result, release decision, DTO, Spring Bean, storage client, release credential, Tool registration, Controller, or write path.
- `nim_create` remains `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`, with no real `8100`, no real deployment POST execution, no Elasticsearch, no `ISysLogService`, and no `sys_log` write.

## [M5.21-69] - NIM validation result probe binding migration contract

**Delivery**: Added a contract-only bridge requiring future durable audit validation result and release decision migration to bind the M5.21-68 receipt validation probe-result-binding report before any validation PASS or release decision can exist.
**Changes**
- Added `NimCreateDurableAuditValidationResultProbeBindingMigrationSupport`.
- Added `NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest`.
- Binds M5.21-68 `bindingPlanDigest`, M5.21-68 `sourceProbeResultContractDigest`, M5.21-58 `migrationPlanDigest`, source audit event, receipt schema, validation plan, interface spec, writer boundary, writer plan, availability plan, and trusted principal digest.
- Rejects missing M5.21-68 binding report, missing M5.21-58 migration report, tampered binding digests, cross-report digest mismatches, forged probe binding success claims, caller-supplied validation/release/probe/audit receipt evidence, and secret-bearing inputs.
- Keeps `legacyMigrationReportAloneAllowed=false`, `probeBindingRequiredBeforeValidationResult=true`, `realValidationResultCreated=false`, `realReleaseDecisionCreated=false`, `releaseEligible=false`, and `writeExecutionAllowed=false`.
- Added `docs/M5_21_SIXTY_NINTH_WAVE_NIM_DURABLE_AUDIT_VALIDATION_RESULT_PROBE_BINDING_MIGRATION_AUDIT_20260607.md`.
**Security**
- This wave does not create a real validator, validation result, release decision, DTO, Spring Bean, storage client, release credential, Tool registration, Controller, or write path.
- `nim_create` remains `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`, with no real `8100`, no real deployment POST execution, no Elasticsearch, no `ISysLogService`, and no `sys_log` write.

## [M5.21-68] - NIM receipt validation probe result binding contract

**Delivery**: Added a contract-only binding layer requiring future durable receipt validation to bind the M5.21-67 storage probe result contract before any receipt validation can run.
**Changes**
- Added `NimCreateDurableAuditReceiptValidationProbeResultBindingSupport`.
- Added `NimCreateDurableAuditReceiptValidationProbeResultBindingSupportTest`.
- Binds M5.21-67 `probeResultContractDigest`, M5.21-57 `validationPlanDigest`, source audit event, typed schema, interface spec, writer boundary, writer plan, availability plan, probe executor plan, and trusted principal digest.
- Rejects schema-only validation, validation-gate-only shortcuts, caller-supplied probe result / receipt / validation evidence, forged validation pass claims, and cross-report digest mismatches.
- Keeps `storageProbeResultBoundForValidation=false`, `serverIssuedProbeResultAccepted=false`, `validationCanRunNow=false`, `durableReceiptValidationPassed=false`, `releaseEligible=false`, and `writeExecutionAllowed=false`.
- Added `docs/M5_21_SIXTY_EIGHTH_WAVE_NIM_DURABLE_AUDIT_RECEIPT_VALIDATION_PROBE_RESULT_BINDING_AUDIT_20260607.md`.
**Security**
- This wave does not create a real validator, DTO, Spring Bean, storage client, receipt, validation result, release decision, or write credential.
- `nim_create` remains `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`, with no real `8100`, no real deployment POST execution, no Elasticsearch, no `ISysLogService`, and no `sys_log` write.

## [M5.21-67] - NIM durable audit storage probe result contract

**Delivery**: Added a contract-only future `NimDurableAuditStorageProbeResult` layer after the M5.21-66 storage probe executor shell.
**Changes**
- Added `NimCreateDurableAuditStorageProbeResultSupport`.
- Added `NimCreateDurableAuditStorageProbeResultSupportTest`.
- Binds M5.21-66 `probeExecutorPlanDigest`, M5.21-56 `schemaDigest`, source audit event, writer plan, availability plan, writer boundary, interface spec, and trusted principal digest.
- Cross-checks that the probe executor report and typed receipt schema report belong to the same upstream write chain.
- Rejects caller-supplied probe result / storage probe receipt instances and forged success claims.
- Keeps `storageProbeExecuted=false`, `storageAvailable=false`, `durableAckVerified=false`, `readAfterWriteVerified=false`, `preWriteAllowed=false`, `writeExecutionAllowed=false`, and `durableReceiptCanBeIssued=false`.
- Added `docs/M5_21_SIXTY_SEVENTH_WAVE_NIM_DURABLE_AUDIT_STORAGE_PROBE_RESULT_AUDIT_20260607.md`.
**Security**
- This wave does not create a real DTO, Spring Bean, storage client, receipt, or release credential.
- `nim_create` remains `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`, with no real `8100`, no `POST /api/{orgId}/deployment`, no Elasticsearch, no `ISysLogService`, and no `sys_log` write.

## [M5.21-66] - NIM durable audit storage probe executor contract

**Delivery**: Added a contract-only storage probe executor shell that binds the M5.21-53 availability gate and M5.21-54 dedicated writer boundary before any future real storage probe can be considered.
**Changes**
- Added `NimCreateDurableAuditStorageProbeExecutorSupport`.
- Added `NimCreateDurableAuditStorageProbeExecutorSupportTest`.
- The positive path returns `IMPLEMENTATION_HOLD` and keeps `storageProbeExecuted=false`, `storageAvailable=false`, `durableAckVerified=false`, `readAfterWriteVerified=false`, `preWriteAllowed=false`, `writeExecutionAllowed=false`, and `durableReceiptCanBeIssued=false`.
- The executor shell rejects forged probe/storage/ack/read-after-write/write/receipt success claims across audit context, trusted principal, availability gate, writer boundary, and diagnostic snapshot inputs.
- Added source-level dependency guard coverage for the new support class so it cannot silently bind Spring, HTTP, Elasticsearch, `ISysLogService`, `java.net`, or real storage writes.
- Added `docs/M5_21_SIXTY_SIXTH_WAVE_NIM_DURABLE_AUDIT_STORAGE_PROBE_EXECUTOR_AUDIT_20260607.md`.
**Security**
- This wave does not enable real storage probing or write execution.
- `nim_create` remains `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`, with no real `8100`, no `POST /api/{orgId}/deployment`, no Elasticsearch, no `ISysLogService`, and no `sys_log` write.

## [M5.21-65] - NIM accepted boolean source guard

**Delivery**: Added a source-level regression guard so future production code cannot consume `releaseDecisionGateReportAccepted` alone as a release approval signal.
**Changes**
- Added `M521NimAcceptedBooleanSourceContractTest`.
- Scans `src/main/java` and rejects standalone reads such as `get("releaseDecisionGateReportAccepted")` or `containsKey("releaseDecisionGateReportAccepted")`.
- Keeps the contract shell allowed to write the compatibility field and its required non-authoritative companion signals.
- Added `docs/M5_21_SIXTY_FIFTH_WAVE_NIM_ACCEPTED_BOOLEAN_SOURCE_GUARD_AUDIT_20260607.md`.
**Security**
- This wave is test/docs-only and does not modify production release logic.
- `nim_create` remains `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`, with no real `8100`, no `POST /api/{orgId}/deployment`, no Elasticsearch, no `ISysLogService`, and no `sys_log` write.

## [M5.21-64] - NIM accepted boolean non-authoritative contract

**Delivery**: Made the legacy `releaseDecisionGateReportAccepted` boolean explicitly non-authoritative so it cannot be consumed alone as a release approval signal.
**Changes**
- Added `releaseDecisionGateReportAcceptedFieldIsCompatibilityOnly=true`.
- Added `releaseDecisionGateReportAcceptedIsAuthoritative=false`.
- Added `releaseDecisionGateReportAcceptedStandaloneConsumptionAllowed=false`.
- Added required companion signals for scope, real state-machine acceptance, digest verification, and write permission status.
- Extended `stateMachineFieldMigration`, `failureContract`, and `forbiddenShortcuts` to ban fallback to the legacy accepted boolean.
- Added `docs/M5_21_SIXTY_FOURTH_WAVE_NIM_ACCEPTED_BOOLEAN_NON_AUTHORITATIVE_CONTRACT_AUDIT_20260607.md`.
**Security**
- This wave does not modify `NimCreateStateMachineSupport` real release logic and does not enable writes.
- `nim_create` remains `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`, with no real `8100`, no `POST /api/{orgId}/deployment`, no Elasticsearch, no `ISysLogService`, and no `sys_log` write.

## [M5.21-63] - NIM state-machine gate report acceptance semantics

**Delivery**: Clarified the M5.21-60 state-machine release decision gate report acceptance semantics so future implementers cannot confuse contract input-shape acceptance with real state-machine release acceptance.
**Changes**
- Added `releaseDecisionGateReportAcceptanceScope=CONTRACT_INPUT_SHAPE_ONLY|NOT_ACCEPTED`.
- Added `releaseDecisionGateReportAcceptanceIsRealStateMachineRelease=false`.
- Added `releaseDecisionGateReportAcceptanceCanEnableWrite=false`.
- Extended `NimCreateStateMachineReleaseDecisionRequirementSupportTest` to assert both accepted-shape and rejected-input semantics.
- Added `docs/M5_21_SIXTY_THIRD_WAVE_NIM_STATE_MACHINE_GATE_REPORT_ACCEPTANCE_SEMANTICS_AUDIT_20260607.md`.
**Security**
- This wave does not modify `NimCreateStateMachineSupport` real release logic and does not enable writes.
- `nim_create` remains `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`, with no real `8100`, no `POST /api/{orgId}/deployment`, no Elasticsearch, no `ISysLogService`, and no `sys_log` write.

## [M5.21-62] - NIM state-machine secret list-item coverage matrix

**Delivery**: Completed the M5.21-61 secret leakage matrix by proving list-item secret rejection for every M5.21-60 state-machine release decision requirement input.
**Changes**
- Added `auditContext.callerEvents[].token` and `trustedPrincipalSnapshot.sessionEvidence[].password` leak cases to `NimCreateStateMachineReleaseDecisionRequirementSupportTest`.
- The existing gate-report list-item case remains in place, so `auditContext`, `trustedPrincipalSnapshot`, and `durableAuditReleaseDecisionGateReport` now each have list-item coverage.
- Added `docs/M5_21_SIXTY_SECOND_WAVE_NIM_STATE_MACHINE_SECRET_LIST_MATRIX_AUDIT_20260607.md`.
**Security**
- This wave is test/docs-only; no production release logic changed.
- `nim_create` remains `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`, with no real `8100`, no `POST /api/{orgId}/deployment`, no Elasticsearch, no `ISysLogService`, and no `sys_log` write.

## [M5.21-61] - NIM state-machine release decision requirement secret coverage hardening

**Delivery**: Hardened the M5.21-60 state-machine release decision report requirement tests by expanding forbidden secret leakage coverage across every contract input and nested evidence shape.
**Changes**
- Extended `NimCreateStateMachineReleaseDecisionRequirementSupportTest` with multi-case secret leakage scenarios.
- Covered top-level `auditContext.token`, nested `auditContext.password`, top-level `trustedPrincipalSnapshot.secret`, nested trusted-principal `Authorization`, top-level `durableAuditReleaseDecisionGateReport.ngcApiKey`, nested `nvaieApiKey`, and list-item `token`.
- Each case still rejects with `STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_INPUT_CONTAINS_FORBIDDEN_SECRET`.
- Each case proves `requirementState=REJECTED`, `inputAccepted=false`, empty `stateMachineRequirementPlan`, `writePermitted=false`, `writeExecutionAllowed=false`, and `realHttpExecutionAllowed=false`.
- Added `docs/M5_21_SIXTY_FIRST_WAVE_NIM_STATE_MACHINE_SECRET_COVERAGE_AUDIT_20260607.md`.
**Security**
- This wave changes tests and docs only; it adds no real release decision, no HTTP client, no Elasticsearch, no `ISysLogService`, no `sys_log` write, no real `8100` access, and no `POST /api/{orgId}/deployment`.
- `nim_create` remains `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`.

## [M5.21-60] - NIM state-machine release decision report requirement contract

**Delivery**: Added a contract-only state-machine release decision report requirement after M5.21-59. It locks the future requirement that `NimCreateStateMachineSupport` must consume and recompute a release decision gate report before any real `writePermitted=true` path can exist, while leaving the current state machine behavior unchanged and held.
**Changes**
- Added `NimCreateStateMachineReleaseDecisionRequirementSupport`, consuming only `auditContext`, `trustedPrincipalSnapshot`, and `durableAuditReleaseDecisionGateReport`.
- Output includes `stateMachineReleaseDecisionReportRequirement=NIM_CREATE_STATE_MACHINE_RELEASE_DECISION_REPORT_REQUIREMENT`, `executionMode=STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_CONTRACT_ONLY`, and `requirementState=IMPLEMENTATION_HOLD|REJECTED`.
- Positive input prepares `stateMachineRequirementSequence`, `requiredFutureStateMachineEvidence`, `stateMachineFieldMigration`, `currentDenyTemplate`, `failureContract`, and `forbiddenShortcuts`.
- The requirement plan binds the M5.21-59 release decision gate plan digest, migration/validation/schema digests, source audit event digest, trusted principal, and future state-machine evidence for validation result digest, release decision digest, body/request/handoff digests, audit receipt id, server-derived idempotency key, and code release switch.
- Current state remains `realStateMachineReleaseDecisionGateReportAccepted=false`, `releaseDecisionGateDigestVerified=false`, `validationResultDigestVerified=false`, `releaseDecisionDigestVerified=false`, `stateMachineReleaseGateImplemented=false`, `stateMachineReleaseBound=false`, `stateMachineCanSetWritePermittedNow=false`, `legacyAuditReceiptReleaseEligibleTrusted=false`, `writePermitted=false`, `writeExecutionAllowed=false`, and `realHttpExecutionAllowed=false`.
- Forged `releaseDecision`, `validationResult`, legacy `auditReceipt.releaseEligible`, write permission, or executor success claims are rejected with `STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_FORGED_RELEASE_CLAIM`; even an empty caller-supplied `releaseDecision` is rejected.
- Added `NimCreateStateMachineReleaseDecisionRequirementSupportTest`.
- Added `docs/M5_21_SIXTIETH_WAVE_NIM_STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_AUDIT_20260607.md`.
**Security**
- This wave adds no real Java release decision/validator/release gate, no Spring Bean, no Elasticsearch, no `ISysLogService`, no `sys_log` write, no HTTP client, no real `8100` access, and no `POST /api/{orgId}/deployment`.
- The requirement plan is not a release decision or release credential; `nim_create` remains `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`.

## [M5.21-59] - NIM durable audit release decision gate contract

**Delivery**: Added a contract-only release decision gate plan after M5.21-58. It describes how a future server-issued `NimDurableAuditReleaseDecision` must bind back into both the state machine and durable write executor, while still issuing no real release credential and performing no I/O.
**Changes**
- Added `NimCreateDurableAuditReleaseDecisionGateSupport`, consuming only `auditContext`, `trustedPrincipalSnapshot`, and `durableAuditValidationResultMigrationReport`.
- Output includes `durableAuditReleaseDecisionGate=NIM_CREATE_DURABLE_AUDIT_RELEASE_DECISION_GATE`, `executionMode=DURABLE_AUDIT_RELEASE_DECISION_GATE_CONTRACT_ONLY`, and `gateState=IMPLEMENTATION_HOLD|REJECTED`.
- Positive input prepares `gateSequence`, `requiredFutureEvidence`, `stateMachineBindingPlan`, `durableExecutorBindingPlan`, `currentDenyTemplate`, `failureContract`, and `forbiddenShortcuts`.
- The plan binds M5.21-58 migration digest, validation plan/schema/interface/boundary/writer/availability digests, trusted principal, and future write-chain evidence including body/request/handoff digests, audit receipt id/event digest, and server-derived idempotency key.
- Current state remains `realReleaseDecisionLoaded=false`, `realReleaseDecisionAccepted=false`, `validationResultDigestVerified=false`, `releaseDecisionDigestVerified=false`, `stateMachineReleaseBound=false`, `durableExecutorReleaseBound=false`, `releaseEligible=false`, `writePermitted=false`, `writeExecutionAllowed=false`, and `realHttpExecutionAllowed=false`.
- Forged `releaseDecision`, `validationResult`, legacy `auditReceipt.releaseEligible`, write permission, or executor success claims are rejected with `DURABLE_AUDIT_RELEASE_DECISION_GATE_FORGED_RELEASE_CLAIM`.
- Added `NimCreateDurableAuditReleaseDecisionGateSupportTest`.
- Added `docs/M5_21_FIFTY_NINTH_WAVE_NIM_DURABLE_AUDIT_RELEASE_DECISION_GATE_AUDIT_20260607.md`.
**Security**
- This wave adds no real Java release decision/validator/release gate, no Spring Bean, no Elasticsearch, no `ISysLogService`, no `sys_log` write, no HTTP client, no real `8100` access, and no `POST /api/{orgId}/deployment`.
- The gate plan is not a release decision or release credential; `nim_create` remains `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`.

## [M5.21-58] - NIM durable audit validation result migration contract

**Delivery**: Added a contract-only migration plan for the future `NimDurableAuditReceiptValidationResult` and `NimDurableAuditReleaseDecision` after the M5.21-57 receipt validation gate. The plan separates validation gate rules, migration planning, server-issued validation results, and release decisions. It still creates no real DTO, Bean, validator, I/O, or release credential.
**Changes**
- Added `NimCreateDurableAuditValidationResultMigrationSupport`, consuming only `auditContext`, `trustedPrincipalSnapshot`, and `durableAuditReceiptValidationGateReport`.
- Output includes `durableAuditValidationResultMigrationPlan=NIM_CREATE_DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_PLAN`, `executionMode=DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_CONTRACT_ONLY`, and `migrationPlanState=IMPLEMENTATION_HOLD|REJECTED`.
- Positive input prepares `migrationSequence`, `validationResultContract`, `releaseDecisionContract`, `legacyCompatibilityPolicy`, `releaseCredentialRules`, `failureContract`, and `forbiddenShortcuts`, bound to the M5.21-57 validation plan digest and upstream schema/interface/boundary/writer/availability digests.
- Current state remains `realValidationResultCreated=false`, `realReleaseDecisionCreated=false`, `storageProbeReceiptValidated=false`, `preWriteDurableAckValidated=false`, `postWriteDurableAckValidated=false`, `digestChainValidated=false`, `trustedPrincipalValidated=false`, `durableReceiptValidationPassed=false`, `releaseEligible=false`, and `writeExecutionAllowed=false`.
- Positive input is still blocked by `DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_IMPLEMENTATION_HOLD`.
- Missing validation gate report is rejected with `DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_REPORT_NOT_READY`.
- Forged `validationResult`, `releaseDecision`, legacy `auditReceipt.releaseEligible`, `validationStatus=PASS`, or write execution claims are rejected with `DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_FORGED_RELEASE_CLAIM`.
- Secret leakage is rejected with `DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_INPUT_CONTAINS_FORBIDDEN_SECRET`.
- Added `NimCreateDurableAuditValidationResultMigrationSupportTest`.
- Added `docs/M5_21_FIFTY_EIGHTH_WAVE_NIM_DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_AUDIT_20260607.md` and updated memory/index/learning docs.
**Security**
- This wave adds no real Java DTO/validator/release gate, no Spring Bean, no Elasticsearch, no `ISysLogService`, no `sys_log` write, no HTTP client, no real `8100` access, and no `POST /api/{orgId}/deployment`.
- The migration plan is not a validation result, release decision, or release credential; legacy `auditReceipt.releaseEligible` is not trusted as a release source.
- `nim_create` remains `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`.

## [M5.21-57] - 第五十七批 NIM durable audit receipt validation gate 契约审计

**交付**: 在 M5.21-56 typed ack/receipt schema 之后，新增未来 `NimDurableAuditReceiptValidator` 的 validation gate 契约，先把真实 receipt/ack 出现后必须通过的 digest、phase、status、principal 和 release decision 校验规则固定下来，仍不执行真实校验或 I/O。

**变更**
- 新增 `NimCreateDurableAuditReceiptValidationGateSupport`，纯数据消费 `auditContext`、`trustedPrincipalSnapshot`、`durableAuditReceiptAckSchemaReport`。
- 输出 `durableAuditReceiptValidationGate=NIM_CREATE_DURABLE_AUDIT_RECEIPT_VALIDATION_GATE`、`executionMode=DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_CONTRACT_ONLY`、`gateState=IMPLEMENTATION_HOLD|REJECTED`。
- 正向输入生成 `validationPlan.validationSequence`、`requiredEvidence`、`releaseDecisionTemplate`、`failureContract`、`forbiddenShortcuts`，并绑定 M5.21-56 schema digest。
- 当前明确保持 `realStorageTouched=false`、`storageProbeReceiptValidated=false`、`preWriteDurableAckValidated=false`、`postWriteDurableAckValidated=false`、`digestChainValidated=false`、`trustedPrincipalValidated=false`、`durableReceiptValidationPassed=false`、`releaseEligible=false`、`writeExecutionAllowed=false`。
- 正向输入仍返回 `DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_IMPLEMENTATION_HOLD`。
- 缺少 schema report 返回 `DURABLE_AUDIT_RECEIPT_ACK_SCHEMA_REPORT_NOT_READY`。
- 伪造 validation pass、typed receipt/ack、release decision 或 write execution claim 返回 `DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_FORGED_PASS_CLAIM`。
- secret 泄漏返回 `DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_INPUT_CONTAINS_FORBIDDEN_SECRET`。
- 新增 `NimCreateDurableAuditReceiptValidationGateSupportTest`。
- 新增 `docs/M5_21_FIFTY_SEVENTH_WAVE_NIM_DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_AUDIT_20260607.md`，并更新 M5.21 波次索引、开发指南和项目记忆。

**安全**
- 本批不创建真实 Java validator/value type，不注入 Spring Bean，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不新增 HTTP client，不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- validation plan 不是 validation pass、durable receipt 或 release credential。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## [M5.21-56] - 第五十六批 NIM durable audit typed ack/receipt schema 契约审计

**交付**: 在 M5.21-55 future writer interface spec 之后，新增 typed storage probe receipt、pre-write ack、post-write ack 和 durable receipt 的 schema 契约，继续保持纯数据、contract-only 和 `IMPLEMENTATION_HOLD`。

**变更**
- 新增 `NimCreateDurableAuditReceiptSchemaSupport`，纯数据消费 `auditContext`、`trustedPrincipalSnapshot`、`durableAuditWriterInterfaceSpecReport`。
- 输出 `durableAuditReceiptAckSchema=NIM_CREATE_DURABLE_AUDIT_RECEIPT_ACK_SCHEMA`、`executionMode=DURABLE_AUDIT_RECEIPT_ACK_SCHEMA_CONTRACT_ONLY`、`schemaState=IMPLEMENTATION_HOLD|REJECTED`。
- 正向输入生成 `typedSchema.storageAvailabilityProbeReceiptSchema`、`preWriteDurableAckSchema`、`postWriteDurableAckSchema`、`durableAuditReceiptSchema`、`digestChainRules`、`failureContract`、`testDoubleRules`，并绑定 M5.21-55 interface spec digest。
- 当前明确保持 `realStorageTouched=false`、`storageProbeExecuted=false`、`storageAvailable=false`、`storageProbeReceiptIssued=false`、`preWriteDurableAckIssued=false`、`postWriteDurableAckIssued=false`、`durableReceiptIssued=false`。
- 正向输入仍返回 `DURABLE_AUDIT_RECEIPT_ACK_SCHEMA_IMPLEMENTATION_HOLD`。
- 缺少 interface spec report 返回 `DURABLE_AUDIT_WRITER_INTERFACE_SPEC_REPORT_NOT_READY`。
- 伪造 typed ack/receipt 或 storage/persistence/receipt success claim 返回 `DURABLE_AUDIT_RECEIPT_SCHEMA_FORGED_SUCCESS_CLAIM`；即使是空的 caller-supplied typed ack 实例也会拒绝。
- secret 泄漏返回 `DURABLE_AUDIT_RECEIPT_SCHEMA_INPUT_CONTAINS_FORBIDDEN_SECRET`，同时区分接口规格里的“禁用字段名清单”和真实 secret material。
- 新增 `NimCreateDurableAuditReceiptSchemaSupportTest`。
- 新增 `docs/M5_21_FIFTY_SIXTH_WAVE_NIM_DURABLE_AUDIT_RECEIPT_ACK_SCHEMA_AUDIT_20260607.md`，并更新 M5.21 波次索引、开发指南和项目记忆。

**安全**
- 本批不创建真实 Java writer/value type，不注入 Spring Bean，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不新增 HTTP client，不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- typed schema 不是 ack instance、durable writer result 或 release credential。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## [M5.21-55] - 第五十五批 NIM durable audit writer 接口规格契约审计

**交付**: 在 M5.21-54 dedicated writer boundary 之后，新增未来 `NimDurableAuditWriter` 的接口规格契约，先把请求、响应、方法、失败语义和 test double 规则固定下来，仍不创建真实接口或接入存储。

**变更**
- 新增 `NimCreateDurableAuditWriterInterfaceSpecSupport`，纯数据消费 `auditContext`、`trustedPrincipalSnapshot`、`dedicatedAuditWriterBoundaryReport`。
- 输出 `durableAuditWriterInterfaceSpec=NIM_CREATE_DURABLE_AUDIT_WRITER_INTERFACE_SPEC`、`executionMode=DURABLE_AUDIT_WRITER_INTERFACE_SPEC_CONTRACT_ONLY`、`interfaceSpecState=IMPLEMENTATION_HOLD|REJECTED`。
- 正向输入生成 `interfaceSpec.requestContract`、`responseContract`、`operationMethods`、`failureContract`、`testDoubleRules`，并绑定 M5.21-54 boundary digest。
- 当前明确保持 `realStorageTouched=false`、`storageProbeExecuted=false`、`storageAvailable=false`、`preWritePersisted=false`、`postWritePersisted=false`、`durableReceiptCanBeIssued=false`。
- 正向输入仍返回 `DURABLE_AUDIT_WRITER_INTERFACE_IMPLEMENTATION_HOLD`。
- 缺少 boundary report 返回 `DEDICATED_AUDIT_WRITER_BOUNDARY_REPORT_NOT_READY`。
- 伪造 storage/persistence/receipt success claim 返回 `DURABLE_AUDIT_WRITER_INTERFACE_FORGED_SUCCESS_CLAIM`；secret 泄漏返回 `DURABLE_AUDIT_WRITER_INTERFACE_INPUT_CONTAINS_FORBIDDEN_SECRET`。
- 新增 `NimCreateDurableAuditWriterInterfaceSpecSupportTest`。
- 新增 `docs/M5_21_FIFTY_FIFTH_WAVE_NIM_DURABLE_AUDIT_WRITER_INTERFACE_SPEC_AUDIT_20260607.md`，并更新 M5.21 波次索引、开发指南和项目记忆。

**安全**
- 本批不创建真实 Java writer 接口，不注入 Spring Bean，不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不新增 HTTP client，不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- interface spec 不是 durable writer result，也不是 release credential。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## [M5.21-54] - 第五十四批 NIM 专用 durable audit writer 边界与测试替身契约审计

**交付**: 在 M5.21-52 writer plan 与 M5.21-53 storage availability gate 之后，新增 dedicated writer boundary / test double contract，明确当前只能验证边界和测试替身约束，不能声明真实存储成功或签发 durable receipt。

**变更**
- 新增 `NimCreateDedicatedDurableAuditWriterBoundarySupport`，纯数据消费 `auditContext`、`trustedPrincipalSnapshot`、`durableAuditWriterPlanReport`、`storageAvailabilityGateReport`。
- 输出 `dedicatedAuditWriterBoundary=NIM_CREATE_DEDICATED_DURABLE_AUDIT_WRITER_BOUNDARY`、`executionMode=DEDICATED_DURABLE_AUDIT_WRITER_BOUNDARY_TEST_DOUBLE_CONTRACT_ONLY`、`writerBoundaryState=IMPLEMENTATION_HOLD|REJECTED`。
- 正向输入生成 `writerBoundaryPlan`，固化未来 `NimDurableAuditWriter` 的 `probe -> pre-write -> post-write -> receipt` 顺序、digest 绑定、可信身份绑定和 receipt release rule。
- 正向输入生成 `testDoubleContract`，只允许断言契约形状、顺序、digest/identity binding 和 fail-closed blocker，禁止断言 `storageAvailable=true`、`preWritePersisted=true`、`postWritePersisted=true` 或 `DURABLE_RECORDED`。
- 当前明确保持 `realStorageTouched=false`、`storageProbeExecuted=false`、`storageAvailable=false`、`preWritePersisted=false`、`postWritePersisted=false`、`durableReceiptCanBeIssued=false`。
- 正向输入仍返回 `DEDICATED_DURABLE_AUDIT_WRITER_BOUNDARY_IMPLEMENTATION_HOLD`。
- 缺少 writer plan / availability gate report 返回 `DURABLE_AUDIT_WRITER_PLAN_REPORT_NOT_READY` / `STORAGE_AVAILABILITY_GATE_REPORT_NOT_READY`。
- 伪造 storage/persistence/receipt success claim 返回 `DEDICATED_AUDIT_WRITER_BOUNDARY_FORGED_SUCCESS_CLAIM`；secret 泄漏返回 `DEDICATED_AUDIT_WRITER_BOUNDARY_INPUT_CONTAINS_FORBIDDEN_SECRET`。
- 新增 `NimCreateDedicatedDurableAuditWriterBoundarySupportTest`。
- 新增 `docs/M5_21_FIFTY_FOURTH_WAVE_NIM_DEDICATED_DURABLE_AUDIT_WRITER_BOUNDARY_AUDIT_20260607.md`，并更新 M5.21 波次索引、开发指南和项目记忆。

**安全**
- 本批不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不新增 HTTP client，不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- boundary plan 与 test double contract 都不是 release credential。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## [M5.21-53] - 第五十三批 NIM durable audit storage 可用性门禁计划契约审计

**交付**: 在 M5.21-52 writer plan 之后新增 storage availability gate 计划契约，明确未来必须先证明 `sys_log`/持久化链路可用，才能写 pre-write intent 或签发 durable receipt。

**变更**
- 新增 `NimCreateDurableAuditStorageAvailabilityGateSupport`，纯数据消费 `auditContext`、`trustedPrincipalSnapshot`、`durableAuditWriterPlanReport`。
- 输出 `durableAuditStorageAvailabilityGate=NIM_CREATE_DURABLE_AUDIT_STORAGE_AVAILABILITY_GATE`、`executionMode=DURABLE_AUDIT_STORAGE_AVAILABILITY_GATE_CONTRACT_ONLY`、`gateState=IMPLEMENTATION_HOLD|REJECTED`。
- 正向输入生成 `availabilityPlan.probeSteps`、`failurePolicy`、`receiptPrerequisites`、`trustedIdentityBinding`。
- 当前明确保持 `storageProbeExecuted=false`、`storageAvailable=false`、`availabilityStatus=UNKNOWN_UNTIL_REAL_PROBE`、`durableReceiptCanBeIssued=false`。
- 正向输入仍返回 `STORAGE_AVAILABILITY_PROBE_IMPLEMENTATION_HOLD`。
- 缺少 writer plan report 返回 `DURABLE_AUDIT_WRITER_PLAN_REPORT_NOT_READY`。
- 伪造 storage available / durable success claim 返回 `STORAGE_AVAILABILITY_GATE_FORGED_SUCCESS_CLAIM`；secret 泄漏返回 `STORAGE_AVAILABILITY_GATE_INPUT_CONTAINS_FORBIDDEN_SECRET`。
- 新增 `NimCreateDurableAuditStorageAvailabilityGateSupportTest`。
- 新增 `docs/M5_21_FIFTY_THIRD_WAVE_NIM_DURABLE_AUDIT_STORAGE_AVAILABILITY_GATE_AUDIT_20260607.md`，并更新 M5.21 波次索引和项目记忆。

**安全**
- 本批不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不新增 HTTP client，不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- availability plan 不是 storage probe result，也不是 release credential。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## [M5.21-52] - 第五十二批 NIM durable audit writer 两阶段计划契约审计

**交付**: 在 M5.21-51 `sys_log` 持久化候选证据之上，新增 NIM 专用 durable audit writer 的两阶段计划契约，明确未来必须先写 pre-write intent、再写 post-write result，同时当前仍不能签发 durable receipt。

**变更**
- 新增 `NimCreateDurableAuditWriterPlanSupport`，纯数据消费 `auditContext`、`trustedPrincipalSnapshot`、`durableAuditStorageReport`，并可选绑定 `writeRequestSpecReport` / `writeExecutionHandoffReport`。
- 输出 `durableAuditWriterPlan=NIM_CREATE_DURABLE_AUDIT_WRITER_PLAN`、`executionMode=DURABLE_AUDIT_WRITER_PLAN_CONTRACT_ONLY`、`writerState=IMPLEMENTATION_HOLD|REJECTED`。
- 正向输入生成 `writerPlan.storageAvailabilityGate`、`trustedIdentityBinding`、`preWriteRecordTemplate`、`postWriteRecordTemplate`、`receiptIssuanceRule`。
- `preWriteRecordTemplate` 固化 future pre-write intent 记录；`postWriteRecordTemplate` 固化 future post-write result 记录，并可绑定 request spec digest、body digest、handoff digest 和服务端幂等键。
- 正向输入仍返回 `DURABLE_AUDIT_STORAGE_CANDIDATE_IMPLEMENTATION_HOLD` 与 `DURABLE_AUDIT_WRITER_IMPLEMENTATION_HOLD`。
- 缺少 storage candidate report 返回 `DURABLE_AUDIT_STORAGE_CANDIDATE_REPORT_NOT_READY`。
- 伪造 durable/release/receipt claim 返回 `DURABLE_AUDIT_WRITER_FORGED_RELEASE_CLAIM`；secret 泄漏返回 `DURABLE_AUDIT_WRITER_INPUT_CONTAINS_FORBIDDEN_SECRET`。
- 新增 `NimCreateDurableAuditWriterPlanSupportTest`。
- 新增 `docs/M5_21_FIFTY_SECOND_WAVE_NIM_DURABLE_AUDIT_WRITER_PLAN_AUDIT_20260607.md`，并更新 M5.21 波次索引和项目记忆。

**安全**
- 本批不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不新增 HTTP client，不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- writer plan 不是 durable receipt，不能替代 `DURABLE_RECORDED + DURABLE_AUDIT_LOG`。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## [M5.21-51] - 第五十一批 NIM durable audit storage 候选契约审计

**交付**: 识别 mature `kube-manager` 的 `sys_log` 系统日志链路作为 NIM durable audit storage 候选证据，并新增纯函数契约，明确它还不能直接签发 NIM 专用 durable audit receipt。

**变更**
- 新增 `NimCreateDurableAuditStorageSupport`，纯数据消费 `auditContext` 和 `trustedPrincipalSnapshot`。
- 输出 `durableAuditStorage=NIM_CREATE_DURABLE_AUDIT_STORAGE_CANDIDATE`、`executionMode=DURABLE_AUDIT_STORAGE_CANDIDATE_CONTRACT_ONLY`、`storageState=IMPLEMENTATION_HOLD|REJECTED`。
- 固化 mature 证据: `SaveLogAspect`、`ISysLogService.saveLog(SysLog)`、`SysLog`、`sys_log`、`GET /api/log`、`/system/log`。
- 正向输入生成脱敏 `sysLogFieldMapping`，但仍 `realStorageTouched=false`、`durable=false`、`releaseEligible=false`、`durableReceiptCanBeIssued=false`。
- 正向输入仍返回 `DEDICATED_NIM_AUDIT_WRITER_NOT_IMPLEMENTED`，防止把通用系统日志误当成 NIM 专用审计 receipt。
- 缺少可信服务端 principal 返回 `TRUSTED_PRINCIPAL_SNAPSHOT_NOT_READY`；secret 泄漏返回 `DURABLE_AUDIT_STORAGE_INPUT_CONTAINS_FORBIDDEN_SECRET`。
- 新增 `NimCreateDurableAuditStorageSupportTest`。
- 新增 `docs/M5_21_FIFTY_FIRST_WAVE_NIM_DURABLE_AUDIT_STORAGE_CANDIDATE_AUDIT_20260607.md`，并更新 M5.21 波次索引和项目记忆。

**安全**
- 本批不连接 Elasticsearch，不调用 `ISysLogService`，不写 `sys_log`，不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- `sys_log` 是候选持久化证据，不是 release credential。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## [M5.21-50] - 第五十批 NIM durable write executor 报告门禁审计

**交付**: `NimCreateStateMachineSupport` 现在显式要求 durable write executor 报告；当前 M5.21-49 shell 报告即使形状合法，也只能证明执行器仍在 `IMPLEMENTATION_HOLD`，不能释放真实写入。

**变更**
- `ReadinessRequest` 新增 `durableWriteExecutorReport`，并保留兼容构造器表达旧负例。
- 状态机输出新增 `durableWriteExecutorReportRequired=true`。
- 缺少 durable executor 报告返回 `DURABLE_WRITE_EXECUTOR_REPORT_NOT_READY`。
- 当前 shell 报告必须绑定 handoff digest、request spec digest、body digest、audit receipt、服务端幂等键和 `executionAttemptSpec`。
- 合法 shell 报告仍追加 `DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD`，保持 `writePermitted=false`。
- 伪造 `executorImplementationAvailable/writeAttempted/writeExecuted/postWriteReadinessTriggered/deploymentId/deploymentUid/writeResult` 会触发 `DURABLE_WRITE_EXECUTOR_SUCCESS_NOT_TRUSTED`。
- 更新状态机、body rebuilder、request spec adapter、write execution handoff、audit readiness 相关测试，统一新门禁语义。
- 新增 `docs/M5_21_FIFTIETH_WAVE_NIM_DURABLE_WRITE_EXECUTOR_REPORT_GATE_AUDIT_20260607.md`，并更新 M5.21 波次索引和项目记忆。

**安全**
- 本批不新增 Tool/Controller，不持有 HTTP client，不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- durable executor report 是必需证据，但当前 shell report 不是 release credential。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## [M5.21-49] - 第四十九批 NIM durable write executor 合同壳审计

**交付**: 新增 `NimCreateDurableWriteExecutorSupport`，为未来 `FUTURE_DURABLE_WRITE_EXECUTOR` 建立 mock-first 入场合同壳；它验证 handoff/request spec 证据，但在真实实现完成前强制 `IMPLEMENTATION_HOLD`，不执行真实写入。

**变更**
- 新增 `NimCreateDurableWriteExecutorSupport`，纯函数消费 `writeExecutionHandoffReport` 和 `writeRequestSpecReport`。
- `prepare(...)` 输出 `durableWriteExecutor=FUTURE_DURABLE_WRITE_EXECUTOR`、`executionMode=DURABLE_WRITE_EXECUTOR_CONTRACT_SHELL`、`executionState=IMPLEMENTATION_HOLD|REJECTED`、`networkAccess=NOT_PERFORMED`、`sideEffect=NONE`、`executionAttemptSpec` 和 `blockedBy`。
- 合法 handoff/request spec 输入也只会得到 `inputAccepted=true`、`writeAttempted=false`、`writeExecuted=false`、`postWriteReadinessTriggered=false` 和 `DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD`。
- executor shell 会复核 request spec digest、body digest、handoff digest、server-derived idempotency key、durable audit handoff 和 post-write readiness handoff。
- `NimCreateStateMachineSupport` 的 ignored caller claims 新增 `durableWriteExecutorReport`、`durableWriteExecutor`、`executorImplementationAvailable`、`writeAttempted`、`writeExecuted`、`writeResult`、`deploymentId`、`deploymentUid`、`postWriteReadinessTriggered`。
- 新增 `NimCreateDurableWriteExecutorSupportTest`。
- 新增 `docs/M5_21_FORTY_NINTH_WAVE_NIM_DURABLE_WRITE_EXECUTOR_SHELL_AUDIT_20260607.md`，并更新 M5.21 波次索引和项目记忆。

**安全**
- 本批不新增 Tool/Controller，不持有 HTTP client，不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- executor shell 报告不是 release credential；当前 `executorImplementationAvailable=false`。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## [M5.21-48] - 第四十八批 NIM 写执行交接与幂等契约审计

**交付**: 新增 `NimCreateWriteExecutionHandoffSupport`，把未来 `nim_create` 真实 durable write executor 前的执行交接、服务端幂等键、pre-write audit handoff 和 post-write readiness handoff 独立合同化；本批仍不执行真实网络写入。

**变更**
- 新增 `NimCreateWriteExecutionHandoffSupport`，纯函数消费 `creationGate`、`auditContext`、`auditReceipt`、`writeBodyRebuildReport`、`writeRequestSpecReport`。
- `prepare(...)` 输出 `writeExecutionHandoff=NIM_CREATE_WRITE_EXECUTION_HANDOFF`、`executionMode=WRITE_EXECUTION_HANDOFF_CONTRACT_ONLY`、`networkAccess=NOT_PERFORMED`、`sideEffect=NONE`、`writeExecutionPrepared`、`futureExecutor=FUTURE_DURABLE_WRITE_EXECUTOR`、`executionHandoffPlan`、`idempotencyKey`、`handoffDigest` 和 `blockedBy`。
- handoff plan 固定为未来 `POST /api/{orgId}/deployment` 形态，但 `realHttpExecutionAllowed=false`，并绑定 `requestSpecDigest`、`bodyDigest`、durable audit receipt 和 server-derived idempotency key。
- `idempotencyKeySource=SERVER_DERIVED_FROM_AUDIT_AND_REQUEST_SPEC`，调用方不能提供或覆盖幂等键。
- `NimCreateStateMachineSupport.ReadinessRequest` 新增 `writeExecutionHandoffReport`，并保留兼容构造器让旧负例可继续表达缺失 handoff。
- 状态机输出新增 `writeExecutionHandoffRequired=true`。
- 缺少 handoff 报告返回 `WRITE_EXECUTION_HANDOFF_REPORT_NOT_READY`；合约不合法、digest/receipt/request spec/audit identity 不匹配返回 `WRITE_EXECUTION_HANDOFF_REPORT_CONTRACT_INVALID`；secret 泄漏返回 `WRITE_EXECUTION_HANDOFF_REPORT_CONTAINS_FORBIDDEN_SECRET`。
- 状态机会复算 `handoffDigest`，并确认 handoff plan 绑定 request spec digest、body digest、pre-write audit handoff 和 post-write readiness handoff。
- 新增 `NimCreateWriteExecutionHandoffSupportTest`，并更新状态机、request spec、body rebuilder、audit readiness 的未来绿色 fixture。
- 新增 `docs/M5_21_FORTY_EIGHTH_WAVE_NIM_WRITE_EXECUTION_HANDOFF_AUDIT_20260607.md`，并更新 M5.21 波次索引和项目记忆。

**安全**
- 本批不新增 Tool/Controller，不持有 HTTP client，不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- handoff 报告不是 release credential，不能替代 trusted policy、HITL、durable audit receipt、body rebuild、request spec、READY readiness executor 或 release switch。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## [M5.21-47] - 第四十七批 NIM 受控 POST request spec 适配器审计

**交付**: 新增 `NimCreateWriteRequestSpecAdapterSupport`，把未来 `nim_create` 真实 POST 前的 HTTP 请求规格独立合同化；本批仍不执行真实网络写入。

**变更**
- 新增 `NimCreateWriteRequestSpecAdapterSupport`，纯函数消费 `creationGate`、`auditContext`、`auditReceipt`、`writeBodyRebuildReport`。
- `compile(...)` 输出 `writeRequestSpecAdapter=NIM_CREATE_WRITE_REQUEST_SPEC_ADAPTER`、`executionMode=POST_REQUEST_SPEC_CONTRACT_ONLY`、`networkAccess=NOT_PERFORMED`、`sideEffect=NONE`、`writeRequestPrepared`、`requestSpec`、`requestSpecDigest` 和 `blockedBy`。
- request spec 固定为未来 `POST /api/{orgId}/deployment` 形态，但 `sideEffect=NONE`，并要求 `bodySource=CONTROLLED_REBUILDER_BODY_COPY`、`callerHeadersAllowed=false`、`authorizationHeaderFromCallerAllowed=false`、`realApiKeyAllowed=false`。
- `NimCreateStateMachineSupport.ReadinessRequest` 新增 `writeRequestSpecReport`。
- 状态机输出新增 `writeRequestSpecRequired=true`。
- 缺少 request spec 报告返回 `WRITE_REQUEST_SPEC_REPORT_NOT_READY`；合约不合法、digest/body/receipt/audit identity 不匹配返回 `WRITE_REQUEST_SPEC_REPORT_CONTRACT_INVALID`；secret 泄漏返回 `WRITE_REQUEST_SPEC_REPORT_CONTAINS_FORBIDDEN_SECRET`。
- 状态机会复算 `requestSpecDigest`，并确认 request body 与 rebuilder body 完全一致。
- 新增 `NimCreateWriteRequestSpecAdapterSupportTest`，并更新状态机、body rebuilder、audit readiness 的未来绿色 fixture。
- 新增 `docs/M5_21_FORTY_SEVENTH_WAVE_NIM_WRITE_REQUEST_SPEC_ADAPTER_AUDIT_20260607.md`，并更新 M5.21 波次索引和项目记忆。

**安全**
- 本批不新增 Tool/Controller，不持有 HTTP client，不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- request spec 报告不是 release credential，不能替代 trusted policy、HITL、durable audit receipt、READY readiness executor 或 release switch。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## [M5.21-46] - 第四十六批 NIM 受控写入 body 重建契约审计

**交付**: 新增 `NimCreateWriteBodyRebuilderSupport`，把未来 `nim_create` 真实 POST 前的 DeploymentDTO 从“provenance 字符串”升级为可检查的受控 body 重建报告；本批仍不开放真实创建。

**变更**
- 新增 `NimCreateWriteBodyRebuilderSupport`，纯函数消费 `creationGate`、`deploymentBodyPreview`、`auditContext`、`auditReceipt`。
- `rebuild(...)` 输出 `writeBodyRebuilder=NIM_CREATE_WRITE_BODY_REBUILDER`、`executionMode=CONTROLLED_BODY_CONTRACT_ONLY`、`networkAccess=NOT_PERFORMED`、`sideEffect=NONE`、`writeBodyPrepared`、`body`、`bodyDigest`、`sourceAuditReceiptId/sourceAuditEventDigest` 和 `blockedBy`。
- 重建 body 只复制 DeploymentDTO 白名单字段，剥离 `organizationId/orgId/userId/conversationId/token` 等执行上下文和密钥字段。
- rebuilder 要求 `creationGate=READY_FOR_SERVER_CONFIRMED_WRITE`、trusted policy passed、preview complete 且 `safeToPost=false`、完整 audit context、durable audit receipt，并校验 receipt 身份与 audit context 匹配。
- `NimCreateStateMachineSupport.ReadinessRequest` 新增 `writeBodyRebuildReport`。
- 状态机输出新增 `writeBodyRebuildRequired=true`。
- 缺少重建报告返回 `WRITE_BODY_REBUILD_REPORT_NOT_READY`；report 合约不合法或未绑定 audit receipt 返回 `WRITE_BODY_REBUILD_REPORT_CONTRACT_INVALID`；report 含 secret 返回 `WRITE_BODY_REBUILD_REPORT_CONTAINS_FORBIDDEN_SECRET`。
- 新增 `NimCreateWriteBodyRebuilderSupportTest`，更新相关状态机绿灯 fixture。
- 新增 `docs/M5_21_FORTY_SIXTH_WAVE_NIM_WRITE_BODY_REBUILDER_AUDIT_20260607.md`，并更新 M5.21 波次索引和项目记忆。

**安全**
- 本批不新增 Tool/Controller，不发真实 HTTP，不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- 重建报告 `releaseCredential=false`，不能替代 trusted policy、HITL、durable audit receipt、READY readiness executor 或 release 开关。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## [M5.21-45] - 第四十五批 NIM readiness HTTP adapter 契约审计

**交付**: 新增 `NimCreateReadinessHttpAdapterSupport`，把 NIM 创建后的 readiness plan 编译为只读 HTTP request specs；本批仍然只做 mock-first/纯数据契约，不注册 Tool、不持有 HTTP client、不访问真实 `8100` 或 NIM 服务。

**变更**
- 新增 `NimCreateReadinessHttpAdapterSupport`。
- `compile(...)` 输出 `readinessHttpAdapter=NIM_CREATE_READINESS_HTTP_ADAPTER`、`executionMode=REQUEST_SPEC_CONTRACT_ONLY`、`networkAccess=NOT_PERFORMED`、`sideEffect=NONE`、`readOnly=true`、`pollOnly=true`、`apiKeyHeaderPolicy=DO_NOT_SEND_REAL_API_KEY`、`requestSpecs`、`derivedSteps`、`executorHandoff`、`pendingBy/blockedBy`。
- adapter 只接受已审计的四段 readiness plan: deployment GET、service 派生、NIM health GET、NIM models GET。
- adapter 只生成 deployment GET、NIM health GET、NIM models GET 三类 request specs；service 仍是派生步骤，不发 HTTP。
- 加固 fail-closed: POST/未知 target/未审计 GET endpoint、不安全 Deployment query、不安全 service URL、真实 Bearer/API Key/secret-shaped 值全部拒绝，且不产出 request specs。
- `NimCreateStateMachineSupport` 的 readiness plan 目标收紧为必须覆盖 `deployment/service/nim-health/nim-models`，与 executor 契约保持一致。
- 新增 `NimCreateReadinessHttpAdapterSupportTest`，覆盖正向规格、缺服务 URL pending、POST/未知 GET/未准备计划/不安全 query/secret/不安全 service URL、state-machine 缺 models plan fail-closed。
- 新增 `docs/M5_21_FORTY_FIFTH_WAVE_NIM_READINESS_HTTP_ADAPTER_AUDIT_20260607.md`，并更新 M5.21 波次索引和项目记忆。

**安全**
- 本批不新增 Tool/Controller，不引入 `KubeManagerHttpClient`、`RestClient`、`java.net` 或任何真实 HTTP 客户端。
- 本批不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`，不轮询真实 NIM 服务，不发送、保存或展示真实 API Key。
- adapter 输出不能作为 `readinessExecutionReport` 或 `nim_create` 写入放行凭据；状态机仍要求 trusted policy、HITL、durable audit receipt、READY executor report、受控 body 重建和 release 开关。


## [M5.21-44] - 第四十四批 NIM readiness 执行报告门禁审计

**交付**: 加严 `NimCreateStateMachineSupport`，未来 `nim_create` 真实写入不再只要求 readiness plan，还必须要求受控 `NimCreateReadinessExecutorSupport` 返回 READY 执行报告；本批仍不开放真实创建。

**变更**
- `NimCreateStateMachineSupport.ReadinessRequest` 新增 `readinessExecutionReport`。
- 状态机输出新增 `readinessExecutionRequired=true`。
- 新增 `validateReadinessExecutionReport(...)`，要求 report 声明:
  - `readinessExecutor=NIM_CREATE_READINESS_EXECUTOR`
  - `sideEffect=NONE`
  - `readOnly=true`
  - `pollOnly=true`
  - `apiKeyHandling=NEVER_GENERATE_STORE_OR_DISPLAY`
  - `apiKeyPlaceholderOnly=true`
  - `forbiddenActionsEnforced=true`
- READY report 必须满足 `ready=true`、`state=READY`、`blockedBy=[]`、`deployment.matched=true`、`service.serviceUrlReady=true`、`health.live=true`、`nextPoll.prepared=false`。
- PENDING/BLOCKED/REJECTED/TIMEOUT report 或含 `blockedBy` 的 report 不能用于写入放行。
- 状态机现在拒绝 readiness execution report 中的真实 Bearer/API Key/secret-shaped 值，同时允许成熟前端占位 API Key 文本。
- ignored caller claims 扩展覆盖 `readinessExecutionReport/readinessExecutor/readinessReady/readinessState`。
- 更新 `NimCreateStateMachineSupportTest`、`NimCreateAuditReadinessSupportTest`、`NimCreateAuditWriterSupportTest`。
- 新增 `docs/M5_21_FORTY_FOURTH_WAVE_NIM_READINESS_REPORT_GATE_AUDIT_20260607.md`，并更新 M5.21 波次索引和项目记忆。

**安全**
- 本批不新增 Tool/Controller，不发真实 HTTP，不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- readiness plan 不是观测结果；未来放行必须看到受控 readiness executor READY report。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## [M5.21-43] - 第四十三批 NIM readiness 只读执行器契约审计

**交付**: 新增 `NimCreateReadinessExecutorSupport`，把 M5.21-40 的 readiness plan 转换为可离线评估的只读执行器契约；本批只消费 mock/离线响应快照，不注册 Tool，不发 HTTP，不访问真实 `8100` 或 NIM 服务。

**变更**
- 新增 `NimCreateReadinessExecutorSupport`。
- `evaluate(...)` 输出 `readinessExecutor=NIM_CREATE_READINESS_EXECUTOR`、`executionMode=OFFLINE_CONTRACT_EVALUATION`、`sideEffect=NONE`、`readOnly=true`、`pollOnly=true`、`pendingBy`、`blockedBy` 和 `nextPoll`。
- readiness plan 必须 prepared、pollOnly、API Key placeholder-only，并覆盖 `deployment/service/nim-health/nim-models`。
- readiness steps 只允许 `GET` 或 `EXTRACT_FROM_DEPLOYMENT_RESPONSE`；出现 POST chat/embedding 立即 `REJECTED`。
- Deployment 回查 0 个时返回 `PENDING` 并准备下一轮；多个命中时返回 `DEPLOYMENT_MATCH_AMBIGUOUS`。
- 服务入口只从 `entranceMap.http/http1` 派生，且必须是 http/https URL。
- health live 信号对齐 mature 前端: `message=Service is live.`、`live=true` 或 `status=live`。
- models 读取对齐 mature 前端: `data[0].id` 或 `available_models[0]`；读取失败返回 `fetch failed`，不阻断已 live 的服务 ready。
- 新增 `NimCreateReadinessExecutorSupportTest` 覆盖成功、等待、阻断、超时、POST 步骤与密钥泄漏。
- 新增 `docs/M5_21_FORTY_THIRD_WAVE_NIM_READINESS_EXECUTOR_AUDIT_20260607.md`，并更新 M5.21 波次索引和项目记忆。

**安全**
- 本批不新增 Tool/Controller，不发真实 HTTP，不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- plan/response 中出现 `Authorization`、`token`、`apiKey`、`secret`、`password` 或真实 Bearer 值时 fail-closed。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## [M5.21-42] - 第四十二批 NIM mock-first 审计写入 receipt 契约审计

**交付**: 新增 `NimCreateAuditWriterSupport`，把未来 `nim_create` 写入前的“审计上下文准备好”与“审计 writer 已接收并持久化”分离；本批只生成 mock receipt 契约，不连接真实持久化，不开放真实创建。

**变更**
- 新增 `NimCreateAuditWriterSupport`。
- `buildMockReceipt(...)` 输出 `storageMode=MOCK_CONTRACT_ONLY`、`durable=false`、`realStorageTouched=false`、`releaseEligible=false` 和 SHA-256 `eventDigest`。
- mock writer 对未准备好的 audit context 或含 token/API Key/secret 的 audit context 返回 `REJECTED`。
- `NimCreateStateMachineSupport.ReadinessRequest` 新增 `auditReceipt`。
- 状态机新增 `validateAuditReceipt(...)`:
  - 缺 receipt 返回 `AUDIT_RECEIPT_NOT_READY`。
  - mock receipt、非 durable receipt、身份字段不匹配或 digest 不合法返回 `AUDIT_RECEIPT_NOT_DURABLE`。
  - receipt 含 secret 返回 `AUDIT_RECEIPT_CONTAINS_FORBIDDEN_SECRET`。
- 状态机未来放行 fixture 必须使用 `receiptStatus=DURABLE_RECORDED`、`storageMode=DURABLE_AUDIT_LOG`、`durable=true`、`realStorageTouched=true`、`releaseEligible=true`。
- 新增 `NimCreateAuditWriterSupportTest`，更新 `NimCreateStateMachineSupportTest` 与 `NimCreateAuditReadinessSupportTest`。
- 新增 `docs/M5_21_FORTY_SECOND_WAVE_NIM_AUDIT_WRITER_RECEIPT_AUDIT_20260607.md`，并更新 M5.21 波次索引和项目记忆。

**安全**
- mock receipt 明确不是生产放行凭据；状态机仍拒绝 `MOCK_CONTRACT_ONLY`。
- 本批不新增 Tool/Controller，不写真实审计表，不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- `nim_create` 继续保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`。

## [M5.21-41] - 第四十一批 NIM 可信策略提供器契约审计

**交付**: 新增 `NimTrustedPolicyProviderSupport`，把未来后端可信链路读取到的 NVAIE license、当前用户角色和当前组织事实转换为 `NimTrustedPolicySnapshot`；本批仍不开放真实创建。

**变更**
- 新增 `NimTrustedPolicyProviderSupport` 与 `TrustedPolicyFacts`，只接收可信事实，不从 Tool 入参推断 license/RBAC/organization。
- 普通组织、非 `SYS_ADMIN`、有效 NVAIE license 可生成 `TRUSTED_PASSED` 策略快照。
- `organizationId=100001`、`SYS_ADMIN`、无效 license 生成 `TRUSTED_BLOCKED`。
- 缺少可信来源、userId、organizationId、roles、license verified 或 license/role/organization 证据时回落 `UNVERIFIED`。
- 新增 `buildProviderReport(...)`，输出 `sideEffect=NONE`、`protectedFromCallerParams=true`、`ignoredCallerClaims` 和策略快照。
- 加严 `NimCreationGateSupport` / `NimCreateStateMachineSupport` 对 `organizationId/orgId/roles/nvaieLicenseVerified/trustedPolicySource/authoritative` 等伪造字段的忽略声明。
- 新增 `NimTrustedPolicyProviderSupportTest`，扩展 creation gate 与 state machine 测试。
- 新增 `docs/M5_21_FORTY_FIRST_WAVE_NIM_TRUSTED_POLICY_PROVIDER_AUDIT_20260606.md`，并更新 M5.21 波次索引和项目记忆。

**安全**
- 本批不新增 HTTP 调用，不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- `TRUSTED_PASSED` 仍不是创建授权；`nim_create` 继续保持 HOLD，还需要服务端 HITL、审计、readiness、受控 body 重建和 release 开关。
- 明确区分 `SYS_ADMIN` 与 `ORG_ADMIN`；不能用宽泛 admin 判断放行或阻断 NIM 创建。
- Tool 入参中的 `licenseValid/role/roles/organizationId/trustedPolicySource/authoritative` 只进入 ignored caller claims。

## [M5.21-40] - 第四十批 NIM 审计上下文与 readiness 计划草案审计

**交付**: 新增 `NimCreateAuditReadinessSupport`，把未来 `nim_create` 写入前的审计上下文与创建后 readiness 轮询计划建模为纯数据结构，并强化 `NimCreateStateMachineSupport` 对审计和 readiness 的校验；本批仍不开放真实创建。

**变更**
- 新增 `NimCreateAuditReadinessSupport`，从 request/conversation/user/org、`creationGate`、`deploymentBodyPreview` 与服务端 `HitlConfirmation` 生成:
  - `auditContext`
  - `readinessPlan`
- `auditContext` 固定 `auditEventType=NIM_CREATE_REQUEST`、`targetTool=nim_create`、`writeBodyProvenance=SERVER_REBUILT_FROM_AUDITED_NIM_STATE`、`secretRedactionApplied=true` 和 API Key 安全策略。
- `readinessPlan` 对齐 mature 前端: 先按名称只读回查 Deployment，再从 `entranceMap.http/http1` 派生 NIM 服务入口，最后只读 GET `/v1/health/live` 与 `/v1/models`。
- `NimCreateStateMachineSupport` 加严:
  - 审计上下文必须包含目标 Tool、可信 body 来源、密钥脱敏和 API Key 策略。
  - readiness 必须覆盖 `deployment/service/nim-health`，并且步骤只能是 `GET` 或从 Deployment 响应派生。
- 新增 `NimCreateAuditReadinessSupportTest`，覆盖审计/readiness 可被状态机接受、调用方 token/API Key 不进入 audit、readiness 中出现 POST 或缺目标时被阻断。
- 新增 `docs/M5_21_FORTIETH_WAVE_NIM_AUDIT_READINESS_PLAN_AUDIT_20260606.md`，并更新 M5.21 波次索引和项目记忆。

**安全**
- 本批不新增 Tool，不新增 HTTP endpoint，不调用真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- readiness 计划只允许只读/派生步骤，明确禁止 `POST /v1/chat/completions`、`POST /v1/embeddings` 和发送真实 Authorization API Key。
- audit/readiness 结构不得携带 token、password、secret、真实 NGC/NIM API Key。
- `apiKeyPlaceholder` 只能是占位说明 `Bearer {input your NGC_API_KEY here}`，不能变成 Agent 生成的真实 key。

## [M5.21-39] - 第三十九批 NIM 创建状态机安全契约审计

**交付**: 新增 `NimCreateStateMachineSupport`，把未来 `nim_create` 真实写入前必须满足的可信策略、服务端 HITL、审计上下文、完整预览、只读 readiness 和禁止 fallback 写入条件固化为纯状态机契约；本批仍不开放真实创建。

**变更**
- 新增 `NimCreateStateMachineSupport`，输出 `NIM_CREATE_WRITE_GUARD`、`writePermitted`、`blockedBy`、`ignoredCallerClaims`、`requiredStages`、`directPreviewReuseAllowed=false`、`fallbackWriteAllowed=false` 和 API Key 安全策略。
- `NimCreateTool` 的 fail-closed 占位返回新增 `data.stateMachine`，让用户/LLM 能看到当前 HOLD 的结构化原因，而不是只得到一行失败文本。
- 新增 `NimCreateStateMachineSupportTest`，覆盖缺少创建门禁、可信策略未通过、HITL target 不匹配、审计/readiness 缺失、预览 body 直接复用、fallback 写入、敏感凭据泄漏和未来全部条件齐全的受控绿灯态。
- 扩展 `HighRiskMutationToolHttpContractTest`，锁定 `nim_create` 仍不调用 HTTP client，且失败结果携带状态机保护信息。
- 新增 `docs/M5_21_THIRTY_NINTH_WAVE_NIM_CREATE_STATE_MACHINE_AUDIT_20260606.md`，并更新 M5.21 波次索引和项目记忆。

**安全**
- 当前 `nim_create` 继续 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`，不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- 状态机要求 `creationGate.gateState=READY_FOR_SERVER_CONFIRMED_WRITE`、`allowedToCreateNow=true`，且 `trustedPolicySnapshot.snapshotState=TRUSTED_PASSED`、`authoritative=true`、`protectedFromCallerParams=true`。
- 只信任 target 精确匹配 `nim_create` 的服务端 `HitlConfirmation`；`confirmed/hitlConfirmed/approved` 等 Tool 入参只进入 `ignoredCallerClaims`。
- 禁止直接复用 `deploymentBodyPreview.bodyDraft` 作为 POST body；未来写入 body 必须由受控 NIM 状态机重新构建。
- 禁止从 NIM preflight 降级调用 `deploy_create_instance` 等 fallback 写 Tool。
- 审计上下文和 readiness 计划不得携带 token、password、secret 或真实 NGC/NIM API Key；创建后只允许只读轮询。

## [M5.21-38] - 第三十八批 NIM 可信策略快照审计

**交付**: 新增 `NimTrustedPolicySnapshot`，把 NIM 创建前必须由后端可信来源完成的 NVAIE license、SYS_ADMIN、system org 检查建模成结构化策略快照，并接入 `creationGate`；本批仍不开放 `nim_create`。

**变更**
- 新增 `NimTrustedPolicySnapshot`，输出 `snapshotState`、`authoritative`、`source`、`protectedFromCallerParams`、`nvaieLicense`、`callerOrgPolicy` 和 `evidence`。
- `NimCreationGateSupport` 新增带 policy snapshot 的重载；公开 preflight 默认使用 `UNVERIFIED` 快照。
- `creationGate` 新增 `trustedPolicySnapshot`，区分未校验、可信通过和可信失败。
- `NimCreationGateSupportTest` 新增策略快照场景，覆盖伪造调用方声明不能变成可信通过、可信策略通过仍保持 gate 关闭、可信策略失败产生明确 blocker。
- 新增 `docs/M5_21_THIRTY_EIGHTH_WAVE_NIM_TRUSTED_POLICY_SNAPSHOT_AUDIT_20260606.md`，并更新 M5.21 波次索引和项目记忆。

**安全**
- `trustedPolicySnapshot` 不是授权凭证；即使 `TRUSTED_PASSED`，`gateState` 仍为 `CLOSED` 且 `allowedToCreateNow=false`。
- 公开 `nim_deployment_preflight` 不从 Tool 入参构造可信通过态；`licenseValid/isSysOrg/sysAdmin/role` 等自报字段仍只进入 `ignoredCallerClaims`。
- 可信失败会返回 `NVAIE_LICENSE_TRUSTED_CHECK_FAILED` / `CALLER_ORG_POLICY_TRUSTED_CHECK_FAILED`，避免把“已检查且失败”和“尚未检查”混在一起。
- 本批不新增 HTTP endpoint，不访问真实 `8100`，不调用 `POST /api/{orgId}/deployment`。

## [M5.21-37] - 第三十七批 NIM 创建门禁与 HITL 卡片草案审计

**交付**: 新增 `NimCreationGateSupport`，在 `nim_deployment_preflight` 的离线预览结果中返回结构化 `creationGate`，明确说明 NIM 创建为什么仍被阻断、哪些调用方声明被忽略，以及未来 HITL 确认卡片需要展示哪些字段；本批仍不开放 `nim_create`。

**变更**
- 新增 `NimCreationGateSupport`，输出 `gateState=CLOSED`、`allowedToCreateNow=false`、`sideEffect=NONE`、`blockedBy`、`ignoredCallerClaims`、`requiredTrustedChecks`、`hitlCardDraft`、`futureWritePath` 和 `nextBestActions`。
- `NimDeploymentPreflightSupport` 将 `deploymentBodyPreview` 传入创建门禁，并在预检计划中新增 `creationGate`。
- 新增 `NimCreationGateSupportTest`，覆盖 CPU-only ready preview 仍保持关闭、伪造 approval/license/HITL/safeToPost 声明被忽略、缺少 `displayName` 时动态阻断。
- 扩展 `NimDeploymentPreflightToolHttpContractTest`，锁定预检 Tool 返回 `creationGate` 且仍只调用 repository、NIM tags、template 三段 GET。
- 新增 `docs/M5_21_THIRTY_SEVENTH_WAVE_NIM_CREATION_GATE_AUDIT_20260606.md`，并更新 M5.21 波次索引和项目记忆。

**安全**
- `creationGate` 只是解释型关闭门禁，不授权、不写审计、不调用 kube-manager、不生成 `HitlConfirmation`。
- 固定阻断项包括 `NIM_CREATE_TOOL_HOLD`、NVAIE license 未可信校验、SYS_ADMIN/system org 策略未可信校验、HITL marker 未签发、审计与 readiness 闭环未完成。
- 动态阻断项覆盖 preview 不完整、缺少 `displayName`、GPU map 未解析、`safeToPost` 标志异常。
- `approved`、`confirmed`、`hitlConfirmed`、`safeToPost`、`licenseValid`、`sysAdmin`、`role` 等调用方入参只会进入 `ignoredCallerClaims`，不能作为创建授权依据。
- `futureWritePath.directUseOfPreviewAllowed=false` 且 `fallbackAllowedFromPreflight=false`，防止把预检草案直接透传给写 Tool 或 fallback Tool。

## [M5.21-36] - 第三十六批 NIM 模板合并与 DeploymentDTO 离线预览审计

**交付**: 新增 `NimTemplateMergeSupport`，把 mature `vue-kube-manager` NIM 一键部署中的 `mergeTemplate + formatApplication` 确定性换算沉淀为 `safeToPost=false` 的离线 DeploymentDTO 草案预览，辅助后续 HITL 设计，但不开放 `nim_create`。

**变更**
- 新增 `NimTemplateMergeSupport`，生成 `deploymentBodyPreview`，包含 `bodyDraft`、`uiMergedDraft`、`gpuResolution`、`requiredBeforeCreate` 和 `protectedFields`。
- `NimDeploymentPreflightSupport` 在预检计划中返回 `deploymentBodyPreview`，让 Agent 能解释模板合并后的请求体草案。
- 新增 `NimTemplateMergeSupportTest`，覆盖模板覆盖保护、单位换算、GPU map 缺失/解析、CPU-only 自动伸缩和缺少 `displayName` 的完整性判断。
- 扩展 `NimDeploymentPreflightToolHttpContractTest`，锁定预检 Tool 仍只读且 preview `safeToPost=false`。
- 新增 `docs/M5_21_THIRTY_SIXTH_WAVE_NIM_TEMPLATE_MERGE_PREVIEW_AUDIT_20260606.md`，并更新 M5.21 波次索引。

**安全**
- 本批不新增 HTTP endpoint，不调用真实 `8100`，不调用 `POST /api/{orgId}/deployment`。
- `deploymentBodyPreview.safeToPost=false` 被测试锁定，缺少 GPU map 或未确认 `displayName` 时 `bodyComplete=false`。
- Agent 侧相对 mature 前端额外保护 `name/displayName/image`，避免模板覆盖用户确认的服务名或已选镜像。
- 公开预检不消费用户/LLM 传入的 `gpuMap`，避免伪造 GPU 解析；未来只能由受控编排传入已审计 GPU map。
- `nim_create` 继续 HOLD，等待 license、系统组织/SYS_ADMIN 禁止策略、HITL 卡片、审计日志、费用/配额、状态轮询全部完成。

## [M5.21-35] - 第三十五批 NIM 部署只读预检编排审计

**交付**: 新增 `nim_deployment_preflight`，按 mature `vue-kube-manager` NIM 一键部署流程读取一键部署目录、NIM tag 与 `templateType=NIM` 模板，形成可审计部署规划草案，但不创建 Deployment。

**变更**
- 新增 `NimDeploymentPreflightTool`，连续调用 `GET /api/{orgId}/repository`、`GET /api/{orgId}/repository/nim/tags`、`GET /api/{orgId}/template`。
- 新增 `NimDeploymentPreflightSupport`，集中处理 repository/tag/image/template 查询参数、候选选择和只读规划结果。
- 新增 `NimDeploymentPreflightToolHttpContractTest`，覆盖三段 GET、可信 orgId、显式 repository/tag、非法 repository fail-closed、无模板 fail-closed 与风险元数据。
- `M511AtlasToolHttpContractTest` 支持一个只读编排 Tool 声明多个 endpoint，并将 `nim_deployment_preflight` 加入白名单。
- 更新 `intents.yml`，新增 `nim_deployment_preflight`，并明确 `nim_create` 当前仍是安全占位。
- 新增 `docs/M5_21_THIRTY_FIFTH_WAVE_NIM_DEPLOYMENT_PREFLIGHT_AUDIT_20260606.md`，并更新 M5.21 波次索引。

**安全**
- `nim_deployment_preflight` 为 `AUTHENTICATED + SENSITIVE_READ + requiresConfirmation=true`，不会导出到 MCP safe manifest。
- 本批不调用真实 `8100`，不调用 `POST /api/{orgId}/deployment`，不创建 NIM 服务，不轮询服务状态，不读取或保存 API Key。
- `nim_create` 继续 HOLD，等待 license、系统组织限制、模板合并、GPU/defaults、HITL 卡片和状态轮询全部完成审计。

## [M5.21-34] - 第三十四批 产品/应用镜像目录敏感只读审计

**交付**: 新增 organization-scoped repository catalog 只读能力，对齐 mature kube-manager 的 `GET /api/{orgId}/repository`、`/category`、`/tags`、`/nim/tags`，明确它们服务 NGC/NV AIE/NIM 产品/应用镜像目录，而不是站点级 registry 配置或普通组织镜像仓库列表。

**变更**
- 新增 `RepositoryCatalogQuerySupport`，集中处理目录列表 query 与 tag 查询的 `repository` 必填校验。
- 新增 `RepositoryCatalogListTool`，对齐 `GET /api/{orgId}/repository`。
- 新增 `RepositoryCatalogCategoryListTool`，对齐 `GET /api/{orgId}/repository/category`。
- 新增 `RepositoryCatalogTagListTool`，对齐 `GET /api/{orgId}/repository/tags?repository=...`。
- 新增 `RepositoryCatalogNimTagListTool`，对齐 `GET /api/{orgId}/repository/nim/tags?repository=...`。
- 新增 `RepositoryCatalogToolHttpContractTest`，覆盖成熟路径、可信 orgId、query 透传、tag 必填、非法 repository fail-closed 和风险元数据。
- 更新 `M511AtlasToolHttpContractTest` 与 `intents.yml`。
- 新增 `docs/M5_21_THIRTY_FOURTH_WAVE_REPOSITORY_CATALOG_READ_AUDIT_20260606.md`，并更新 M5.21 波次索引。

**安全**
- 四个 Tool 均为 `AUTHENTICATED + SENSITIVE_READ + requiresConfirmation=true`，不会导出到 MCP safe manifest。
- 本批没有调用真实 `8100`，没有接入镜像拉取、重试、删除、推送或 NIM 部署创建。
- `/api/registry/repo-tag` 继续 HOLD，因为它可能触发站点级 registry 外部枚举，语义不同于本批本地目录 tag 状态。

## [M5.21-33] - 第三十三批 镜像注册处站点级敏感只读对齐审计

**交付**: 将 `RegistryListTool` 从不存在的组织路径 `/api/{orgId}/registry` 对齐为成熟 kube-manager 的站点级 `GET /api/registry`，并明确它查询的是镜像注册处配置，不是组织内产品 repository 目录。

**变更**
- `RegistryListTool` 路径从 `/api/{orgId}/registry` 改为 `/api/registry`。
- `RegistryListTool` 移除 `page/limit/keyword` 标准列表契约，只暴露成熟后端可选 `keyWord`。
- `RegistryListTool` 元数据标记为 `SENSITIVE_READ + requiresConfirmation=true`。
- 新增 `RegistrySiteToolHttpContractTest`，覆盖站点路径、`keyWord` 透传、`keyword` alias 兼容、无分页 schema 和风险元数据。
- 更新 `M511AtlasToolHttpContractTest`、`ListToolParameterPassThroughContractTest`、`ListToolParameterSpecContractTest` 与 `intents.yml`。
- 新增 `docs/M5_21_THIRTY_THIRD_WAVE_REGISTRY_SITE_READ_AUDIT_20260606.md`，并更新 M5.21 波次索引。

**安全**
- 本批没有调用真实 `8100`，没有接入 registry 新增、更新、删除或 repo-tag 外部枚举。
- `RegistrySiteDTO` 返回注册处 URL 与 username，按站点级敏感读取走人工确认。

## [M5.21-32] - 第三十二批 下载任务进度按任务 ID 敏感只读审计

**交付**: 新增 `DownloadTaskProgressTool`，对齐成熟 kube-manager 的 `GET /api/{orgId}/download/progress/{id}`，让 Agent 可以在找到下载任务后单独读取实时进度。

**变更**
- 新增 `DownloadTaskProgressTool`，只暴露必填下载任务 `id`，不接受 `page/limit/keyword`。
- 新增 `DownloadTaskQuerySupport`，统一下载任务状态/进度 Tool 的 ID schema 与 URL path 正整数校验。
- `UploadStatusListTool` 改为复用 `DownloadTaskQuerySupport`，保持状态与进度的路径注入防护一致。
- 新增 `DownloadTaskProgressToolHttpContractTest`，覆盖成熟路径、空 query、非法 `id` 短路、参数 schema 和风险元数据。
- 更新 `M511AtlasToolHttpContractTest` 与 `intents.yml`，新增 `download_task_progress`。
- 新增 `docs/M5_21_THIRTY_SECOND_WAVE_DOWNLOAD_PROGRESS_READ_AUDIT_20260606.md`，并更新 M5.21 波次索引。

**安全**
- 本批没有调用真实 `8100`，没有接入下载任务开始、暂停、恢复或删除。
- 任务进度可能包含任务状态、已下载大小和总大小，仍按 `SENSITIVE_READ + requiresConfirmation=true` 处理。

## [M5.21-31] - 第三十一批 下载任务状态按任务 ID 敏感只读对齐审计

**交付**: 将 `UploadStatusListTool` 从不存在的伪分页状态列表对齐为成熟 kube-manager 的 `GET /api/{orgId}/download/status/{id}`，保留旧 intentId 兼容，但 Tool Schema 明确要求下载任务 `id`。

**变更**
- `UploadStatusListTool` 路径从 `/api/{orgId}/download/status` 改为 `/api/{orgId}/download/status/{id}`。
- `UploadStatusListTool` 移除 `page/limit/keyword` 标准列表契约，只暴露必填 `id`。
- `UploadStatusListTool` 元数据标记为 `SENSITIVE_READ + requiresConfirmation=true`。
- 新增 `DownloadTaskStatusToolHttpContractTest`，覆盖成熟路径、空 query、非法 `id` 短路、参数 schema 和风险元数据。
- 更新 `M511AtlasToolHttpContractTest`、`ListToolParameterPassThroughContractTest`、`ListToolParameterSpecContractTest` 与 `intents.yml`。
- 新增 `docs/M5_21_THIRTY_FIRST_WAVE_DOWNLOAD_STATUS_READ_AUDIT_20260606.md`，并更新 M5.21 波次索引。

**安全**
- 本批没有调用真实 `8100`，没有接入下载任务开始、暂停、恢复、进度读取或删除。
- 任务状态可能包含文件路径、任务归属、大小和状态元数据，因此按敏感读取走人工确认。
- 后续 M5.21-32 已单独接入 `GET /api/{orgId}/download/progress/{id}` 进度读取；下载任务开始、暂停、恢复和删除仍 HOLD。

## [M5.21-30] - 第三十批 MIG 配置按 GPU ID 只读对齐审计

**交付**: 将 `MigConfigListTool` 从历史伪分页列表对齐为成熟 kube-manager 的 `GET /api/mig/{gpuId}`，保留旧 intentId 兼容，但 Tool Schema 明确要求 `gpuId`。

**变更**
- `MigConfigListTool` 路径从 `/api/{orgId}/migConfig` 改为 `/api/mig/{gpuId}`。
- `MigConfigListTool` 移除 `page/limit/keyword` 标准列表契约，只暴露必填 `gpuId`。
- `MigConfigListTool` 权限从 `PUBLIC` 收敛为 `AUTHENTICATED`，风险元数据标记为 `READ + requiresConfirmation=false`。
- 新增 `MigConfigReadToolHttpContractTest`，覆盖成熟路径、空 query、非法 `gpuId` 短路、参数 schema 和权限/风险元数据。
- `McpToolManifestService` 导出规则收紧为 `PUBLIC + READ + requiresConfirmation=false`，防止登录态只读 Tool 出现在外部 MCP 安全清单。
- 更新 `M511AtlasToolHttpContractTest`、`ListToolParameterPassThroughContractTest`、`ListToolParameterSpecContractTest` 与 `intents.yml`。
- 新增 `docs/M5_21_THIRTIETH_WAVE_MIG_CONFIG_READ_AUDIT_20260606.md`，并更新 M5.21 波次索引。

**安全**
- 本批没有调用真实 `8100`，没有接入 `POST/PUT/DELETE /api/mig`。
- MIG 增删改仍按 `SYS_ADMIN_ONLY` 高风险管理操作 HOLD，后续需要单独 HITL、权限和审计设计。
- `mig_config_list` 虽是普通 READ，但因权限为 `AUTHENTICATED`，不会导出到 MCP manifest。

## [M5.21-29] - 第二十九批 Legacy GET Tool HTTP 元数据与路径对齐审计

**交付**: 补齐 7 个历史 GET Tool 的 `@AtlasToolMapping` HTTP/risk 元数据，并修正 `file_material_list`、`inbox_message_list` 与成熟 kube-manager 后端不一致的路径。
**变更**
- `DataSetListTool`、`FileListTool`、`DownloadTaskListTool`、`FileMaterialListTool`、`InboxMessageListTool` 标记为 `SENSITIVE_READ + requiresConfirmation=true`。
- `ImageQueryTool`、`PytorchJobListTool` 标记为普通 `READ`。
- `FileMaterialListTool` 路径从 `/api/{orgId}/file-material` 对齐为 `/api/{orgId}/material/folders`。
- `InboxMessageListTool` 路径从 `/api/{orgId}/message` 对齐为 `/api/{orgId}/inbox-message`。
- `M511AtlasToolHttpContractTest` 新增 legacy GET endpoint 精确白名单；`ListToolParameterPassThroughContractTest` 更新路径期望。
- 新增 `docs/M5_21_TWENTY_NINTH_WAVE_LEGACY_GET_METADATA_AUDIT_20260606.md`，并更新 M5.21 波次索引。
**安全**
- 本批没有调用真实 `8100`，没有新增写入、删除、状态变更或文件内容读取能力。
- `RegistryListTool`、`MigConfigListTool`、`UploadStatusListTool`、Experiment 列表和 RBAC/组织/审批敏感管理面列表继续 HOLD，等待单独证据与权限边界审计。

## [M5.21-28] - 第二十八批 文件/存储准备上下文敏感只读 Tool 审计

**交付**: 补齐部署、训练任务和课程环境创建前的文件/存储准备上下文能力，迁移既有挂载路径、存储选项、存储详情 Tool 为敏感只读，并新增用户挂载路径、用户额外挂载路径、PVC 挂载选项和训练存储上下文只读 Tool；所有文件内容读取和文件/存储变更操作继续 HOLD。

**变更**
- `FileVolumePathTool`、`FileStorageOptionTool`、`FileSelectStorageTool` 迁移为 `AUTHENTICATED + SENSITIVE_READ + requiresConfirmation=true`，并移除普通只读 page/limit 预期。
- 新增 `FileUserVolumePathTool`、`FileUserExtraVolumePathTool`、`FileClaimedVolumeOptionListTool`、`FileTrainStorageTool`。
- 新增 `FileStorageQuerySupport`，集中处理文件/存储只读 Tool 的最小参数白名单。
- 新增 `FileStorageReadToolHttpContractTest` 与 `docs/M5_21_TWENTY_EIGHTH_WAVE_FILE_STORAGE_READ_AUDIT_20260606.md`。
- `M511AtlasToolHttpContractTest` 新增 file/storage `SENSITIVE_READ` endpoint 精确白名单。
- `intents.yml` 新增 `file_user_volume_path`、`file_user_extra_volume_path`、`file_claimed_volume_option_list`、`file_train_storage` 意图；`file_volume_path`、`file_storage_option`、`file_select_storage` 复用原入口。

**安全**
- 本批没有调用真实 8100，也没有调用文件预览、下载、上传、编辑、复制移动、压缩解压、删除、存储申请、扩容或删除接口。
- 新增 Tool 均标记为 `AUTHENTICATED + SENSITIVE_READ + requiresConfirmation=true`；无参接口固定空 query，`selectStorage` 仅透传 `name`。

## [M5.21-27] - 第二十七批 BCM 用户与节点分配敏感只读 Tool 审计

**交付**: 补齐 Slurm/BareMetal 创建前的组织内用户与节点分配盘点能力，新增 BCM 用户、Slurm 已分配节点、BareMetal 已分配节点三个敏感只读 Tool。

**变更**
- 新增 `BcmUserListTool`、`BcmSlurmNodeAllocationListTool`、`BcmBareMetalNodeAllocationListTool`。
- 新增 `BcmAllocationReadToolHttpContractTest` 与 `docs/M5_21_TWENTY_SEVENTH_WAVE_BCM_ALLOCATION_READ_AUDIT_20260606.md`。
- `M511AtlasToolHttpContractTest` 新增 BCM allocation `SENSITIVE_READ` endpoint 精确白名单。
- `intents.yml` 新增 `bcm_user_list`、`bcm_slurm_node_allocation_list`、`bcm_bare_metal_node_allocation_list` 意图。

**安全**
- 本批没有调用真实 8100，也没有创建 Slurm/BareMetal、切换 SSH/Sudo 或访问站点管理员跨组织接口。
- 三个新增 Tool 均标记为 `AUTHENTICATED + SENSITIVE_READ + requiresConfirmation=true`，且不透传用户给出的组织、搜索或写操作字段。

## [M5.21-26] - 第二十六批 HPC 环境与 Lmod module 敏感只读 Tool 审计

**交付**: 补齐 HPC 作业提交前的软件环境分析能力，新增 Miniconda 环境列表与 Lmod module 列表读取 Tool；所有会改变 HPC 软件栈的操作继续 HOLD。

**变更**
- 新增 `HpcEnvironmentListTool`，接入 `GET /api/{orgId}/hpc-env/environments/{clusterId}`。
- 新增 `HpcModuleListTool`，接入 `GET /api/{orgId}/hpc-env/modules?clusterId=...`。
- 新增 `HpcEnvironmentModuleToolHttpContractTest` 与审计文档。
- 新增 `docs/M5_21_WAVE_INDEX_20260606.md`，汇总 M5.21 mature kube-manager 对齐批次。
- `M511AtlasToolHttpContractTest` 新增 HPC environment/module `SENSITIVE_READ` endpoint 精确白名单。
- `intents.yml` 新增 `hpc_environment_list` 与 `hpc_module_list` 意图。

**安全**
- 本批没有调用真实 8100，也没有创建、删除环境、安装 package、安装或删除 module。
- 两个新增 Tool 均标记为 `AUTHENTICATED + SENSITIVE_READ + requiresConfirmation=true`。

## [M4-PX.3] — SafeToolExecutor 与 execute_node fail-closed 最小安全闭环

**周期**: 2026-05-25
**交付**: 在 M4-PX.2 只规划 POC 基础上，先抽取统一安全工具执行层 `SafeToolExecutor`，让既有 Graph `tool_call` 复用且行为兼容；同时新增 `execute_node` 并将 PLAN 路径升级为 `PLAN -> plan_node -> execute_node -> END`。本阶段 `execute_node` 默认 fail-closed，不自动调用 Tool、不写 `tool_result`、不创建 `hitl_confirmation`。

### Added

- 新增 `com.atlas.tool.execution` 包：
  - `SafeToolExecutor`：统一执行边界，集中处理 Tool 查找、权限校验、受保护参数过滤、HITL 校验、ThreadLocal 绑定/恢复和结果归一化。
  - `SafeToolExecutionRequest`：承载服务端可信上下文、业务参数、服务端确认 marker 与执行来源。
  - `SafeToolExecutionResult`：保持与既有 Graph `tool_call` 返回结构兼容。
  - `SafeToolExecutionSource`：记录 Graph tool_call、后续 execute_node、ReAct、ToolCallback 等执行来源。
- `AtlasGraphConfig` 新增 `execute_node`、`execute_node_result`、`execute_result`、`execute_steps` State key。
- PLAN 路由从 `PLAN -> plan_node -> END` 升级为 `PLAN -> plan_node -> execute_node -> END`。
- 新增 `SafeToolExecutorTest`，覆盖普通 READ、伪造上下文字段过滤、高危无确认拦截、ThreadLocal 快照恢复。

### Changed

- Graph `tool_call` 不再内联 Tool 执行链，改为委托 `SafeToolExecutor.executeIntent(...)`。
- M5.13 HITL 源码契约更新为：Graph 负责读取服务端确认 marker，真实 `tool.execute(...)` 前的守卫由 `SafeToolExecutor` 统一完成。
- M4-PX 契约测试更新为锁定 `execute_node` fail-closed：即使 `PlanResult.executable=true`，本阶段也仍返回 `EXECUTE_GATE_NOT_OPEN`，等待后续 READ-only 单步门控。

### Safety

- `SafeToolExecutor` 对 `token/orgId/organizationId/userId/conversationId` 等受保护字段执行过滤，系统上下文字段最后写入，防止 LLM/Plan 参数伪造租户或用户边界。
- 缺失 orgId、未注册 Tool、权限不足、HITL 未确认、Tool 异常均返回未执行结果，保持 fail-closed。
- ThreadLocal token/orgId 在执行前保存快照，执行后 `finally` 恢复，避免线程池污染或嵌套执行破坏外层上下文。
- `execute_node` 当前只读取 `plan_result/plan_steps` 并返回停止原因，不调用 kube-manager、不调用 Tool、不写 `tool_result`、不生成人工确认 marker。

### Verified

- 定向测试：`mvn -q -Dtest=SafeToolExecutorTest,M42PlanExecuteSafetyContractTest,M513HitlFailClosedContractTest,ActionTypeTest,AtlasBrainMockTest,SupervisorGraphReactRoutingTest test` → ✅ 通过。
- 单测隔离修复后：`mvn -q -Dtest=SafeToolExecutorTest test` → ✅ 通过。
- 全量测试：`mvn -q test` → ✅ 通过（228 tests）。
- 空白检查：`git diff --check` → ✅ 通过。
- 新增行敏感扫描：`ADDED_LINE_SECRET_SUSPECTS 0` → ✅ 通过。
- 三路 Review：安全架构、测试契约、工程落地均 PASS。

### Risk / Deferred

- `execute_node` 目前是安全占位，不执行真实计划步骤；后续若开放执行，必须先补 READ-only 单步白名单、参数 schema 校验、预算控制、审计日志和 HITL resume 语义。
- 当前参数过滤是执行参数层面的受保护字段过滤，后续可补嵌套对象/数组递归脱敏与日志/HITL 展示侧脱敏一致性契约。
- 后续建议新增全局源码契约：扫描所有 `tool.execute(...)` 调用点，逐步强制 Graph/ReAct/ToolCallback 等入口统一收口到 `SafeToolExecutor`。
- 后续建议补“高危 + 服务端可信确认 marker 成功执行”和“Tool 异常后 ThreadLocal 恢复”的细化单测。

---

## [M4-PX.2] — Plan-and-Execute + Reflection 最小 POC 闭环

**周期**: 2026-05-24
**交付**: 在 M5 安全底座完成后，回到 M4 Plan-and-Execute 专项，按专家会诊结论先落地最小安全 POC：新增 PLAN 决策类型、Graph `plan_node`、`PlanEngine` 结构化计划与单次 Reflection 自检，并通过契约测试锁定“只规划、不执行、不绕过 HITL”的安全边界。

### Added

- `BrainDecision.ActionType` 新增 `PLAN`，支持 AtlasBrain 输出显式规划决策。
- `AtlasBrain` 新增 PLAN 触发规则与确定性守卫：显式 `/plan`、`/px`、先规划、只出方案、不要执行等请求进入 PLAN；守卫优先级固定为 `HITL_CONFIRM > PLAN > DELEGATE_REACT > CALL_TOOL`。
- `AtlasGraphConfig` 新增 `plan_node`、`plan_node_result`、`plan_result`、`plan_steps` State key，并增加 `PLAN -> plan_node -> END` 路由。
- 新增 `com.atlas.plan` 包：`PlanEngine`、`PlanResult`、`PlanStep`、`PlanStepStatus`、`ReflectionResult`。
- 新增/扩展测试：`M42PlanExecuteSafetyContractTest`、`SupervisorGraphReactRoutingTest`、`ActionTypeTest`、`AtlasBrainMockTest`。

### Safety

- `plan_node` 只调用 `PlanEngine.plan(...)` 生成结构化计划和最终展示文本，不调用 Tool、不访问 kube-manager、不写入 `tool_result`、不创建/写入 `hitl_confirmation`。
- 高危关键词命中时，即使 LLM 已返回 `PLAN` 或用户使用 `/plan` 前缀，也会被 SafetyGuard 强制提升为 `HITL_CONFIRM`。
- Reflection 当前只做单次结构自检，不自动重试、不自动 replan、不自动执行，避免绕过 M5 HITL fail-closed 安全边界。

### Verified

- 定向测试：`mvn -q -Dtest=ActionTypeTest,AtlasBrainMockTest,SupervisorGraphReactRoutingTest,M42PlanExecuteSafetyContractTest,M513HitlFailClosedContractTest,ToolRegistryPromptContractTest,ReActEventRiskMetadataTest test` → ✅ 通过。
- 编译检查：`mvn -q -DskipTests compile` → ✅ 通过。
- 空白检查：`git diff --check` → ✅ 通过。
- 全量测试：`mvn -q test` → ✅ 通过。

### Risk / Deferred

- 本阶段是 Plan-and-Execute 最小 POC，只生成计划，不包含 execute_node / reflection_node 的完整多轮循环。
- `PlanEngine` 当前为规则化计划生成，后续可接入 LLM Planner、ToolRegistry 风险元数据、预算控制和 SSE timeline 事件。
- 前端计划展示目前复用普通 answer 文本；后续可读取 `plan_steps` 做专用 Timeline/确认卡片。

---
## [M5.20] — MCP / Memory / Observability 最小安全闭环

**周期**: 2026-05-24
**交付**: 在 M5.18/M5.19 完成 Tool 风险元数据治理后，补齐 M5 最小可验收闭环：MCP 安全 Manifest、最近 10 次对话摘要 Memory、Micrometer Agent 指标与 Actuator 暴露配置。MCP 本阶段不直接开放完整可写调用能力，而是采用安全导出门，避免外部 Agent 绕过 HITL。

### Added

- 新增 `com.atlas.mcp.McpToolManifestService`：生成安全 MCP Manifest，只导出已声明 endpoint、`operationType=READ`、`requiresConfirmation=false` 的普通只读 Tool；`SENSITIVE_READ/CREATE/UPDATE/DELETE/ACTION/UNKNOWN` 默认 fail-closed。
- 新增 `com.atlas.mcp.McpManifestController`：提供 `/api/agent/mcp/manifest` 只读清单接口，返回策略、导出 Tool 数和脱敏后的 Tool 元数据，不暴露真实后端 endpoint。
- 新增 `com.atlas.memory.ConversationSummaryMemoryStore` 与 `MemoryController`：支持按已认证会话保存/查询最近 10 次摘要；缺失 X-Session-Id 时 fail-closed，写入时自动脱敏 token/password/apiKey/secret 等敏感字段。
- 新增 `com.atlas.observability.AgentMetricsService` 与 `AgentMetricsController`：记录 ReAct run、Tool call、HITL block 三类核心指标，并通过 `/api/agent/metrics/snapshot` 提供轻量快照。
- `ReActEngine` 接入指标记录，推理结束、Tool 执行完成、HITL 阻断均进入 Micrometer 计数/计时。
- `application.yml` 增加 Actuator 暴露：`health/info/metrics/prometheus`。
- 新增 M5.20 契约测试：
  - `M520McpManifestSafetyContractTest`
  - `ConversationSummaryMemoryStoreTest`
  - `AgentMetricsServiceTest`

### Changed

- M5 MCP 方向从“立即对外暴露全部 Tool”调整为“安全 Manifest 先行”：先让外部 Agent 可发现安全只读能力，写/删/动作和敏感读能力必须等待 MCP 调用层接入 HITL/审计后再开放。
- `ToolRegistry` 增加系统级 metadata 列表，供 MCP Manifest 与审计场景使用；不改变用户可见 Tool 的权限判断。

### Verified

- 定向测试：`mvn -q -Dtest=M520McpManifestSafetyContractTest,ConversationSummaryMemoryStoreTest,AgentMetricsServiceTest,M511AtlasToolHttpContractTest,M513HitlFailClosedContractTest test` → ✅ 通过。
- 全量测试：`mvn -q test` → ✅ 通过。
- 静态覆盖统计：Tool total=110，declared=81，READ no-confirm=53，SENSITIVE_READ confirmed=7，mutating/action confirmed=20，actual mutating undeclared=0，risky_without_confirmation=0。
- 安全边界：MCP Manifest 不导出 endpoint、不导出高风险 mutation、不导出敏感 GET、不导出 UNKNOWN。

### Risk / Deferred

- 本阶段的 Memory 是内存型最近摘要存储，不是 Redis/Chroma 长期向量记忆；重启后会丢失，后续再接 Redis/向量检索与 System Prompt 注入。
- MCP 本阶段只提供安全 Manifest，不提供 stdio/sse 可执行 Server；完整 MCP Tool 调用必须在 HITL、审计、权限上下文、限流和观测全部接线后再开放。
- Observability 已有 Micrometer 计数/计时与 Actuator 暴露，但 LLM token 成本、TraceId 全链路、SSE 连接数仍是后续增强项。

---

## [M5.19] — 写/删/ACTION 高风险 Tool 元数据治理

**周期**: 2026-05-24
**交付**: 对真实 POST/DELETE/PUT/PATCH 或动作型 Tool 补齐 `operationType` 与 `requiresConfirmation=true`，并新增高风险 endpoint 精确白名单契约，确保 HITL fail-closed 不被绕过。

### Added

- 18 个真实高风险 mutation/action Tool 补充风险元数据，覆盖创建、删除、重启、停止、提交、拉取、卸载、仓库新增等操作。
- `M511AtlasToolHttpContractTest` 增加 M5.19 高风险 endpoint 白名单断言，锁定 file/tool/endpoint/operationType/requiresConfirmation。

### Verified

- 定向测试：`M511AtlasToolHttpContractTest`、`M513HitlFailClosedContractTest`、`ToolRegistryPromptContractTest`、`ReActEventRiskMetadataTest` → ✅ 通过。
- 全量测试：`mvn -q test` → ✅ 通过。
- 静态扫描：真实 POST/DELETE/PUT/PATCH 未声明数量 = 0，高风险未确认数量 = 0。

---

## [M5.18] — 敏感 GET / SENSITIVE_READ 风险语义治理

**周期**: 2026-05-24
**交付**: 新增并落地 `SENSITIVE_READ` 风险语义，将日志、用户、权限、LDAP、角色、订单、配额等敏感 GET 从普通 READ 免确认面中剥离出来。

### Added

- `AtlasToolMapping.OperationType` 增加 `SENSITIVE_READ`，明确 HTTP GET 与业务风险语义分离。
- `HitlGuard` fail-closed 策略覆盖 `SENSITIVE_READ`：除普通 READ 外均需要确认。
- 多个敏感 GET Tool 迁移为 `SENSITIVE_READ` + `requiresConfirmation=true`。
- `ToolRegistryPromptContractTest`、`ReActEventRiskMetadataTest` 扩展风险元数据断言。

### Verified

- 定向测试：`mvn -q -Dtest=M511AtlasToolHttpContractTest,M513HitlFailClosedContractTest,ToolRegistryPromptContractTest,ReActEventRiskMetadataTest test` → ✅ 通过。
- 结论：GET 不再等同于免确认 READ，MCP/外部 Agent 不得默认开放敏感读能力。

## [M5.17] — Tool HTTP/风险元数据第四批基础设施 GET/READ 扩面

**周期**: 2026-05-24
**交付**: 在 M5.16 第三批 READ 扩面基础上，继续按“专家会诊前置 + 先实验再铺开”原则，为第四批 15 个基础设施/运行态查询 Tool 补充 `httpMethod/apiEndpoints/operationType=READ`，并新增 M5.17 endpoint 精确白名单契约测试，防止动态路径和 dashboard 近似路径被写错。

### Added

- 第四批 15 个低风险 GET/READ Tool 补充 HTTP/风险元数据：
  - query 查询类：`cluster_query`、`node_query`、`node_metrics`、`gpu_query`、`gpu_metrics`、`pod_status`、`daemonset_status`、`deployment_status`、`service_status`、`resource_monitor`、`resource_preset_list`；
  - network 查询类：`network_query`、`ingress_query`；
  - deploy 查询类：`slurm_cluster_list`、`slurm_node_list`。
- `M511AtlasToolHttpContractTest` 新增 `m517InfrastructureReadEndpoints_shouldMatchReviewedWhitelist`，对白名单 15 个 Tool 做精确 endpoint 断言。
- 新增阶段记录：`docs/m5/M5.17_tool_metadata_read_expansion_notes_20260524.md`。

### Verified

- 专家会诊：✅ 安全、源码契约/测试、架构推进三方确认本批只覆盖基础设施纯查询能力，排除 RBAC/LDAP/用户/组织/配额/文件/上传下载/审批/写删动作。
- M511 HTTP 元数据契约：`mvn -q -Dtest=M511AtlasToolHttpContractTest test` → ✅ 通过。
- HITL/风险定向回归：`mvn -q -Dtest=M513HitlFailClosedContractTest,ToolRegistryPromptContractTest,ReActEventRiskMetadataTest test` → ✅ 通过。
- 后端编译：`mvn -q -DskipTests compile` → ✅ 通过。
- 全量测试：`mvn -q test` → ✅ 通过。
- 空白检查：`git diff --check` → ✅ 通过。
- 敏感信息扫描：`secret_suspects=0` → ✅ 通过。

### Coverage

- Tool 总数：110。
- 已声明 HTTP 元数据：58（由 M5.16 的 43 扩展到 58）。
- READ 白名单：55（由 M5.16 的 40 扩展到 55）。
- 未迁移 Tool 继续保持 UNKNOWN / fail-closed，仍不因覆盖率目标放松安全边界。

### Risk / Deferred

- 本批包含节点、Pod、Deployment、GPU、Slurm 等基础设施运行态信息；虽然不改变后端/K8s 状态，但会暴露拓扑与资源状态，必须继续依赖后端 org 隔离、权限校验和审计。
- `resource_preset_list` 目前按资源规格只读查询处理；若后端未来返回价格策略、账户策略或其他敏感管理信息，应升级为敏感 READ。
- RBAC、LDAP、用户、组织、配额、文件上传/下载、审批、创建/删除/停止/扩缩容仍不纳入免确认 READ。

---

## [M5.16] — Tool HTTP/风险元数据第三批 GET/READ 扩面与 endpoint 精确契约

**周期**: 2026-05-24
**交付**: 在 M5.13 fail-closed 与 M5.14/M5.15 分批扩面基础上，继续经专家会诊确认后，为第三批 15 个低风险 GET/READ Tool 补充 `httpMethod/apiEndpoints/operationType=READ`，并增强 `M511AtlasToolHttpContractTest` 的 endpoint 精确白名单校验，防止动态尾段或非 org-scoped 路径被写错。

### Added

- 第三批 15 个低风险 GET/READ Tool 补充 HTTP/风险元数据：
  - deploy 查询类：`bare_metal_app_list`、`compose_list`、`helm_chart_info`、`helm_chart_search`、`helm_release_history`、`helm_release_list`、`helm_repo_list`、`mpi_job_detail`、`mpi_job_list`；
  - diag 查询类：`log_query`；
  - query 查询类：`cloud_resource_list`、`currency_query_list`、`deployment_detail`、`image_detail_by_name`、`node_detail`。
- `M511AtlasToolHttpContractTest` 新增 `m516ReadExpansionEndpoints_shouldMatchReviewedWhitelist`，对白名单 15 个 Tool 做精确 endpoint 断言：
  - `HelmReleaseHistoryTool` 必须声明 `/api/{orgId}/helm/releases/{release}/histories`；
  - `MpiJobDetailTool` 必须声明 `/api/{orgId}/mpi-job/{id}`；
  - `LogQueryTool` 必须保持 `/api/log`，不得机械套成 `/api/{orgId}/log`；
  - `DeploymentDetailTool` / `ImageDetailByNameTool` / `NodeDetailTool` 保持源码真实 query 路径，不臆造 `/{id}` 或 `/{name}`。
- 加固 `API_ENDPOINTS_PATTERN`，支持 endpoint 字符串内部包含 `{orgId}`、`{id}`、`{release}` 等占位符，修复旧正则在第一个 `}` 提前截断的问题。

### Verified

- 专家会诊：✅ 安全、源码契约/测试、架构推进三方确认 15/15 可纳入 READ；同时要求同步 endpoint 精确契约测试。
- M511 HTTP 元数据契约：`mvn -q -Dtest=M511AtlasToolHttpContractTest test` → ✅ 通过。
- HITL fail-closed 契约：`mvn -q -Dtest=M513HitlFailClosedContractTest test` → ✅ 通过。
- 元数据/风险定向回归：`mvn -q -Dtest=M511AtlasToolHttpContractTest,ToolRegistryPromptContractTest,ReActEventRiskMetadataTest test` → ✅ 通过。
- 后端编译：`mvn -q -DskipTests compile` → ✅ 通过。
- 全量测试：`mvn -q test` → ✅ 通过。
- 空白检查：`git diff --check` → ✅ 通过。
- 敏感信息扫描：`secret_suspects=0` → ✅ 通过。

### Coverage

- Tool 总数：110。
- 已声明 HTTP 元数据：43（由 M5.15 的 28 扩展到 43）。
- READ 白名单：40（由 M5.15 的 25 扩展到 40）。
- 未迁移 Tool 继续保持 UNKNOWN / fail-closed，仍不因覆盖率目标放松安全边界。

### Risk / Deferred

- `log_query` 是只读日志查询，但日志内容可能包含运行时敏感信息；本阶段按 READ 纳入，前提是依赖后端权限、脱敏、行数限制和审计，不扩展为下载/导出。
- `cloud_resource_list`、`node_detail`、`deployment_detail` 等会暴露资产、拓扑、镜像、规格等只读元数据；仍属于 READ，但需要依赖 org 级隔离。
- `helm_repo_list` 只按仓库元数据 READ 处理，不能包含仓库认证凭据；如果后端返回 secret/token，后续应调整为敏感 READ 或 HITL。
- RBAC、LDAP、用户、组织、配额、下载/上传、文件数据、审批、写操作、删除操作仍不纳入本批免确认 READ。

---

## [M5.15] — Tool HTTP/风险元数据第二批 GET/READ 扩面收尾

**周期**: 2026-05-24
**交付**: 在 M5.14 首批 GET/READ 元数据扩面基础上，继续按“专家会诊前置 + 先实验再铺开”原则，为第二批 13 个低风险 GET/READ Tool 补充 `httpMethod/apiEndpoints/operationType=READ`，并修复测试夹具未声明 READ 元数据导致的 HITL fail-closed 全量测试失败。

### Added

- 第二批 13 个低风险 GET/READ Tool 补充 HTTP/风险元数据：
  - Dashboard 查询类：`dashboard_deployment_count`、`dashboard_image_count`、`dashboard_easy_flow`；
  - 系统/模型查询类：`sys_info_map`、`sys_model_list`、`model_list`；
  - GPU 查询类：`gpu_global_list`、`gpu_map_detail`、`gpu_detail_list`；
  - 资源/镜像/节点查询类：`namespace_status`、`node_allocation`、`image_repository`；
  - 裸金属模板查询类：`bare_metal_template`（deploy agent 下的只读模板查询）。
- `AtlasToolCallbackTest` 增加测试专用安全 READ `ToolMetadata`，使参数归一化测试在不放松生产 fail-closed 的前提下真正执行测试 Tool。
- `ReActEngineMultiStepE2ETest` 将测试内存 Tool 拆为带 `@AtlasToolMapping(operationType=READ)` 的 `PodStatusRecordingTool` / `EventQueryRecordingTool`，明确多步成功路径中的测试 Tool 为只读查询。

### Verified

- 专家会诊：✅ 已完成低风险 GET/READ 扩面方案与测试失败修复会诊；结论为“补测试夹具 READ 元数据，不修改生产 fail-closed”。
- 失败用例复跑：`mvn -q -Dtest=AtlasToolCallbackTest,ReActEngineMultiStepE2ETest test` → ✅ 通过。
- HITL 安全契约：`mvn -q -Dtest=M513HitlFailClosedContractTest test` → ✅ 通过。
- 元数据/风险定向回归：`mvn -q -Dtest=M511AtlasToolHttpContractTest,ToolRegistryPromptContractTest,ReActEventRiskMetadataTest test` → ✅ 通过。
- 后端编译：`mvn -q -DskipTests compile` → ✅ 通过。
- 全量测试：`mvn -q test` → ✅ 通过。
- 空白检查：`git diff --check` → ✅ 通过。

### Coverage

- Tool 总数：110。
- 已声明 HTTP 元数据：28（由 M5.14 的 15 扩展到 28）。
- READ 白名单：25。
- 仍未迁移 Tool 继续保持 UNKNOWN / fail-closed，不因覆盖率目标放松安全边界。

### Risk / Deferred

- 本阶段仍只覆盖人工确认的低风险 GET/READ Tool；下载/导出、审批、配额变更、写操作、删除操作、敏感 admin-only Tool 暂不纳入免确认 READ。
- `bare_metal_template` 虽位于 deploy agent，但实际 HTTP 行为是查询模板列表，已按 READ 记录；后续 deploy 域写操作仍需单独高风险治理。
- 两个测试失败的根因是测试夹具未携带 READ 元数据被 HITL fail-closed 正确拦截，不是本轮 13 个生产 Tool 注解变更引起；已通过补测试元数据闭环。

---

## [M5.14] — Tool HTTP/风险元数据首批 GET/READ 扩面治理

**周期**: 2026-05-24
**交付**: 在 M5.11 小样本契约、M5.12 风险透明化、M5.13 fail-closed HITL 守卫基础上，继续按“先实验再铺开”原则扩展低风险 GET/READ Tool 元数据覆盖，并加固契约测试对未来 BaseTool 继承 HTTP Client 场景的识别能力。

### Added

- 加固 `M511AtlasToolHttpContractTest`：
  - 新增 `BASE_TOOL_FILE` 与 `EXTENDS_BASE_TOOL_PATTERN`；
  - 新增 `readVisibleClientFieldNames(...)`，在 Tool 继承 `BaseTool` 时合并基类可见 `KubeManagerHttpClient` 字段；
  - 保持源码级静态测试边界，不启动 Spring、不调用 kube-manager、不触发真实数据面请求。
- 首批 10 个低风险 GET/READ Tool 补充 `httpMethod/apiEndpoints/operationType`：
  - 公共首页展示类：`home_model_list`、`home_industry_list`、`home_nim_list`、`home_industry_class_list`、`home_repository_list`；
  - 普通只读查询类：`quota_my_list`、`resource_usage_list`、`namespace_list`、`table_list`、`cluster_overview`。

### Verified

- 专家会诊：✅ 已完成 Java 契约测试、安全 HITL、批量迁移三路会诊；结论为“READ 是白名单，不是 GET 同义词，必须小样本推进”。
- M511 HTTP 元数据契约：`mvn -q -Dtest=M511AtlasToolHttpContractTest test` → ✅ 通过。
- 定向回归组合：`mvn -q -Dtest=M511AtlasToolHttpContractTest,ToolRegistryPromptContractTest,ReActEventRiskMetadataTest,M513HitlFailClosedContractTest test` → ✅ 通过。
- 后端编译：`mvn -q -DskipTests compile` → ✅ 通过。
- 空白检查：`git diff --check` → ✅ 通过。
- 独立 Review：✅ 未发现 blocker；确认未误标高风险 Tool、endpoint 与源码路径一致、未泄露凭据、未改业务逻辑。

### Coverage

- Tool 总数：110。
- 已声明 HTTP 元数据：15（由 5 扩展到 15）。
- 未声明 HTTP 元数据：95。
- GET/READ 白名单：12（原 `EventQueryTool`、`StorageQueryTool` + 本阶段新增 10 个）。

### Risk / Deferred

- 本阶段只迁移人工确认过的低风险 GET/READ 小样本；POST/DELETE/ACTION/敏感 admin-only/下载导出类 Tool 不在本批范围。
- 契约测试仍为源码正则扫描，短期足够保护小样本；后续若元数据治理继续扩大，可考虑抽象 JavaParser/AST Analyzer。
- 大量历史 Tool 仍保持未声明/UNKNOWN，由 M5.13 fail-closed 继续保护，宁可多拦截，不误放行。

---

## [M5.13] — HITL fail-closed 执行层强拦截前后端同步治理

**周期**: 2026-05-23
**交付**: 在 M5.12 风险透明化基础上，将 HITL 从提示/占位升级为后端执行层 fail-closed 强拦截：高风险 Tool 未经服务端可信人工确认一律拒绝执行；确认后只放行精确目标 Tool；前端同步确认流缺字段 fail-closed 与风险文案。

### Added

- 后端新增 `com.atlas.hitl.HitlConfirmation`：服务端可信确认 marker，仅由 `HITLController.confirmAndResume` 在 `confirmToken` 校验成功后创建。
- 后端新增 `com.atlas.hitl.HitlGuard`：统一执行层守卫，依据 `ToolRegistry.ToolMetadata` 判断风险：
  - `requiresConfirmation=true` 必须确认；
  - `operationType != READ` 必须确认；
  - metadata 缺失时 fail-closed，避免未知 Tool 被默认为安全。
- `ToolRegistry` 增加按 intent/tool 名称解析元数据能力，供执行层守卫使用。
- `AtlasGraphConfig.supervisorGraph` 的 `tool_call` 节点在 `tool.execute(...)` 前读取 `hitl_confirmation` 并调用 `HitlGuard`。
- `ReActEngine`、`AtlasOrchestrator` legacy fallback、`graph.bridge.AtlasToolCallback`、`tool.core.AtlasToolCallback` 均在直接 execute 前接入 `HitlGuard`，关闭多入口绕过风险。
- `HITLController` 明确注入 `@Qualifier("supervisorGraph")`，confirm 后恢复进入可读取 `hitl_confirmation` 的 `tool_call` 链路。
- `supervisorGraph` 的 supervisor 节点优先复用 resume 注入的 `brain_decision`，避免确认后的 `CALL_TOOL` 被重新 LLM/Brain 决策覆盖。
- 普通新会话与 clarify/resume 非确认路径均显式 `hitl_confirmation=null`，避免 checkpoint/同线程状态继承旧确认。
- 新增后端源码契约测试 `M513HitlFailClosedContractTest`，锁定多入口 guard、确认 marker、clarify 清理、确认后复用决策等关键结构。
- 前端 `kube-agent-vue`：
  - `useChat.ts` 修复/增强 SSE confirm/clarify 解析；
  - `ChatView.vue` 对缺 `threadId/confirmToken` 的确认流 fail-closed，不继续调用后端确认接口；
  - `ChatBubble.vue` 将风险提示文案从“建议确认”调整为“执行前确认”，避免误导为软提示。
- 新增前端源码契约脚本 `scripts/m513-hitl-contract-test.cjs`。

### Verified

- 后端 M5.13 定向契约测试：`mvn -q -Dtest=M513HitlFailClosedContractTest test` → ✅ 通过。
- 后端编译：`mvn -q -DskipTests compile` → ✅ 通过。
- 前端契约：`node scripts/m513-hitl-contract-test.cjs` → ✅ 通过。
- 前端构建：`npm run build`（`vue-tsc && vite build`）→ ✅ 通过；仅有 Element Plus 依赖 Rollup 注释警告。
- 后端/前端 `git diff --check` → ✅ 通过。
- 新增行敏感信息扫描：后端/前端 `secret_suspects=0`。
- 三轮独立 Review：
  - 第 1 轮发现多 execute 入口绕过与 clarify 旧 marker 继承风险；已修复。
  - 第 2 轮发现确认后恢复链路可能被重新决策覆盖；已修复。
  - 第 3 轮结论：未发现高风险绕过、旧确认继承或确认后不可执行等严重问题，M5.13 闭环通过。
- 本阶段未对真实 kube-manager 执行删除/修改/创建类破坏性请求，仅做源码契约、编译构建与逻辑级验证。

### Risk / Deferred

- 当前 `HitlConfirmation` 主要校验 target 与服务端来源；后续可继续把 threadId 纳入 `HitlGuard.verify` 参数以收紧边界。
- `com.atlas.graph.bridge.AtlasToolCallback` 与 `com.atlas.tool.core.AtlasToolCallback` 存在同名类，当前均已接入 guard，但后续建议统一命名或合并，降低维护误改概率。
- 后续可补充非字符串级运行时集成测试，模拟 `HITL_CONFIRM -> confirm -> supervisorGraph tool_call -> HitlGuard 放行 -> tool.execute` 全链路。

---

## [M5.12] — Tool 风险元数据透明化前后端同步治理

**周期**: 2026-05-23
**交付**: 将 M5.11 建立的 Tool HTTP/风险注解元数据接入 ToolRegistry Prompt 与 ReAct SSE metadata，并同步在 kube-agent-vue 前端 ReAct 时间线展示风险标签、确认提示和占位能力提醒。M5.12 明确只做风险透明化，不冒充执行层安全边界；真正 fail-closed HITL 强拦截留到 M5.13。

### Added

- 后端 `ToolRegistry.ToolMetadata` 新增并保留：
  - `httpMethod`：Tool 声明的 HTTP 方法；
  - `apiEndpoints`：内部 API 路径模板，仅保存在元数据中，不写入 Prompt/前端展示；
  - `operationType`：READ/CREATE/UPDATE/DELETE/ACTION/PLACEHOLDER 风险语义；
  - `requiresConfirmation`：是否建议人工确认。
- `ToolRegistry.buildSystemPromptForCurrentUser()` 新增紧凑风险标签：
  - 例：`operationType=ACTION, httpMethod=POST, requiresConfirmation=true`；
  - 明确提示 M5.12 风险标签只是辅助判断，不是执行层强拦截。
- `ReActEvent` 新增带 `extraMetadata` 的 `toolStart/toolDone` 重载；旧方法保持兼容。
- `ReActEngine` 在工具事件中透传非敏感风险摘要：`httpMethod/operationType/requiresConfirmation`。
- 前端 `kube-agent-vue`：
  - 新增 `ToolRiskMetadata/ToolOperationType` 类型；
  - `ChatBubble.vue` ReAct 时间线展示 `READ/GET`、`ACTION/POST`、`DELETE/DELETE` 等风险 chip；
  - 展示“建议确认”标签；
  - 对 `DELETE` 与 `PLACEHOLDER` 显示明确风险/占位提示。

### Verified

- 专家会诊：✅ 已收敛为“风险透明化优先，不泄露 endpoint，不把提示冒充安全边界”。
- 后端定向测试：`mvn -q -Dtest=ToolRegistryPromptContractTest,ReActEventRiskMetadataTest test` → ✅ 通过。
- 前端类型检查与构建：`npm run build`（内部执行 `vue-tsc && vite build`）→ ✅ 通过；仅出现 Element Plus 依赖 Rollup 注释警告。
- 后端 `git diff --check`：✅ 通过。
- 前端 `git diff --check`：✅ 通过。
- 本阶段未启动服务、未调用真实 kube-manager、未执行真实删除/修改请求。

### Risk / Deferred

- M5.12 不提供 fail-closed 执行强拦截；`requiresConfirmation=true` 在本阶段只作为透明化提示和后续 HITL 输入。
- 历史未迁移 Tool 仍可能显示 `operationType=UNKNOWN` 或未声明 HTTP；后续需继续按批次铺开 M5.11 元数据。
- 前端展示依赖后端 SSE metadata，旧服务未重启或旧事件不会显示风险 chip。
- M5.13 建议接入执行层 Human-in-the-loop 强确认，禁止高危 Tool 仅靠 Prompt/UI 提示执行。

---

## [M5.11] — Atlas Tool HTTP 元数据契约小样本治理

**周期**: 2026-05-23
**交付**: 建立 Tool 注解元数据与真实 kube-manager HTTP 调用的一致性契约小样本；扩展 `@AtlasToolMapping` 承载 `httpMethod/apiEndpoints/operationType/requiresConfirmation`，并用源码级静态测试保护已迁移 Tool，避免 LLM/ToolRegistry/安全策略误把写删操作当只读操作。

### Added

- `AtlasToolMapping` 新增兼容字段：
  - `httpMethod`：声明真实 kube-manager HTTP 方法，默认空字符串兼容历史 Tool；
  - `apiEndpoints`：声明一个或多个 API 路径模板，支持多路径 fallback；
  - `operationType`：声明 READ/CREATE/UPDATE/DELETE/ACTION/PLACEHOLDER 等业务风险语义；
  - `requiresConfirmation`：声明高风险 Tool 是否需要 Human-in-the-loop 确认。
- 小样本迁移 5 类代表性 Tool：
  - `event_query`：GET + READ；
  - `storage_status`：多路径 GET fallback + READ；
  - `mpi_job_submit`：POST + ACTION + requiresConfirmation；
  - `image_delete`：DELETE + DELETE + requiresConfirmation；
  - `deploy_scale`：NONE + PLACEHOLDER + requiresConfirmation。
- 新增 `M511AtlasToolHttpContractTest`：
  - 只校验已声明 `httpMethod` 的 Tool，避免一次性引爆 110 个历史 Tool；
  - 静态扫描 `KubeManagerHttpClient` 字段变量名与 `get/post/delete/put/patch` 调用；
  - 校验声明方法与真实调用一致；
  - 校验写删/占位风险语义不能伪装成 READ；
  - 校验 DELETE/ACTION/PLACEHOLDER 必须 `requiresConfirmation=true`；
  - 校验非 NONE Tool 必须声明 `apiEndpoints`。

### Verified

- 专家会诊：✅ 架构专家、测试契约专家、安全生产就绪专家均建议建立 Tool HTTP 元数据契约；结论为先小样本验证，再分批铺开。
- 定向契约测试：`mvn -Dtest=M511AtlasToolHttpContractTest test` → ✅ Tests run: 1, Failures: 0, Errors: 0。
- 编译打包：`mvn -DskipTests package` → ✅ BUILD SUCCESS。
- `git diff --check`：✅ 通过。
- 静态 Review 扫描：✅ 当前仅 5 个 Tool 声明 `httpMethod`，方法与真实 `KubeManagerHttpClient` 调用一致；本阶段未调用真实 kube-manager，未执行真实删除/修改请求。

### Risk / Deferred

- M5.11 仍是小样本强契约，历史 105 个左右 Tool 仍保持 `operationType=UNKNOWN`/未声明 HTTP 元数据，后续需按 GET → DELETE → POST/ACTION → 特殊 Tool 分批铺开。
- `DeployScaleTool` 当前被明确标记为 `PLACEHOLDER`，后续真实接入 PATCH/scale 前必须解除占位并补充真实 HTTP 契约与执行层 HITL 拦截。
- 本阶段只把元数据写入注解并用源码契约保护，尚未把风险元数据注入 ToolRegistry Prompt 或执行层强制拦截；后续 M5.12/M5.13 可继续推进 prompt 暴露与 runtime fail-closed。

---

## [M5.10] — ArchUnit 架构级安全边界契约治理

**周期**: 2026-05-23
**交付**: 引入 ArchUnit 作为架构级静态契约测试，补强 M5.9 源码字符串契约；将 HTTP 出口治理从“源码扫描”扩展到“包/类依赖边界”层面，继续保证不访问、不修改 kube-manager 真实数据。

### Added

- 新增 test scope 依赖：`com.tngtech.archunit:archunit-junit5:1.3.0`。
- 新增 `M510ArchitectureBoundaryTest`，包含 3 条架构规则：
  - 白名单外生产代码不得直接依赖底层 HTTP 客户端；
  - `com.atlas.tool..` 不得依赖底层 HTTP 客户端，只能通过受控网关访问 kube-manager；
  - `com.atlas.controller..` 不得直接依赖 `com.atlas.tool.impl..`，避免绕过 Orchestrator/ReAct/ToolRegistry 编排链路。
- 底层 HTTP 客户端覆盖范围包括：`RestClient`、`RestTemplate`、`WebClient`、`java.net.*`、`OkHttp`、`Feign/OpenFeign`、Apache HttpClient 4/5。

### Verified

- 专家会诊：✅ Java 架构专家建议小步引入 ArchUnit；安全专家复核 PASS，明确 ArchUnit 只做静态分析，不启动 Spring、不访问 kube-manager。
- 开源调研：TNG/ArchUnit 是用于 Java architecture rules 的开源架构测试库，官方定位为 plain Java unit testing 下检查架构/编码规则。
- 定向验证：`mvn test -q -Dtest=M510ArchitectureBoundaryTest` → ✅ 通过。
- 安全组合回归：`mvn test -q -Dtest=M510ArchitectureBoundaryTest,M59HttpSecurityBoundaryContractTest,KubeManagerHttpClientTokenFallbackSecurityTest,M57FallbackOrgIdSourceContractTest` → ✅ 通过。
- 打包：`mvn -q -DskipTests package` → ✅ BUILD SUCCESS。
- `git diff --check`：✅ 通过。
- Diff 敏感信息/危险执行扫描：✅ 未发现新增密钥、PAT、危险进程执行、`eval/exec` 等模式。

### Risk / Deferred

- M5.10 不替代 M5.9：ArchUnit 负责结构/依赖边界，M5.9 继续负责 `resolveToken()` 与 `resolveUserTokenRequired()` 的方法体语义。
- 当前先落最小三条规则，避免一次性引入过重分层约束造成历史代码大规模返工；后续可继续扩展到 service/orchestrator/react/config 等层级边界。
- 本阶段仍严格零数据影响：未启动服务、未调用真实 kube-manager API、未执行真实删除/修改操作。

---

## [M5.9] — HTTP 出口与 fallback token 源码契约治理

**周期**: 2026-05-23
**交付**: 新增源码级安全契约测试，锁定 kube-manager 统一 HTTP 出口与 M5.8 token fallback 边界；避免未来业务 Tool 绕过 `KubeManagerHttpClient` 或重新把 sysadmin fallback token 接回业务默认路径。

### Added

- 新增 `M59HttpSecurityBoundaryContractTest`，覆盖 3 类源码契约：
  - 白名单外生产代码不得直接创建/注入 HTTP 客户端访问 kube-manager 数据面；
  - `KubeManagerHttpClient#get/post/delete` 必须使用 `resolveUserTokenRequired`；
  - `resolveToken()` 只能作为系统任务 fallback 能力保留，不得被业务路径调用。
- HTTP 出口白名单显式区分：
  - `KubeManagerHttpClient`：统一 kube-manager 数据面 HTTP 出口；
  - `AuthController`：登录代理入口；
  - `ModelDownloader`：外部 Embedding 模型下载，不访问 kube-manager 数据面。

### Verified

- 快速专家 Review 会诊：✅ PASS with Notes；结论为当前源码契约测试安全、无 kube-manager 数据影响，建议扩展 HTTP 出口模式后合入。
- 按专家建议补强 HTTP 出口扫描模式：覆盖 `RestClient/RestTemplate/WebClient/HttpURLConnection/HttpClient/openConnection/OkHttp/Feign/Apache HttpClient` 等常见绕过路径。
- 定向逻辑验证：`mvn test -q -Dtest=M59HttpSecurityBoundaryContractTest` → ✅ 通过。
- 安全组合回归：`mvn test -q -Dtest=M59HttpSecurityBoundaryContractTest,KubeManagerHttpClientTokenFallbackSecurityTest,M57FallbackOrgIdSourceContractTest` → ✅ 通过。
- 打包：`mvn -q -DskipTests package` → ✅ BUILD SUCCESS。
- `git diff --check`：✅ 通过。
- Diff 敏感信息/危险执行扫描：✅ 未发现新增密钥、PAT、危险进程执行、`eval/exec` 等模式。

### Risk / Deferred

- 本阶段严格遵守“避免影响 kube-manager 数据”：未启动服务、未调用真实 kube-manager API、未执行真实删除/修改操作，只做源码契约与单元逻辑验证。
- 当前契约为源码字符串级扫描，不是 AST/ArchUnit 级强约束；后续如引入 ArchUnit，可进一步把“包级依赖约束”和“方法调用约束”升级为结构化架构测试。
- `AuthController` 目前按文件级白名单放行直接 HTTP；后续若该类新增非登录代理的数据面调用，应拆分更细粒度白名单或迁移到统一 client。

---

## [M5.8] — 业务 Tool 禁止 sysadmin fallback token 自动降级

**周期**: 2026-05-23
**交付**: 将 `KubeManagerHttpClient#get/post/delete` 业务请求入口收口为“必须使用用户 ThreadLocal Token”；缺少可信用户上下文时 fail-closed，避免 Agent Tool 在无用户会话时透明降级为 sysadmin token 代跑。

### Added

- 新增 `KubeManagerHttpClientTokenFallbackSecurityTest`，覆盖 5 个安全边界：
  - GET 缺用户 Token 时拒绝请求且不触发 fallback 登录；
  - POST 缺用户 Token 时拒绝请求且不触发 fallback 登录；
  - DELETE 缺用户 Token 时拒绝请求且不触发 fallback 登录；
  - GET 存在用户 Token 时只使用用户 Token，不触发 fallback；
  - 保留系统任务 Token 解析入口 `resolveToken()` 的 fallback 能力，作为未来显式系统任务白名单能力。

### Changed

- `KubeManagerHttpClient#get/post/delete`：从 `resolveToken()` 切换为 `resolveUserTokenRequired(operation, path)`。
- 新增 `resolveUserTokenRequired` 私有方法：业务请求缺用户 Token 时抛出 `IllegalStateException`，并输出安全拒绝日志。
- 保留 `resolveToken()`，但文档明确限制为未来显式系统任务入口，禁止业务 Tool 默认路径调用。

### Verified

- 定向测试：`mvn test -q -Dtest=KubeManagerHttpClientTokenFallbackSecurityTest` → ✅ 5 tests, 0 failures。
- M5.7/M5.8 安全组合回归：`mvn test -q -Dtest=KubeManagerHttpClientResolveOrgIdSecurityTest,M57FallbackOrgIdSourceContractTest,BaseToolOrganizationIdGovernanceTest,KubeManagerHttpClientTokenFallbackSecurityTest` → ✅ 17 tests, 0 failures。
- 全量测试：`mvn test -q` → ✅ 182 tests, 0 failures, 0 errors, 0 skipped。
- 打包：`mvn -q -DskipTests package` → ✅ BUILD SUCCESS。
- `git diff --check`：✅ 通过。
- Diff 敏感信息/危险执行扫描：✅ 未发现新增密钥、PAT、危险进程执行、`eval/exec` 等模式。

### Risk / Deferred

- 本轮先做 HTTP 客户端业务入口 fail-closed，不扩大到所有上层 Tool/HITL/Graph 结构；后续可继续审计是否存在绕过 `get/post/delete` 的独立 HTTP 出口。
- `resolveToken()` 仍保留 fallback 语义，必须仅用于显式 SYSTEM_CONTEXT_ALLOWED 系统任务；未来若新增调用方，需要白名单、审计日志和测试保护。
- 当前验证以单元/回归测试为主，未重启本地服务做真实 SSE；原因是本次安全边界位于 HTTP 客户端 token 解析层，MockRestServiceServer 已精确断言请求不会发出或只携带用户 Token。

---

## [M5.7] — fallbackOrgId 可信语义彻底收口与登录 fail-safe 治理

**周期**: 2026-05-22
**交付**: 将 `fallbackOrgId` 从可信租户上下文中彻底移除：删除 getter/config 字段语义，`resolveOrgId` 改为强类型 fail-closed，登录反查失败不创建 session，并用源码扫描契约测试防止默认组织语义回流。

### Added

- 新增 `OrgIdResolutionException`，用 `USERNAME_EMPTY`、`TOKEN_UNAVAILABLE`、`USER_NOT_FOUND`、`INVALID_RESOLVED_ORG_ID` 等 Reason 锁定 orgId 解析失败原因。
- 新增 `M57FallbackOrgIdSourceContractTest`，扫描生产源码，禁止 `fallbackOrgId/getFallbackOrgId/atlas.backend.fallback-org-id/默认租户` 等可信上下文语义回流。
- 新增 `KubeManagerHttpClientResolveOrgIdSecurityTest`，覆盖空用户名、缺 token、sysadmin token 前置校验、用户未找到、非法 orgId 立即 fail-safe、可信 orgId 正向返回等 7 个边界。
- 新增 `AuthControllerLoginFailSafeTest`，验证登录成功但无法解析可信 orgId 时不创建 session。
- 新增治理方案文档：`docs/M5_7_FALLBACK_ORG_ID_GOVERNANCE_PROPOSAL_20260522.md`。

### Changed

- `KubeManagerHttpClient#resolveOrgId(username, authToken)`：
  - 必须要求 username 与本次登录 token 非空；
  - 不再使用 sysadmin/fallback token 代查普通用户租户；
  - 不再返回或缓存默认组织；
  - 用户命中但 orgId 为空、`null` 或 `1` 时立即抛异常，不继续扫桶洗白；
  - 移除 username-only orgId cache，避免跨 session / 跨租户复用旧组织上下文。
- `AuthController#login`：登录响应缺可信 orgId 时，用本次登录 token 反查；反查失败返回 502 并拒绝创建 session。
- `AtlasOrchestrator`、`AsyncContextHolder`、`AtlasGraphConfig` 清理 fallbackOrgId/fallback 文案残留，统一表达“缺可信 orgId 则 fail-safe”。
- `CHANGELOG` 中 M5.6 deferred 项已由本阶段关闭。

### Verified

- M5.7 定向测试：`mvn -Dtest=M57FallbackOrgIdSourceContractTest,KubeManagerHttpClientResolveOrgIdSecurityTest,AuthControllerLoginFailSafeTest test` → ✅ 9 tests, 0 failures。
- M5.6/M5.7 组合回归：`mvn -Dtest=TokenPropagatingTaskDecoratorTest,AsyncContextHolderTest,AtlasOrchestratorOrgIdGuardTest,M57FallbackOrgIdSourceContractTest,KubeManagerHttpClientResolveOrgIdSecurityTest,AuthControllerLoginFailSafeTest test` → ✅ 21 tests, 0 failures。
- 全量测试：`mvn test` → ✅ 177 tests, 0 failures, BUILD SUCCESS。
- 打包：`mvn -DskipTests package` → ✅ BUILD SUCCESS。
- `git diff --check`：✅ 通过。
- Diff 敏感信息扫描：✅ `SECRET_SCAN_FINDINGS 0`。
- 独立 Review 第一轮：❌ 发现 username-only orgId cache 与 sysadmin token 前置校验问题。
- 独立 Review 第二轮：✅ PASS，第一轮 blocker 全部关闭。

### Closed

- 关闭 M5.6 deferred：`KubeManagerHttpClient#getFallbackOrgId()` 与 `atlas.backend.fallback-org-id` 可信配置语义已清理。

---

## [M5.6] — 异步上下文传播与 fallbackOrgId 可信语义治理

**周期**: 2026-05-22
**交付**: 将异步执行、旧 `/chat/graph`、Graph delegate、HITL resume 的安全上下文统一升级为 `token + orgId` 原子传播；移除执行链路中 `fallbackOrgId` 作为可信租户来源的兜底语义，缺 orgId 时 fail-safe。

### Added

- 新增 `AsyncContextHolder` token+orgId Runnable/Supplier/Callable/supplyAsync 重载。
- 新增 `DelegatingExecutorTest`，覆盖代理 Executor 的 token+orgId 传播与恢复。
- 新增 `TokenPropagatingTaskDecoratorTest`，覆盖 Spring TaskDecorator 捕获提交时安全上下文。
- 扩展 `AsyncContextHolderTest`，覆盖 orgId 传播、恢复和空上下文隔离。

### Changed

- `AsyncContextHolder` 统一使用“保存旧值 → 绑定快照 → finally 恢复旧值”。
- `DelegatingExecutor` 新增 token+orgId 构造并委托 `AsyncContextHolder`。
- `AtlasAsyncConfig.TokenPropagatingTaskDecorator` 同时捕获并传播 token 与 orgId。
- `AtlasOrchestrator` 旧 `/chat/graph` 入口和异步 graphTask 使用 token+orgId 包装；传统 Tool fallback 缺 orgId 时安全拒绝。
- `AtlasGraphConfig#tool_call` 缺 orgId 时 fail-safe，不再调用 `getFallbackOrgId()`；`delegate` 只信 `state.orgId` 或 ThreadLocal，不再信孤立 `organizationId` fallback。
- `HITLController` confirm/clarify resume 从 checkpoint 恢复 token+orgId，并使用 `AsyncContextHolder.wrap` 执行。

### Verified

- M5.6 定向回归：17 tests, 0 failures, BUILD SUCCESS。
- 全量测试：168 tests, 0 failures, BUILD SUCCESS。
- `git diff --check`：通过。
- Diff 敏感信息扫描：`NO_NEW_SENSITIVE_IN_DIFF`。
- 独立 Review 两轮：第一轮 CONCERN 已修复，第二轮 PASS。

### Deferred

- `KubeManagerHttpClient#getFallbackOrgId()` getter 与配置注释已在 M5.7 关闭。

---

## [M5.5] — orgId 来源治理与跨租户参数污染防护

**周期**: 2026-05-22
**交付**: 将 orgScoped Tool 的组织来源从不可信 params 收口到可信 ThreadLocal/session 上下文；治理 ReAct、Graph tool_call、Graph delegate 三条参数合并链路，防止 LLM Action 或 BrainDecision parameters 覆盖 `organizationId/orgId`。

### Added

- 新增 `BaseToolOrganizationIdGovernanceTest`，锁定 `BaseTool#resolveOrganizationId` 不再接受 params 中的 `organizationId/orgId` 作为 path 权威来源。
- 新增 `OrganizationIdGovernanceRepresentativeToolTest`，覆盖 Dashboard、Deployment、Storage 写操作以及 `GpuQueryTool`、`ClusterOverviewTool`、`ImageQueryTool` 三个 legacy Tool 的跨租户注入防护。
- 新增 M5.5 orgId 来源治理审计种子文档：`docs/M5_5_ORG_ID_SOURCE_AUDIT_SEED_20260522.md`。

### Changed

- `BaseTool#resolveOrganizationId(params)` 改为只信任 `UserPermissionContext.CURRENT_ORG_ID`。
- `ReActEngine#mergeInitialAndActionParams` 过滤受保护上下文字段，LLM Action 不得覆盖或新增 `token/organizationId/orgId/conversationId/userId`。
- `AtlasGraphConfig#tool_call` 对 Brain/LLM parameters 过滤受保护字段，系统上下文字段最后写入，并绑定/清理 token 与 orgId ThreadLocal。
- `AtlasGraphConfig#delegate` 增加 orgId state strategy、子图输入透传、ThreadLocal 绑定和 finally 清理。
- `GpuQueryTool`、`ClusterOverviewTool`、`ImageQueryTool` 统一改为 `resolveOrganizationId(params)`。
- M4/M5 既有契约测试更新为使用 ThreadLocal 可信 orgId，不再通过 params 模拟租户上下文。

### Verified

- M5.5 定向测试：13 tests, 0 failures, BUILD SUCCESS。
- M5 参数治理回归：28 tests, 0 failures, BUILD SUCCESS。
- 全量测试：161 tests, 0 failures, BUILD SUCCESS。
- `git diff --check`：通过。
- Diff 敏感信息扫描：`NO_NEW_SENSITIVE_IN_DIFF`。
- 独立 Review 两轮：第一轮 CONCERN 已修复，第二轮 PASS，可提交。

### Deferred

- `fallbackOrgId` 的可信语义、`AtlasAsyncConfig` TaskDecorator orgId 传播、旧 `/chat/graph` 入口上下文治理进入后续 M5.6 专项。

---

## [M5.3] — GLOBAL/PUBLIC/NO_ORG 首页公共接口 page/limit-only 契约

**周期**: 2026-05-22
**交付**: 按后端/API、安全/RBAC、测试架构三路专家会诊结论，将 5 个 `/api/public/home-info/*` 首页公共展示 Tool 纳入受限 `page/limit-only` 参数契约；`keyword/name/search/kw` 不暴露也不透传；`limit` 最大值锁定为 100。`GpuGlobalListTool` 与 `SysModelListTool` 继续 full HOLD。

### Added

- 新增 `BaseTool#pageLimitOnlyParameterSpecs()`：只声明 `page`、`limit`，不包含 `keyword` 或搜索别名。
- 新增 `BaseTool#buildPageLimitOnlyQuery(params, maxLimit)`：只构建 `page/limit` query，忽略 `keyword/name/search/kw/orgId/organizationId` 等旁路参数，并对 `limit` 执行上限校验。
- 新增 `HomeInfoPublicPageLimitContractTest`，覆盖 5 个首页公共 Tool 的参数契约、真实透传、旁路参数不透传、`limit > 100` 拒绝、非法分页拒绝。
- `SensitiveListToolHoldContractTest` 新增 M5.3 覆盖：`GpuGlobalListTool` 与 `SysModelListTool` 不暴露 `page/limit/keyword`。

### Changed

- `HomeIndustryClassListTool`、`HomeIndustryListTool`、`HomeModelListTool`、`HomeNimListTool`、`HomeRepositoryListTool` 从固定 `page=1&limit=100` 改为受限 `page/limit-only` 查询。
- 上述 5 个 Tool 显式 rethrow `AtlasToolValidationException`，保留 `VALUE_OUT_OF_RANGE`、`TYPE_MISMATCH` 等结构化错误码。

### Deferred

- `GpuGlobalListTool`（`/api/gpu`）与 `SysModelListTool`（`/api/model`）属于全局/跨组织资源入口，继续 HOLD，不开放 `page/limit/keyword`。
- 本阶段不修改 `PUBLIC` 权限注解；GLOBAL/PUBLIC 权限收敛后续单独专项处理。

### Verified

- TDD 红灯：新增测试首次运行 4 failures，准确暴露 home-info 无参数契约、未透传 page/limit、未限制 limit 上限。
- 定向绿灯：`/usr/share/maven/bin/mvn -Dtest=HomeInfoPublicPageLimitContractTest,SensitiveListToolHoldContractTest test` → 7 tests, 0 failures, BUILD SUCCESS。
- 邻近回归：`/usr/share/maven/bin/mvn -Dtest=HomeInfoPublicPageLimitContractTest,SensitiveListToolHoldContractTest,ListToolParameterSpecContractTest,ListToolParameterPassThroughContractTest test` → 12 tests, 0 failures, BUILD SUCCESS。
- 全量测试：`/usr/share/maven/bin/mvn test` → 149 tests, 0 failures, BUILD SUCCESS。
- `git diff --check`：通过。
- 本次 diff 敏感信息扫描：`DIFF_SECRET_SCAN_FINDINGS 0`。
- 独立 pre-commit Review：PASS，无阻断项。

---

## [M5.2] — RBAC 管理面列表参数 HOLD 保护

**周期**: 2026-05-22
**交付**: 按后端/API、安全/RBAC、测试架构三路专家会诊共识，不开放 RBAC 管理面列表的 `page/limit/keyword`，而是将 HOLD 决策测试化，防止后续批量脚本误把身份源、组织、权限菜单、注册审核、角色边界列表接入普通列表三件套。

### Changed

- `SensitiveListToolHoldContractTest` 从 M5.1 的“仅禁止 keyword”升级为禁止敏感列表暴露 `page`、`limit`、`keyword` 三类标准列表查询参数。
- `OrderListTool`、`QuotaReceiveListTool` 的 HOLD 语义同步升级：在权限/审计专项完成前，不暴露翻页、批量枚举和搜索枚举能力。

### Added

- 新增 M5.2 RBAC 管理面 HOLD 覆盖：`LdapConfigListTool`、`OrganizationListTool`、`PermissionMenuListTool`、`RegisterAuditListTool`、`RoleAssignableListTool`、`RoleEditableListTool`。

### Deferred

- 本阶段不修改生产代码，不给上述 6 个 RBAC Tool 接入 `listQueryParameterSpecs()` / `buildListQuery(params)`。
- 当前 `PUBLIC` 权限注解被专家会诊确认为安全债务，但不与参数 HOLD 混改；后续单独进入 RBAC 权限收敛专项。

### Verified

- TDD 红灯：临时突变 `LdapConfigListTool` 暴露标准列表参数后，`SensitiveListToolHoldContractTest` 准确失败并拦截 `page` 暴露。
- 最小绿灯：撤销突变后，`/usr/share/maven/bin/mvn -Dtest=SensitiveListToolHoldContractTest test` → 2 tests, 0 failures, BUILD SUCCESS。
- 邻近回归：`/usr/share/maven/bin/mvn -Dtest=ListToolParameterSpecContractTest,ListToolParameterPassThroughContractTest,SensitiveListToolHoldContractTest test` → 7 tests, 0 failures, BUILD SUCCESS。
- 全量测试：`/usr/share/maven/bin/mvn test` → 144 tests, 0 failures, BUILD SUCCESS。
- `git diff --check`：通过。
- 新增行敏感信息扫描：`SECRET_SCAN_FINDINGS 0`。
- 独立 pre-commit Review：PASS，无阻断问题。

---

## [M5.1] — 账务域低风险货币列表参数契约与敏感列表 HOLD 保护

**周期**: 2026-05-22
**交付**: 进入 M5 敏感域专项后，按专家会诊结论仅将低风险账务元数据 `CurrencyQueryListTool` 纳入标准 `page/limit/keyword` 参数契约，同时为订单与审批列表建立 HOLD 防误开放测试。

### Added

- 新增 `SensitiveListToolHoldContractTest`，锁定 `OrderListTool` 与 `QuotaReceiveListTool` 在权限、字段脱敏与审计专项完成前不得暴露 `keyword` 搜索能力。

### Changed

- `CurrencyQueryListTool` 新增标准 `page/limit/keyword` 参数契约。
- `CurrencyQueryListTool` 执行层从固定 `page=1&limit=100` 改为 `buildListQuery(params)`，真实透传调用方分页与关键词。
- `CurrencyQueryListTool` 显式 rethrow `AtlasToolValidationException`，保留结构化参数错误码与 suggestions。

### Deferred

- `OrderListTool` 继续 HOLD：订单/租赁账务敏感列表，接入前需确认租户隔离、可见范围、字段脱敏、keyword 搜索字段与审计策略。
- `QuotaReceiveListTool` 继续 HOLD：配额审批/RBAC 语义敏感，接入前需确认审批人可见范围、权限策略与审计记录。

### Verified

- TDD 红灯：新增契约测试后，`currency_query_list` 因未声明 `page`、仍固定分页、非法分页未短路而失败，符合预期。
- 定向测试：`/usr/share/maven/bin/mvn -Dtest=ListToolParameterSpecContractTest,ListToolParameterPassThroughContractTest,SensitiveListToolHoldContractTest test` → 6 tests, 0 failures, BUILD SUCCESS。
- 全量测试：`/usr/share/maven/bin/mvn test` → 143 tests, 0 failures, BUILD SUCCESS。
- `git diff --check`：通过。
- 新增行敏感信息扫描：`SECRET_SCAN_FINDINGS 0`。
- 独立 pre-commit Review：PASS，无阻断问题。

---

## [M4.8] — 账务配额候选安全分层与标准列表 Tool 小批铺开

**周期**: 2026-05-22
**交付**: 在 ACCOUNT/RBAC/GLOBAL 剩余固定分页 Tool 复扫后，按专家会诊结论仅将 2 个低风险组织内列表 Tool 纳入标准 `page/limit/keyword` 契约。

### Changed

- `ResourceUsageListTool`、`QuotaMyListTool` 新增标准 `page/limit/keyword` 参数契约。
- 上述 2 个 Tool 执行层从固定 `page=1&limit=100` 改为 `buildListQuery(params)`，真实透传用户自然语言指定的分页与关键词。
- 上述 2 个 Tool 显式 rethrow `AtlasToolValidationException`，保留 BaseTool 统一结构化错误返回。

### Deferred

- 暂缓 `QuotaReceiveListTool`：审批/待办/RBAC 语义，需先确认后端权限过滤与审计边界。
- 暂缓 `OrderListTool`：订单/账务敏感列表，需先确认租户隔离、可见范围与 keyword 字段语义。
- 暂缓 `CurrencyQueryListTool`、RBAC 管理类、GLOBAL/PUBLIC/NO_ORG、Dashboard/count 与特殊字段类 Tool，后续按专项治理。

### Verified

- 红灯验证：新增契约测试后，`resource_usage_list` 因未声明 `page`、执行层仍固定分页而失败，符合 TDD 预期。
- 定向测试：`mvn -Dtest=ListToolParameterSpecContractTest,ListToolParameterPassThroughContractTest test` → 5 tests, 0 failures, BUILD SUCCESS。
- 全量测试：`mvn test` → 142 tests, 0 failures, BUILD SUCCESS。
- `git diff --check`：通过。
- 新增行敏感信息扫描：`SECRET_SCAN_FINDINGS 0`。
- 独立 pre-commit Review：PASS，无阻断问题。

---

## [M4.7] — 标准列表 Tool 参数契约第五批铺开

**周期**: 2026-05-22
**交付**: 将标准列表参数真实透传模式继续扩展到 2 个 Slurm/上传状态类列表 Tool。

### Changed

- `SlurmClusterListTool`、`UploadStatusListTool` 新增标准 `page/limit/keyword` 参数契约。
- 上述 2 个 Tool 执行层从固定 `page=1&limit=100` 改为 `buildListQuery(params)`，真实透传用户自然语言指定的分页与关键词。
- 上述 2 个 Tool 显式 rethrow `AtlasToolValidationException`，保留 BaseTool 统一结构化错误返回。

### Verified

- 定向测试：`mvn -Dtest=ListToolParameterSpecContractTest,ListToolParameterPassThroughContractTest test` → 5 tests, 0 failures, BUILD SUCCESS。
- 全量测试：`mvn test` → 142 tests, 0 failures, BUILD SUCCESS。
- 独立 pre-commit Review：PASS，无阻断问题。

---

## [M4.6] — 标准列表 Tool 参数契约第四批铺开

**周期**: 2026-05-22
**交付**: 将标准列表参数真实透传模式继续扩展到 7 个课件/消息/GPU/命名空间/数据表/Slurm 类列表 Tool。

### Changed

- `CoursewareListTool`、`DownloadTaskListTool`、`InboxMessageListTool`、`MigConfigListTool`、`NamespaceListTool`、`TableListTool`、`SlurmNodeListTool` 新增标准 `page/limit/keyword` 参数契约。
- 上述 7 个 Tool 执行层从固定 `page=1&limit=100` 改为 `buildListQuery(params)`，真实透传用户自然语言指定的分页与关键词。
- 上述 7 个 Tool 显式 rethrow `AtlasToolValidationException`，保留 BaseTool 统一结构化错误返回。

### Verified

- 定向测试：`mvn -Dtest=ListToolParameterSpecContractTest,ListToolParameterPassThroughContractTest test` → 5 tests, 0 failures, BUILD SUCCESS。
- 全量测试：`mvn test` → 142 tests, 0 failures, BUILD SUCCESS。
- 独立 pre-commit Review：通过，无阻断问题。

---

## [M4.5] — 标准列表 Tool 参数契约第三批铺开

**周期**: 2026-05-22
**交付**: 将标准列表参数真实透传模式继续扩展到 8 个 deploy/实验/Helm/外链类列表 Tool。

### Changed

- `BareMetalAppListTool`、`CloudResourceListTool`、`ComposeListTool`、`ExperimentInstanceListTool`、`ExperimentTemplateListTool`、`ExternalLinkListTool`、`HelmRepoListTool`、`HelmReleaseListTool` 新增标准 `page/limit/keyword` 参数契约。
- 上述 8 个 Tool 执行层从固定 `page=1&limit=100` 改为 `buildListQuery(params)`，真实透传用户自然语言指定的分页与关键词。
- 上述 8 个 Tool 显式 rethrow `AtlasToolValidationException`，保留 BaseTool 统一结构化错误返回。

### Verified

- 定向测试：`mvn -Dtest=ListToolParameterSpecContractTest,ListToolParameterPassThroughContractTest test` → 5 tests, 0 failures, BUILD SUCCESS。
- 全量测试：`mvn test` → 142 tests, 0 failures, BUILD SUCCESS。
- 独立 pre-commit Review：通过，无阻断问题。

---


## [M4.4] — 高频列表 Tool 参数契约第二批铺开

**周期**: 2026-05-22
**交付**: 将 M4.3 的 schema-first 列表参数真实透传模式，从首批 4 个 Tool 扩展到 8 个高频资产/模板类列表 Tool。

### Added

- `BaseTool#listQueryParameterSpecs(String)`：统一生成 page / limit / keyword 参数契约，避免各列表 Tool 复制粘贴后 alias 或描述漂移。
- `ListToolParameterSpecContractTest`、`ListToolParameterPassThroughContractTest` 扩展到 12 个列表 Tool，覆盖第二批 P0 高频列表。

### Changed

- `DataSetListTool`、`ModelListTool`、`FileListTool`、`RegistryListTool`、`TensorBoardListTool`、`JobTemplateListTool`、`TemplateListTool`、`ResourcePresetListTool` 新增标准列表参数契约。
- 上述 8 个 Tool 的执行层从固定 `page=1&limit=100` 改为 `buildListQuery(params)`，真实透传用户传入的 `page/limit/keyword`。
- 上述 8 个 Tool 显式 rethrow `AtlasToolValidationException`，保留 BaseTool.wrapCall 的 `errorCode/suggestions` 结构化错误语义。

### Verified

- 定向测试：`mvn -Dtest=ListToolParameterSpecContractTest,ListToolParameterPassThroughContractTest test` → 5 tests, 0 failures, BUILD SUCCESS。

---

## [M4.3] — 列表 Tool 参数真实透传

**周期**: 2026-05-22
**交付**: M4.2 首批列表 Tool 的 `page/limit/keyword` 从 schema 声明推进到执行层真实消费。

### Added

- `BaseTool#buildListQuery()`：统一构建列表接口 query map，集中处理分页默认值、keyword 空白过滤和严格正整数校验，避免小数 Number 被截断。
- `ListToolParameterPassThroughContractTest`：锁定 `MpiJobListTool`、`PytorchJobListTool`、`FileMaterialListTool`、`GpuDetailListTool` 的参数透传、非法分页、结构化错误返回契约。

### Changed

- 4 个列表 Tool 对 AtlasToolValidationException 显式 rethrow，保留 BaseTool.wrapCall 的 errorCode/suggestions 语义。
- `MpiJobListTool`、`PytorchJobListTool`、`FileMaterialListTool`、`GpuDetailListTool` 从固定 `page=1&limit=100` 改为消费用户传入的 `page/limit/keyword`。

### Verified

- 定向测试：`mvn -Dtest=ListToolParameterPassThroughContractTest,ListToolParameterSpecContractTest,KubeManagerHttpClientUrlContractTest test` → 6 tests, 0 failures。
- 全量测试：`mvn test` → 142 tests, 0 failures, BUILD SUCCESS。

---

## [M4.2] — ReAct 多步成功回归与 URL Query 契约

**周期**: 2026-05-22
**交付**: ReAct 多步成功路径 E2E、KubeManager GET query 构造契约测试、小批列表 Tool 参数契约扩展。

### Added

- `ReActEngineMultiStepE2ETest`：覆盖 `pod_status -> event_query -> Final Answer` 多步成功链路。
- `KubeManagerHttpClientUrlContractTest`：锁定 GET path/query 不混淆、不二次编码。
- `ListToolParameterSpecContractTest`：锁定列表 Tool 的 `page/limit/keyword` 参数契约。
- `MpiJobListTool`、`PytorchJobListTool`、`FileMaterialListTool`、`GpuDetailListTool` 新增分页/关键词参数契约。

### Fixed

- `KubeManagerHttpClient#get()` 改用 `RestClient.uri(builder -> ...)` 构造 path/query，避免 query 被编码进 path 或发生二次编码。

### Verified

- 目标组合测试：`mvn -Dtest=ReActEngineMultiStepE2ETest,KubeManagerHttpClientUrlContractTest,ListToolParameterSpecContractTest test` → 3 tests, 0 failures。
- 全量测试：`mvn test` → 138 tests, 0 failures, BUILD SUCCESS。

---

## [M4.1] — Tool Schema 与参数契约

**周期**: 2026-05-20
**交付**: ReAct/LLM 工具调用参数契约化，schema-first 参数归一化，首批 Tool 参数规格扩展。

### Added

- `ToolParameterSpec`：为每个 Tool 提供 canonical 参数、类型、必填、description、aliases。
- `ToolInputSchemaBuilder`：从参数契约生成 JSON Schema。
- `BaseTool#getParameterSpecs()`：Tool 自描述参数契约入口。
- `ToolParameterNormalizer` schema-first 模式：优先使用 Tool 自身 spec 做 alias 归一化。
- `ToolRegistry.findByName()` 与 `buildSystemPromptForCurrentUser()`：为 ReAct Prompt 提供轻量工具目录。
- `ReActPromptBuilder` 增强：明确要求 LLM 使用 canonical 参数名调用工具。
- 首批 Tool 参数契约：
  - `diagnose_pod`
  - `log_query`
  - `deployment_detail`
  - `node_detail`

### Changed

- `AtlasToolCallback#getToolDefinition()` 使用精确 inputSchema。
- `AtlasToolCallback#call()` 执行前统一经过参数归一化。
- ReAct Prompt 工具目录只展示 canonical 参数，不展开 aliases，避免 Prompt 膨胀。
- `deployment_detail` / `node_detail` 的 URL 查询参数从手拼 `?name=` 改为 query map。
- 后续专项开始清理剩余 `path += "?"` query 拼接点。

### Fixed

- 修复 `deployment_detail` 手拼 query 被 URI builder 编码为 `%253F` 导致 `400 BAD_REQUEST` 的问题。
- 同步修复 `node_detail` 同类潜在问题。
- 首批扩展后验证 `log_query` 的 `podName/lines` 参数链路正确。

### Verified

- 单测：`ToolParameterNormalizerTest`、`ToolRegistryPromptContractTest`、`ToolInputSchemaBuilderTest`、`AtlasToolCallbackTest`。
- 目标测试结果：`Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`。
- 打包：`mvn -DskipTests package` BUILD SUCCESS。
- 真实 SSE E2E：
  - `查询部署实例 aaaa 的详情` → `event:done`，日志确认 `/api/100002/deployment 参数={limit=100, name=aaaa, page=1}`。
  - `查看 pod nginx-not-exist-schema 最近 50 行日志` → `event:done`，日志确认 `podName=nginx-not-exist-schema, lines=50`。

---

## [M3.2] — ReAct 多步诊断引擎 MVP

**周期**: 2026-05-20
**交付**: 手写 ReAct MVP、Graph/Orchestrator 接入、SSE 事件化、真实 E2E 稳定性修复。

### Added

- 手写 `ReActEngine` 主循环。
- `ReActMemory`：Thought/Action/Observation 管理。
- `ReActPromptBuilder`：动态构造 ReAct System Prompt。
- `ReActResult`：统一 ReAct 结果封装。
- `ReActEvent`、`ReActEventSink`、`ReActEventSinkRegistry`：ReAct 生命周期事件输出。
- `BrainDecision.ActionType.DELEGATE_REACT`：AtlasBrain 可将诊断类任务委派给 ReAct。
- StateGraph `react_node`：Graph 内部可执行 ReAct 节点。
- SSE 事件化：thinking、tool_start、tool_done、observation、content、error、done。
- `/react`、`/deep` 强制 ReAct 前缀。
- K8s 故障关键词强制 ReAct 路由。

### Changed

- ReAct 初始参数透传 `userId/token/organizationId/conversationId`。
- Graph State 不再存放运行期对象，改用 registry/sessionId 间接发布事件，避免 checkpoint 序列化污染。
- 高危操作优先进入 HITL，避免被 ReAct 抢占。
- 目标资源不存在时提前收敛，减少无效多轮推理。

### Fixed

- 修复 ReAct SSE E2E 路由不稳定。
- 修复 SSE data 多行 JSON 解析错误。
- 修复 StateGraph checkpoint 序列化 Lambda / 运行期对象异常。
- 修复 ReAct 目标资源不存在时无效多轮循环。
- 修复重复 content 输出问题。

### Verified

- ReAct 相关单测与 Graph 接入测试已多轮通过。
- 真实 SSE E2E 已验证 `/react 诊断 ... CrashLoopBackOff` 等路径。
- 服务健康检查 `/actuator/health` 返回 `UP`。

---

## [M2] — 查询全覆盖与质量加固

**周期**: 2026-05-14 ~ 2026-05-20
**交付**: 前端 9 大模块 109 个 Tool 覆盖，orgId/token 链路修复，查询类 E2E 基础能力。

### Added

- 109 个 `@AtlasToolMapping` Tool，覆盖前端 9 大模块主要按钮/API。
- `BaseTool#resolveOrganizationId()` 统一 orgId 解析。
- `extractData()` 与统一 ToolResult 返回。
- 默认值注册机制，对齐部分前端创建表单默认值。

### Fixed

- 修复登录 API 返回纯 JWT String 时无法从响应体解析 orgId 的问题。
- 修复异步线程中 token/orgId 丢失问题。
- 修复多处硬编码 orgId 导致跨租户数据错乱的风险。

---

## [M1] — 智能引擎与意图全链路

**周期**: 2026-05-14 ~ 2026-05-18
**交付**: L1-L4 意图路由 + AtlasBrain 决策 + StateGraph 编排 + HITL SSE 后端基础。

### Added

- L1 规则意图路由 — 关键词精确匹配，零 token 快速短路。
- L2 Embedding 语义预筛 — all-MiniLM-L6-v2 ONNX Runtime 本地部署。
- L3 LLM 意图分类 — ChatClient 结构化输出分类，失败降级。
- L4 fallback — LLM 不可用时使用规则/模糊兜底。
- AtlasBrain 认知决策中枢。
- StateGraph 编排引擎。
- HITL SSE 流式确认后端基础。
- TimedDecisionCache。
- ThreadLocal Token 透传。

### Security

- `@ToolPermission` + `@Isolation(SYS_ADMIN_ONLY)` + 权限过滤基础。
- HITL 命令式确认基础机制。

---

## [M0] — Atlas v2.x 基线（归档）

**周期**: 2026-05-12 ~ 2026-05-14
**交付**: 23 个 DomainPlugin + SSE 流式 + ChatMemory 持久化 + 权限网关。

### Added

- AtlasOrchestrator v2 编排器。
- DomainRouter 领域路由。
- PermissionGateway 权限网关。
- MessageWindowChatMemory。
- 环境配置分离。
- SSE 流式基础。
- MCP Server 初版探索。

---

## 后续计划

- `[M4.1 持续]` ToolParameterSpec 分批覆盖 detail/query/diagnose 类 Tool。
- `[M4.2]` Tool Prompt 长度预算与按 agent/意图裁剪。
- `[M4.3]` ReAct 多步成功路径 E2E 与真实存在资源诊断验证。
- `[M5]` Memory / MCP / Observability / Guardrails。

---

## 参考

- 完整路线图见 `ROADMAP.md`。
- 架构审计报告见 `ARCHITECTURE_AUDIT_20260518.md`。
- 文档治理方案见 `DOCUMENTATION_GOVERNANCE_REPORT.md`。
- 会话连续性快照见 `docs/会话上下文快照_20260520.md`。
