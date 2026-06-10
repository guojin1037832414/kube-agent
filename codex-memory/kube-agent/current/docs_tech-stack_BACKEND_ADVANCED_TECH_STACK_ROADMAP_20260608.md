# Backend Advanced Tech Stack Roadmap - 2026-06-08

## 目标口径

一期目标是顶级 kube-manager Agent Core，不是普通生产级后端，也不是只读 demo。技术选型必须同时满足：

- 当前主线可构建、可测试、可恢复；
- Agent 执行边界可审计、可追踪、可评测；
- 供应链、CI、SBOM、质量门禁进入工程默认路径；
- Java / Spring / Agent 框架升级以兼容性矩阵推进，不用不可构建的版本号伪装先进。

## 2026-06-10 M5.82 Top-Tier-Technology-Introduction-Playbook Rule

M5.82 adds the backend-owned latest-technology introduction playbook:

```text
GET /api/agent/observability/top-tier/technology-introduction-playbook
```

This is now the Phase 1 rule after M5.81:

- "Introduce all advanced technologies" means publish a governed path for every advanced technology lane before runtime authority expands.
- The playbook composes official watch, compatibility matrix, evidence readiness, and backend modernization decision read models.
- It keeps 10 lanes in scope: Java 21/25, Spring Boot 4, Spring AI 2.0.0-RC2, OpenAI Responses/Agents patterns, MCP runtime, A2A provenance, OTel GenAI adapter, Memory/RAG/GraphRAG/reranker/vector store, kube-manager writes, and supply-chain/CI quality.
- It requires eight stages before runtime binding: official source, compatibility matrix, evidence readiness, compatibility branch, focused regression tests, Vue read-only workbench, multi-expert release review, and separate runtime binding slice.
- It makes multi-expert review explicit: architecture, security, frontend Vue, eval quality, Memory/RAG, and release manager reviews.
- The top-tier Vue workbench package now contains five routes: technology introduction playbook, official watch, compatibility matrix, evidence readiness, and backend modernization decision.
- Vue readiness control plane now tracks 18 dashboard/workbench targets.
- Runtime MCP, A2A, retrieval, CI blocking, kube-manager writes, dependency upgrades, and Phase 2 NIM/HPC/Slurm/BCM remain closed until their evidence gates pass.

Technology judgment: the most advanced Phase 1 architecture is evidence-first adoption. A top-tier Agent is not made perfect by blindly installing every newest runtime; it becomes trustworthy when every advanced technology has official sources, compatibility tests, reviewed evidence, Vue visibility, release review, rollback memory, and a separate runtime binding decision.

Next order after M5.82:

- Wire `vue-kube-manager` to consume the five-page latest-technology workbench when the frontend repo is writable.
- Capture real reviewed redacted eval trace evidence.
- Complete Memory/RAG reviewed trace fixtures.
- Add Java 21/25, Spring Boot 4, and Spring AI 2.0.0-RC2 compatibility branches only after the current mainline stays green.
- Prototype MCP/A2A/RAG behind SafeToolExecutor, release gates, reviewed evidence, Vue visibility, and recovery memory.
- Keep kube-manager writes, CI blocking, MCP runtime, A2A runtime handoff, retrieval prompt influence, and Phase 2 NIM/HPC/Slurm/BCM release-gated.

## 2026-06-10 M5.81 Backend-Technology-Modernization-Decision Rule

M5.81 adds the backend-owned modernization decision endpoint:

```text
GET /api/agent/observability/top-tier/backend-technology-modernization-decision
```

This is now the Phase 1 rule after M5.80:

- Java/Spring remains the preferred typed control plane for identity, RBAC, Tool authority, HITL, audit, replay, eval, release gates, Vue read models, and recovery memory.
- Latest technology remains fully in scope, but moves through official source watch, compatibility matrix, evidence readiness, reviewed tests, release gate, and Git-reviewed runtime binding.
- Spring AI 2 preview tracking is refreshed to `2.0.0-RC2`; it remains compatibility-matrix work, not a mainline dependency.
- The top-tier Vue workbench package now contains four routes: official watch, compatibility matrix, evidence readiness, and backend modernization decision.
- Vue readiness control plane now tracks 17 dashboard/workbench targets.
- Runtime MCP, A2A, retrieval, CI blocking, kube-manager writes, dependency upgrades, and Phase 2 NIM/HPC/Slurm/BCM remain closed until their evidence gates pass.

Technology judgment: Java/Spring is not being kept because it is old or easy. It is being kept because a top-tier Agent needs a typed authority control plane before it needs a flashy runtime label. The newest technologies are learned, tracked, and designed through compatibility lanes before they are allowed to affect production behavior.

Next order after M5.81:

- Wire `vue-kube-manager` to consume the four-page workbench package when the frontend repo is writable.
- Capture real reviewed redacted eval trace evidence.
- Complete Memory/RAG reviewed trace fixtures.
- Add Java 21/25, Spring Boot 4, and Spring AI 2.0.0-RC2 compatibility branches only after the current mainline stays green.
- Prototype MCP/A2A/RAG behind SafeToolExecutor, release gates, reviewed evidence, Vue visibility, and recovery memory.
- Keep kube-manager writes, CI blocking, MCP runtime, A2A runtime handoff, retrieval prompt influence, and Phase 2 NIM/HPC/Slurm/BCM release-gated.

## 2026-06-10 M5.80 Advanced-Technology-Evidence-Readiness Rule

M5.80 adds the backend-owned evidence-readiness layer for the advanced technology compatibility matrix:

```text
GET /api/agent/observability/top-tier/advanced-technology-compatibility-matrix/evidence-readiness
```

This is now the Phase 1 rule after M5.77-M5.79:

- Every advanced technology lane must show evidence readiness before runtime or dependency adoption.
- The evidence-readiness layer composes only read models from compatibility matrix, reviewed eval trace evidence, and Memory/RAG reviewed trace evidence manifest.
- Current state remains blocked because reviewed trace anchors and Memory/RAG reviewed fixtures are still empty.
- `vue-kube-manager` should render this as a read-only evidence board, not as an enable/upgrade page.
- M5.79 workbench package now contains three routes: official watch, compatibility matrix, and evidence readiness.
- Runtime MCP, A2A, retrieval, CI blocking, kube-manager writes, dependency upgrades, and Phase 2 NIM/HPC/Slurm/BCM remain closed.

Technology judgment: "latest technology" is now represented as a chain of evidence: official source -> matrix lane -> evidence readiness -> reviewed tests -> release gate -> runtime binding. This is the safe path for a top-tier Agent that must be modern, learnable, auditable, and recoverable.

Next order after M5.80, superseded by the M5.81 section above:

- Wire `vue-kube-manager` to consume M5.79/M5.80/M5.81 when the frontend repo is writable.
- Capture real reviewed redacted eval trace evidence.
- Complete Memory/RAG reviewed trace fixtures.
- Promote release-blocking eval gates only after reviewed evidence exists.
- Keep runtime MCP, A2A, retrieval, CI blocking, kube-manager writes, and Phase 2 NIM/HPC/Slurm/BCM closed until their gates pass.

## 2026-06-10 M5.79 Top-Tier-Vue-Workbench-Implementation-Package Rule

M5.79 adds the backend-owned implementation package for the latest-technology Vue workbench:

```text
GET /api/agent/observability/top-tier/vue-workbench-implementation-package
```

This is now the Phase 1 frontend handoff rule after M5.76 and M5.78:

- `vue-kube-manager` should implement the official watch page and compatibility matrix page from a single backend-published package.
- The package publishes route specs, API client bindings, page assemblies, shared component contracts, acceptance fixtures, and forbidden runtime controls.
- The package embeds both source binding specs: official watch binding spec and compatibility matrix binding spec.
- All API client bindings are GET-only, admin-only, mocked-fixture-friendly, and do not require kube-manager `8100`.
- Runtime/dependency buttons remain absent for Java/Spring/Spring AI upgrades, MCP `tools/call`, A2A handoff, retrieval/vector/reranker/GraphRAG, CI blocking, kube-manager writes, and Phase 2 domain reopening.

Technology judgment: a top-tier Agent frontend needs a backend-owned implementation package, not only separate data endpoints. The workbench must be usable and teachable while remaining governed by backend evidence and release gates.

Multi-expert decision:

- Newton / frontend-contract review pointed out the missing cross-page workbench contract; M5.79 implements it.
- Faraday / backend-architecture review recommended a follow-up evidence-readiness layer; keep that as the likely next backend slice.

Next order after M5.79:

- M5.80 evidence-readiness is now complete; use the newer M5.81 section above as the current rule.
- Wire `vue-kube-manager` to consume the expanded four-page workbench package when that repo is writable.
- Continue reviewed redacted eval and Memory/RAG trace evidence curation.
- Keep runtime MCP, A2A, retrieval, CI blocking, kube-manager writes, and Phase 2 NIM/HPC/Slurm/BCM closed until their gates pass.

## 2026-06-10 M5.78 Compatibility-Matrix-Vue-Binding-Spec Rule

M5.78 adds the backend-owned Vue binding spec for the advanced technology compatibility matrix:

```text
GET /api/agent/observability/top-tier/advanced-technology-compatibility-matrix/vue-binding-spec
```

This is now the Phase 1 frontend implementation rule for the latest-technology compatibility workbench:

- `vue-kube-manager` should render M5.77 from backend-published component specs, field bindings, table column groups, state rendering rules, disabled action bindings, fixtures, and checklist.
- The binding spec embeds `sourceMatrix`, so the frontend can drill from UI layout to official-source and migration-gate evidence.
- Runtime/dependency buttons remain absent for Java/Spring/Spring AI upgrades, MCP `tools/call`, A2A handoff, retrieval/vector/reranker/GraphRAG, CI blocking, kube-manager writes, and Phase 2 domain reopening.
- Frontend tests should use mocked HTTP fixtures and assert no runtime backend calls or kube-manager `8100` calls.
- The Vue readiness control plane now tracks 14 dashboard/workbench targets and includes `advanced-technology-compatibility-matrix-binding-spec`.

Technology judgment: the newest technology stack is now represented as watch -> dashboard -> binding spec and matrix -> binding spec. This keeps the UI rich and teachable while preserving backend-owned evidence, policy, and release gates.

Next order after M5.78:

- Wire `vue-kube-manager` to consume the matrix binding spec and render the compatibility matrix workbench.
- Add frontend fixture tests for hidden runtime/dependency buttons.
- Continue reviewed redacted eval and Memory/RAG trace evidence curation.
- Keep runtime MCP, A2A, retrieval, CI blocking, kube-manager writes, and Phase 2 NIM/HPC/Slurm/BCM closed until their gates pass.

## 2026-06-10 M5.77 Advanced-Technology-Compatibility-Matrix Rule

M5.77 adds the backend-owned compatibility matrix for latest technology adoption:

```text
GET /api/agent/observability/top-tier/advanced-technology-compatibility-matrix
```

This is now the Phase 1 rule before any dependency/runtime upgrade:

- Java 21/25, Spring Boot 4, Spring AI 2, OpenAI Agents/Responses runtime patterns, MCP tools/call, A2A handoff, OTel GenAI/MCP semconv, GraphRAG/rerankers/vector stores, kube-manager writes, SBOM/dependency audit, and CI blocking all enter the matrix first.
- The matrix publishes source baselines, candidate targets, required evidence, migration gates, blocked shortcuts, and test lanes.
- Current mainline remains Java 17 + Spring Boot 3.5.x + Spring AI 1.1.x until compatibility evidence proves a safe migration.
- Future runtime authority requires official source review, compatibility branch, focused tests, security/privacy regression, Vue readonly evidence, recovery memory, and Git-reviewed release decision.

Technology judgment: M5.77 is a higher-fidelity interpretation of "use the latest technologies". It keeps the latest technologies inside Phase 1 scope, but represents them as testable upgrade lanes rather than unsafe runtime changes. This is how a top-tier Agent stays modern without becoming fragile.

Next order after M5.77:

- Bind `vue-kube-manager` to render the compatibility matrix.
- Add compatibility branches for Java 21/25, Spring Boot 4, and Spring AI 2 as separate reviewed slices.
- Continue reviewed redacted eval and Memory/RAG trace evidence curation before retrieval/runtime expansion.
- Keep MCP tools/call, A2A handoff, CI blocking, kube-manager writes, and Phase 2 NIM/HPC/Slurm/BCM closed until their gates pass.

## 2026-06-09 M5.76 Official-Version-Protocol-Watch-Vue-Binding-Spec Rule

M5.76 adds the backend-owned Vue binding spec for the latest-technology governance dashboard:

```text
GET /api/agent/observability/top-tier/official-version-protocol-watch/vue-binding-spec
```

This is now the Phase 1 frontend implementation rule for advanced Agent technology workbenches:

- The backend publishes component specs, field bindings, table column groups, state rendering rules, disabled action bindings, fixtures, and implementation checklist.
- `vue-kube-manager` should implement the page from the binding spec instead of inventing governance logic.
- Runtime controls remain absent for dependency upgrades, MCP `tools/call`, A2A handoff, retrieval/vector/reranker/GraphRAG, CI blocking, kube-manager writes, and Phase 2 domain reopening.
- The binding spec embeds the M5.75 source dashboard so the frontend can drill down from UI rules to official-source evidence.
- The Vue readiness control plane now tracks 12 dashboard/workbench targets and includes `official-version-protocol-watch-binding-spec`.

Technology judgment: the newest technology stack is now represented as watch -> dashboard -> binding spec -> fixture lane before runtime authority. This is the right advanced pattern for a top-tier Agent because the UI can become a high-quality learning and operator surface while the backend continues to own evidence, policy, and release gates.

Official references rechecked for this anchor:

- Spring AI reference: https://docs.spring.io/spring-ai/reference/
- Spring Boot documentation: https://docs.spring.io/spring-boot/index.html
- OpenAI Agents SDK guide: https://platform.openai.com/docs/guides/agents
- Model Context Protocol latest specification: https://modelcontextprotocol.io/specification/latest
- OpenTelemetry GenAI semantic conventions: https://opentelemetry.io/docs/specs/semconv/gen-ai/
- A2A protocol specification: https://a2a-protocol.org/latest/specification/
- OWASP Top 10 for LLM Applications: https://genai.owasp.org/llm-top-10/

