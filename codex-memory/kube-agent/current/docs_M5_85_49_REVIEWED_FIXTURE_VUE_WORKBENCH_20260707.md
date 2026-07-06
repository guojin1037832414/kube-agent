# M5.85-49 Reviewed Fixture Vue Workbench

Date: 2026-07-07 Asia/Shanghai

## Scope

Implemented the official `vue-kube-manager` read-only frontend binding for the kube-agent reviewed fixture workflow.

Frontend repository:

- `F:\gitProject\vue-kube-manager`
- Branch: `develop`
- Route: `/system/agent-reviewed-fixture`
- Backend read endpoint: `GET /api/agent/observability/eval/workbench/reviewed-fixture-vue-binding-spec`

## Completed

- Added `src/api/agent-observability.js`.
  - Uses independent Agent base API: `VUE_APP_AGENT_BASE_API || /agent-api`.
  - Reads only the reviewed fixture Vue binding spec.
  - Unwraps kube-agent `ApiResponse(success/data/message)`.
  - Sends optional in-memory `X-Session-Id`.
  - Does not reuse kube-manager `X-Token`.
  - Does not write session/token/password data to localStorage or docs.
- Added `src/views/agent/reviewed-fixture/index.vue`.
  - Renders summary metrics, binding status, safety evidence, component specs, field bindings, workflow stages, disabled action bindings, implementation checklist, and raw read-model JSON.
  - Keeps all runtime actions absent; the only user action is refresh/read.
- Updated `src/router/index.js`.
  - Adds `/system/agent-reviewed-fixture` under System.
  - Keeps the page as a frontend read-only projection; kube-agent backend remains the authoritative admin-only guard.
- Updated `vue.config.js`.
  - Adds `/agent-api` proxy to `VUE_APP_AGENT_BACKEND_API || http://localhost:8300`.
  - Rewrites `/agent-api` away before forwarding to kube-agent.
- Updated `.env.development` and `.env.staging`.
  - Adds `VUE_APP_AGENT_BASE_API=/agent-api`.
  - Adds `VUE_APP_AGENT_BACKEND_API=http://localhost:8300`.
- Added `tests/unit/api/agent-observability.spec.js`.
  - Covers session header normalization, ApiResponse unwrapping, sanitized failure propagation, and the exact read-only endpoint.
- Updated `README.md` and `README-zh.md`.
  - Documents the route, endpoint, env vars, and forbidden runtime powers.

## Verification

- `npx eslint src\api\agent-observability.js src\views\agent\reviewed-fixture\index.vue src\router\index.js`
  - Passed.
- `$env:VUE_APP_TITLE='Kube'; $env:VUE_APP_BILLING_MODEL='true'; npm run test:unit -- --runTestsByPath tests/unit/api/agent-observability.spec.js --runInBand`
  - Passed: 4 tests.
  - Jest warned about a pre-existing `.claude/worktrees/.../package.json` naming collision; the test still passed.
- `npm run build:stage`
  - Completed successfully.
  - Build emitted a pre-existing eslint-loader warning for `src/permission.js` unused `whiteList`.
- `npm run lint`
  - Still fails on pre-existing unrelated issues:
    - `src/permission.js`: unused `whiteList`.
    - `src/views/ai4s/course-preview/index.vue`: two `vue/no-parsing-error` parse errors.
- `git diff --check`
  - Passed with Windows LF/CRLF warnings only.
- Sensitive literal scan for the real test password:
  - No hits in touched frontend files, tests, or docs.

## Safety Invariant

This slice is frontend read-only rendering only.

It does not:

- upload fixture JSON;
- create reviewed fixture files;
- write `eval-trace-sets.json`;
- mutate catalog state;
- execute Tool, MCP, LLM, RAG, or kube-manager calls;
- invoke HITL;
- write audit or memory;
- enable CI blocking;
- grant release authority;
- upgrade dependencies;
- open Phase 2 NIM/HPC/Slurm/BCM authority.

## Next Work

1. Run the workbench against a live kube-agent admin session and verify the `/agent-api` proxy path end to end.
2. Expand `vue-kube-manager` read-only rendering for reviewed fixture candidate workbench, human review package, human review gate, and readiness detail pages.
3. Prepare the first real reviewed fixture only through human Git review after digest verification; do not add runtime fixture write/upload shortcuts.
