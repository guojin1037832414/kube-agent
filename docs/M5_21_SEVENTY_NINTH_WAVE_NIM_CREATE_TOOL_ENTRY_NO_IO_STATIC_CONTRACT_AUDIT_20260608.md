# M5.21-79 NIM create Tool entry no-I/O static contract audit

> Scope: this wave hardens the public `nim_create` Tool entry. It removes the unused HTTP client dependency from the Tool entry and adds a source-level guard that keeps the entry `NONE + PLACEHOLDER` until the reviewed NIM write chain is explicitly released.

## Background

M5.21-49 to M5.21-78 built increasingly strict inner guards for the future NIM write path: state machine, durable writer, storage probe, validation result, release decision, code switch, source guard, and static source contracts.

The public `NimCreateTool` entry already returned fail-closed and declared:

- `httpMethod=NONE`
- `apiEndpoints={}`
- `operationType=PLACEHOLDER`
- `requiresConfirmation=true`

However, it still accepted a `KubeManagerHttpClient` constructor dependency even though the dependency was unused. That shape is risky for a top-tier Agent project because future edits could quietly turn the placeholder entry into a real HTTP caller before the reviewed NIM write chain is ready.

## Delivered Changes

- Removed the unused `KubeManagerHttpClient` dependency from `NimCreateTool`.
- `NimCreateTool` is still a Spring `@Component`, but its constructor is now no-arg and has no runtime I/O client.
- Updated `HighRiskMutationToolHttpContractTest` to construct `new NimCreateTool()` and still verify the placeholder entry fails closed.
- Added `M521NimCreateToolEntryStaticContractTest`.
- The static contract asserts the public Tool entry still:
  - maps `name="nim_create"`
  - maps `intentId="nim_create"`
  - declares `httpMethod="NONE"`
  - declares `apiEndpoints={}`
  - declares `operationType=PLACEHOLDER`
  - requires confirmation
  - remains authenticated only
  - calls `NimCreateStateMachineSupport.evaluateCurrentPlaceholderHold(params)`
  - returns `AtlasToolResult.fail(...)`
  - returns the state-machine report under `KEY_DATA`
- The contract statically rejects:
  - `KubeManagerHttpClient`
  - `httpClient`
  - HTTP mutation calls
  - runtime env/property shortcuts
  - Spring injection shortcuts beyond the Tool component itself
  - Elasticsearch / `ISysLogService` / `sys_log`
  - kube-manager `8100`
  - direct success-state writes

## Security Boundary

This wave does not open `nim_create`.

Still absent:

- no real `POST /api/{orgId}/deployment`
- no HTTP client in `NimCreateTool`
- no real `8100`
- no durable writer
- no storage probe
- no durable receipt
- no release decision
- no code release switch
- no Spring write executor registration

## Verification

Passed:

```bash
mvn -q "-Dtest=M521NimCreateToolEntryStaticContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,ToolRegistryPermissionTest" test
mvn -q "-Dtest=M521NimCreateToolEntryStaticContractTest,M521NimDurableAuditWriterProbeBoundaryStaticContractTest,M521NimRuntimeSourceGuardBindingContractTest,HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,ToolRegistryPermissionTest" test
git diff --check
mvn -q test
```

Full test note: `ToolRegistryPermissionTest` starts Spring and logs local `KubeManagerHttpClient` initialization for the wider app context, but `NimCreateTool` itself no longer receives or stores that client.

Full test note: local `model.onnx` download timed out and Atlas degraded to L1 embedding mode, but Maven exited 0. This remains an accepted degraded-test-path signal, not an M5.21-79 failure.

## Expert Review

- Backend/API reviewer: accepted. Public `nim_create` entry no longer has an unused client that could become a direct POST path.
- Security reviewer: accepted. The entry can expose a fail-closed Tool affordance without holding a mutation client.
- Agent architecture reviewer: accepted. The Tool entry now mirrors its declared manifest shape: placeholder in metadata and placeholder in runtime dependencies.
- Learning reviewer: accepted. This shows a useful Agent design principle: remove unused dangerous dependencies, not only unused code paths.

## Learning Note

For high-risk Agent tools, "not used today" is weaker than "not injectable." A placeholder Tool should not own an HTTP or storage dependency until the audited execution boundary is ready. This keeps the public affordance, manifest, constructor shape, and runtime behavior aligned.
