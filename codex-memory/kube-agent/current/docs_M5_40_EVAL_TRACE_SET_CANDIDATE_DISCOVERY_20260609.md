# M5.40 Eval Trace-Set Candidate Discovery

## Why This Slice Matters

M5.39 added curation review, but operators still needed to know which trace IDs were worth reviewing. M5.40 adds a redacted candidate-discovery layer that reads recent audit evidence and produces trace-level candidates for a target trace set.

This completes the next part of the eval evidence path:

1. Recent redacted audit events exist.
2. Candidate discovery summarizes trace anchors for a target trace set.
3. Curation review evaluates selected candidate trace IDs.
4. Human/Git review promotes approved trace IDs into `eval-trace-sets.json`.
5. CI gate bundle can eventually become blocking after reviewed evidence exists.

## New Backend Contract

- `AgentAuditQueryService#recentEvents(...)`
- `AgentEvalTraceSetCandidate`
- `AgentEvalTraceSetCandidateDiscoveryResponse`
- `AgentEvalTraceSetCandidateDiscoveryService`
- `GET /api/agent/observability/eval/trace-sets/{traceSetId}/candidates?limit=50`

The response includes:

- target trace-set identity
- attached suite id
- audit query backend
- inspected event/trace counts
- recommended candidate trace IDs
- per-trace redacted candidate summaries
- discovery policy
- privacy proof

## Candidate Recommendation Rules

M5.40 does not claim a trace is release-ready. It only recommends traces that are worth sending to M5.39 curation review.

- `phase1-core-golden`: successful ordinary read/final evidence with no high-risk or failure outcome.
- `phase1-redaction-regression`: final evidence with protected-parameter summary evidence.
- `phase1-high-risk-prewrite`: high-risk evidence with both `PRE_EXECUTION` and `FINAL` phases.
- `phase1-red-team-safety`: blocked, error, or business-failure evidence.

All discovered traces remain visible for operator inspection, but only recommended traces are emitted in `candidateTraceIds`.

## Safety Boundary

- Admin-only HTTP endpoint.
- Reads `AgentAuditQueryEvent` only, never raw audit records.
- Returns trace IDs, counts, closed-vocabulary enums, evidence tags, and privacy metadata only.
- No raw principal, organization, conversation, endpoint, reason text, or parameter values.
- No LLM calls.
- No external calls.
- No Tool execution.
- No kube-manager calls.
- No catalog mutation.
- No NIM/HPC/Slurm/BCM Phase 2 scope reopened.

## Learning Takeaway

Top-tier Agent eval should separate discovery, review, and promotion:

- Discovery answers: "Which redacted traces look relevant?"
- Review answers: "Do these candidates pass the deterministic gate?"
- Promotion answers: "Has a human/Git-reviewed catalog change turned them into release evidence?"

Keeping these steps separate prevents a convenient admin query from becoming an accidental release authority.
