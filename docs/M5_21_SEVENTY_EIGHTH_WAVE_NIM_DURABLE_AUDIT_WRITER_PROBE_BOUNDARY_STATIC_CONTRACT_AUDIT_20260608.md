# M5.21-78 NIM durable audit writer/probe boundary static contract audit

> Scope: this wave hardens the future durable audit writer/probe boundary without opening any real write, storage, HTTP, Spring, `8100`, Elasticsearch, `ISysLogService`, or `sys_log` path.

## Background

M5.21-52 to M5.21-67 built a contract-only chain for durable audit storage, writer planning, dedicated writer boundary, storage availability gate, storage probe executor, storage probe result, and receipt validation binding.

M5.21-76 and M5.21-77 then protected release-switch source-guard binding in the current state-machine and durable-executor shells. The next useful gap is closer to the future real durable writer: if a caller hides `storageAvailable=true`, `DURABLE_RECORDED`, or similar success claims inside nested diagnostic maps/lists, the dedicated writer boundary must reject those claims before preparing any boundary plan.

## Delivered Changes

- Hardened `NimCreateDedicatedDurableAuditWriterBoundarySupport`.
- `hasForgedSuccessClaim` now recursively scans nested maps and list items, matching the stricter style already used by `NimCreateDurableAuditStorageProbeExecutorSupport`.
- Added a behavior regression test:
  - `boundary_shouldRejectNestedForgedStorageAndReceiptClaims`
  - It proves nested `storageAvailable=true` and list-item `receiptStatus=DURABLE_RECORDED` are rejected.
- Added `M521NimDurableAuditWriterProbeBoundaryStaticContractTest`.
- The static contract reads production source files only; it first focuses on the two direct future I/O boundary shells:
  - `NimCreateDedicatedDurableAuditWriterBoundarySupport.java`
  - `NimCreateDurableAuditStorageProbeExecutorSupport.java`
- It also scans the wider M5.21 durable audit/release chain for source-level drift:
  - storage candidate
  - writer plan
  - availability gate
  - writer interface spec
  - receipt schema
  - storage probe result
  - probe-result validation binding
  - enhanced validation migration
  - validation result contract
  - release decision contract
  - code release switch contract
  - runtime source guard
  - durable write executor
  - state machine
- It asserts both shells remain HOLD-only and continue to expose false state for:
  - `realStorageTouched`
  - `storageProbeExecuted`
  - `storageAvailable`
  - `preWritePersisted`
  - `postWritePersisted`
  - `durableReceiptCanBeIssued`
  - `durableReceiptIssued`
  - `writeExecutionAllowed`
  - `realHttpExecutionAllowed`
- It asserts digest binding remains in place for writer plan, availability plan, boundary plan, audit event, and source boundary evidence.
- It asserts the cross-chain digest vocabulary remains present from `storagePlanDigest` through `sourceGuardMatrixDigest`.
- It asserts forged-claim blocker names remain present across availability/probe/receipt-validation/release/switch/source-guard layers.
- It statically rejects shortcuts:
  - environment/property reads
  - Spring component/bean/controller injection
  - HTTP client binding
  - Elasticsearch runtime client binding
  - `ISysLogService` injection
  - direct `.saveSysLog(` or `.save(` calls
  - kube-manager `8100`
  - direct success-state `result.put(..., true)`

## Security Boundary

This wave does not create a real durable writer or storage probe. It only tightens the current boundary:

- no real `8100`
- no real `POST /api/{orgId}/deployment`
- no HTTP client
- no Spring registration
- no Elasticsearch client
- no `ISysLogService`
- no `sys_log` write
- no durable receipt
- no write release

`sys_log` remains only a mature storage candidate described in the contract and documentation. It is not used as a live persistence path here.

## Verification

Passed:

```bash
mvn -q "-Dtest=M521NimDurableAuditWriterProbeBoundaryStaticContractTest,NimCreateDedicatedDurableAuditWriterBoundarySupportTest,NimCreateDurableAuditStorageProbeExecutorSupportTest" test
mvn -q "-Dtest=M521NimDurableAuditWriterProbeBoundaryStaticContractTest,NimCreateDedicatedDurableAuditWriterBoundarySupportTest,NimCreateDurableAuditStorageProbeExecutorSupportTest,NimCreateDurableAuditStorageAvailabilityGateSupportTest,NimCreateDurableAuditStorageProbeResultSupportTest,NimCreateDurableAuditReceiptValidationProbeResultBindingSupportTest" test
git diff --check
mvn -q test
```

Full test note: local `model.onnx` download timed out and Atlas degraded to L1 embedding mode, but Maven exited 0. This remains an accepted degraded-test-path signal, not an M5.21-78 failure.

## Expert Review

- Safety reviewer: accepted. The wave closes a concrete bypass class: nested/list success claims in boundary inputs.
- Architecture reviewer: accepted. The static contract forces future real writer/probe integration to be deliberate and reviewable.
- Learning reviewer: accepted. This is a good example of contract-first Agent engineering: before introducing real side effects, protect the boundary against both behavior drift and source-level drift.

## Learning Note

A top-tier Agent write path cannot trust "success-shaped" data just because it appears in a diagnostic object. Success claims must be server-issued, typed, digest-bound, and produced by the reviewed boundary that owns the side effect. Until that boundary exists, nested caller evidence is still caller evidence and must fail closed.
