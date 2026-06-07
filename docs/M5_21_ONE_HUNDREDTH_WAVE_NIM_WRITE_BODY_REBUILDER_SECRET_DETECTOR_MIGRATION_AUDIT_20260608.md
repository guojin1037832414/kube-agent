# M5.21-100 NIM Write Body Rebuilder Secret Detector Migration Audit - 2026-06-08

## Scope

This wave migrates the controlled NIM write body rebuilder secret-material scanner to the shared NIM forbidden secret material detector:

- `src/main/java/com/atlas/tool/impl/NimCreateWriteBodyRebuilderSupport.java`
- `src/test/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetectorUsageContractTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateWriteBodyRebuilderSupportTest.java`

The rebuilder remains a pure contract component. It rebuilds a future DeploymentDTO body from audited state only; it does not execute HTTP, does not call kube-manager `8100`, does not call a real NIM service, and does not perform `POST /api/{orgId}/deployment`.

## What Changed

- `NimCreateWriteBodyRebuilderSupport` now delegates secret-material scanning to `NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(...)`.
- The rebuilder uses `NimForbiddenSecretMaterialDetector.textValuePolicy()` because controlled write body inputs and rebuilt body output must not carry real credential material.
- The local `FORBIDDEN_SECRET_KEYS`, local `looksLikeSecretValue(...)`, and local recursive secret scanner were removed.
- The body allowlist still checks forbidden field names through the shared `NimForbiddenSecretMaterialDetector.isForbiddenSecretKey(...)`.
- `PROTECTED_CONTEXT_KEYS` and `isProtectedContextKey(...)` intentionally remain local because protected identity/audit/context stripping is a different safety category from credential leakage detection.
- `NimForbiddenSecretMaterialDetectorUsageContractTest` now prevents the rebuilder from reintroducing local detector drift and locks the call site to `textValuePolicy()`.
- Rebuilder tests now prove plain `Authorization: present`, `token=false`, forbidden-key collection values, list-carried `Authorization=Bearer ...` metadata, and allowlisted body `commands` carrying `Authorization=Bearer ...` all fail closed.

## Policy Comparison

- Old rebuilder behavior rejected non-blank values under forbidden keys, including Boolean/Number values such as `token=false`.
- The shared `textValuePolicy()` preserves that behavior.
- Despite the policy name, this is not text-only for forbidden keys: Boolean/Number/collection values under forbidden keys are still rejected when their string form is non-blank.
- Old rebuilder behavior rejected secret-looking strings such as Bearer headers, `token=...`, `secret=...`, `password=...`, cloud key shapes, GitHub tokens, Slack tokens, NGC/NVAIE markers, and API-key assignments.
- The shared detector preserves those value checks and keeps recursive `Map` / `List` scanning, including `List<String>` secret-like values.
- The migration is intended to be policy-equivalent for secret-material scanning. The only structural change is that the field-name and value matching now live in one shared utility.
- `nonBooleanNumberValuePolicy()` / `receiptSchemaPolicy()` would incorrectly allow forbidden-key Boolean/Number values, while `strictRecursivePolicy()` does not match this rebuilder's old collection semantics. They are intentionally not used here.
- Protected context stripping remains separate: `organizationId`, `userId`, `conversationId`, `requestId`, audit objects, HITL confirmation, creation gate, readiness reports, and similar identity/control-plane fields are not "secrets" but are still forbidden inside the future write body.

## What Did Not Change

- Blocker codes stayed unchanged:
  - `WRITE_BODY_REBUILD_INPUT_CONTAINS_FORBIDDEN_SECRET`
  - `WRITE_BODY_CONTAINS_FORBIDDEN_SECRET`
  - `WRITE_BODY_CONTAINS_FORBIDDEN_FIELD`
- Valid sanitized input still produces `writeBodyPrepared=true` and a digest-bound rebuilt body.
- Invalid or secret-bearing input still produces `writeBodyPrepared=false`, empty body, and empty `bodyDigest`.
- The rebuilder still strips protected context and only copies allowlisted DeploymentDTO fields.
- No request-spec execution was added.
- No durable write executor implementation was added.
- No real storage, HTTP, or NIM execution path was added.
- No deployment `POST /api/{orgId}/deployment` was added.
- `nim_create` remains `HOLD` / mock-first / placeholder.

## Multi-Expert Review Notes

- Backend/API lens: no controller, bean, HTTP client, `8100` integration, or persistence boundary changed.
- Security/RBAC lens: credential-leak detection is centralized, while protected context stripping remains local and explicit.
- Agent architecture lens: body rebuilding is not execution authority; it only prepares a digest-bound body contract for later gates.
- Test architecture lens: static usage contracts prevent local blacklist drift, and behavior tests lock `textValuePolicy()` semantics against accidental policy swaps.
- Learning lens: a top-tier Agent separates "this value is a credential" from "this field is caller-controlled authority/context." Both can be dangerous, but they deserve different detectors.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimForbiddenSecretMaterialDetectorUsageContractTest,NimForbiddenSecretMaterialDetectorTest,NimCreateWriteBodyRebuilderSupportTest,NimCreateStateMachineSupportTest,NimCreateWriteRequestSpecAdapterSupportTest,NimCreateWriteExecutionHandoffSupportTest" test
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

- Migrate the remaining large `NimCreateStateMachineSupport` local secret scanner only after a separate policy comparison, because it spans many input surfaces and forged-claim checks.
- Keep resolving NIM write-chain safety through small, reviewable slices without opening real writes.
