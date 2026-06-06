# kube-agent Project Mission And Memory

## Ultimate Goal

`kube-agent` is not only a production Agent project. It is also a long-term learning project whose goal is to help the owner grow from an Agent beginner into an Agent master.

The target is to build a top-tier, near-perfect Kubernetes/cloud/HPC Agent on top of the existing mature `kube-manager` and `vue-kube-manager` capabilities.

## Product Direction

- Use `kube-manager` backend and `vue-kube-manager` frontend as the primary capability evidence.
- Keep Tool behavior aligned with real mature APIs instead of guessing paths or inventing unsupported features.
- Build an Agent that is safe, auditable, explainable, recoverable, and extensible.
- Treat this as both an engineering system and a learning artifact: architecture, code, tests, and docs should teach clearly.

## Engineering Standards

- Prefer modern Agent development patterns and up-to-date safety practices.
- Use explicit Tool metadata:
  - `httpMethod`
  - `apiEndpoints`
  - `operationType`
  - `requiresConfirmation`
- Separate normal `READ`, `SENSITIVE_READ`, `CREATE`, `UPDATE`, `DELETE`, and `ACTION`.
- Keep dangerous or unclear abilities fail-closed until evidence, permission boundary, tests, and docs are ready.
- Do not call real kube-manager `8100` during audit/migration waves unless explicitly required and safely scoped.
- Prefer static contract tests and mock HTTP client tests for Tool migration.
- Keep implementation changes scoped and reversible.

## Multi-Expert Workflow

For meaningful changes, think and document through multiple expert lenses:

- Backend/API expert: verifies mature controller paths, HTTP methods, DTO/query/body shape, and backend semantics.
- Frontend/product expert: verifies actual UI usage, workflow intent, and user-facing behavior from `vue-kube-manager`.
- Security/RBAC expert: classifies risk, tenant boundaries, HITL requirements, and excessive-agency risks.
- Agent architecture expert: checks Tool schema, ReAct/Plan/Execute behavior, memory, observability, and MCP exposure.
- Test architecture expert: designs contract, unit, and regression tests without relying on real side effects.
- Documentation/learning expert: leaves Chinese comments and docs that help the owner learn Agent development deeply.

## Documentation And Chinese Comments

- Add Chinese technical documentation whenever a capability wave, architectural decision, or safety boundary is completed.
- Add Chinese code comments when they clarify non-obvious Agent, safety, or API-contract logic.
- Avoid noisy comments for obvious code.
- Keep audit docs updated in `docs/`.
- Keep changelog and wave index updated for M5.21+ work.

## Memory And Recovery Rule

The user requires persistent project memory grouped by project under:

`H:\codex重要文件\kube-agent`

After every completed chunk:

1. Update a repo-local memory/progress file.
2. Sync the relevant memory/progress docs to `H:\codex重要文件\kube-agent`.
3. Include current status, changed files, tests, decisions, HOLD items, and next steps.

This is mandatory so future conversations can fully recover progress and context.

## Git Rule

After each completed chunk of meaningful work:

1. Run relevant tests/checks.
2. Update docs and memory.
3. Commit the completed chunk.
4. Push the commit.

Do not revert unrelated dirty worktree changes. If the worktree contains unrelated existing changes, only stage/commit the files belonging to the completed chunk.

## Current Long-Running Track

Current track:

`M5.21 kube-manager Tool alignment/audit waves`

Recently completed:

`M5.21-30 MIG config read alignment by GPU ID`

Latest checkpoint:

- Date: 2026-06-06 20:23 Asia/Shanghai.
- Branch: `codex/m521-29-top-agent-mission`.
- Verification passed for M5.21-30:
  - `mvn -q test`
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - Static secret scan found no real credentials; only documentation/config comments mention api-key/password terms.
- External recovery docs were synced to `H:\codex重要文件\kube-agent`.
- The current M5.21-30 wave set is ready to commit and push as a recovery checkpoint.

Latest in-progress/completed chunk after checkpoint:

- Date: 2026-06-06 20:23 Asia/Shanghai.
- M5.21-30 completed implementation and targeted verification:
  - `MigConfigListTool` now calls mature `GET /api/mig/{gpuId}` instead of old `/api/{orgId}/migConfig`.
  - It requires explicit positive integer `gpuId`, exposes no `page/limit/keyword`, and is `AUTHENTICATED + READ`.
  - Added `MigConfigReadToolHttpContractTest`.
  - Targeted test passed: `mvn -q "-Dtest=MigConfigReadToolHttpContractTest,ListToolParameterPassThroughContractTest,ListToolParameterSpecContractTest,M511AtlasToolHttpContractTest" test`.
  - Full test passed: `mvn -q test`.
  - MCP manifest export was tightened to `PUBLIC + READ + requiresConfirmation=false + declared endpoint`, so authenticated read Tools such as `mig_config_list` are not exported to the external MCP safe manifest.

Recommended next work:

- Continue with a narrow mature evidence-backed Tool alignment wave.
- Good candidates:
  - Registry/repository path migration after resolving `/api/registry` vs `/api/{orgId}/repository`.
  - Download status/progress migration requiring task `id`.
  - Another mature GET area with clean backend/frontend evidence.