Next order after M5.76:

- Wire `vue-kube-manager` to consume the binding spec and render the official version/protocol watch page.
- Add Playwright/front-end contract fixtures once the frontend workspace is available.
- Continue reviewed redacted eval and Memory/RAG trace evidence curation.
- Run compatibility-matrix branches before Java/Spring/Spring AI/OpenAI/MCP/A2A/OTel/RAG runtime or dependency upgrades.
- Keep NIM / HPC / Slurm / BCM paused for Phase 2.

## 2026-06-09 M5.75 Official-Version-Protocol-Watch-Dashboard Rule

M5.75 adds the backend-owned Vue dashboard for latest-technology governance:

```text
GET /api/agent/observability/top-tier/official-version-protocol-watch/dashboard
```

This is now the Phase 1 UI-facing rule for adopting advanced Agent technologies:

- The backend, not Vue, owns the official-source evidence, technology-track status, adoption gates, blocked shortcuts, disabled runtime actions, and safety/privacy policy.
- `vue-kube-manager` should render `sourceCards`, `technologyTrackCards`, `adoptionGateRows`, `blockedRuntimeShortcutRows`, `disabledRuntimeActions`, and `renderSections`.
- Runtime enablement buttons must stay absent for dependency upgrades, MCP `tools/call`, A2A handoff, retrieval/vector/reranker/GraphRAG, CI blocking, kube-manager writes, and Phase 2 domain reopening.
- The official source watch now includes the 2026-06-02 NSA MCP Security Cybersecurity Information as `nsa-mcp-security-2026-06`; `officialSourceCount=8`.
- This source refresh improves the MCP security gate but does not open the MCP runtime call plane.

Technology judgment: the latest security guidance is most valuable when it changes gates, evidence, and operator visibility before it changes execution. M5.75 keeps Java/Spring as the governed control plane and gives Vue a faithful evidence dashboard. Full MCP runtime, A2A, retrieval, GraphRAG, rerankers, vector stores, and CI blocking remain later reviewed slices with compatibility tests, eval gates, audit/replay evidence, SafeToolExecutor/HITL boundaries, and recovery checkpoints.

Official references checked for this anchor:

- NSA MCP Security Cybersecurity Information: https://media.defense.gov/2026/Jun/02/2003943289/-1/-1/0/CSI_MCP_SECURITY.PDF
- Model Context Protocol specification: https://modelcontextprotocol.io/specification/2025-11-25
- Spring AI reference: https://docs.spring.io/spring-ai/reference/
- OpenAI Responses migration guide: https://platform.openai.com/docs/guides/migrate-to-responses
- OpenAI Agents SDK guide: https://platform.openai.com/docs/guides/agents-sdk/
- OpenTelemetry GenAI semantic conventions: https://opentelemetry.io/docs/specs/semconv/gen-ai/
- A2A protocol specification: https://a2a-protocol.org/latest/specification/
- OWASP Top 10 for LLM Applications: https://genai.owasp.org/llm-top-10/

Next order after M5.75:

- Wire `vue-kube-manager` to render the dashboard and keep all runtime controls disabled/absent.
- Continue reviewed redacted eval and Memory/RAG trace evidence curation.
- Add compatibility-matrix tests before any Java/Spring/Spring AI/OpenAI/MCP/A2A/OTel/RAG dependency/runtime upgrade.
- Keep NIM / HPC / Slurm / BCM paused for Phase 2.

## 2026-06-09 M5.74 Official-Version-Protocol-Watch Rule

M5.74 adds the backend-owned official version/protocol watch:

```text
GET /api/agent/observability/top-tier/official-version-protocol-watch
```

This is the Phase 1 rule for adopting the latest Agent technologies without destabilizing the governed Java/Spring control plane:

- Official source claims are now explicit read-model data:
  Spring AI reference, OpenAI Responses migration guide, OpenAI Agents SDK guide, MCP 2025-11-25 specification, A2A latest specification, OpenTelemetry GenAI semantic conventions, and OWASP LLM Top 10.
- The watch publishes eight technology tracks:
  Java/Spring governed control plane, Spring AI Memory/RAG/MCP, OpenAI Responses/Agents interop, MCP runtime call plane, A2A handoff provenance, OTel GenAI adapter, OWASP LLM risk controls, and advanced RAG/GraphRAG/rerankers/vector stores.
- It introduces explicit adoption gates:
  official-source review, compatibility matrix before upgrade, contract before runtime, safe authority boundary, trace/audit/replay before prompt or tool influence, Vue read model before controls, and Phase 2 domain pause.
- It blocks shortcuts:
  blind latest-version bump, direct MCP `tools/call`, direct A2A runtime authority, direct retrieval prompt influence, OTel GenAI as primary schema while still development-level, and replacing the Java/Spring control plane with an external Agent runtime.
- It integrates with the advanced technology adoption contract, top-tier readiness overview, Phase 1 roadmap, and Vue readiness control plane.

Technology judgment: this is the correct interpretation of "引入全部最先进技术" for a top-tier Agent. The latest official technologies are now visible and teachable, but runtime authority still requires evidence. Java/Spring remains the mainline because identity, RBAC, audit, eval gates, release governance, and recovery memory need a stable typed control plane. OpenAI Responses/Agents, MCP runtime, A2A, Spring AI RAG/VectorStore/MCP, OTel GenAI, OWASP LLM controls, GraphRAG, rerankers, and vector stores move through watch -> matrix -> contract -> evidence -> Vue -> reviewed runtime binding.

Official references checked for this anchor:

- Spring AI reference: https://docs.spring.io/spring-ai/reference/
- OpenAI Responses migration guide: https://platform.openai.com/docs/guides/migrate-to-responses
- OpenAI Agents SDK guide: https://platform.openai.com/docs/guides/agents-sdk/
- Model Context Protocol specification: https://modelcontextprotocol.io/specification/2025-11-25
- A2A protocol specification: https://a2a-protocol.org/latest/specification/
- OpenTelemetry GenAI semantic conventions: https://opentelemetry.io/docs/specs/semconv/gen-ai/
- OWASP Top 10 for LLM Applications: https://genai.owasp.org/llm-top-10/

Next order after M5.74:

- Wire `vue-kube-manager` to render the official version/protocol watch dashboard.
- Keep collecting reviewed redacted eval and Memory/RAG trace evidence.
- Only after reviewed evidence exists, prototype MCP `tools/call`, A2A handoff, retrieval runtime, or CI blocking as separate release-gated slices.
- Keep NIM / HPC / Slurm / BCM paused for Phase 2.

## 2026-06-09 M5.73 Memory-RAG-Reviewed-Trace-Evidence-Manifest Rule

M5.73 adds the backend-owned reviewed trace-evidence manifest:

```text
GET /api/agent/observability/memory-rag/workbench/trace-set-curation/review-manifest
```

The advanced Agent stack now has a typed fixture-intake contract before reviewed redacted trace IDs are added to the catalog:

- It composes only Memory/RAG contracts and readiness:
  `AgentMemoryRagTraceSetCurationContractService.contract()`,
  `AgentMemoryRagSourceEvidenceDigestContractService.contract()`,
  `AgentMemoryRagDurableMemoryLifecycleContractService.contract()`,
  `AgentMemoryRagEvalGateContractService.contract()`,
  `AgentMemoryRagEvalSuiteBindingContractService.contract()`, and
  `AgentMemoryRagReadinessService.readiness()`.
- It publishes required trace-set rows for citation fidelity, privacy/tenant isolation, and lifecycle policy.
- It publishes `requiredTraceAnchorSchema`, `requiredDigestEvidence`, `evidenceIntakeSchema`, `reviewWorkflow`, `manifestPolicy`, `safety`, and `privacy`.
- It maps Spring AI Memory/RAG/VectorStore, OpenAI Agents tracing/guardrails/evals, MCP tools/resources/prompts, OpenTelemetry GenAI, A2A provenance, and OWASP LLM risks into reviewed evidence gates.
- It does not accept caller trace IDs and keeps `traceIdsVisibleInManifest=false`.
- It keeps eval runtime, retrieval runtime, vector-store calls, embedding/reranker/LLM calls, MCP `tools/call`, kube-manager calls, catalog mutation, and CI blocking closed.
- Phase 2 NIM / HPC / Slurm / BCM remains paused and is not reopened by this work.

Technology judgment: Java/Spring remains the Phase 1 control plane because the project is building governed Agent authority, not a prompt-only demo. The newest technologies are introduced as trace/eval/review/telemetry/provenance contracts first. Spring Boot 4, Spring AI 2, Java 21/25, full MCP runtime, A2A runtime handoff, GraphRAG, rerankers, and vector stores stay in compatibility-matrix or later gated runtime slices until this reviewed evidence lane becomes real.

Official references checked for this anchor:

- Spring AI reference: https://docs.spring.io/spring-ai/reference/
- OpenAI Agents SDK: https://openai.github.io/openai-agents-python/
- Model Context Protocol specification: https://modelcontextprotocol.io/specification/2025-11-25
- OpenTelemetry GenAI semantic conventions: https://opentelemetry.io/docs/specs/semconv/gen-ai/
- A2A protocol specification: https://a2a-protocol.org/latest/specification/
- OWASP Top 10 for LLM Applications: https://owasp.org/www-project-top-10-for-large-language-model-applications/

Next order after M5.73:

- Curate authoritative reviewed redacted trace fixtures through human/Git review.
- Generate advisory Memory/RAG gate-bundle evidence only after reviewed fixtures exist.
- Promote CI blocking only in a separate reviewed slice.
- Bind durable memory store and retrieval runtime only after source digest, lifecycle, tenant/privacy, eval evidence, Vue visibility, and recovery memory pass.
- Run separate compatibility spikes for Java 21/25, Spring Boot 4, Spring AI 2, MCP runtime, A2A, GraphRAG, rerankers, and vector stores.

## 2026-06-09 M5.72 Memory-RAG-Trace-Set-Curation-Workbench Rule

M5.72 adds the backend-owned Vue workbench:

```text
GET /api/agent/observability/memory-rag/workbench/trace-set-curation/overview
```

The advanced Agent stack now has an operator-facing Memory/RAG curation surface before reviewed trace IDs, advisory gate bundles, retrieval runtime, or CI blocking are promoted:

- It composes only `AgentMemoryRagTraceSetCurationContractService.contract()`, `AgentMemoryRagEvalSuiteBindingContractService.contract()`, and `AgentMemoryRagReadinessService.readiness()`.
- It publishes three Vue curation cards for citation fidelity, privacy/tenant isolation, and lifecycle policy.
- It publishes `suiteLatchCard`, `disabledRuntimeActions`, `renderHints`, `workbenchPolicy`, `safety`, and `privacy`.
- It makes candidate discovery, curation review, trace-set gate, and gate bundle explicit disabled actions for the UI.
- It integrates with the Vue readiness control plane, Phase 1 roadmap, Memory/RAG readiness, and the underlying curation contract endpoint map.
- Phase 2 NIM / HPC / Slurm / BCM remains paused and is not reopened by this workbench.

Technology judgment: Java/Spring remains the right Phase 1 mainline because the project needs a governed Agent control plane. The newest Agent technologies are not rejected; they are staged behind evidence. Spring AI RAG/VectorStore/MCP, MCP runtime tools/resources/prompts, OpenTelemetry GenAI adapters, OpenAI Agents SDK-style guardrails/tracing/handoffs/evals, A2A provenance, GraphRAG, rerankers, and vector stores move through contracts, read models, eval evidence, Vue visibility, and compatibility matrices before runtime authority.

Official references checked for this anchor:

- Spring AI reference: https://docs.spring.io/spring-ai/reference/
- Model Context Protocol specification: https://modelcontextprotocol.io/specification/2025-11-25
- OpenTelemetry GenAI semantic conventions: https://opentelemetry.io/docs/specs/semconv/gen-ai/
- OpenAI Agents SDK: https://openai.github.io/openai-agents-python/
- A2A protocol specification: https://a2a-protocol.org/latest/specification/

Next order after M5.72:

- Curate reviewed redacted trace IDs through human/Git review.
- Generate advisory Memory/RAG gate-bundle evidence only after reviewed trace IDs exist.
- Promote CI blocking only in a separate reviewed slice.
- Bind durable memory store and retrieval runtime only after source digest, lifecycle, tenant/privacy, eval evidence, Vue visibility, and recovery memory pass.
- Run separate compatibility spikes for Java 21/25, Spring Boot 4, Spring AI 2, MCP runtime, A2A, GraphRAG, rerankers, and vector stores.

## 2026-06-09 M5.71 Memory-RAG-Trace-Set-Curation-Contract Rule

M5.71 adds the backend-owned curation contract:

```text
GET /api/agent/observability/memory-rag/trace-set-curation-contract
```

The advanced Agent stack now has a Vue/Git-review contract that exposes Memory/RAG trace-set gaps before reviewed trace IDs or runtime authority can be promoted:

- It composes `AgentEvalTraceSetCatalogService.catalog()` and `AgentEvalSuiteCatalogService.catalog()` only.
- It reports `TRACE_SETS_DEFINED_REVIEWED_EVIDENCE_NOT_CURATED` in the current catalog state.
- It publishes a `suiteRuntimeLatch` for `memory-rag-release-gate`.
- It publishes three trace-set rows for `memory-rag-citation-fidelity`, `memory-rag-privacy-tenant`, and `memory-rag-lifecycle-policy`.
- Each row includes `rowStatus`, `policyKeysPresent`, `missingPolicyKeys`, `policyMismatches`, `policyLatchDeclaredClosed`, `blockedReasons`, and `missingEvidence`.
- Missing policy keys now fail closed as visible blockers. This prevents a catalog edit from silently relying on backend default values.
- The gate-bundle endpoint is exposed as a future-stage descriptor with `runtimeAllowedNow=false`, not as an enabled runtime button.
- Phase 2 NIM / HPC / Slurm / BCM remains paused and is not reopened by this contract work.

Technology judgment: the Java backend remains a strong main language for Phase 1 because the project is building a governed control plane, not just a prompt demo. Java/Spring gives typed contracts, mature security filters, deterministic tests, Actuator/Micrometer/OpenTelemetry integration, SBOM/quality gates, and maintainable enterprise deployment. The improvement path is not to replace Java; it is to let Java own the control plane while adopting the latest Agent runtimes through evidence-first adapters.

