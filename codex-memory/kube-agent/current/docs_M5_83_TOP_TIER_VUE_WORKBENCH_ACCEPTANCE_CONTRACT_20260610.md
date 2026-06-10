# M5.83 Top-tier Vue Workbench Acceptance Contract

## Goal

M5.83 adds a backend-owned acceptance contract for the top-tier Agent technology workbench that will be implemented in `vue-kube-manager`.

Endpoint:

```text
GET /api/agent/observability/top-tier/vue-workbench-acceptance-contract
```

This slice does not edit `vue-kube-manager`. It gives the frontend a concrete contract: route mount shape, API client functions, mocked fixtures, Jest scenarios, forbidden selectors, security rules, and learning checkpoints.

## Why This Matters

The project goal is a top-tier Agent, not only a production Agent. That means advanced technology must become learnable, reviewable, and testable before it becomes runtime authority.

M5.82 introduced the latest-technology playbook. M5.83 turns the playbook and the existing implementation package into a frontend acceptance contract, so the next Vue slice can be built without guessing:

```text
backend read models
  -> Vue workbench implementation package
  -> M5.83 acceptance contract
  -> mocked frontend pages and tests
  -> reviewed release evidence
  -> later runtime binding slices
```

## Published Contract

The response publishes:

- `frontendStackFactCount=6`
- `routeMountSpecCount=5`
- `apiClientSpecCount=8`
- `pageFixtureSpecCount=5`
- `acceptanceScenarioCount=10`
- `forbiddenRuntimeSelectorCount=12`
- `implementationFileCount=10`
- `testCommandCount=3`

The endpoint embeds `AgentTopTierVueWorkbenchImplementationPackageResponse` as `sourceImplementationPackage`, then fails closed if the source package ever opens runtime authority.

## Vue Target

The contract is intentionally locked to the observed `vue-kube-manager` stack:

- Vue 2.6.x, not Vue 3.
- vue-router 3.x, not vue-router 4.
- Element UI 2.x, not Element Plus.
- axios through `src/utils/request.js`, not direct `fetch` or `axios.create`.
- Vue CLI 4 / Jest under `tests/unit/**/*.spec.js`, not a new frontend toolchain.

This is an important engineering lesson: "latest technology" does not mean forcing every repository to its newest major framework immediately. A top-tier Agent preserves a stable host application and introduces modern Agent governance through contracts, tests, and evidence first.

## Route Acceptance

The frontend must mount five read-only pages:

```text
/agent/top-tier/technology-introduction-playbook
/agent/top-tier/official-version-protocol-watch
/agent/top-tier/advanced-technology-compatibility-matrix
/agent/top-tier/advanced-technology-evidence-readiness
/agent/top-tier/backend-technology-modernization-decision
```

Each route must use:

- `src/router/index.js`
- `asyncRoutes`
- `BackendLayout`
- parent path `/agent`
- stable PascalCase `routeName`
- `withPermission=true`
- menu path matching `/api/{organizationId}/permission/menu/my`

## API Acceptance

The frontend must add `src/api/agent-observability.js` with GET-only functions:

- `fetchTopTierVueWorkbenchAcceptanceContract`
- `fetchTopTierVueWorkbenchImplementationPackage`
- `fetchTechnologyIntroductionPlaybook`
- `fetchOfficialVersionProtocolWatchVueBindingSpec`
- `fetchAdvancedTechnologyCompatibilityMatrixVueBindingSpec`
- `fetchAdvancedTechnologyCompatibilityMatrixEvidenceReadiness`
- `fetchBackendTechnologyModernizationDecision`
- `fetchVueReadinessControlPlane`

The API client contract is precise:

- import `request` from `@/utils/request`
- method is `get`
- mocked acceptance tests do not require a real backend
- production read-model GET calls are allowed
- mutating backend calls are not allowed
- view code unwraps `ApiResponse.data`, not a legacy `result` field

## Fixture And Jest Acceptance

Fixtures live under:

```text
tests/unit/fixtures/agent-top-tier-workbench.js
```

View tests live under:

```text
tests/unit/views/agent/top-tier
```

The contract requires mocked HTTP responses for 200, 401, and 403 paths. Jest should assert:

- the five pages render the expected Element UI structures
- `.el-table`, `.el-tag`, `.el-alert`, `.el-empty`, and `.el-tabs` appear where expected
- all forbidden runtime selectors are absent
- the API module exports no `post`, `put`, `patch`, or `delete` methods
- source JSON panels are read-only and do not use `v-html`

## Forbidden Runtime Controls

The following controls must not exist in the Vue DOM:

- dependency upgrade buttons
- Spring Boot 4 / Spring AI 2 upgrade buttons
- MCP `tools/call`
- A2A runtime handoff
- RAG / GraphRAG runtime enable buttons
- CI blocking enable button
- kube-manager write button
- durable receipt issuer
- HITL invocation button
- Phase 2 NIM/HPC/Slurm/BCM reopen button

This is the key learning pattern: absence is a test. A top-tier Agent proves dangerous authority is absent before it later proves that authority is safe.

## Standards Alignment

M5.83 keeps modern Agent standards visible without starting those runtimes:

- OpenAI Agents primitives such as tools, handoffs, guardrails, sessions/HITL, tracing, and evals are rendered as governance evidence only.
- MCP authorization and token-flow concerns remain in the compatibility/security gate; this endpoint does not start MCP runtime.
- OpenTelemetry GenAI semantic conventions remain adapter-gated; this endpoint emits no GenAI spans.
- OWASP LLM Top 10 risks are mapped to frontend learning checks, including prompt injection, insecure output handling, excessive agency, and sensitive information disclosure.

## Security Boundary

M5.83 is:

- admin-only
- GET-only
- read-only
- fixture-only
- acceptance-contract-only
- source-package-composition-only
- external-call-free at request time

It does not:

- modify `pom.xml`
- upgrade dependencies
- edit `vue-kube-manager`
- call kube-manager or port `8100`
- execute Tools or `SafeToolExecutor`
- invoke HITL
- run LLMs
- call MCP `tools/call`
- run A2A handoff
- run retrieval/vector/embedding/reranker/GraphRAG
- write memory or audit
- enable CI blocking
- touch NIM/HPC/Slurm/BCM Phase 2 work

## Verification

Focused verification:

```text
mvn -q "-Dtest=AgentTopTierVueWorkbenchAcceptanceContractServiceTest,AgentVueReadinessControlPlaneServiceTest,AgentPhase1ExecutionRoadmapServiceTest,AgentTopTierReadinessOverviewServiceTest,AgentAdvancedTechnologyAdoptionContractServiceTest,ObservabilityControllerTest,ObservabilityControllerSecurityContractTest,AgentSecurityConfigWebMvcTest" test
```

Result: passed on 2026-06-10.

## Next Work

The next safe frontend slice is to implement the five-page workbench in `vue-kube-manager` from this contract, using mocked fixtures and Jest first.

Runtime MCP, A2A, retrieval, CI blocking, kube-manager writes, Java/Spring/Spring AI major upgrades, and Phase 2 NIM/HPC/Slurm/BCM remain separate release-gated work.
