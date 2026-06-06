# kube-agent Project Mission And Memory

## Ultimate Goal

`kube-agent` is not only a production Agent project. It is also a long-term learning project whose goal is to help the owner grow from an Agent beginner into an Agent master.

The target is to build a top-tier, near-perfect Kubernetes/cloud/HPC Agent on top of the existing mature `kube-manager` and `vue-kube-manager` capabilities.

The owner explicitly clarified on 2026-06-06 that the target is higher than a normal production-grade Agent: this should become a top-tier learning project for mastering Agent development. Implementation should therefore prefer modern, evidence-backed Agent patterns, strong safety boundaries, rich Chinese documentation/comments, and multi-expert iterative review, while still staying grounded in mature `kube-manager` / `vue-kube-manager` behavior.

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

`M5.21-35 NIM deployment preflight sensitive read orchestration`

Latest checkpoint:

- Date: 2026-06-06 21:36 Asia/Shanghai.
- Branch: `codex/m521-29-top-agent-mission`.
- M5.21-35 implemented, verified, synced, and ready to commit:
  - Added `NimDeploymentPreflightTool` for read-only NIM deployment planning.
  - Added `NimDeploymentPreflightSupport` for safe repository/tag/image/template selection.
  - The Tool calls only mature GET endpoints:
    - `GET /api/{orgId}/repository`
    - `GET /api/{orgId}/repository/nim/tags`
    - `GET /api/{orgId}/template`
  - It returns `sideEffect=NONE` and `preflightOnly=true`, with catalog/tag/template candidates and next HITL requirements.
  - `nim_create` remains fail-closed HOLD and does not call deployment create.
  - Verification passed:
    - `mvn -q "-Dtest=NimDeploymentPreflightToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
    - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
    - Static secret scan found 0 matches.
    - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - External recovery docs were synced to `H:\codex重要文件\kube-agent`.

- Date: 2026-06-06 21:16 Asia/Shanghai.
- Branch: `codex/m521-29-top-agent-mission`.
- M5.21-33 was completed, committed, and pushed:
  - Commit: `e5ba040 fix(M5.21): align registry site read tool`
  - `RegistryListTool` now uses `GET /api/registry`, optional `keyWord`, and `SENSITIVE_READ + requiresConfirmation=true`.
  - Registry create/update/delete and `/api/registry/repo-tag` remain HOLD.
  - Verification passed:
    - `mvn -q "-Dtest=RegistrySiteToolHttpContractTest,ListToolParameterPassThroughContractTest,ListToolParameterSpecContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
    - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
    - Static secret scan found no real credentials; only documentation/config comments mention api-key/password terms.
    - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - External recovery docs were synced to `H:\codex重要文件\kube-agent`.
- M5.21-32 was committed and pushed:
  - Commit: `2825387 feat(M5.21): add download task progress read tool`
- M5.21-31 was committed and pushed:
  - Commit: `e25738a fix(M5.21): align download task status read tool`
- M5.21-30 was committed and pushed:
  - Commit: `b5d4132 fix(M5.21): align MIG config read tool`
- Verification passed for M5.21-32 before commit:
  - `mvn -q test`
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - Static secret scan found no real credentials; only documentation/config comments mention api-key/password terms.
- External recovery docs were synced to `H:\codex重要文件\kube-agent`.

Latest in-progress/completed chunk after checkpoint:

- Date: 2026-06-06 21:16 Asia/Shanghai.
- M5.21-34 implemented, committed, and pushed:
  - Commit: `404d80e feat(M5.21): add repository catalog read tools`
  - Added `RepositoryCatalogListTool` for mature `GET /api/{orgId}/repository`.
  - Added `RepositoryCatalogCategoryListTool` for `GET /api/{orgId}/repository/category`.
  - Added `RepositoryCatalogTagListTool` for `GET /api/{orgId}/repository/tags`, requiring explicit `repository`.
  - Added `RepositoryCatalogNimTagListTool` for `GET /api/{orgId}/repository/nim/tags`, requiring explicit `repository`.
  - Added `RepositoryCatalogQuerySupport` to keep repository catalog schema separate from registry site and image repository semantics.
  - All four Tools are `AUTHENTICATED + SENSITIVE_READ + requiresConfirmation=true`.
  - Targeted test passed: `mvn -q "-Dtest=RepositoryCatalogToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`.
  - Final verification passed: `git -c safe.directory=F:/gitProject/kube-agent diff --check`, static secret scan with 0 matches, and `mvn -q test`.
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - External recovery docs were synced to `H:\codex重要文件\kube-agent`.
  - HOLD: image pull/retry/delete/push/build/load, NIM deployment creation, and `GET /api/registry/repo-tag`.

- Date: 2026-06-06 20:50 Asia/Shanghai.
- M5.21-33 completed implementation and targeted verification:
  - `RegistryListTool` now calls mature site endpoint `GET /api/registry` instead of old `/api/{orgId}/registry`.
  - It exposes optional `keyWord` only, with `keyword` as alias, and no longer exposes `page/limit`.
  - It is `SENSITIVE_READ + requiresConfirmation=true` because registry site DTO returns URL and username.
  - `GET /api/{orgId}/repository` is recorded as a separate product/application repository catalog candidate, not mixed into registry.
  - Added `RegistrySiteToolHttpContractTest`.
  - Targeted test passed: `mvn -q "-Dtest=RegistrySiteToolHttpContractTest,ListToolParameterPassThroughContractTest,ListToolParameterSpecContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`.

Recommended next work:

- Commit and push M5.21-35 if not already done.
- Continue NIM orchestration through safe slices:
  - design NIM HITL card and audited DTO merge,
  - or pick another mature GET area with clean backend/frontend evidence.