Latest stack posture checked on 2026-06-09:

- Current mainline: Java 17 runtime baseline, Spring Boot 3.5.x, Spring AI 1.1.x, Micrometer/OpenTelemetry bridge, deterministic eval contracts, SafeToolExecutor, durable audit/replay, MCP manifest/governance, and Vue-ready read models.
- Near-term compatibility matrix: Java 21/25 migration, Spring Boot 4, Spring AI 2, virtual threads, structured concurrency, OpenTelemetry GenAI adapters, full MCP runtime, A2A handoff/provenance, GraphRAG, rerankers, and vector stores.
- Adoption rule: major framework upgrades and Agent runtime authority must pass source-owned contracts, compatibility tests, security/eval gates, Vue operator visibility, and recovery checkpoints before becoming mainline.
- Do not blind-upgrade to Spring Boot 4 or Spring AI 2 from inside a Memory/RAG contract slice. Treat those as explicit compatibility-matrix slices with rollback and benchmark evidence.

Official references checked for this anchor:

- Spring Boot reference: https://docs.spring.io/spring-boot/
- Spring AI reference: https://docs.spring.io/spring-ai/reference/
- Model Context Protocol specification: https://modelcontextprotocol.io/specification/2025-11-25
- OpenTelemetry GenAI semantic conventions: https://opentelemetry.io/docs/specs/semconv/gen-ai/
- OpenAI Agents SDK: https://openai.github.io/openai-agents-python/
- A2A protocol specification: https://a2a-protocol.org/latest/specification/

Next order after M5.71:

- Wire Vue to render Memory/RAG trace-set curation rows and blocked reasons.
- Curate reviewed redacted trace IDs through Git review.
- Generate advisory Memory/RAG gate-bundle evidence only after reviewed trace IDs exist.
- Promote CI blocking only in a separate reviewed slice.
- Bind durable memory store and retrieval runtime only after source digest, lifecycle, tenant/privacy, eval evidence, Vue visibility, and recovery memory pass.
- Run separate compatibility spikes for Java 21/25, Spring Boot 4, Spring AI 2, MCP runtime, A2A, GraphRAG, rerankers, and vector stores.

## 2026-06-09 M5.70 Memory-RAG-Trace-Set-Catalog Rule

M5.70 advances the Memory/RAG roadmap with three Git-reviewed catalog lanes:

```text
memory-rag-citation-fidelity
memory-rag-privacy-tenant
memory-rag-lifecycle-policy
```

The advanced Agent stack now has concrete trace-set homes for Memory/RAG release evidence before retrieval runtime can influence prompts:

- All three trace sets bind to `memory-rag-release-gate`.
- All three have `traceIds=[]` until reviewed redacted evidence is curated.
- All three carry `catalogOnlyUntilReviewed=true`, `suiteRuntimeExecutionAllowed=false`, `runtimeRetrievalAllowed=false`, and `ciBlockingAllowed=false`.
- Trace-set gates for these rows return `SUITE_RUNTIME_DISABLED` with `suiteGate=null` because the attached Memory/RAG suite remains non-runnable.
- A second latch now protects the trace-set row itself: if the suite is later promoted but the row still has `suiteRuntimeExecutionAllowed=false` or `catalogOnlyUntilReviewed=true` with empty `traceIds`, the gate returns `TRACE_SET_RUNTIME_DISABLED`.
- The eval-suite binding contract now reports `TRACE_SETS_DEFINED_REVIEWED_EVIDENCE_NOT_CURATED`.
- The eval-suite binding contract derives Memory/RAG trace-set runtime, retrieval, and CI policy from the catalog rows, so policy drift becomes visible.
- `memoryRagTraceSetBound=false` still blocks retrieval, because no reviewed trace ids exist.
- Phase 2 NIM / HPC / Slurm / BCM remains paused and is not reopened by this catalog work.

Technology judgment: the project is now ready to curate Memory/RAG evidence, but not ready to execute Memory/RAG runtime. Current best practice for a top-tier Agent is to treat RAG source fidelity, privacy/tenant isolation, lifecycle/retention, red-team traces, deterministic evals, and operator visibility as prerequisites for vector-store or reranker authority. Spring AI VectorStore, GraphRAG, rerankers, MCP resources, OpenAI Agents/Evals-style runtime loops, A2A provenance, and OpenTelemetry GenAI adapters stay in the evidence-first lane until these trace sets contain reviewed redacted anchors.

Latest-technology anchor checked on 2026-06-09:

- OpenAI Agents SDK-style tools, handoffs, guardrails, sessions, tracing, HITL, and eval loops map to kube-agent contracts, replay evidence, and workbench gates before runtime delegation expands.
- Spring AI ChatClient, advisors, chat memory, RAG, VectorStore, MCP, eval, and observability remain the preferred Java/Spring integration surface once compatibility and evidence gates pass.
- MCP tools/resources/prompts, consent, and tool-safety guidance map to SafeToolExecutor, admin-only catalogs, and future MCP governance.
- OpenTelemetry GenAI agent/model spans and metrics stay adapter-level because the conventions are still evolving; `atlas.agent.*` remains the stable internal contract.
- A2A Agent Card, task, message, artifact, streaming, and security concepts map to future handoff/provenance work after local evidence gates mature.
- GraphRAG, rerankers, and vector stores remain Phase 1 core targets, but only after reviewed trace ids and Memory/RAG advisory gate bundles exist.

Official references checked for this anchor:

- OpenAI Agents SDK: https://openai.github.io/openai-agents-python/
- Spring AI reference: https://docs.spring.io/spring-ai/reference/
- Model Context Protocol specification: https://modelcontextprotocol.io/specification/2025-11-25
- OpenTelemetry GenAI semantic conventions: https://opentelemetry.io/docs/specs/semconv/gen-ai/
- A2A protocol specification: https://a2a-protocol.org/latest/specification/
- Microsoft GraphRAG: https://microsoft.github.io/graphrag/

Next order after M5.70:

- Curate reviewed redacted trace ids for `memory-rag-citation-fidelity`, `memory-rag-privacy-tenant`, and `memory-rag-lifecycle-policy`.
- Generate advisory Memory/RAG gate-bundle evidence only after curated traces exist.
- Add Vue workbench visibility for Memory/RAG trace-set state and blockers.
- Promote CI blocking only in a separate reviewed slice.
- Bind durable memory store and retrieval runtime only after source digest, lifecycle, tenant/privacy, eval evidence, Vue visibility, and recovery memory pass.

## 2026-06-09 M5.69 Memory-RAG-Release-Gate-Suite-Catalog Rule

M5.69 advances the Memory/RAG roadmap with a built-in deterministic suite:

```text
memory-rag-release-gate
```

The advanced Agent stack now has a concrete Memory/RAG release-gate suite catalog entry before retrieval runtime can influence prompts:

- The suite defines all nine Memory/RAG gate check codes required by M5.62 and mapped by M5.68.
- The default minimum score is `95`, and `failOnWarnings=true`.
- The suite is catalog-only and non-runnable in M5.69: `runtimeExecutionAllowed=false`.
- The eval-suite binding contract now reports `SUITE_CHECKS_DEFINED_TRACE_SETS_NOT_CURATED`.
- `memoryRagEvalSuiteBound=true` means check-code mapping only. It does not mean trace-set evidence is curated, eval runtime is executed, CI blocking is enabled, or retrieval is allowed.
- `memoryRagTraceSetBound=false`, `evalRuntimeExecuted=false`, `ciBlockingEnabled=false`, `retrievalRuntimeAllowedNow=false`, `mappedGateCheckCount=9`, and `missingGateCheckCount=0`.
- Existing named suite runtime endpoints reject `memory-rag-release-gate` until reviewed trace-set evidence and a later explicit advisory runtime slice exist.
- Phase 2 NIM / HPC / Slurm / BCM remains paused and is not reopened by this catalog work.

Technology judgment: the project should keep treating "latest technology" as an evidence-first adoption discipline. Responses/Agents-style orchestration, Spring AI Memory/RAG/MCP/eval/observability, MCP latest tools/resources/prompts governance, OpenTelemetry GenAI telemetry, A2A handoff/provenance, GraphRAG, rerankers, vector stores, and future Java/Spring upgrades should pass through compatibility matrices, deterministic contracts, reviewed trace evidence, Vue read models, and recovery checkpoints before becoming runtime authority.

Next order after M5.69:

- Add Git-reviewed trace-set catalog entries for `memory-rag-citation-fidelity`, `memory-rag-privacy-tenant`, and `memory-rag-lifecycle-policy`.
- Curate reviewed redacted trace ids.
- Add advisory Memory/RAG gate-bundle and Vue workbench visibility.
- Promote CI blocking only in a separate reviewed slice.
- Bind durable memory store and retrieval runtime only after source digest, lifecycle, tenant/privacy, eval evidence, Vue visibility, and recovery memory pass.

## 2026-06-09 M5.68 Memory-RAG-Eval-Suite-Binding-Before-Retrieval Rule

M5.68 advances the fourth Phase 1 roadmap step with:

```text
GET /api/agent/observability/memory-rag/eval-suite-binding-contract
```

The advanced Agent stack now has an explicit Memory/RAG eval-suite binding contract before retrieval runtime can influence prompts:

- The nine Memory/RAG gate checks from M5.62 must be mapped to deterministic suite check codes.
- Future trace sets must cover citation fidelity, privacy/tenant isolation, and lifecycle policy evidence.
- At M5.68, built-in suites did not yet contain the required Memory/RAG check codes, so the binding remained `CONTRACT_DEFINED_NOT_BOUND`.
- CI blocking, eval runtime execution, retrieval runtime, vector-store binding, and prompt influence remain closed.
- Vue can render exactly which gate checks and trace-set evidence are missing instead of guessing from scattered endpoints.

Technology judgment: latest Agent technology is now represented as evidence contracts, not unchecked runtime wiring. OpenAI Agents SDK / Responses patterns, Spring AI Memory/RAG/MCP APIs, MCP latest tools/resources/prompts semantics, OpenTelemetry GenAI spans, and A2A artifact provenance are all Phase 1 targets. They become production authority only after deterministic suites, reviewed redacted traces, audit/replay evidence, Vue operator visibility, and recovery memory are in place.

Official-version check on 2026-06-09:

- OpenAI official Agent docs and Agents SDK docs emphasize tools, handoffs, guardrails, sessions, tracing, and eval loops as first-class Agent surfaces.
- Spring AI official reference covers ChatClient, advisors, chat memory, VectorStore RAG, MCP, eval, and observability; the project keeps the verified Spring AI 1.1.x mainline and tracks Spring AI 2.x in the compatibility matrix.
- MCP latest specification is `2025-11-25`; tools, resources, and prompts remain governed protocol surfaces rather than direct authority.
- OpenTelemetry GenAI semantic conventions are still development-stage; `atlas.agent.*` remains the stable internal contract and maps outward through adapters.
- A2A v1.0 exposes Agent Card, tasks, messages, streaming, artifacts, and security concepts; Phase 1 tracks this as future handoff/provenance after local evidence gates mature.

## 2026-06-09 M5.67 Release-Blocking-Eval-Gate-Before-CI Rule

M5.67 advances the third Phase 1 roadmap step with:

```text
GET /api/agent/observability/eval/release-blocking-gate-contract
```

The advanced Agent stack now has a release-blocking gate contract before CI can consume eval artifacts as blockers:

- Reviewed redacted trace evidence and gate-bundle release eligibility must both be true.
- Empty trace sets keep the release gate closed.
- Human Git review remains required.
- The CI blocking switch is intentionally absent in this slice.
- Runtime authority remains unchanged even when future evidence is ready.

Technology judgment: current Agent best practice is not "turn on every new runtime." It is to convert advanced capabilities into governed evidence: OpenAI-style tracing/evals/guardrails, MCP tool safety, OpenTelemetry GenAI spans, OWASP LLM safety gates, and W3C trace context all feed a server-owned release contract first. Only after the contract is reviewed should CI wiring or runtime authority be considered.

## 2026-06-09 M5.66 Reviewed-Trace-Evidence-Before-Release-Gates Rule

M5.66 advances the second Phase 1 roadmap step with:

```text
GET /api/agent/observability/eval/reviewed-trace-evidence
```

The advanced Agent stack now has a release-evidence contract:

- Reviewed redacted trace anchors must exist before eval gates can become release-blocking.
- Candidate discovery, curation review, Vue catalog patch review, human Git review, and gate bundle regeneration are separate evidence stages.
- Empty trace sets intentionally keep `reviewedEvidenceReady=false`, `releaseBlockingAllowedNow=false`, and `ciBlockingEnabled=false`.
- Future reviewed anchors still do not automatically enable release blocking; that promotion must be an explicit later slice.

Technology judgment: modern Agent engineering treats traces as release artifacts. OpenAI Agents-style tracing/evals, MCP tool governance, OTel GenAI telemetry, OWASP LLM security gates, and W3C trace context all point to the same rule: runtime authority should expand only after reviewed evidence proves behavior, privacy, tenant isolation, and replayability.

## 2026-06-09 M5.65 Vue Readiness Control Plane Gate

M5.65 advances the first M5.64 roadmap step with:

```text
GET /api/agent/observability/top-tier/vue-readiness-control-plane
```

The advanced technology stack now has a frontend binding contract:

- Vue should render backend-owned read models before any runtime control.
- Each dashboard must have explicit primary endpoint and render fields.
- Operator states must distinguish ready, partial, blocked, contract-defined-not-bound, and Phase 2 paused.
- The UI must not expose write retry toggles, state-changing kube-manager calls, MCP runtime `tools/call`, retrieval prompt influence, CI blocking switches, HITL triggers, durable receipt issuance, or dependency upgrades.

Technology judgment: modern Agent UX is not a generic admin panel. It is a governed operator control plane. The backend owns the state model and safety boundary; Vue renders evidence and blockers first.

## 2026-06-09 M5.64 Phase 1 Execution Roadmap Gate

M5.64 adds the backend-owned roadmap endpoint:

```text
GET /api/agent/observability/top-tier/phase1-execution-roadmap
```

This makes the advanced-tech strategy executable in order:

