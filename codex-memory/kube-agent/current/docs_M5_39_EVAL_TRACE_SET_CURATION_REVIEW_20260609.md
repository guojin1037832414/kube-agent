# M5.39 Eval Trace-Set Curation Review

## Why This Slice Matters

M5.38 made trace-set gates visible in CI, but the trace sets are still empty by design. A top-tier Agent cannot move from evidence-only CI to blocking release gates by simply accepting ad-hoc request trace IDs.

M5.39 adds the missing middle protocol: candidate trace IDs can be evaluated and reviewed before a human/Git catalog patch promotes them into `observability/eval-trace-sets.json`.

## New Backend Contract

- `AgentEvalTraceSetCurationReviewArtifact`
- `AgentEvalTraceSetCatalogService#curationReview(...)`
- `POST /api/agent/observability/eval/trace-sets/{traceSetId}/curation-review`

The review artifact contains:

- trace set identity
- attached suite identity
- normalized candidate trace IDs
- compact candidate suite gate
- review verdict
- `readyForCatalogReview`
- review-only curation policy
- privacy proof

## Review Verdicts

- `READY_FOR_CATALOG_REVIEW`: candidates are non-empty and pass the attached suite gate.
- `REJECT_EMPTY_CANDIDATES`: no valid candidate trace IDs survived normalization.
- `REJECT_EVAL_GATE_FAILED`: candidates exist but fail the deterministic suite gate.
- `UNKNOWN_SUITE`: defensive fallback if a trace set references an unknown suite.

## Important Safety Boundary

This endpoint is not a runtime catalog writer.

- `catalogMutationAllowed=false`
- `catalogMutated=false`
- `candidateTraceIdsPromotedToCatalog=false`
- `requiresHumanReview=true`
- `requiresGitReview=true`

That means a caller can prove candidate evidence quality, but cannot secretly convert those candidates into release evidence.

## Candidate Trace ID Normalization

The curation-review path only forwards W3C-compatible trace anchors:

- `trc_` plus 32 lowercase hex characters
- 32 lowercase hex characters

Free text, whitespace-bearing strings, duplicate IDs, and loose local labels such as `trc_not_w3c` are filtered before the suite gate runs. This prevents a review artifact from echoing arbitrary request-body text as if it were release evidence.

## Relationship To M5.38

M5.38 publishes the whole trace-set gate bundle as CI evidence.

M5.39 adds the review artifact needed before the trace-set catalog can safely move from empty placeholders to real curated trace IDs.

The intended path is now:

1. Capture persisted redacted replay evidence.
2. Run curation review for the target trace set.
3. If `READY_FOR_CATALOG_REVIEW`, patch `eval-trace-sets.json` through Git review.
4. Regenerate the M5.38 gate bundle.
5. Only after reviewed evidence exists, enable blocking CI policy.

## Safety Rules

- Admin-only HTTP endpoint.
- Deterministic local evaluation only.
- No LLM calls.
- No external calls.
- No Tool execution.
- No kube-manager calls.
- No runtime mutation of `eval-trace-sets.json`.
- No raw principal, organization, conversation, endpoint, reason, or parameter values.
- No NIM/HPC/Slurm/BCM Phase 2 scope reopened.

## Learning Takeaway

Advanced Agent eval needs a promotion protocol, not only a score. The score says whether candidate evidence passes. The promotion protocol says who is allowed to turn that candidate evidence into versioned release evidence, where the change is reviewed, and how CI can reproduce the decision later.
