# M5.37 Eval Trace Set Catalog Learning Note

## Why This Slice Matters

M5.37 adds the missing evidence-catalog layer between named eval suites and compact CI gate artifacts.

A top-tier Agent eval system should not rely on an operator remembering which trace IDs to paste into a request. It needs typed, versioned, reviewable evidence sets that CI, frontend workbenches, release workflows, and human reviewers can discuss by stable names.

## New Backend Contracts

- `AgentEvalTraceSetDefinition`: metadata for a curated golden/red-team trace set.
- `AgentEvalTraceSetCatalogResponse`: admin-only catalog response.
- `AgentEvalTraceSetGateArtifact`: compact trace-set gate artifact that wraps a compact suite gate artifact.
- `AgentEvalTraceSetCatalogService`: loads the versioned catalog and runs trace-set gates.
- `src/main/resources/observability/eval-trace-sets.json`: versioned classpath source.

Admin-only endpoints:

- `GET /api/agent/observability/eval/trace-sets`
- `POST /api/agent/observability/eval/trace-sets/{traceSetId}/gate`

## Built-In Trace Sets

- `phase1-core-golden`
- `phase1-redaction-regression`
- `phase1-high-risk-prewrite`
- `phase1-red-team-safety`

These entries intentionally start with empty `traceIds`. Empty curated evidence fails closed, which is safer than pretending placeholder trace IDs are real replay captures.

## Architecture Pattern

```text
Suite Definition
    = quality standard

Trace Set Definition
    = curated evidence source

Gate Artifact
    = machine-readable release decision
```

This separation prevents authority confusion:

- A suite does not discover raw audit data.
- A trace set does not execute Tools.
- A gate artifact does not embed frontend-sized replay/debug payloads.
- CI consumes compact verdicts and trace anchors, then humans drill down through admin replay/eval APIs when needed.

## Safety Rules

- Admin-only at Spring Security URL and method levels.
- Redacted-only metadata and artifacts.
- No LLM calls.
- No external network calls.
- No Tool execution.
- No kube-manager calls.
- No durable writes.
- Request-provided trace IDs are ignored for trace-set gates; curated gates must come from the versioned catalog.

## Learning Takeaway

Advanced Agent evaluation is not only about scoring. It is about evidence governance.

The system must prove what was evaluated, which standard was used, whether evidence coverage was complete, and whether missing evidence failed closed. That is why M5.37 is a key step from "manual eval endpoint" toward "top-tier release-quality Agent control plane".