- First wire Vue to backend-owned read models: readiness overview, advanced technology adoption, phase roadmap, kube-manager governance, Memory/RAG readiness, eval workbench, and MCP governance.
- Then curate reviewed redacted eval trace evidence.
- Then promote deterministic eval gates from advisory evidence to reviewed release gates.
- Then bind Memory/RAG eval suites, durable memory lifecycle evidence, and retrieval only after citation/source/digest/privacy gates are ready.
- Then prototype MCP runtime `tools/list` and `tools/call` only through identity, consent, HITL, audit, eval, rate limits, and `SafeToolExecutor`.
- Then add Agent handoff/A2A provenance after local authority and eval evidence are stable.

Technology judgment: this is the practical way to "introduce all advanced technologies" without lowering Phase 1 quality. The mainline remains buildable and auditable while latest Spring AI, MCP, Responses/Agents-style tracing/tools/handoffs, OTel GenAI, A2A, GraphRAG, rerankers, and vector stores move through evidence gates.

Do not start yet: NIM runtime reopening, HPC/Slurm/BCM plugins, kube-manager state-changing writes, unsafe MCP tool calls, retrieval prompt influence before eval gates, or blind Spring Boot 4 / Spring AI 2 mainline migration.

Official-version check refreshed by M5.81 on 2026-06-10:
- Spring Boot docs list `4.0.6` and `3.5.14` as stable lines.
- Spring AI docs list `1.1.7` as the stable line and `2.0.0-RC2` as preview/compatibility-matrix work.
- MCP 2025-11-25 remains the latest tracked protocol snapshot; runtime `tools/call` still requires human-in-the-loop safety expectations and local authority binding.
- OpenTelemetry GenAI semantic conventions remain `Development`, so internal stable fields stay the mainline contract.

## 2026-06-09 M5.63 Advanced Technology Adoption Gate

M5.63 makes the advanced-technology strategy queryable from the backend:

```text
GET /api/agent/observability/top-tier/advanced-technology-adoption-contract
```

The key decision is to keep two lanes:

- Stable mainline: Java 17, Spring Boot 3.5.x, Spring AI 1.1.x, SafeToolExecutor, deterministic eval, trace/audit/replay, Memory/RAG contracts, MCP governance, kube-manager governance.
- Compatibility matrix: Java 21/25/26, Spring Boot 4, Spring AI 2, Responses/Agents runtime mapping, full MCP runtime, OTel GenAI semantic conventions, A2A, GraphRAG, rerankers, and vector stores.

The backend now exposes this distinction as a contract instead of burying it in docs. That matters because "latest technology" is now a gated engineering policy:

- no blind major-version upgrades;
- no prompt-only security;
- no external protocol authority bypass;
- no vector-first RAG before citations, digests, lifecycle, and eval gates;
- no Vue runtime control before backend read-model evidence;
- no Phase 2 NIM / HPC / Slurm / BCM scope creep.

Teaching conclusion: Java/Spring is not old-fashioned in this project; it is the control-plane spine. The advanced work is to integrate new Agent protocols and runtimes through contracts, evals, audit, replay, and governance, not to replace the control plane with an unverified runtime stack.

## 2026-06-09 M5.62 Eval-Gate-Before-Retrieval Rule

M5.62 refines the Memory/RAG roadmap again: future retrieval must be eval-gated before it is runtime-bound.

Current mainline:
- `GET /api/agent/observability/memory-rag/eval-gate-contract` exposes the admin-only read model.
- `GET /api/agent/observability/memory-rag/readiness` reports `memoryRagEvalGateContractDefined=true` and `memoryRagEvalGateContractBound=false`.
- The top-tier readiness overview requires `bind-memory-rag-eval-gate-before-retrieval-runtime`.
- Runtime eval execution, CI blocking promotion, trace evidence mutation, retrieval runtime, and prompt evidence injection remain closed.

Required eval gate chain:
- reviewed redacted trace set id;
- server-owned Memory/RAG eval suite id;
- source evidence digest and durable lifecycle digest;
- retrieval policy digest and tenant partition digest;
- expected citation seed and redaction policy digest;
- citation fidelity, source digest integrity, privacy leakage, tenant isolation, retention/staleness, delete/export/recovery proof, retrieval policy budget, unsupported-answer, and prompt-injection checks.

Compatibility matrix:
- OpenAI Agents SDK-style guardrails and tracing can require these gates before handoff or prompt evidence injection;
- MCP resources and future tool results can be evaluated without granting MCP `tools/call` authority;
- A2A artifacts can carry gate evidence as cross-agent provenance;
- Spring AI VectorStore metadata can later carry gate digests and retrieval policy digests;
- OTel GenAI eval/retrieval spans can map stable internal gate fields after the semconv line stabilizes.

Teaching conclusion: advanced RAG is eval-gated RAG. Retrieval is not eligible because it is semantically close; it is eligible only after deterministic checks prove citation, source custody, privacy, tenant isolation, lifecycle validity, and policy-budget compliance.

## 2026-06-09 M5.61 Durable-Memory-Lifecycle-First Rule

M5.61 refines the Memory/RAG roadmap again: future persistent memory must be lifecycle-first before it is storage-first or vector-first.

Current mainline:
- `GET /api/agent/observability/memory-rag/durable-memory-lifecycle-contract` exposes the admin-only read model.
- `GET /api/agent/observability/memory-rag/readiness` reports `durableMemoryLifecycleContractDefined=true` and `durableMemoryLifecycleContractBound=false`.
- The top-tier readiness overview links the new lifecycle contract from the Memory/RAG card.
- Durable store, retrieval runtime, delete/export/recovery jobs, and prompt evidence injection remain closed.

Required lifecycle chain:
- stable `memoryRecordId`;
- tenant partition digest derived from trusted principal / organization / policy facts;
- source evidence digest from M5.60;
- retention policy id with TTL and legal-hold separation;
- delete tombstone proof digest;
- redacted export proof digest;
- recovery checkpoint digest;
- eval gate digest before prompt influence.

Compatibility matrix:
- Spring AI `VectorStore` metadata can later carry lifecycle fields alongside source/chunk digests;
- MCP resources can expose memory/resource metadata without opening runtime `tools/call`;
- A2A artifacts can carry export/recovery proof digests for cross-agent provenance;
- OTel GenAI retrieval spans can map lifecycle digests without raw memory/source text;
- OpenAI Agents SDK-style tracing and guardrails can require lifecycle evidence before handoff or prompt evidence injection.

Teaching conclusion: advanced durable memory is not "pick PostgreSQL, Redis, or a vector DB." It is a lifecycle protocol. Storage is implementation detail; top-tier behavior comes from tenant isolation, retention, delete/export proofs, recovery, eval gates, and operator visibility.

## 2026-06-09 M5.60 Source-Evidence-Digest-First RAG Rule

M5.60 refines the Memory/RAG roadmap from citation-first to digest-first + citation-first.

Current mainline:
- `MemoryRagSourceEvidenceDigestDeriver` defines pure Java source/chunk/evidence digest derivation.
- `GET /api/agent/observability/memory-rag/source-evidence-digest-contract` exposes the admin-only read model.
- `GET /api/agent/observability/memory-rag/readiness` reports `sourceEvidenceDigestContractDefined=true` and `sourceEvidenceDigestContractBound=false`.
- Retrieval runtime remains closed.

Required digest chain:
- stable source id and bounded source type;
- source URI digest, tenant scope digest, source ACL digest;
- redaction status and redaction policy digest;
- retention policy;
- redacted source content and metadata digests;
- redacted chunk digest;
- retrieval policy digest;
- server-derived `sourceDigest`, `chunkDigest`, `evidenceDigest`, and `citationSeed`.

Compatibility matrix:
- Spring AI `VectorStore` metadata can carry digest fields without raw source leakage;
- MCP resources and future `tools/call` results can map to the same source evidence envelope;
- A2A task artifacts can reference `evidenceDigest` for cross-agent provenance;
- OTel GenAI retrieval spans can emit stable digest anchors instead of raw prompt/source text;
- OpenAI-style Agent guardrails can require digest evidence before handoff or prompt evidence injection.

Teaching conclusion: advanced RAG is not "turn on embeddings." It is a signed-looking, deterministic, tenant-scoped evidence chain. M5.60 keeps the production mainline safe while preparing the exact fields that future VectorStore, GraphRAG, reranker, MCP, A2A, OTel, and guardrail layers will consume.

## 2026-06-09 M5.59 Citation-First RAG Rule

M5.59 refines the Memory/RAG roadmap again: future retrieval must be citation-first and source-digest-first.

Current mainline:
- `GET /api/agent/observability/memory-rag/citation-source-contract` defines source/citation fields.
- `GET /api/agent/observability/memory-rag/readiness` now reports `citationSourceContractDefined=true`.
- Retrieval runtime remains closed.

Required source chain:
- source identity and bounded source type;
- redacted source digest and chunk digest;
- tenant/principal scope;
- redaction status;
- retention/delete/export policy;
- citation id and freshness metadata;
- eval coverage before runtime binding.

Compatibility matrix:
- Spring AI VectorStore and document metadata mapping;
- hybrid retrieval with digest-preserving chunks;
- reranker outputs with citation-preserving provenance;
- GraphRAG source graph edges with tenant ACLs;
- OTel GenAI retrieval spans mapped from stable internal evidence fields.

Teaching conclusion: advanced RAG is a custody protocol. Search quality matters, but a top-tier Agent first needs evidence identity, tenant isolation, redaction, citations, freshness, evals, and operator visibility.

## 2026-06-09 M5.58 Memory/RAG Advanced Technology Rule

M5.58 adds the Memory/RAG readiness contract and refines the advanced-technology rule for the learning layer:

```text
advanced Memory/RAG direction
        |
        +-- current mainline: safe summary memory + readiness contract
        |
        +-- required before runtime: durable store + tenant partition + citation + eval
        |
        +-- compatibility matrix: Spring AI VectorStore, GraphRAG, reranker, hybrid search
```

Current mainline:
- `ConversationSummaryMemoryStore` remains the only active memory store.
- `GET /api/agent/observability/memory-rag/readiness` reports readiness and gaps.
- The endpoint is admin-only, read-only, no-retrieval, no-LLM, no-vector-store, no-kube-manager-call.

Required before runtime Memory/RAG:
- durable memory store with retention, delete, export, and recovery metadata;
- tenant-aware persistent partitioning and per-source ACLs;
- redacted ingestion pipeline for kube-manager docs/runbooks and future operator evidence;
- citation/source contract with source type, digest, tenant scope, redaction status, and prompt evidence budget;
- deterministic evals for citation fidelity, privacy leakage, tenant isolation, and stale retrieval;
- Vue readiness workbench that renders backend-owned state without creating retrieval/write authority.

Compatibility matrix:
- Spring AI VectorStore abstraction;
- hybrid lexical/vector retrieval;
- reranker and multi-vector retrieval;
- GraphRAG and source graph enrichment;
- OpenTelemetry GenAI retrieval span mapping;
- Java 21/25 and Spring Boot 4 / Spring AI 2 compatibility branches.

Teaching conclusion: "引入全部最先进技术" does not mean enabling every retrieval component today. For top-tier Agent work, advanced means every memory or retrieved fact is governed by owner, source, retention, citation, privacy, eval, and replay evidence before it can affect runtime answers.

## 2026-06-09 M5.57 最新顶级技术引入规则

M5.57 把“引入全部最先进的技术，然后完成最新修订的终极目标”落成一条工程规则：

```text
advanced technology
        |
        +-- stable mainline: 当前能构建、测试、审计、回放、评测、恢复
        |
        +-- compatibility matrix: 技术方向先进，但还需要版本、运行时、依赖生态验证
```

主线已经承接的先进能力：
- Spring Security URL + method 双层授权；
- trusted principal 与租户/资源归属校验；
- `SafeToolExecutor` 作为唯一真实 Tool 执行边界；
- trace、redacted audit、durable audit readiness、replay timeline、deterministic eval gate；
- Resilience4j 治理 kube-manager HTTP outlet，读重试，写请求不自动重试；
- kube-manager / eval / MCP / top-tier readiness 的后端拥有型治理 workbench read model；
- MCP safe manifest 与 governance overview，暂不开放 runtime `tools/call`；
- workspace-local recovery memory 与 SHA256 恢复清单。

兼容矩阵继续跟进：
- Java 21 / Java 25 / Java 26 toolchain；
- Spring Boot 4 / Spring Framework 7；
- Spring AI 2.x 与 Spring AI Alibaba 后续兼容性；
- 完整 MCP runtime server / broker、`tools/list`、`tools/call`、structured output、annotations、consent、rate limits、SafeToolExecutor binding；
- OpenTelemetry GenAI semantic conventions 从内部 `atlas.agent.*` 字段迁移；
- A2A / Agent Card / 多 Agent 互操作；
- GraphRAG、reranker、多向量库、可引用证据质量；
- virtual threads / structured concurrency，等待运行时和依赖行为压测。

二期暂停范围仍然是 NIM / HPC / Slurm / BCM。暂停的是专家域插件，不是一期顶级标准。

教学结论：顶级不是今天把所有最新框架都压进生产主线，而是让每个新能力都经过 source-owned contract、redaction、identity、audit、replay、eval、frontend governance、recovery memory 和可回滚升级路径。

2026-06-09 官方资料复核：
- Spring Boot 官方文档显示 4.0.x 与 3.5.x 都在稳定文档线中，且 Boot 4 需要 Java 17+，所以它进入兼容矩阵而不是盲目主线替换。
- Spring AI 官方 2.0 文档说明 2.0.x 支持 Spring Boot 4.x，但该线仍在 development；文档同时提示最新稳定版本使用 1.1.7。
- MCP tools 规范明确了 `tools/list`、`tools/call`、`outputSchema`、`structuredContent` 与 annotations，所以一期需要继续朝 MCP 兼容演进，但执行层必须经 SafeToolExecutor / HITL / audit / eval 绑定。
- OpenTelemetry GenAI semantic conventions 仍标注 `Status: Development`，所以项目继续用内部 `atlas.agent.*` 稳定字段承接证据，再以兼容层逐步映射。

## 当前可落地先进线

已采用的第一批先进工程底座：

