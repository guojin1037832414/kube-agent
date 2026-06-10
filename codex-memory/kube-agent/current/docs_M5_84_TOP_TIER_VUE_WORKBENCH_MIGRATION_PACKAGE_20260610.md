# M5.84 Top-tier Vue Workbench Migration Package - 2026-06-10

## Purpose

M5.84 turns the M5.83 top-tier Vue workbench acceptance contract into a concrete migration package for `vue-kube-manager`.

This is deliberately a backend-owned, read-only, dry-run package. The current writable workspace is `F:/gitProject/kube-agent`; the frontend repository `F:/gitProject/vue-kube-manager` is readable but not writable in this session, and its Git status requires a safe-directory decision before edits. So M5.84 does not pretend the frontend was changed. Instead, it publishes the exact route snippets, file blueprints, GET-only API exports, Jest test blueprints, validation scans, and forbidden-runtime assertions that should be applied once the frontend repo is writable and trusted.

Endpoint:

```text
GET /api/agent/observability/top-tier/vue-workbench-migration-package
```

Schema and status:

```text
schemaVersion=agent-top-tier-vue-workbench-migration-package.v1
migrationStatus=MIGRATION_PACKAGE_READY_TO_APPLY_TO_VUE_KUBE_MANAGER
```

## Delivered Contract

- `repositoryFactCount=5`
- `routePatchCount=5`
- `fileBlueprintCount=10`
- `apiExportCount=8`
- `testBlueprintCount=9`
- `validationCheckCount=8`
- `forbiddenRuntimeAssertionCount=12`
- `directFrontendWritePerformed=false`
- `frontendRepositoryWritableInCurrentWorkspace=false`
- `gitSafeDirectoryRequired=true`
- `acceptanceContractEmbedded=true`
- `readOnlyMigrationOnly=true`
- `runtimeControlAllowed=false`

The package embeds `AgentTopTierVueWorkbenchAcceptanceContractResponse` so the migration output stays tied to the source acceptance contract instead of drifting into a handwritten frontend plan.

## Migration Shape

The target frontend remains the observed `vue-kube-manager` stack:

- Vue 2 / Element UI
- `asyncRoutes` in `src/router/index.js`
- `BackendLayout`
- API wrappers through `@/utils/request`
- Jest + Vue Test Utils under `tests/unit/**/*.spec.js`
- Menu filtering by exact path match: `menus.some(menu => menu.path === route.path)`

The route migration rule is:

- Add parent `/agent` under `asyncRoutes` if missing.
- Keep the parent route without `withPermission` unless the backend menu API also returns `/agent`.
- Add five absolute child routes under `/agent/top-tier/*`.
- Set child `withPermission=true`.
- Keep all runtime-control buttons absent.

## Generated File Blueprints

The package describes these frontend files or folders:

- `src/api/agent-observability.js`
- `src/router/index.js`
- `src/views/agent/top-tier/components`
- `src/views/agent/top-tier/technology-introduction-playbook/index.vue`
- `src/views/agent/top-tier/official-version-protocol-watch/index.vue`
- `src/views/agent/top-tier/advanced-technology-compatibility-matrix/index.vue`
- `src/views/agent/top-tier/advanced-technology-evidence-readiness/index.vue`
- `src/views/agent/top-tier/backend-technology-modernization-decision/index.vue`
- `tests/unit/fixtures/agent-top-tier-workbench.js`
- `tests/unit/views/agent/top-tier`

The API module blueprint exports only GET functions, unwraps `response.data`, uses `@/utils/request`, and forbids `fetch`, `axios.create`, `post`, `put`, `patch`, and `delete`.

## Validation Package

The validation checks are intentionally simple and operator-friendly:

```text
npm run lint
npm run test:unit
npm run test:ci
rg "/agent/top-tier" src/router/index.js
rg "method: '(post|put|patch|delete)'" src/api/agent-observability.js
rg "data-test='(mcp-tools-call|kube-manager-write|enable-rag-runtime)'" src tests
git diff --check
mvn -q -Dtest=AgentTopTierVueWorkbenchMigrationPackageServiceTest test
```

The `rg` mutation scans are expected to return no matches except explicit negative-test fixture strings.

## Latest Technology Boundary

M5.84 keeps the Phase 1 goal ambitious: the project is still building a top-tier Agent and Agent learning system, not a reduced MVP.

The newest technology lanes remain in scope, but this slice introduces them through evidence-first contracts and visible workbench migration rules:

- Spring Boot official docs currently show stable `4.0.6`, while `3.5.14` remains a stable line too. This project keeps the current verified mainline until compatibility branches pass.
- Spring AI official docs show stable `1.1.7`; the `2.0` line is still treated as a compatibility lane until build, security, eval, and recovery evidence exists.
- OpenAI Agents guidance describes agents as applications that plan, call tools, collaborate across specialists, and keep state; it also points to Responses API when one model call plus tools and app-owned logic is enough. M5.84 maps those concepts into frontend governance and tests, not direct runtime handoff.
- MCP latest specification is `2025-11-25` and includes resources, prompts, tools, sampling, roots, and elicitation. M5.84 keeps MCP visible as evidence and forbidden runtime assertions; it does not expose `tools/call`.
- A2A latest released specification is `1.0.0`; M5.84 keeps A2A as a provenance and compatibility lane, not runtime handoff.
- OpenTelemetry GenAI semantic conventions remain `Development`, so GenAI span mapping must stay opt-in/evidence-gated.

Reference links:

- [Spring Boot Reference](https://docs.spring.io/spring-boot/reference/index.html)
- [Spring AI Reference](https://docs.spring.io/spring-ai/reference/index.html)
- [OpenAI Agents SDK guide](https://platform.openai.com/docs/guides/agents)
- [MCP latest specification](https://modelcontextprotocol.io/specification/latest)
- [A2A latest specification](https://a2a-protocol.org/latest/specification/)
- [OpenTelemetry GenAI semantic conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/)

## Safety Invariant

M5.84 is admin-only, GET-only, read-only, migration-package-only, dry-run-only, source-contract-composition-only, and external-call-free at request time.

It does not:

- write `vue-kube-manager`
- call kube-manager or port `8100`
- execute Tools or `SafeToolExecutor`
- invoke HITL
- call LLMs
- expose MCP runtime `tools/call`
- run A2A runtime handoff
- execute retrieval, vector stores, embeddings, rerankers, or GraphRAG
- run evals
- write memory or audit
- issue durable receipts
- enable CI blocking
- upgrade dependencies
- touch NIM / HPC / Slurm / BCM Phase 2 work

## Learning Note

M5.84 teaches an important Agent engineering habit: a top-tier Agent does not gain trust by hiding runtime power behind a new UI. It gains trust when the UI migration itself is a reviewed artifact with source contracts, route rules, API method limits, mocked fixtures, XSS-safe rendering, forbidden selectors, validation commands, and recovery memory.

For this project, "introduce all advanced technologies" means every advanced lane must become visible, teachable, testable, and release-gated before it is allowed to mutate runtime behavior.

## Next Step

The next safe frontend step is to make `vue-kube-manager` writable/trusted, apply the M5.84 package manually or with a reviewed patch, run the frontend lint/unit/CI checks, and commit/push the frontend plus backend recovery memory. Runtime MCP, A2A, retrieval, CI blocking, kube-manager writes, Java/Spring/Spring AI major upgrades, and Phase 2 NIM/HPC/Slurm/BCM remain separate release-gated slices.
