# M5.38 Eval Trace-Set Gate Bundle CI Artifact

## Why This Slice Matters

M5.37 created a versioned trace-set catalog. M5.38 makes that catalog visible to CI by generating a machine-readable gate bundle artifact during tests and uploading it with backend quality artifacts.

This is a key step toward a top-tier Agent release process: CI should not only say "tests passed"; it should also preserve evidence about Agent safety, eval coverage, and release readiness.

## New Backend Contract

- `AgentEvalTraceSetGateBundleArtifact`
- `AgentEvalTraceSetCatalogService#gateBundle(...)`
- `POST /api/agent/observability/eval/trace-sets/gate-bundle`
- `target/agent-eval/trace-set-gate-bundle.json`

The bundle summarizes the whole versioned trace-set catalog:

- total trace sets
- passed/failed trace sets
- empty trace sets
- failed and empty trace-set IDs
- per-trace-set compact gate artifacts
- privacy proof
- CI policy metadata

## CI Integration

`.github/workflows/backend-quality.yml` now uploads:

- `target/site/jacoco/`
- `target/spotbugsXml.xml`
- `target/bom.*`
- `target/agent-eval/`

`AgentEvalTraceSetGateBundleArtifactTest` writes the JSON artifact into `target/agent-eval/` during Maven tests.

## Why `ciBlockingEnabled=false`

The current trace sets intentionally contain empty `traceIds` until real persisted redacted replay captures are curated. Empty evidence must fail closed, but the project should not block all CI builds before the evidence capture pipeline exists.

So M5.38 uses an evidence-first policy:

- CI uploads the eval bundle.
- The bundle reports `gateVerdict=FAIL` and `releaseEligible=false` while trace sets are empty.
- The bundle also reports `ciBlockingEnabled=false`.
- A future milestone can switch to strict blocking after real golden/red-team trace IDs are curated and reviewed.

## Safety Rules

- Admin-only HTTP endpoint.
- Deterministic local evaluation only.
- No LLM calls.
- No external calls.
- No Tool execution.
- No kube-manager calls.
- No raw principal, organization, conversation, endpoint, reason, or parameter values.
- No NIM/HPC/Slurm/BCM Phase 2 scope reopened.

## Learning Takeaway

The mature pattern is to introduce CI gates in stages:

1. Define the typed gate artifact.
2. Publish it as CI evidence.
3. Populate real curated evidence.
4. Make the gate blocking after the evidence set is reviewed.

This avoids both extremes: invisible quality checks that nobody can inspect, and premature hard gates that fail for missing infrastructure rather than real product risk.