- Spring Boot 3.5.x 稳定线；
- Spring AI 1.1.7 补丁线；
- Java 17 作为当前可构建基线；
- Maven Enforcer 锁定 Java / Maven 最低版本；
- JaCoCo 生成覆盖率报告；
- CycloneDX 生成 SBOM；
- SpotBugs 进入 quality profile；
- Surefire / Failsafe 显式进入构建；
- Micrometer Tracing + OpenTelemetry OTLP 依赖进入可观测底座；
- Resilience4j 进入 HTTP 出口韧性治理底座；
- Testcontainers 进入后续真实依赖集成测试底座；
- GitHub Actions 后端质量门禁。

M5.23-1 已把可观测能力从“依赖底座”推进到“运行时内核”：

- `AgentTraceContext` 统一生成、绑定、恢复 traceId；
- traceId 进入 MDC、SafeToolExecutor、Graph state、ReAct timeline、SSE trace event、HITL resume 和 ToolCallback；
- 外部 trace 候选值经过长度/字符集/空白控制字符校验，避免日志和 MDC 注入；
- traceId 被视为控制平面字段，不作为业务 Tool 参数透传。

M5.24-1 已把 trace 内核推进到 kube-manager HTTP outlet：

- `AgentTraceContext` 可以把内部 `trc_ + 32hex` 转换为标准 W3C `traceparent`；
- `KubeManagerHttpClient` 的用户业务请求统一传播 `X-Trace-Id` 与 `traceparent`；
- GET / POST / PATCH / PUT / DELETE / `resolveOrgId` 都走同一个 header helper；
- fallback login 暂不接入业务 trace helper，避免混淆认证 bootstrap 与用户 Tool 调用；
- 源码契约禁止未来重新手写业务 `X-Token` header 而漏掉 trace。

M5.25-1 已把 trace 内核推进到 Agent 审计证据层：

- 新增 `AgentAuditEvent` / `AgentAuditOutcome` / `AgentAuditRecorder` / `AgentAuditSnapshotProvider`；
- `SafeToolExecutor` 对成功、业务失败、权限/HITL/schema 阻断、Tool 异常都记录 trace-aware audit event；
- 审计事件绑定 Tool 风险元数据、执行来源、租户/用户上下文、参数摘要和结果词表；
- 参数摘要不保存参数值，observability snapshot 只暴露脱敏诊断摘要；
- diagnostic snapshot 提供 `schemaVersion`、`generatedAt` 与 `replayCapabilities`，为前端回放、OpenTelemetry event/span 映射和后续持久化审计定义最小稳定契约；
- 当前实现仍是 in-memory diagnostic recorder，后续需要接入 durable storage、OpenTelemetry event/span 和前端回放。

M5.26-1 已把审计事件推进到遥测投影层：

- 新增 `AgentAuditTelemetryProjection` / `AgentAuditTelemetryProjector`；
- 稳定内部属性使用 `atlas.agent.*` 命名空间，作为前端回放、durable audit 和 Agent eval 的长期契约；
- OTel / GenAI 相关字段放入 `experimentalOtelAttributes`，作为可迁移兼容层；
- admin 观测快照的 recent audit summary 现在携带 telemetry projection；
- 投影层不携带 raw principal、raw reason、endpoint 字符串或参数值。

M5.27-1 已把审计遥测投影推进到 Micrometer Observation 发布层：

- 新增 `AgentAuditTelemetryPublisher`，Observation 名称为 `atlas.agent.audit`，事件名称为 `atlas.agent.audit.recorded`；
- `InMemoryAgentAuditRecorder` 在写入内存诊断快照后发布 Observation，发布失败不影响 Tool 执行结果或 audit snapshot；
- low-cardinality 标签只包含 bounded enum/boolean/名称类字段，高波动的 `auditId`、`traceId`、时间和计数进入 high-cardinality key values；
- publisher 只消费 M5.26 的脱敏投影，不导出 raw principal、conversation、reason、endpoint 或参数值；
- 当前仍是诊断/观测链路，后续高风险写路径必须增加 durable audit pre-write fail-closed gate。

M5.28-1 已把 Resilience4j 推进到 kube-manager 业务 HTTP 出口：

- 新增 `KubeManagerHttpResiliencePolicy`，让韧性语义成为显式代码边界，而不是散在方法注解上；
- GET 走 read policy：Retry + CircuitBreaker + Bulkhead；
- POST/PATCH/PUT/DELETE 走 write policy：CircuitBreaker + Bulkhead，不自动重试；
- 移除旧 `HttpRetryConfig` 和 Spring Retry 注解路径，避免写操作被统一方法注解误重试；
- 写请求重试继续 HOLD，直到 idempotency key、durable audit、HITL 和 release evidence 全部具备。

M5.49-1 已把 Resilience4j HTTP outlet 治理推进到 admin-only 可观测摘要：
- 新增 `GET /api/agent/observability/kube-manager/http-outlet/health-summary`，只读本地配置与 Resilience4j registry，不调用 kube-manager、`RestClient` 或 `/api/login`。
- 摘要暴露 redacted backend facts、GET read retry effective policy、WRITE no-auto-retry effective policy、circuit breaker state、bulkhead state、safety proof 和 privacy proof。
- 即使 `kubeManagerWrite` retry 实例存在，也标记为 `configuredButInactive=true`，避免操作员误以为写请求已经自动重试。
- 该端点不提供 ping、token refresh、fallback login、circuit breaker reset、bulkhead config change 或 enable write retry 动作。

M5.50-1 adds the next safe Resilience4j governance layer: an admin-only write retry readiness contract.
- New endpoint: `GET /api/agent/observability/kube-manager/http-outlet/write-retry-readiness`.
- It always reports `readinessVerdict=NOT_READY`, `writeRetryEnabled=false`, and `automaticWriteRetryAllowed=false`.
- It treats any configured `kubeManagerWrite` retry instance as configured-but-inactive evidence, not an active write retry path.
- It lists the release prerequisites for any future controlled write retry: server-derived idempotency key, durable prewrite receipt, HITL/release evidence, read-after-write verification, bounded retry predicate, operation allowlist/RBAC, compensation/replay evidence, CI gate, and operator observability.
- It does not call kube-manager, `RestClient`, `/api/login`, Tool execution, LLMs, external services, audit writers, or durable receipt writers.
- It does not mutate Retry/CircuitBreaker/Bulkhead registries and does not provide a runtime enable switch.

Technology judgment: this is the correct advanced-agent path for dangerous reliability features. Read retry can improve availability; write retry can amplify side effects. Therefore the project keeps write retry disabled while making the future enablement protocol visible, testable, and teachable.

M5.51-1 turns the first M5.50 prerequisite into a generic contract:
- New pure Java `KubeManagerWriteIdempotencyKeyDeriver` derives `km-write-v1-{sha256}` from trusted server-side evidence.
- The input contract requires audit receipt id/digest, request spec digest, principal fingerprint, organization fingerprint, operation type, HTTP method, path template, request body digest, and release evidence digest.
- Caller-provided idempotency keys are not represented in the input contract and cannot override the derived key.
- New admin-only endpoint `GET /api/agent/observability/kube-manager/http-outlet/write-idempotency-contract` describes the contract without exposing raw keys or raw evidence.
- The contract is not bound to `KubeManagerHttpClient`, does not inject headers, and does not enable write retry.

Technology judgment: this is how advanced Agent systems should introduce idempotency. The trusted evidence contract comes before HTTP binding and long before retry enablement.

M5.52-1 turns the next M5.50 prerequisites into source-owned contracts:
- `KubeManagerWriteSafetyContractCatalog` owns review-only allowlist entries and the generic post-write readback contract.
- `KubeManagerWriteOperationAllowlistEntry` and `KubeManagerPostWriteReadbackContract` keep write eligibility and readback verification as typed Java protocol objects.
- New admin-only endpoint `GET /api/agent/observability/kube-manager/http-outlet/write-operation-safety-contract` describes allowlist/RBAC/readback evidence without binding runtime writes.
- M5.50 readiness now reports allowlist/RBAC/readback contracts as existing but not bound to the HTTP outlet; runtime retry eligible write operation count remains `0`.
- The contract does not scan `ToolRegistry`, does not inspect `tool/impl`, does not call kube-manager, does not execute readback, does not issue audit receipts, and does not enable write retry.

Technology judgment: this is the correct advanced-agent pattern for dangerous write authority. OpenAI Agents SDK-style guardrails/tracing, MCP tool interoperability, OTel GenAI spans, and durable execution ideas all point toward the same requirement: runtime authority must sit behind explicit contracts, evidence, and observability, not behind prompt-only conventions.

M5.53-1 adds the retry governance layer before any runtime retry binding:
- `KubeManagerWriteRetryGovernanceCatalog` owns failure classes, the bounded retry predicate contract, and review-only compensation policies.
- New admin-only endpoint `GET /api/agent/observability/kube-manager/http-outlet/write-retry-governance-contract` reports `CONTRACT_DEFINED_NOT_BOUND`.
- Future retry candidates are documented for transient transport/gateway/rate-limit cases, but every failure class remains `runtimeRetryableNow=false`; runtime retryable failure class count is `0`.
- Never-retry categories include caller validation errors, authn/authz denial, tenant/ownership mismatch, conflicts, and unknown acceptance without readback.
- Compensation policies are operator-review-only; no automatic compensation policy, compensation executor, release switch, Resilience4j predicate binding, HTTP outlet binding, readback execution, or write retry enablement is added.

Technology judgment: top-tier Agent retry governance should be explicit before it is executable. The system now has typed contracts for idempotency, operation allowlist/RBAC/readback, failure classification, bounded retry predicate, and compensation guidance, but the runtime remains fail-closed until durable receipts, readback evidence, HITL/release evidence, eval gates, and operator observability are bound together.

M5.54-1 adds the release gate layer before any runtime write binding:
- `KubeManagerWriteReleaseGateCatalog` owns durable prewrite receipt and HITL/release evidence contracts.
- New admin-only endpoint `GET /api/agent/observability/kube-manager/http-outlet/write-release-gate-contract` reports `CONTRACT_DEFINED_NOT_BOUND`.
- The durable receipt contract lists required digest fields, but `issuerExists=false` and `durableStorageMutationAllowed=false`.
- The release evidence contract requires server HITL confirmation, reviewer/release decision digest, eval gate bundle digest, operation safety digest, retry governance digest, operator intent digest, and tenant ownership digest.
- Caller flags, LLM approval text, frontend checkbox-only claims, durable executor success claims, legacy migration reports, and post-write success responses are rejected as release evidence sources.
- No HITL invocation, audit write, durable receipt issuance, release signature, runtime release switch, HTTP outlet binding, or write retry enablement is added.

Technology judgment: this closes another dangerous gap in the generic write chain. A top-tier Agent must not let prompt text, UI flags, or successful side effects become release authority. Release authority must be a typed evidence contract that can later be bound to durable audit, HITL, eval gates, and Git/release review.

M5.55-1 adds the frontend workbench layer for this governance chain:
- New admin-only endpoint `GET /api/agent/observability/kube-manager/http-outlet/governance-workbench/overview` composes M5.49-M5.54 into one Vue-ready read model.
- The overview exposes six governance cards, recommended workflow, next actions, policy proof, and aggregate privacy proof.
- Current state remains `WRITE_GOVERNANCE_NOT_READY`: `boundRuntimeContractCount=0`, `releaseGateOpen=false`, `writeRetryEnabled=false`, and `automaticWriteRetryAllowed=false`.
- The endpoint is frontend-navigation-only. It does not call kube-manager, execute Tools, invoke HITL, issue receipts, mutate durable storage, mutate Resilience4j, open a release gate, or enable write retry.

Technology judgment: advanced Agent platforms need UI-level governance contracts, not just backend endpoints. This keeps `vue-kube-manager` from guessing scattered safety states and keeps all write authority behind future code review, durable evidence, eval gates, and release review.

M5.56-1 adds the next MCP interoperability layer without opening execution:
- New authenticated endpoint `GET /api/agent/mcp/governance/overview` composes the existing safe MCP manifest into a governance read model.
- The overview reports `governanceStatus=MANIFEST_ONLY_NOT_CALLABLE`, `manifestMode=safe-readonly-manifest`, exported/blocked tool counts, governance cards, blocked capabilities, future enablement protocol, safety proof, and privacy proof.
- Current state remains discovery/governance only: `mcpServerRuntimeEnabled=false`, `toolsCallEnabled=false`, `externalToolExecutionEnabled=false`, and `callerProvidedToolCallAccepted=false`.
- The endpoint does not add a real MCP server, `tools/call`, streaming call plane, Tool execution, `SafeToolExecutor` invocation, HITL invocation, audit write, durable receipt issuance, runtime registry mutation, kube-manager call, `RestClient`, or `WebClient`.

Technology judgment: the latest MCP spec line makes `tools/list`, `tools/call`, structured content, annotations, output schema, and security controls first-class concerns. A top-tier Java Agent should adopt that direction through contract-first layers: safe manifest, governance overview, then a future separately reviewed `tools/call` binding through identity, tenant, consent, HITL, durable audit, eval gates, rate limits, and `SafeToolExecutor`.

M5.29-1 已把 Spring Security 推进到 HTTP 安全入口：

- 新增 `AgentSecurityConfig`，用 `SecurityFilterChain` 承接标准 Web 安全主线；
- `AuthTokenFilter` 作为 Security filter 注册，负责把 kube-manager Bearer session 从 `UserPermissionContext` 桥接成标准 `Authentication`；
- `/api/agent/observability/**` 与非 health/info 的 `/actuator/**` 已由 Spring Security admin role 保护；
- 关闭默认 basic/form/logout，并用显式 no-op `UserDetailsService` 避免 Spring Boot 生成默认开发用户；
- 普通 Agent API 暂时保持 `permitAll`，后续按端点和方法级授权逐步迁移。

M5.29-2 已把当前用户解析推进到统一 principal 层：

- 新增 `AgentPrincipal` / `AgentPrincipalResolver`；
- resolver 优先读取 Spring Security `Authentication`，忽略 anonymous，再回落 legacy `UserPermissionContext`；
- `ObservabilityController` 已迁移到 resolver，为后续 controller guard、audit actor、method security 统一读取当前主体打底。

M5.29-3 已把统一 principal 推进到审计 actor 证据层：

