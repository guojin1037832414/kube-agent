# M5.21-119 NIM Readiness Target Taxonomy Closed List Audit - 2026-06-08

## Scope

This wave hardens NIM readiness target taxonomy validation across the read-only readiness path:

- `NimCreateReadinessExecutorSupport`
- `NimCreateReadinessHttpAdapterSupport`
- `NimCreateStateMachineSupport`

All three consumers now reject a readiness plan whose `targets` list is a superset of the reviewed readiness targets.

Touched production files:

- `src/main/java/com/atlas/tool/impl/NimCreateReadinessExecutorSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateReadinessHttpAdapterSupport.java`
- `src/main/java/com/atlas/tool/impl/NimCreateStateMachineSupport.java`

Touched tests:

- `src/test/java/com/atlas/tool/impl/NimCreateReadinessExecutorSupportTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateReadinessHttpAdapterSupportTest.java`
- `src/test/java/com/atlas/tool/impl/NimCreateStateMachineSupportTest.java`

## What Changed

- Replaced readiness target superset checks with exact equality checks.
- The only reviewed readiness targets remain:
  - `deployment`
  - `service`
  - `nim-health`
  - `nim-models`
- Added regressions that append `nim-chat` to `targets` while leaving the audited readiness steps unchanged.
- The executor rejects the forged target superset with `READINESS_PLAN_NOT_EXECUTABLE`.
- The HTTP adapter rejects the forged target superset before building any request specs.
- The state machine holds the write path with `READINESS_PLAN_NOT_READY`.

## Why This Matters

Readiness `targets` are not harmless display metadata. They define the reviewed aftercare taxonomy that future write release may depend on. A plan that adds `nim-chat`, `embedding`, or another unreviewed target without matching reviewed code could make downstream components believe extra readiness dimensions exist or were planned.

A top-tier Agent treats such protocol lists as closed source-owned taxonomies. New readiness targets require reviewed implementation, tests, documentation, and release governance.

## Multi-Expert Review Notes

- Backend/API lens: no kube-manager client, `8100`, controller, Tool registration, or deployment POST was added.
- Frontend/product lens: the closed targets still match the mature NIM aftercare flow: deployment lookup, service entrance derivation, health live, and model readback.
- Security/RBAC lens: unreviewed target expansion is rejected even when the extra value looks read-only.
- Agent architecture lens: readiness plans now behave like protocol contracts, not caller-extensible task lists.
- Test architecture lens: tests mutate only `targets`, not `steps`, proving the regression covers metadata-only expansion.
- Learning lens: exact equality is often the right choice for safety taxonomies; `containsAll` is suitable for capabilities, not for release-proof protocols.

## Verification

Passed:

```bash
mvn -q "-Dtest=NimCreateReadinessExecutorSupportTest,NimCreateReadinessHttpAdapterSupportTest,NimCreateStateMachineSupportTest" test
```

Final verification passed:

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

- Continue scanning for any remaining proof taxonomies that use superset acceptance.
- Start the next reviewed release-binding proof slice without opening real writes.
- Later, factor shared source-owned readiness target constants if another reviewed readiness target is introduced.
