# M5.21-98 NIM Readiness HTTP Adapter Placeholder-Aware Secret Detector Migration Audit - 2026-06-08

## Scope

This wave migrates the readiness HTTP adapter local secret scanner to the shared NIM forbidden secret material detector:

- `src/main/java/com/atlas/tool/impl/NimCreateReadinessHttpAdapterSupport.java`
- `src/test/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetectorUsageContractTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateReadinessHttpAdapterSupportTest.java`

The adapter remains a request-spec compiler only. It produces future read-only GET specs from the readiness plan; it does not execute HTTP, does not call kube-manager `8100`, does not call a real NIM service, and does not send Authorization headers or real API keys.

## What Changed

- `NimCreateReadinessHttpAdapterSupport` now delegates secret-material scanning to `NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(...)`.
- The adapter uses `textValuePolicyAllowing(Set.of(NimCreateReadinessExecutorSupport.API_KEY_PLACEHOLDER))` to preserve the fixed non-secret placeholder exception.
- The local adapter `FORBIDDEN_SECRET_KEYS`, `looksLikeSecretValue(...)`, and `normalizeKey(...)` copy was removed.
- `NimForbiddenSecretMaterialDetectorUsageContractTest` now prevents the adapter from reintroducing local detector drift.
- Adapter tests now prove the fixed API-key placeholder is allowed only outside forbidden secret keys, while `token=<placeholder>` and real Bearer/API-key material remain rejected.

## Policy Comparison

- Old adapter behavior allowed exactly `Bearer {input your NGC_API_KEY here}` as a placeholder string.
- The new shared policy allows that same placeholder only through an explicit call-site allowlist.
- Forbidden-key values such as `token`, `Authorization`, `password`, `secret`, and API-key variants remain rejected when non-blank.
- Real secret-looking strings such as `Bearer real-key-material`, cloud key patterns, GitHub tokens, Slack tokens, and API-key/token/password assignments remain rejected.
- Service URLs containing bearer/API-key material still reject before any NIM request spec is produced.
- Nested `Map` and `List` values remain recursively scanned.

## What Did Not Change

- Blocker code stayed unchanged:
  - `READINESS_ADAPTER_CONTAINS_FORBIDDEN_SECRET`
- Adapter output remains `REQUEST_SPEC_CONTRACT_ONLY`.
- Adapter remains read-only and poll-only.
- Request specs still disable request bodies, headers, Authorization headers, and real API keys.
- No real HTTP execution was added.
- No kube-manager `8100` access was added.
- No NIM service call was added.
- No deployment `POST /api/{orgId}/deployment` was added.
- `nim_create` remains `HOLD` / mock-first / placeholder.

## Multi-Expert Review Notes

- Backend/API lens: no controller, bean, client, or network path changed. The adapter still only compiles offline request specs.
- Security/RBAC lens: the placeholder exception is explicit and narrow; secret-bearing keys and real secret-looking strings fail closed.
- Agent architecture lens: readiness HTTP adapter output is not execution authority. It only prepares a future read-only executor contract.
- Test architecture lens: usage contract prevents local blacklist drift; adapter tests lock placeholder compatibility and service URL secret rejection.
- Learning lens: even harmless placeholders need named policy. The system should make exceptions visible to reviewers instead of hiding them in per-class helper code.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimForbiddenSecretMaterialDetectorUsageContractTest,NimForbiddenSecretMaterialDetectorTest,NimCreateReadinessHttpAdapterSupportTest,NimCreateReadinessExecutorSupportTest,NimCreateAuditReadinessSupportTest,NimCreateStateMachineSupportTest" test
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
- No request headers or request bodies in adapter-generated specs.
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

- Continue scanning the remaining older NIM support shells with local detector copies only after explicit old/new policy comparison.
- Higher-value next options: compare `NimCreateAuditWriterSupport` or return to reviewed durable writer/probe boundary design without opening writes.
