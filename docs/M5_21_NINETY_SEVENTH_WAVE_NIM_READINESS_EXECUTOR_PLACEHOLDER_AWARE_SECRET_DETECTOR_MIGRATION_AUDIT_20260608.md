# M5.21-97 NIM Readiness Executor Placeholder-Aware Secret Detector Migration Audit - 2026-06-08

## Scope

This wave migrates one older readiness-chain local secret scanner to the shared NIM forbidden secret material detector while preserving its special placeholder rule:

- `src/main/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetector.java`
- `src/main/java/com/atlas/tool/impl/NimCreateReadinessExecutorSupport.java`
- `src/test/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetectorTest.java`
- `src/test/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetectorUsageContractTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateReadinessExecutorSupportTest.java`

The readiness executor is still an offline/read-only contract evaluator. It does not call kube-manager `8100`, does not call a real NIM service, and does not send Authorization headers or real API keys.

## What Changed

- Added `NimForbiddenSecretMaterialDetector.textValuePolicyAllowing(Set<String>)` so call sites can explicitly allow known non-secret placeholders that look like secret material.
- `NimCreateReadinessExecutorSupport` now delegates secret-material scanning to `NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(...)`.
- The old local readiness executor `FORBIDDEN_SECRET_KEYS`, `looksLikeSecretValue(...)`, and `normalizeKey(...)` copy was removed.
- `NimForbiddenSecretMaterialDetectorUsageContractTest` now prevents `NimCreateReadinessExecutorSupport` from reintroducing a local forbidden secret key/value scanner.
- Tests now prove the fixed API-key placeholder is allowed only outside forbidden secret keys, while real bearer material and placeholder-under-`token` remain rejected.

## Policy Comparison

- Old readiness executor behavior allowed exactly `Bearer {input your NGC_API_KEY here}` as a placeholder string.
- The new shared policy allows that same placeholder only through a call-site-supplied allowlist.
- Forbidden-key values such as `token`, `Authorization`, `password`, `secret`, and API-key variants remain rejected when non-blank.
- Real secret-looking strings such as `Bearer real-key-material`, cloud key patterns, GitHub tokens, Slack tokens, and API-key/token/password assignments remain rejected.
- Nested `Map` and `List` values remain recursively scanned.

## What Did Not Change

- Blocker code stayed unchanged:
  - `READINESS_CONTAINS_FORBIDDEN_SECRET`
- Readiness executor output remains `OFFLINE_CONTRACT_EVALUATION`.
- Readiness stays read-only and poll-only.
- No real HTTP execution was added.
- No kube-manager `8100` access was added.
- No NIM service call was added.
- No deployment `POST /api/{orgId}/deployment` was added.
- `nim_create` remains `HOLD` / mock-first / placeholder.

## Multi-Expert Review Notes

- Backend/API lens: no controller, bean, client, or network path changed. This is pure support-layer contract code.
- Security/RBAC lens: the placeholder exception is explicit and narrow; it cannot authorize real API-key handling and does not allow placeholder text under secret-bearing keys.
- Agent architecture lens: readiness evidence remains post-write readback evidence only. It cannot become release authority or a write permit.
- Test architecture lens: detector-level tests lock the allowlist policy, while readiness executor tests lock the call-site boundary.
- Learning lens: exceptions should be named and parameterized, not hidden in another local blacklist. A top-tier Agent makes compatibility exceptions reviewable.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimForbiddenSecretMaterialDetectorUsageContractTest,NimForbiddenSecretMaterialDetectorTest,NimCreateReadinessExecutorSupportTest,NimCreateAuditReadinessSupportTest,NimCreateStateMachineSupportTest" test
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
- No deployment `POST /api/{orgId}/deployment`.
- No runtime write behavior opened.
- No state-machine release binding implementation added.
- No durable executor release binding implementation added.
- No validation result signer or release decision signer added.
- No code release switch implementation added.
- No Elasticsearch, `ISysLogService`, or `sys_log` write added.
- `nim_create` remains `HOLD` / mock-first / placeholder.

## Follow-Up Candidates

- Consider migrating `NimCreateReadinessHttpAdapterSupport` to the same placeholder-aware shared policy after a separate policy comparison.
- Continue small, test-backed detector cleanup only where old/new semantics are explicitly compared.
