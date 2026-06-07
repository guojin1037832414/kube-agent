# M5.21-102 NIM Secret Detector Global Drift Contract Audit - 2026-06-08

## Scope

This wave adds a global static contract for NIM create support classes that use secret-material scanning:

- `src/test/java/com/atlas/tool/core/NimForbiddenSecretMaterialDetectorUsageContractTest.java`

No production code was changed in this wave. The goal is to prevent future local forbidden-key/value matcher drift after the M5.21-86 through M5.21-101 shared detector migration work.

## What Changed

- Added `allNimCreateSecretScannerSources_shouldUseSharedDetectorWithoutLocalMatcherDrift()`.
- The test dynamically walks `src/main/java/com/atlas/tool/impl`.
- It selects every `NimCreate*.java` source that contains `containsForbiddenSecretMaterial(`.
- For every discovered source, it asserts:
  - the source delegates to `NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial`;
  - the source does not define or carry local `FORBIDDEN_SECRET_KEYS`;
  - the source does not define local `looksLikeSecretValue(`;
  - the source does not define local `isForbiddenSecretKey(`;
  - the source does not define local `secretBearingValue(`;
  - the source does not reintroduce documented-field-name secret scanner forks.

## Why This Matters

- The existing hand-written policy groups still matter because they lock exact call-site policy choices such as:
  - `textValuePolicy()`;
  - `textValuePolicyAllowing(API_KEY_PLACEHOLDER)`;
  - `nonBooleanNumberValuePolicy()`;
  - `receiptSchemaPolicy()`;
  - `strictRecursivePolicy()`.
- The new dynamic scanner contract guards a different risk: a future `NimCreate*Support` class can no longer add a local scanner and accidentally stay outside the hand-written migrated-support list.
- This keeps the top-tier Agent learning artifact honest: shared safety primitives must be enforced by tests that cover both known policy decisions and future drift.

## Multi-Expert Review Notes

- Backend/API lens: no controller, HTTP client, kube-manager `8100` path, storage, or runtime behavior changed.
- Security/RBAC lens: this is a test-only guard that prevents credential detector forks from returning through new support classes.
- Agent architecture lens: centralizing detector behavior makes the NIM write chain easier to reason about and audit.
- Test architecture lens: hand-written lists express policy intent; dynamic discovery catches omissions.
- Learning lens: mature Agent engineering uses tests not only to prove current behavior, but also to constrain future extension patterns.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimForbiddenSecretMaterialDetectorUsageContractTest,NimForbiddenSecretMaterialDetectorTest" test
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

- Continue shrinking duplicated safety checks where they are truly duplicate, but keep authority/forgery guards separate from credential detectors.
- Consider a similar dynamic contract for protected-context stripping once the remaining call sites are fully categorized.
