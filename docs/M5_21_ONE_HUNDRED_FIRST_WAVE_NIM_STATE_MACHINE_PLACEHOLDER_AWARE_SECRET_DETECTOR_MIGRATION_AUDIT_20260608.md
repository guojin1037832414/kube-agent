# M5.21-101 NIM State Machine Placeholder-Aware Secret Detector Migration Audit - 2026-06-08

## Scope

This wave migrates the large NIM create state-machine secret-material scanner to the shared NIM forbidden secret material detector:

- `src/main/java/com/atlas/tool/impl/NimCreateStateMachineSupport.java`
- `src/test/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetectorUsageContractTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateStateMachineSupportTest.java`

The state machine remains a pure guard. It evaluates readiness of future write prerequisites, but it does not call kube-manager `8100`, does not call a real NIM service, does not send Authorization headers, and does not execute `POST /api/{orgId}/deployment`.

## What Changed

- `NimCreateStateMachineSupport` now delegates credential-material scanning to `NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(...)`.
- The local `FORBIDDEN_SECRET_KEYS`, local `looksLikeSecretValue(...)`, and local recursive secret scanner were removed.
- The state machine uses `NimForbiddenSecretMaterialDetector.textValuePolicyAllowing(Set.of(NimCreateReadinessExecutorSupport.API_KEY_PLACEHOLDER))`.
- `NimForbiddenSecretMaterialDetectorUsageContractTest` now covers the state machine in the no-local-copy shared-detector contract and locks it away from `receiptSchemaPolicy()`, `nonBooleanNumberValuePolicy()`, and `strictRecursivePolicy()`.
- State-machine tests now prove:
  - the fixed readiness API-key placeholder can appear in a non-forbidden documentation/hint position;
  - `refreshToken` suffix keys reject;
  - Boolean values under forbidden secret keys reject;
  - list-carried `Authorization=Bearer ...` values reject;
  - forbidden-key placeholder values and `token=<placeholder>` assignment strings reject.
  - audit receipt, write body rebuild, request spec, execution handoff, durable executor, code release switch, and runtime source guard reports keep their secret-material blocker coverage.

## Policy Comparison

- Old state-machine behavior rejected non-blank values under exact forbidden keys such as `token`, `secret`, `password`, `authorization`, `authHeader`, and `bearerToken`.
- The shared `textValuePolicyAllowing(...)` preserves non-blank forbidden-key rejection, including Boolean/Number values such as `token=false`.
- The placeholder allowance is deliberately narrow: only the exact fixed value `Bearer {input your NGC_API_KEY here}` is allowed when it appears as a non-secret-looking documentation value outside forbidden keys.
- The allowance does not permit:
  - real Bearer tokens;
  - real API keys;
  - `token=<placeholder>` assignment strings;
  - the placeholder under a forbidden key such as `token`;
  - `Authorization=Bearer ...` list or nested values.
- This is a deliberate hardening migration, not a loose compatibility refactor. The shared detector also catches suffix-style keys such as `refreshToken` and assignment-like strings such as `Authorization=Bearer ...` that the older local state-machine scanner did not fully cover.
- `receiptSchemaPolicy()` would be wrong here because the state machine is validating runtime guard inputs, not documenting forbidden field names.
- `nonBooleanNumberValuePolicy()` would be wrong here because `token=false` and similar forbidden-key scalars must still fail closed.
- `strictRecursivePolicy()` would change the old state-machine value semantics and is not the selected compatibility target.

## What Did Not Change

- Blocker codes stayed unchanged, including:
  - `AUDIT_CONTEXT_CONTAINS_FORBIDDEN_SECRET`
  - `AUDIT_RECEIPT_CONTAINS_FORBIDDEN_SECRET`
  - `READINESS_PLAN_CONTAINS_FORBIDDEN_SECRET`
  - `READINESS_EXECUTION_REPORT_CONTAINS_FORBIDDEN_SECRET`
  - write-chain and release/source-guard secret blockers.
- The state machine still requires server-side HITL, trusted policy, audit context, durable receipt, controlled body rebuild, request spec, handoff, durable executor report, code release switch contract report, runtime source guard report, readiness plan, and readiness execution report before any future write can even be considered.
- Forged release/write/source-guard claim scanners remain separate authority guards. They are not credential-material detectors and should not be collapsed into the shared secret detector.
- No request execution was added.
- No durable write executor implementation was added.
- No real storage, HTTP, or NIM execution path was added.
- No deployment `POST /api/{orgId}/deployment` was added.
- `nim_create` remains `HOLD` / mock-first / placeholder.

## Multi-Expert Review Notes

- Backend/API lens: no controller, bean, HTTP client, `8100` integration, storage layer, or persistence boundary changed.
- Security/RBAC lens: state-machine inputs now share the same credential detector as the rest of the NIM write chain while preserving a narrow non-secret placeholder exception.
- Agent architecture lens: placeholder-aware scanning supports learning/demo readiness artifacts without turning examples into authority or credentials.
- Test architecture lens: usage-contract tests prevent local scanner drift, and behavior tests pin both the placeholder exception and the deliberate hardening cases.
- Learning lens: a top-tier Agent should distinguish examples/placeholders from secrets, while still rejecting the same text when it is attached to a forbidden field or assignment shape.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimForbiddenSecretMaterialDetectorUsageContractTest,NimForbiddenSecretMaterialDetectorTest,NimCreateStateMachineSupportTest,NimCreateAuditReadinessSupportTest,NimCreateAuditWriterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateReadinessHttpAdapterSupportTest" test
```

Passed:

```bash
git diff --check
mvn -q test
```

Full Maven note: local `model.onnx` download timed out and Atlas degraded to L1 embedding mode, but Maven exited 0.

## Security Invariants

- No real `8100` access.
- No real NIM service HTTP call.
- No Authorization header sending.
- No real API key generation, storage, display, or forwarding.
- No durable audit table write.
- No Elasticsearch, `ISysLogService`, or `sys_log` write.
- No deployment `POST /api/{orgId}/deployment`.
- No runtime write behavior opened.
- No state-machine release binding implementation added.
- No durable executor release binding implementation added.
- No validation result signer or release decision signer added.
- No code release switch implementation added.
- `nim_create` remains `HOLD` / mock-first / placeholder.

## Follow-Up Candidates

- Continue eliminating remaining local secret scanner copies only after policy comparison per call site.
- Keep real `nim_create` write release blocked until durable audit writer, receipt validation, release decision, code switch, runtime binding, durable executor, and post-write readiness proof are implemented and separately reviewed.