- `SafeToolExecutor` 在执行入口捕获 `AgentPrincipal` 快照，再绑定 Tool 执行兼容所需的 ThreadLocal；
- `AgentAuditEventFactory` 优先使用可信 principal 的 username / organizationId，避免 caller-supplied `SafeToolExecutionRequest.userId()` 成为审计权威；
- SecurityContext 主路径和 legacy UserPermissionContext 回落路径都有契约测试，保证迁移期间不打断 SSE/Tool 兼容入口。

M5.29-4 已把前端 `X-Session-Id` 会话推进到 Spring Security，并开启首批非聊天端点授权：

- `AuthTokenFilter` 在无 Bearer header 时通过 `SessionStore` 将 `X-Session-Id` 解析为服务端 `SessionData`，再生成标准 `Authentication`；
- Bearer header 继续作为优先身份来源；即使 Bearer 未知，也不自动降级到 `X-Session-Id`，避免多身份来源的权限/审计分裂；
- `/api/agent/memory/**` 与 `/api/agent/mcp/**` 已进入 `.authenticated()`；
- `MemoryController` 使用 `AgentPrincipalResolver` 的 username 作为长期记忆 owner，不再把 raw session id 作为身份；
- chat/SSE/conversation 暂不一起锁定，后续必须先迁移它们的数据归属语义，再进入 endpoint/method authorization。

M5.29-5 已把 conversation 元数据 owner 迁移到可信 principal：

- `ConversationController` 通过 `AgentPrincipalResolver` 解析 owner，不再把 raw `X-Session-Id` 或 `anonymous` 当作用户身份；
- create/list/detail/delete/title-update 全部以 principal username 做资源归属收敛；
- `/api/agent/conversations` 与 `/api/agent/conversations/**` 已进入 `.authenticated()`；
- chat/SSE 流式运行时仍作为独立 follow-up，因为那里还涉及 token/org/trace/SSE/Graph/ReAct 上下文传播。

M5.29-6 已把 Chat/SSE 流式运行时迁移到可信 runtime identity：

- `/api/agent/chat/stream`、`/api/agent/chat/graph`、`/api/agent/hitl/**` 已进入 `.authenticated()`；
- `AtlasOrchestrator` 从 `AgentPrincipalResolver` + `SessionStore` 解析 user/token/org，从 `ConversationStore` 校验 conversation owner；
- 请求体 `userId`、raw `X-Session-Id`、未校验 `conversationId` 不再决定运行时身份；
- SSE/Graph/HITL 使用 `run-*` / `graph-*` 作为非敏感关联 ID，不复用 raw `ses_*`；
- HITL resume 增加 checkpoint owner 校验，防止跨用户恢复执行。

M5.29-7 已把剩余 Agent API 面收口到默认认证兜底：

- `AgentSecurityConfig` 已启用 `@EnableMethodSecurity`；
- 除显式 bootstrap/compatibility whitelist 外，`/api/agent/**` 默认 `.authenticated()`；
- `ObservabilityController#snapshot()` 叠加方法级 admin guard，形成 URL matcher + method security 的双层保护；
- 新增 Agent Controller 默认不会因 `.anyRequest().permitAll()` 匿名暴露。

M5.30-1 已把 durable audit 推进到可验证底座：

- `AgentAuditDurableSink` 定义持久审计写入边界，后续可替换为数据库、搜索存储、Kafka 或安全日志服务；
- `JsonlAgentAuditDurableSink` 提供第一版 redacted append-only JSONL 实现；
- `InMemoryAgentAuditRecorder` 继续提供快速诊断快照，同时可选写入 durable sink；
- admin snapshot 新增 `durability` 与 `durableRetention` 能力描述；
- `SafeToolExecutor` 对高风险 `CREATE` / `UPDATE` / `DELETE` / `ACTION` / `PLACEHOLDER` 增加 durable audit readiness gate，生产可配置为缺少持久证据时 fail closed。

### M5.30-3 Durable Audit Prewrite Receipt

M5.30-3 已经把 durable audit 从 readiness gate 升级为 prewrite receipt gate：

- `AgentAuditDurableReceipt` 表示本次高风险调用已经获得持久审计预写回执。
- `AgentAuditOutcome.PREPARED` 表示执行前证据，不代表业务成功。
- `JsonlAgentAuditDurableSink` 使用 `recordPhase=PRE_EXECUTION` 和 `recordPhase=FINAL` 区分执行前证据与最终结果证据。
- `SafeToolExecutor` 在 `failClosedForHighRisk=true` 时要求高风险 Tool 执行前必须成功 `prewriteHighRisk(...)`，否则不调用真实 Tool。
- `operationType=UNKNOWN`、缺失 metadata、`operationType=null` 在强审计模式下全部 fail closed。

这说明当前 Java/Spring 主线不是落后，而是正在把现代 Agent 工程的关键能力变成可测试的安全协议：身份、HITL、trace、durable audit、redaction、admin query、pre-execution receipt 都已经进入同一条执行边界。下一步应继续补 database/search-backed audit query、retention/export、frontend replay timeline、Agent eval、RAG/persistent Memory 和 read-only MCP schema adapter。

## 最新 Agent 标准的落地顺序

以下技术代表 2026 年 Agent 工程的先进方向，但必须按可验证顺序接入：

| 标准/技术 | 一期定位 | 当前落点 | 下一步 |
|---|---|---|---|
| OpenTelemetry / GenAI semantic conventions | 统一观测模型 | 已有 Micrometer Tracing + OTLP 依赖，M5.23 建立 traceId 内核，M5.24 接入 HTTP outlet，M5.25 接入审计事件模型，M5.26 建立审计 telemetry projection，M5.27 发布审计 Observation；GenAI semconv 当前仍按实验/发展中标准对待 | 将 LLM、Tool、HTTP、HITL、audit 映射为同一 trace 下的 span/timeline，并保留属性名兼容层 |
| MCP (Model Context Protocol) | 外部 Tool / Resource / Prompt 暴露协议 | 暂不直接开放生产写工具；MCP 规范继续快速演进，最新规范要通过 manifest/schema adapter 消化 | 先做只读 Tool manifest 与 schema adapter，写工具继续 HITL/HOLD，调用层必须走 SafeToolExecutor |
| A2A (Agent2Agent) | 多 Agent 互操作协议 | 当前多专家流程仍以内部角色和 Graph 编排为主；A2A 作为 Phase 1 互操作实验轨，不替代安全执行边界 | 在执行边界、trace、audit 稳定后，评估 Agent Card / Task / streaming adapter |
| OWASP LLM / Agentic AI 安全实践 | 红队和安全门禁 | 已有 HITL、protected params、fail-closed、direct execute contract | 扩展 eval harness：prompt injection、tool misuse、excessive agency、sensitive data |
| Spring Boot 4 / Spring AI 2 | 下一代 Java Agent 栈 | 当前主线保持 Boot 3.5 + Spring AI 1.1.7 | 开兼容矩阵分支验证 Spring Framework 7、Tomcat 11、Spring AI Tool API |
| Java 21 / 25 | 运行时升级目标 | Java 17 仍是当前可构建底座 | CI matrix + 依赖兼容后再考虑主线升级 |

顶级 Agent 的“先进”不是堆满协议名，而是每个协议都能被安全边界、trace、审计、评测和恢复记忆承接。

## 官方版本依据

2026-06-10 M5.81 复核官方文档后的事实基线：

- Spring Boot 官方文档当前稳定线同时包含 `4.0.6` 和 `3.5.14`；`4.0.6` 需要 Java 17+，并要求 Spring Framework 7.0.7+。
- Spring AI 官方文档当前稳定线是 `1.1.7`，`2.0.0-RC2` 仍在 Preview 区域。
- Oracle Java SE 路线图将 Java SE 17、21、25 都列为 LTS，其中 Java 25 GA 于 2025-09，Premier Support 到 2030-09。
- MCP 官方规范持续迭代，M5.81 当前跟踪最新 `2025-11-25` 快照；本项目仍只把它作为受控外部 Tool 发现与调用协议接入，不能绕过权限、HITL、审计和 SafeToolExecutor。
- OpenTelemetry GenAI 语义约定对 Agent/LLM/Tool 很关键，但官方状态仍是 Development，并且要求现有 instrumentation 不要默认切到最新实验约定；本项目继续先以内部字段映射和兼容层落地，避免直接把发展中属性名固化成无法迁移的数据库契约。

因此，本项目主线当前采用 `Spring Boot 3.5.14 + Spring AI 1.1.7 + Java 17` 作为可验证稳定底座；`Spring Boot 4 + Spring AI 2 + Java 21/25` 进入兼容性矩阵和试验分支。

参考：

- [Spring Boot System Requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Boot Documentation Index](https://docs.spring.io/spring-boot/index.html)
- [Spring AI Reference](https://docs.spring.io/spring-ai/reference/index.html)
- [Oracle Java SE Support Roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)
- [Model Context Protocol Specification](https://modelcontextprotocol.io/specification)
- [OpenTelemetry GenAI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/)
- [Kubernetes Releases](https://kubernetes.io/releases/)
- [OpenAI Agents SDK](https://platform.openai.com/docs/guides/agents-sdk/)

## 2026-06-09 Latest Technology Baseline Refresh

最新官方资料再次确认：一期应该“引入全部先进能力”，但方式是主线可验证、兼容矩阵可试验。

主线继续推进：

- Spring Security method/URL 双层授权。
- Durable audit、pre-execution receipt、retention/export metadata、redacted query、replay timeline。
- Micrometer / OpenTelemetry：先保持内部 `atlas.agent.*` 稳定属性，再把 GenAI development 语义放到兼容层。
- MCP：先做 read-only manifest/schema adapter；`tools/call` 未来必须进入 `SafeToolExecutor`、HITL、trace、audit。
- Agent eval：把 trace replay、audit evidence、must-block red-team case 变成发布门禁。
- RAG / persistent Memory：基于 Spring AI VectorStore 抽象，但必须带租户隔离、引用证据、脱敏和可删除。
- Kubernetes 能力：以当前维护分支 1.36/1.35/1.34 为兼容目标，优先适配 read/query、Gateway API、状态回放、事件/日志/资源解释，不在一期恢复 NIM/HPC/Slurm/BCM 专项插件。

兼容矩阵继续验证：

- Java 21 / Java 25 LTS toolchain。
- Spring Boot 4 / Spring Framework 7 / Spring AI 2.x。
- OpenAI Agents SDK / Responses API 的 tracing、handoff、tool 调用思想。
- 完整 MCP broker、A2A / Agent Card、GraphRAG、reranker、多向量库、virtual threads / structured concurrency。

教学结论：所谓“最新技术”不是把所有 RC 或实验协议直接压进主线，而是让每项新能力都通过安全边界、测试、文档、恢复记忆和可回滚路径进入项目。M5.32-1 的 replay timeline 就是这种路线：先把证据语义稳定下来，再让前端、OTel、eval 和未来数据库索引复用。

## 为什么不直接把主线改成 Java 25 / Spring Boot 4

当前开发机 JDK 是 Java 17。直接把 `java.version` 提到 21 或 25 会让本地 `mvn test` 立即失效，破坏“每轮可验证、可提交、可恢复”的工程纪律。

Spring Boot 4.0.x 官方系统要求是 Java 17+，但它会同时带来 Spring Framework 7、Tomcat 11、Servlet 6.1 等生态跃迁。更关键的是，Spring AI 当前稳定线仍是 1.1.7，面向 Spring Boot 4 的 Spring AI 2.x 仍属于预览/候选线；项目内还必须确认 Spring AI Alibaba Agent / Graph、Knife4j、ONNX Runtime、DJL tokenizer、Actuator、Tracing 和大量 Tool 契约测试的兼容性。因此 Boot 4 / Spring AI 2 属于下一阶段兼容矩阵，而不是本轮直接强切。

顶级 Agent 的工程标准不是盲目追最新主版本，而是让升级路径被测试、文档、回滚和恢复记忆保护。

## 下一阶段迁移矩阵

| 阶段 | 目标 | 验收 |
|---|---|---|
| A | Java 17 + Spring Boot 3.5.x + Spring AI 1.1.7 | 当前主线全量测试、SBOM、JaCoCo、quality profile 可运行 |
| B | Java 21 / Java 25 toolchain 验证 | CI matrix 通过，虚拟线程/HTTP 客户端压测有结论，运行时镜像和开发机都可恢复 |
| C | Spring Boot 4 / Spring Framework 7 / Spring AI 2 试验分支 | 编译、单测、Spring AI Alibaba Graph、Knife4j、Actuator、Tracing 全部兼容 |
| D | Java 25 LTS 主线候选 | 仅在依赖生态、部署镜像、IDE、CI、观测和安全扫描全部明确支持后推进 |

## 一期顶级 Agent Core 技术欠账

- 统一执行内核：ReAct、Graph、ToolCallback、legacy fallback 已全部通过 `SafeToolExecutor`；后续新增入口必须继续受契约测试约束。
- HTTP 证据链：M5.24 已完成基础 trace header 传播，M5.25 已形成 auditId/traceId 事件内核；后续要补 idempotency key、tenant evidence、baggage 与真实 OpenTelemetry client span。
- HTTP 韧性：M5.28 已把 GET 接入 Resilience4j read retry/circuit/bulkhead；写请求接入 circuit/bulkhead 但默认不自动重试，后续必须绑定 idempotency key / audit / HITL 后才能考虑受控重试。
- 连接治理：从简单 request factory 过渡到连接池或 WebClient，并暴露连接池指标。
- OpenTelemetry：M5.23/M5.24/M5.25/M5.26/M5.27 已完成 traceId 内核、HTTP 出口传播、审计事件模型、审计 telemetry projection 和审计 Observation 发布；后续要把 intent、plan、tool、HTTP、HITL、audit、final answer 映射为 span/timeline/audit 统一证据链。
- 审计持久化：M5.25 已完成内存诊断 recorder，M5.30-1 已完成可插拔 durable sink、redacted JSONL 和高风险 fail-closed gate；下一步要增加 admin-only 查询 API、trace/audit 索引、保留策略和数据库/搜索存储替换实现。
- CI 门禁：SBOM、SCA、SpotBugs、覆盖率、secret scan、Agent eval 必须进入发布流程。
- 安全主干：M5.29-1 已引入 Spring Security `SecurityFilterChain` 并完成 observability/actuator 第一层保护；M5.29-2 已新增 `AgentPrincipalResolver` 统一当前主体解析；M5.29-3 已让 SafeToolExecutor 审计 actor 绑定统一 principal 快照；M5.29-4 已桥接前端 `X-Session-Id` 并把 memory/mcp 首批非聊天端点迁移到 `.authenticated()`；M5.29-5 已把 conversation 元数据 owner 迁移到 trusted principal 并保护 conversations 端点；M5.29-6 已迁移 chat/SSE/HITL runtime identity；M5.29-7 已把 `/api/agent/**` 收口为默认 authenticated 并开启方法级安全。后续重点转向更细粒度方法授权、durable audit、eval 和 replay。

## 多专家审计后的 Phase 1 技术优先级

2026-06-08 多专家审计结论：当前 Java / Spring 技术选型足够先进，短板不在“再堆新框架”，而在把已有先进底座真正接入 Agent 执行闭环。

2026-06-09 生产运维复核进一步补充：当前最需要进入主线的是 Spring Security 标准入口、durable audit、硬质量门禁、Resilience4j retry predicate、OTel span/timeline、RAG/Memory、Agent eval 和 read-only MCP schema adapter。Java 21/25、Spring Boot 4、Spring AI 2、A2A、完整 MCP broker、GraphRAG 和 virtual threads 继续走兼容矩阵，不直接压到可恢复主线。

| 优先级 | 技术任务 | 验收口径 |
|---|---|---|
| P0 | Resilience4j 真正治理 kube-manager HTTP outlet | M5.28 已完成 READ retry/circuit/bulkhead 与 WRITE no-auto-retry 第一层；下一步补 idempotency key、metrics 和高风险写 release 条件 |
| P0 | CI 从报告生成升级为硬门禁 | SpotBugs/SCA/secret scan/coverage/Agent eval 失败能阻断合并或发布 |
| P0 | `SafeToolExecutor` 唯一真实执行边界持续守护 | 新增 Graph/ReAct/ToolCallback/插件入口不能直调 `BaseTool.execute(...)` |
| P1 | Micrometer + OpenTelemetry span 化 | request、intent、plan、LLM、tool、HTTP、HITL、audit、final answer 能在同一 trace 下回放 |
| P1 | Spring Security 主线化 | M5.29-1 已完成 Bearer 身份桥接和诊断面保护；M5.29-2 已完成 principal resolver；M5.29-3 已完成审计 actor 可信快照；M5.29-4 已完成 `X-Session-Id` 会话桥接和 memory/mcp 首批 authenticated 端点；M5.29-5 已完成 conversation owner/endpoint 迁移；M5.29-6 已完成 chat/SSE/HITL runtime identity；M5.29-7 已完成 `/api/agent/**` 默认认证兜底和观测方法级 admin guard；下一步做更细粒度 method guard 与 durable audit API 授权 |
| P1 | Testcontainers 真实集成测试 | 覆盖 kube-manager HTTP contract、鉴权失败、trace header、重试/熔断边界 |
| P2 | Java 21/25 与 Spring Boot 4 / Spring AI 2 兼容矩阵 | 先在 CI matrix 或试验分支验证，不破坏当前可恢复主线 |

## 2026-06-09 最新多专家复核

Archimedes 复核后的结论与当前路线一致：一期主线不应该把“全部最先进技术”理解成一次性升级所有主版本，而是把能形成闭环的先进能力落到主线，把生态迁移和实验协议放入兼容矩阵。

主线继续推进：

- Spring Security 主线化；
- Resilience4j 真正治理 kube-manager HTTP outlet；
- Micrometer / OpenTelemetry 把 request、intent、plan、LLM、Tool、HTTP、HITL、audit、final answer 串成同一条 trace/timeline；
- durable audit 替换当前 in-memory diagnostic recorder；
- 最小 RAG：Spring AI VectorStore + kube-manager API / 运维 runbook 文档摄取 + 引用证据；
- persistent Memory：摘要记忆持久化、租户隔离、脱敏和可删除；
- MCP 先做 read-only manifest / schema adapter，未来 `tools/list` / `tools/call` 仍必须经过 `SafeToolExecutor`。

继续放入兼容矩阵：

- Java 21 / 25、Spring Boot 4、Spring Framework 7、Spring AI 2；
- OTel GenAI development 字段直接固化为数据库契约；
- 完整外部 MCP Server / broker / tool market；
- A2A / Agent Card / 跨 Agent 互操作；
- GraphRAG、知识图谱、reranker、多向量库并行；
- virtual threads / structured concurrency 压测分支；
- NIM / HPC / Slurm / BCM 专项插件继续作为二期。

下一两个里程碑建议：

- M5.28：Security + Resilience + durable audit + CI hard gate，把“可以安全执行和可追责”继续做硬。
- M5.29：RAG + persistent Memory + read-only MCP + Agent eval，把“会学习、会引用证据、会被评测”接入一期顶级 Agent Core。

学习重点：顶级 Agent 的技术先进性最终体现在“闭环能力”：能安全执行、能解释原因、能追踪证据、能评测回归、能恢复现场。框架版本只是入口，工程闭环才是主体。

## 学习重点

顶级 Agent 的技术先进性不是“用了最新版本号”这么浅。真正先进的是：

- 任何升级都可验证；
- 任何执行都可审计；
- 任何失败都可恢复；
- 任何能力都能解释它为什么安全；
- 任何新技术都服务于 Agent 的可靠性、可控性和学习价值。

## M5.30-2 Update - Admin Audit Query Read Model

M5.30-2 adds the first admin-only redacted audit query read model:

- `AgentAuditQueryService` is the replaceable read boundary for audit lookup.
- `AgentAuditQueryEvent` and `AgentAuditQueryResponse` are the redacted DTO contract.
- Current lookup supports `auditId` and `traceId` against the in-memory ring buffer.
- `/api/agent/observability/audit/index`, `/api/agent/observability/audit/id/{auditId}`, and `/api/agent/observability/audit/trace/{traceId}` are protected by observability admin URL rules and method-level `@PreAuthorize`.
- Query responses intentionally omit raw principal, organization, conversation, endpoint strings, reason text, and parameter values.

Next audit storage upgrades should replace the in-memory query backend with JSONL scan, PostgreSQL/search index, retention metadata, export controls, and frontend replay timeline DTOs.

## M5.31-1 Update - JSONL Durable Audit Query

M5.31-1 delivers the next audit storage upgrade without changing the public admin API:

- `JsonlAgentAuditQueryService` reads the redacted durable JSONL evidence stream.
- `InMemoryAgentAuditRecorder` remains the primary `AgentAuditQueryService` facade and automatically prefers JSONL lookup when durable JSONL is enabled and available.
- `auditId` lookup can now return the multi-phase evidence chain for a high-risk action: `PRE_EXECUTION/PREPARED` plus `FINAL`.
- `traceId` lookup can recover durable evidence newest-first, which is the first backend step toward frontend replay timeline and Agent eval reports.
- Query metadata now distinguishes `backend=jsonl-reverse-scan` from `backend=in-memory-ring-buffer`.

This is still a bounded first-stage implementation. The next advanced storage steps are retention/export policy, database/search indexing, and replay timeline DTOs. The important Phase 1 lesson is that top-tier Agent evidence must be recoverable after process restart; otherwise audit, replay, and red-team evaluation are too dependent on volatile memory.

## M5.31-2 Update - Durable Audit Lifecycle Metadata

M5.31-2 adds the first durable audit lifecycle contract:

- Retention metadata: configured retention days and max file bytes.
- Export metadata: export enabled flag, redacted format, admin-only requirement, and explicit `downloadEndpointImplemented=false`.
- Query metadata: configurable but bounded scan/result limits.

This is intentionally metadata-only. A top-tier Agent should not add an audit download endpoint before it has a clear redacted-only export contract, operator-visible retention policy, and tests proving no raw principal, endpoint, reason, or parameter values are exposed. Future work should implement retention enforcement, export jobs, and database/search indexing behind the same admin-only and redacted-only contract.

## M5.33-1 Update - Deterministic Agent Eval Foundation

M5.33-1 moves Agent eval from roadmap language into the backend mainline:

- `AgentEvalReportService` evaluates redacted replay evidence without LLM calls or external network calls.
- `/api/agent/observability/eval/trace/{traceId}` is protected by observability admin URL rules and method-level `@PreAuthorize`.
- The eval report checks replay order, trace consistency, phase sequence, impossible execution/result combinations, high-risk prewrite evidence, high-risk confirmation markers, outcome health, truncation, and privacy.
- The report includes `deterministic=true`, `llmUsed=false`, and `externalCalls=false`, making it suitable as a future CI/release-gate input.

2026-06-10 M5.81 official-source refresh:

- Spring AI 1.1.7 remains the current stable Spring AI mainline for this project; Spring AI 2.0.0-RC2 remains compatibility-matrix work. Official Spring AI docs confirm Tool Calling, MCP, Vector Store/RAG, observability, and evaluator APIs as first-class directions.
- Spring Boot official docs list 4.0.6 and 3.5.14 as stable documentation lines. This project stays on the already verified Spring Boot 3.5.14 mainline until the Boot 4 / Framework 7 compatibility matrix passes.
- MCP specification work continues to evolve quickly; M5.81 tracks the official 2025-11-25 snapshot. Phase 1 should expose read-only manifest/schema first; future `tools/call` must still pass through `SafeToolExecutor`, HITL, trace, audit, and eval.
- OpenTelemetry GenAI/agent semantic conventions are still marked Development, so kube-agent should keep stable internal `atlas.agent.*` attributes and map to GenAI semconv through a compatibility layer rather than freezing experimental names into storage.
- OpenAI's current Agent/Evals guidance reinforces the same architecture direction: tools, guardrails, memory/vector stores, orchestration, trace grading, and eval workflows are core Agent capabilities. kube-agent implements the same ideas in a Java/Spring control plane bound to kube-manager safety requirements.

References:

- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Spring AI reference documentation](https://docs.spring.io/spring-ai/reference/)
- [Spring Boot documentation index](https://docs.spring.io/spring-boot/index.html)
- [Model Context Protocol specification](https://modelcontextprotocol.io/specification)
- [OpenTelemetry GenAI semantic conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/)
- [OpenAI Agents guide](https://platform.openai.com/docs/guides/agents)
- [OpenAI trace grading](https://platform.openai.com/docs/guides/trace-grading)

## M5.34-1 Update - Eval Suite As Release-Gate Input

M5.34-1 turns eval into a suite-level gate foundation:

- `AgentEvalSuiteRequest` accepts trace ids plus gate policy (`minimumScore`, `failOnWarnings`).
- `AgentEvalSuiteResponse` returns aggregate pass/fail/warning counts, failed/warning trace ids, minimum score, average score, privacy proof, and embedded per-trace reports.
- The suite reuses deterministic single-trace eval reports instead of inventing a second scoring path.

This matches the current advanced Agent engineering direction: eval should be part of the control plane, not an offline afterthought. The next steps are to add curated golden cases, must-block red-team suites, CI JSON export, and eventually frontend replay/eval workbench integration.

## M5.34-2 Update - Eval Suite Gate Hardening

M5.34-2 tightens the suite foundation into a safer gate contract:

- Suite defaults and caps now live in `AgentEvalReportService` so HTTP, future CI jobs, and internal callers share the same policy.
- Per-trace replay `limit` is bounded before evaluation, and requested `minimumScore` is clamped to `0..100`.
- A suite evaluates at most 50 deduplicated trace ids. If the request contains more, the response records `caseLimitExceeded=true`, exposes `skippedTraceIds`, and fails the gate.
- Strict `failOnWarnings=true` remains the default; warning-only suites pass only when the caller explicitly relaxes the policy.

This closes a common release-gate failure mode: partial evaluation must not look like a full PASS. The next eval work should add named golden suites, must-block red-team traces, machine-readable CI export, and frontend replay/eval workbench integration.

## M5.35-1 Update - Named Eval Suite Catalog

M5.35-1 implements the next eval roadmap item: named suites are now discoverable and runnable through admin-only APIs.

- `GET /api/agent/observability/eval/suites` returns a deterministic catalog of built-in suite definitions.
- `POST /api/agent/observability/eval/suites/{suiteId}/run` runs a named suite with caller-provided trace ids.
- Built-in suite ids are `core-safety-smoke`, `high-risk-prewrite`, `redaction-regression`, and `release-gate-strict`.
- Named runs use suite defaults when request fields are omitted, then delegate to the existing hardened suite gate.
- The catalog and run contracts state `redactedOnly=true`, `deterministic=true`, `llmUsed=false`, `externalCalls=false`, `toolExecution=false`, and `kubeManagerCalls=false`.

Technology judgment: this is the right way to introduce advanced Agent eval into the Java/Spring mainline. It makes evaluation a typed, testable control-plane object without introducing a second LLM evaluator, a real kube-manager call, or a Tool execution path. The next advanced steps are CI JSON export, persisted golden/red-team trace sets, Vue replay/eval workbench integration, and release workflow wiring.

## M5.36-1 Update - Compact CI Gate Artifact

M5.36-1 delivers the first machine-readable CI/release gate artifact for named eval suites:

- `POST /api/agent/observability/eval/suites/{suiteId}/gate` returns `AgentEvalSuiteGateArtifact`.
- The artifact includes stable pass/fail fields, score fields, case coverage fields, warning/failure counters, trace anchors, gate policy, and privacy proof.
- It intentionally excludes embedded per-trace reports and replay timelines.
- The existing `/run` endpoint remains the richer human/admin diagnostic path.

Technology judgment: CI should consume compact contracts, not frontend-sized diagnostic payloads. This keeps release workflows fast and auditable while preserving the ability to drill down by trace id when a gate fails. The next advanced steps are persisted golden/red-team trace sets, CI job wiring, Vue replay/eval workbench integration, and signed release decision metadata.

## M5.37-1 Update - Versioned Eval Trace Set Catalog

M5.37-1 implements the next eval roadmap item: curated golden/red-team trace sets are now versioned control-plane objects.

- `GET /api/agent/observability/eval/trace-sets` returns the trace set catalog.
- `POST /api/agent/observability/eval/trace-sets/{traceSetId}/gate` runs the trace set's attached named suite and returns `AgentEvalTraceSetGateArtifact`.
- Catalog source is `classpath:observability/eval-trace-sets.json`.
- Built-in trace set ids are `phase1-core-golden`, `phase1-redaction-regression`, `phase1-high-risk-prewrite`, and `phase1-red-team-safety`.
- Trace sets start with empty `traceIds` intentionally; missing curated evidence fails closed instead of passing as an empty success.
- Request-provided trace ids are ignored by trace-set gates, so ad-hoc local evidence cannot silently replace curated catalog evidence.
- The catalog/gate contracts state `redactedOnly=true`, `deterministic=true`, `llmUsed=false`, `externalCalls=false`, `toolExecution=false`, and `kubeManagerCalls=false`.

Technology judgment: this completes the first typed eval chain for Phase 1: suite = quality standard, trace set = curated evidence source, gate artifact = machine-readable release decision. The next advanced steps are CI workflow wiring around trace-set gates, frontend replay/eval workbench integration, and persisted capture jobs that populate real redacted trace ids.

## M5.38-1 Update - Trace-Set Gate Bundle CI Artifact

M5.38-1 wires the trace-set gate catalog into backend quality evidence:

- `POST /api/agent/observability/eval/trace-sets/gate-bundle` returns `AgentEvalTraceSetGateBundleArtifact`.
- `AgentEvalTraceSetGateBundleArtifactTest` writes `target/agent-eval/trace-set-gate-bundle.json`.
- `.github/workflows/backend-quality.yml` now uploads `target/agent-eval/` with the backend quality artifacts.
- A source-level CI workflow contract test protects the artifact path.
- The bundle keeps `artifactOnly=true`, `embeddedReports=false`, `embeddedReplay=false`, `redactedOnly=true`, `llmUsed=false`, `externalCalls=false`, `toolExecution=false`, and `kubeManagerCalls=false`.
- The bundle sets `ciBlockingEnabled=false` until curated trace sets contain real persisted redacted replay captures.

Technology judgment: this is the correct intermediate step before strict CI blocking. A top-tier Agent should publish eval evidence early, but it should not convert empty trace-set catalogs into misleading release decisions. The next step is to populate curated trace IDs through safe capture jobs, then flip the CI policy from evidence-only to blocking once the evidence set is real and reviewed.

## M5.39-1 Update - Trace-Set Curation Review Artifact

M5.39-1 adds the missing promotion protocol between candidate trace evidence and versioned release evidence:

- `POST /api/agent/observability/eval/trace-sets/{traceSetId}/curation-review` evaluates caller-provided candidate trace IDs against the trace set's attached named suite.
- `AgentEvalTraceSetCurationReviewArtifact` records the candidate gate, review verdict, `readyForCatalogReview`, review-only policy, and privacy proof.
- Candidate trace IDs are filtered to W3C-compatible anchors before evaluation, so arbitrary request text is not echoed as release evidence.
- The endpoint never mutates `observability/eval-trace-sets.json`; promotion still requires human review and a Git catalog patch.

Technology judgment: this is how advanced Agent eval moves safely toward blocking CI. The system can now prove that candidate traces are good enough for review, while still preventing ad-hoc runtime requests from silently becoming release evidence. The next step is a persisted redacted capture workflow that helps operators discover candidate trace IDs, then a reviewed catalog patch that lets M5.38's bundle become genuinely release-blocking.

## M5.40-1 Update - Trace-Set Candidate Discovery

M5.40-1 adds the candidate discovery step before M5.39 curation review:

- `AgentAuditQueryService#recentEvents(...)` exposes a bounded redacted recent-event read model across in-memory and JSONL backends.
- `GET /api/agent/observability/eval/trace-sets/{traceSetId}/candidates` groups recent audit events by W3C-compatible trace ID.
- Candidate summaries contain only trace anchors, counts, closed-vocabulary operation/outcome data, evidence tags, recommendation state, and privacy proof.
- Recommendation logic is trace-set aware: golden read traces, redaction traces, high-risk prewrite traces, and red-team safety traces are surfaced differently.

Technology judgment: this gives eval operations a real evidence workflow without granting new authority. Discovery is read-only and advisory, curation review is deterministic and review-only, and catalog promotion still requires a Git-reviewed patch. This separation is the mature Agent pattern for moving from observability data to release-blocking evidence.

## M5.48-1 Update - Eval Workbench Gate Bundle Summary

M5.48-1 adds the page-level gate bundle summary contract for the future `vue-kube-manager` eval workbench:

- `GET /api/agent/observability/eval/workbench/gate-bundle-summary` returns `AgentEvalWorkbenchGateBundleSummaryResponse`.
- `AgentEvalWorkbenchGateBundleSummaryService` wraps the current catalog gate bundle without accepting caller-provided trace IDs.
- The response adds bundle summary, trace-set gate rows, CI artifact metadata, blocker summary, next actions, endpoint templates, workbench policy, and privacy proof.
- The capability manifest now includes `workbench-gate-bundle-summary`, and the recommended workflow points Vue to this release-gate summary after catalog patch review.
- Trace-set detail, promotion workflow, and catalog patch review responses now expose `workbenchGateBundleSummary`.
- The contract remains admin-only, read-only, summary-only, no request trace-id override, no catalog mutation, no runtime catalog write, no CI blocking enablement, no Tool execution, no kube-manager call, no LLM/external call, and no embedded replay/report payloads.

Technology judgment: top-tier Agent release governance needs a UI layer that explains CI evidence without becoming CI authority. `gate-bundle-summary` makes release readiness inspectable by humans while preserving Git review and reviewed trace evidence as the only path toward future blocking gates.

## M5.47-1 Update - Eval Workbench Catalog Patch Review Model

M5.47-1 adds the page-level catalog patch review contract for the future `vue-kube-manager` eval workbench:

- `POST /api/agent/observability/eval/workbench/trace-sets/{traceSetId}/catalog-patch-review` returns `AgentEvalWorkbenchCatalogPatchReviewResponse`.
- `AgentEvalWorkbenchCatalogPatchReviewService` wraps the existing review-only catalog patch proposal artifact instead of introducing runtime catalog mutation.
- The response adds sanitized patch operations, trace delta, candidate gate summary, review checklist, next actions, endpoint templates, workbench policy, and privacy proof.
- The capability manifest now includes `workbench-catalog-patch-review`, and the recommended workflow points Vue to this Git-review contract after workbench promotion workflow.
- Trace-set detail now exposes `workbenchCatalogPatchReview` while preserving lower-level raw backend endpoints.
- The contract remains admin-only, no catalog mutation, no JSON Patch application, no runtime catalog write, no Tool execution, no kube-manager call, no LLM/external call, and no embedded replay/report payloads.

Technology judgment: a top-tier Agent eval workbench needs both evidence generation and evidence review contracts. `catalog-patch-review` turns the patch proposal into a human-reviewable UI model without granting runtime write authority, which keeps Agent evaluation, frontend UX, CI evidence, and Git governance aligned.

## M5.46-1 Update - Eval Workbench Promotion Workflow Result Model

M5.46-1 adds the page-level promotion workflow result contract for the future `vue-kube-manager` eval workbench:

- `POST /api/agent/observability/eval/workbench/trace-sets/{traceSetId}/promotion-workflow` returns `AgentEvalWorkbenchPromotionWorkflowResponse`.
- `AgentEvalWorkbenchPromotionWorkflowService` wraps the existing redacted promotion workflow artifact instead of inventing a second evidence path.
- The response adds UI steps, patch summary, candidate gate summary, next actions, endpoint templates, workbench policy, and privacy proof.
- The capability manifest now includes `workbench-promotion-workflow`, and the recommended workflow points Vue to this page-ready contract after trace-set detail.
- Trace-set detail now exposes `workbenchPromotionWorkflow` while preserving the raw backend `promotionWorkflow` endpoint.
- The contract remains admin-only, no catalog mutation, no runtime catalog write, no Tool execution, no kube-manager call, no LLM/external call, and no embedded replay/report payloads.

Technology judgment: a top-tier Agent workbench should not ask the frontend to reconstruct release governance from raw artifacts. Backend-owned page contracts keep evidence semantics, security policy, and next-action guidance consistent across API, UI, CI, and recovery documentation while preserving human Git review as the only catalog promotion authority.

## M5.45-1 Update - Eval Workbench Trace-Set Detail Read Model

M5.45-1 adds the trace-set detail layer for the future `vue-kube-manager` eval workbench:

- `GET /api/agent/observability/eval/workbench/trace-sets/{traceSetId}` returns `AgentEvalWorkbenchTraceSetDetailResponse`.
- The detail response includes one UI row, curated trace anchors, evidence requirements, compact gate state, promotion checklist, next actions, endpoint templates, policy proof, and privacy proof.
- The capability manifest now includes `workbench-trace-set-detail`, so Vue can move from overview to detail through backend-discovered contracts.
- Detail stays read-only: no candidate discovery execution, no raw audit query, no Tool execution, no kube-manager call, no LLM/external call, and no catalog mutation.
- Replay timeline and per-trace eval reports remain explicit drill-down payloads instead of being embedded into the detail page.

Technology judgment: this closes a common frontend governance gap. A workbench needs a detail layer between overview and workflow execution, otherwise page code starts reconstructing release semantics from multiple APIs. Keeping detail as a typed read model preserves backend ownership of evidence rules while still giving operators an ergonomic UI path.

## M5.44-1 Update - Eval Workbench Overview Read Model

M5.44-1 adds the frontend landing read model for the future `vue-kube-manager` eval workbench:

- `GET /api/agent/observability/eval/workbench/overview` returns `AgentEvalWorkbenchOverviewResponse`.
- `AgentEvalWorkbenchTraceSetView` converts each trace set into a UI-ready row with status, next action, workflow paths, replay/eval drill-down templates, and policy/privacy proof.
- The overview composes capability metadata, trace-set catalog rows, and compact gate-bundle state while keeping replay timelines and per-trace reports out of the landing payload.
- The capability manifest now includes `workbench-overview`, so Vue can discover the overview contract before rendering it.
- The endpoint stays admin-only, read-only, no raw audit query, no candidate discovery, no Tool execution, no kube-manager call, no LLM, no external call, and no catalog mutation.

Technology judgment: top-tier Agent frontends need contract-driven read models, not ad-hoc UI reconstruction of backend release state. The overview layer is the right bridge between backend governance artifacts and a human operator workbench because it shows what needs attention without silently granting promotion or execution authority.

## M5.43-1 Update - Eval Workbench Capability Manifest

M5.43-1 adds a self-describing backend manifest for future `vue-kube-manager` eval workbench screens:

- `GET /api/agent/observability/eval/workbench/capabilities` returns `AgentEvalWorkbenchCapabilitiesResponse`.
- Capabilities include trace-set catalog, candidate discovery, curation review, catalog patch proposal, promotion workflow, gate bundle, replay timeline, and trace eval report.
- The manifest exposes response schema versions, endpoint templates, recommended workflow order, and safety flags.
- It stays metadata-only: no audit query, no eval run, no Tool execution, no kube-manager call, no catalog mutation.

Technology judgment: advanced Agent frontends should be contract-driven. A capability manifest lets the Vue workbench evolve with backend schema and governance rules instead of hard-coding hidden workflow assumptions.

## M5.42-1 Update - Promotion Workflow Artifact

M5.42-1 adds the backend contract a future Vue eval workbench needs for evidence promotion:

- `POST /api/agent/observability/eval/trace-sets/{traceSetId}/promotion-workflow` returns `AgentEvalTraceSetPromotionWorkflowArtifact`.
- The service composes candidate discovery, recommended trace selection, curation review, and catalog patch proposal.
- It remains `workflowOnly=true`, `artifactOnly=true`, `catalogMutationAllowed=false`, `runtimeCatalogWrite=false`, `redactedOnly=true`, `llmUsed=false`, `externalCalls=false`, `toolExecution=false`, and `kubeManagerCalls=false`.
- The workflow intentionally limits selected recommended candidates so operator review does not silently become unbounded release evidence.

Technology judgment: frontend eval workbenches should render typed backend evidence workflows, not reimplement release-state logic. This keeps Agent governance consistent across CLI, admin APIs, CI, and future Vue screens.

## M5.41-1 Update - Catalog Patch Proposal Artifact

M5.41-1 closes the next eval release-governance gap: reviewed candidates can now be converted into a typed JSON Patch proposal without mutating the catalog at runtime.

- `POST /api/agent/observability/eval/trace-sets/{traceSetId}/catalog-patch-proposal` returns `AgentEvalTraceSetCatalogPatchProposalArtifact`.
- The artifact reuses curation review and becomes `READY_FOR_GIT_REVIEW` only when the candidate suite gate passes and new trace IDs would be added.
- JSON Patch targets `src/main/resources/observability/eval-trace-sets.json` by JSON Pointer, for example `/0/traceIds`.
- The contract stays `artifactOnly=true`, `reviewOnly=true`, `catalogMutationAllowed=false`, `runtimeCatalogWrite=false`, `redactedOnly=true`, `llmUsed=false`, `externalCalls=false`, `toolExecution=false`, and `kubeManagerCalls=false`.

Technology judgment: top-tier Agent release evidence should flow through typed promotion artifacts instead of runtime mutation. This is the safer bridge from observability data to Git-reviewed CI evidence. The next advanced steps are Vue eval workbench integration, catalog patch preview UX, real curated trace population from safe local captures, and then flipping the gate bundle from evidence-only to blocking once the catalog has reviewed real evidence.

## 2026-06-10 M5.81 Latest Official Technology Check

The Phase 1 mainline continues to follow "stable verified core + compatibility matrix":

- Spring Boot official docs list stable `4.0.6` and `3.5.14`; this repository remains on verified `3.5.14` while tracking Boot 4 migration under tests.
- Spring AI official docs list stable `1.1.7` and preview `2.0.0-RC2`; this repository remains on verified `1.1.7` while tracking Spring AI 2 under compatibility work.
- MCP latest tracked specification is `2025-11-25`; Phase 1 should keep MCP work behind safe manifest/schema adapters until authorization, consent, and Tool safety contracts are complete.
- OpenTelemetry semantic conventions are at `1.41.1` and include Generative AI / MCP areas; the mainline keeps stable `atlas.agent.*` attributes and isolates experimental semconv attributes until contract tests prove they are safe.

Rule: a technology is "introduced" into this project only after it has a typed contract, security boundary, tests, documentation, recovery memory, and CI/release evidence. Version-chasing without those gates is not top-tier engineering.
